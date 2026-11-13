package com.jochana.driverule.core.data.local.profile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DriverProfileEntity::class,
        LocalProfileSelectionEntity::class,
        LocalOnboardingStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class DriverProfileDatabase : RoomDatabase() {
    abstract fun driverProfileDao(): DriverProfileDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_onboarding_state` (
                        `singleton_id` INTEGER NOT NULL,
                        `permission_guide_completed` INTEGER NOT NULL,
                        PRIMARY KEY(`singleton_id`)
                    )
                    """.trimIndent(),
                )
                // Existing users should not repeat setup merely because the app was updated.
                db.execSQL(
                    """
                    INSERT INTO local_onboarding_state(singleton_id, permission_guide_completed)
                    SELECT 1, CASE WHEN EXISTS(SELECT 1 FROM driver_profile) THEN 1 ELSE 0 END
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): DriverProfileDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                DriverProfileDatabase::class.java,
                "drive-rule-profiles.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
