package com.jochana.driverule.core.data.local.profile

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.core.data.repository.RoomDriverProfileRepository
import com.jochana.driverule.domain.profile.ProfileMutationResult
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverProfileMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DriverProfileDatabase::class.java,
    )

    @Test
    fun existingVersionOneProfilesKeepTheirSelectionAndSkipNewGuide() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "profile-v1-existing-${UUID.randomUUID()}.db"
        try {
            migrationHelper.createDatabase(databaseName, 1).apply {
                execSQL("INSERT INTO driver_profile(id, name, last_selected_at) VALUES (7, '기존 운전자', 1234)")
                execSQL("INSERT INTO local_profile_selection(singleton_id, profile_id) VALUES (1, 7)")
                close()
            }
            migrationHelper.runMigrationsAndValidate(
                databaseName, 2, true, DriverProfileDatabase.MIGRATION_1_2,
            ).close()

            val database = Room.databaseBuilder(context, DriverProfileDatabase::class.java, databaseName)
                .addMigrations(DriverProfileDatabase.MIGRATION_1_2).build()
            try {
                val repository = RoomDriverProfileRepository(database)
                val restored = repository.observeProfiles().first()
                assertEquals(7L, restored.activeProfileId)
                assertEquals("기존 운전자", restored.activeProfile?.name)
                assertFalse(restored.permissionGuidePending)
                assertEquals(1234L, database.driverProfileDao().findProfile(7)?.lastSelectedAt)
                assertTrue(repository.createProfile("추가 운전자") is ProfileMutationResult.Success)
                assertFalse(repository.observeProfiles().first().permissionGuidePending)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun emptyVersionOneDatabaseStillShowsGuideAfterItsFirstRegistration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "profile-v1-empty-${UUID.randomUUID()}.db"
        try {
            migrationHelper.createDatabase(databaseName, 1).close()
            migrationHelper.runMigrationsAndValidate(
                databaseName, 2, true, DriverProfileDatabase.MIGRATION_1_2,
            ).close()

            val database = Room.databaseBuilder(context, DriverProfileDatabase::class.java, databaseName)
                .addMigrations(DriverProfileDatabase.MIGRATION_1_2).build()
            try {
                val repository = RoomDriverProfileRepository(database)
                val empty = repository.observeProfiles().first()
                assertTrue(empty.profiles.isEmpty())
                assertFalse(empty.permissionGuidePending)
                assertTrue(repository.createProfile("운전자 1") is ProfileMutationResult.Success)
                assertTrue(repository.observeProfiles().first().permissionGuidePending)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
