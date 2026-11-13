package com.jochana.driverule.core.data.local.profile

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverProfileDao {
    // One query keeps the profile, selection, and first-run guide snapshot consistent.
    @Query(
        """
        SELECT p.id, p.name, selection.profile_id AS selected_profile_id,
               CASE WHEN COALESCE(onboarding.permission_guide_completed, 0) = 0
                    THEN 1 ELSE 0 END AS permission_guide_pending
        FROM driver_profile AS p
        LEFT JOIN local_profile_selection AS selection ON selection.singleton_id = 1
        LEFT JOIN local_onboarding_state AS onboarding ON onboarding.singleton_id = 1
        ORDER BY p.id ASC
        """,
    )
    fun observeSnapshotRows(): Flow<List<DriverProfileSnapshotRow>>

    @Query("SELECT COUNT(*) FROM driver_profile")
    suspend fun countProfiles(): Int

    @Query("SELECT * FROM driver_profile WHERE id = :id")
    suspend fun findProfile(id: Long): DriverProfileEntity?

    @Query(
        """
        SELECT p.* FROM driver_profile AS p
        INNER JOIN local_profile_selection AS selection ON selection.profile_id = p.id
        WHERE selection.singleton_id = 1
        """,
    )
    suspend fun findSelectedProfile(): DriverProfileEntity?

    @Query("SELECT * FROM local_onboarding_state WHERE singleton_id = 1")
    suspend fun findOnboardingState(): LocalOnboardingStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOnboardingState(state: LocalOnboardingStateEntity)

    @Insert
    suspend fun insertProfile(profile: DriverProfileEntity): Long

    @Query("UPDATE driver_profile SET last_selected_at = :selectedAt WHERE id = :id")
    suspend fun updateLastSelectedAt(id: Long, selectedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSelection(selection: LocalProfileSelectionEntity)
}

data class DriverProfileSnapshotRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "selected_profile_id")
    val selectedProfileId: Long?,
    @ColumnInfo(name = "permission_guide_pending")
    val permissionGuidePending: Boolean,
)
