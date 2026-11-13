package com.jochana.driverule.domain.repository

import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.domain.profile.ProfileMutationResult
import kotlinx.coroutines.flow.Flow

interface DriverProfileRepository {
    /** Read failures propagate to the caller; they are never treated as a first launch. */
    fun observeProfiles(): Flow<ProfileSnapshot>

    /** Creates and selects a profile atomically. */
    suspend fun createProfile(name: String): ProfileMutationResult

    suspend fun selectProfile(id: Long): ProfileMutationResult

    /** Records that the first-run guide was handled, not that an OS permission was granted. */
    suspend fun completePermissionGuide(): ProfileMutationResult
}
