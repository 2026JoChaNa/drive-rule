package com.jochana.driverule.core.data.repository

import androidx.room.withTransaction
import com.jochana.driverule.core.data.local.profile.DriverProfileDatabase
import com.jochana.driverule.core.data.local.profile.DriverProfileEntity
import com.jochana.driverule.core.data.local.profile.LocalProfileSelectionEntity
import com.jochana.driverule.core.data.local.profile.LocalOnboardingStateEntity
import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.domain.profile.ProfileMutationResult
import com.jochana.driverule.domain.profile.ProfileNamePolicy
import com.jochana.driverule.domain.profile.ProfileNameValidation
import com.jochana.driverule.domain.repository.DriverProfileRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomDriverProfileRepository(
    private val database: DriverProfileDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : DriverProfileRepository {
    private val dao = database.driverProfileDao()

    override fun observeProfiles(): Flow<ProfileSnapshot> = dao.observeSnapshotRows()
        .map { rows ->
            ProfileSnapshot(
                profiles = rows.map { DriverProfile(id = it.id, name = it.name) },
                activeProfileId = rows.firstOrNull()?.selectedProfileId,
                permissionGuidePending = rows.firstOrNull()?.permissionGuidePending ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun createProfile(name: String): ProfileMutationResult {
        val validation = ProfileNamePolicy.validate(name)
        if (validation is ProfileNameValidation.Invalid) {
            return ProfileMutationResult.InvalidName(validation.error)
        }
        val normalizedName = (validation as ProfileNameValidation.Valid).normalizedName
        return storageMutation {
            database.withTransaction {
                val profileCount = dao.countProfiles()
                if (profileCount > 0 && dao.findOnboardingState()?.permissionGuideCompleted != true) {
                    return@withTransaction ProfileMutationResult.PermissionGuideRequired
                }
                if (profileCount >= ProfileNamePolicy.MAX_PROFILES) {
                    return@withTransaction ProfileMutationResult.LimitReached
                }
                val id = dao.insertProfile(
                    DriverProfileEntity(
                        name = normalizedName,
                        lastSelectedAt = nowEpochMillis(),
                    ),
                )
                dao.saveSelection(LocalProfileSelectionEntity(profileId = id))
                if (profileCount == 0) {
                    dao.saveOnboardingState(LocalOnboardingStateEntity(permissionGuideCompleted = false))
                }
                ProfileMutationResult.Success(DriverProfile(id, normalizedName))
            }
        }
    }

    override suspend fun selectProfile(id: Long): ProfileMutationResult = storageMutation {
        database.withTransaction {
            val profile = dao.findProfile(id)
                ?: return@withTransaction ProfileMutationResult.ProfileNotFound
            if (dao.findOnboardingState()?.permissionGuideCompleted != true) {
                return@withTransaction ProfileMutationResult.PermissionGuideRequired
            }
            dao.updateLastSelectedAt(id, nowEpochMillis())
            dao.saveSelection(LocalProfileSelectionEntity(profileId = id))
            ProfileMutationResult.Success(DriverProfile(profile.id, profile.name))
        }
    }

    override suspend fun completePermissionGuide(): ProfileMutationResult = storageMutation {
        database.withTransaction {
            val profile = dao.findSelectedProfile()
                ?: return@withTransaction ProfileMutationResult.ProfileNotFound
            dao.saveOnboardingState(LocalOnboardingStateEntity(permissionGuideCompleted = true))
            ProfileMutationResult.Success(DriverProfile(profile.id, profile.name))
        }
    }

    private suspend fun storageMutation(
        block: suspend () -> ProfileMutationResult,
    ): ProfileMutationResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Do not log the name or expose database details in user-facing errors.
        ProfileMutationResult.StorageError
    }
}
