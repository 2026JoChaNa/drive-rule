package com.jochana.driverule.core.data.local.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Android-local selection state; it is not part of a server API contract. */
@Entity(
    tableName = "local_profile_selection",
    foreignKeys = [
        ForeignKey(
            entity = DriverProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class LocalProfileSelectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 1,
    @ColumnInfo(name = "profile_id")
    val profileId: Long,
)
