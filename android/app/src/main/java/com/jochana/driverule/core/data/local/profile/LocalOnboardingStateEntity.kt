package com.jochana.driverule.core.data.local.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** App-level guide progress. Permission grants themselves remain owned by Android. */
@Entity(tableName = "local_onboarding_state")
data class LocalOnboardingStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 1,
    @ColumnInfo(name = "permission_guide_completed")
    val permissionGuideCompleted: Boolean,
)
