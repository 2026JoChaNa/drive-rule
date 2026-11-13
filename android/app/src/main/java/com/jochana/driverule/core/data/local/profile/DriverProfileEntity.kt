package com.jochana.driverule.core.data.local.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "driver_profile")
data class DriverProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "last_selected_at")
    val lastSelectedAt: Long?,
)
