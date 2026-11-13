package com.jochana.driverule.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jochana.driverule.core.data.local.profile.DriverProfileDatabase
import com.jochana.driverule.domain.profile.ProfileMutationResult
import com.jochana.driverule.domain.profile.ProfileNameError
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDriverProfileRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: DriverProfileDatabase
    private lateinit var repository: RoomDriverProfileRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DriverProfileDatabase::class.java).build()
        repository = RoomDriverProfileRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun invalidNameDoesNotCreateAProfile() = runBlocking {
        assertEquals(
            ProfileMutationResult.InvalidName(ProfileNameError.Empty),
            repository.createProfile("   "),
        )
        assertEquals(
            ProfileMutationResult.InvalidName(ProfileNameError.TooLong),
            repository.createProfile("가".repeat(21)),
        )
        assertTrue(repository.observeProfiles().first().profiles.isEmpty())
        assertFalse(repository.observeProfiles().first().permissionGuidePending)
    }

    @Test
    fun concurrentCreatesCannotExceedThreeProfiles() = runBlocking {
        repository.createProfile("운전자 1")
        repository.completePermissionGuide()
        val results = coroutineScope {
            (2..9).map { index ->
                async(Dispatchers.IO) { repository.createProfile("운전자 $index") }
            }.awaitAll()
        }
        assertEquals(2, results.count { it is ProfileMutationResult.Success })
        assertEquals(6, results.count { it == ProfileMutationResult.LimitReached })
        val snapshot = repository.observeProfiles().first()
        assertEquals(3, snapshot.profiles.size)
        assertNotNull(snapshot.activeProfile)
        assertFalse(snapshot.permissionGuidePending)
    }

    @Test
    fun unknownSelectionDoesNotReplaceTheCurrentDriver() = runBlocking {
        val created = repository.createProfile("  김하늘  ") as ProfileMutationResult.Success
        assertEquals("김하늘", created.profile.name)
        assertEquals(ProfileMutationResult.ProfileNotFound, repository.selectProfile(Long.MAX_VALUE))
        assertEquals(created.profile, repository.observeProfiles().first().activeProfile)
    }

    @Test
    fun selectedProfileSurvivesDatabaseReopeningAndClockRollback() = runBlocking {
        val databaseName = "profile-persistence-${UUID.randomUUID()}.db"
        var diskDatabase = Room.databaseBuilder(
            context,
            DriverProfileDatabase::class.java,
            databaseName,
        ).build()
        try {
            var currentTime = 2_000L
            var diskRepository = RoomDriverProfileRepository(diskDatabase) { currentTime }
            val first = diskRepository.createProfile("운전자 1") as ProfileMutationResult.Success
            diskRepository.completePermissionGuide()
            diskRepository.createProfile("운전자 2")
            currentTime = 1_000L
            assertEquals(first, diskRepository.selectProfile(first.profile.id))
            diskDatabase.close()

            diskDatabase = Room.databaseBuilder(
                context,
                DriverProfileDatabase::class.java,
                databaseName,
            ).build()
            diskRepository = RoomDriverProfileRepository(diskDatabase)
            val restored = diskRepository.observeProfiles().first()
            assertEquals(2, restored.profiles.size)
            assertEquals(first.profile, restored.activeProfile)
            assertFalse(restored.permissionGuidePending)
        } finally {
            diskDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun firstRegistrationAndGuideRemainPendingAcrossDatabaseReopening() = runBlocking {
        val databaseName = "permission-guide-persistence-${UUID.randomUUID()}.db"
        var diskDatabase = Room.databaseBuilder(context, DriverProfileDatabase::class.java, databaseName).build()
        try {
            var diskRepository = RoomDriverProfileRepository(diskDatabase)
            val first = diskRepository.createProfile("운전자 1") as ProfileMutationResult.Success
            assertTrue(diskRepository.observeProfiles().first().permissionGuidePending)
            diskDatabase.close()

            diskDatabase = Room.databaseBuilder(context, DriverProfileDatabase::class.java, databaseName).build()
            diskRepository = RoomDriverProfileRepository(diskDatabase)
            val restoredPending = diskRepository.observeProfiles().first()
            assertEquals(first.profile, restoredPending.activeProfile)
            assertTrue(restoredPending.permissionGuidePending)
            assertEquals(first, diskRepository.completePermissionGuide())
            assertEquals(first, diskRepository.completePermissionGuide())
            diskDatabase.close()

            diskDatabase = Room.databaseBuilder(context, DriverProfileDatabase::class.java, databaseName).build()
            diskRepository = RoomDriverProfileRepository(diskDatabase)
            assertFalse(diskRepository.observeProfiles().first().permissionGuidePending)
            assertTrue(diskRepository.createProfile("운전자 2") is ProfileMutationResult.Success)
            assertFalse(diskRepository.observeProfiles().first().permissionGuidePending)
        } finally {
            diskDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun pendingGuidePreventsAddingOrSelectingProfilesAtStorageBoundary() = runBlocking {
        val first = repository.createProfile("운전자 1") as ProfileMutationResult.Success
        assertEquals(ProfileMutationResult.PermissionGuideRequired, repository.createProfile("운전자 2"))
        assertEquals(ProfileMutationResult.PermissionGuideRequired, repository.selectProfile(first.profile.id))
        val snapshot = repository.observeProfiles().first()
        assertEquals(listOf(first.profile), snapshot.profiles)
        assertEquals(first.profile, snapshot.activeProfile)
        assertTrue(snapshot.permissionGuidePending)
    }

    @Test
    fun failureWritingGuideRollsBackTheFirstProfileAndSelection() = runBlocking {
        failGuideWrites()
        assertEquals(ProfileMutationResult.StorageError, repository.createProfile("김하늘"))
        val snapshot = repository.observeProfiles().first()
        assertTrue(snapshot.profiles.isEmpty())
        assertEquals(null, snapshot.activeProfile)
        assertFalse(snapshot.permissionGuidePending)
    }

    @Test
    fun failedCompletionPreservesPendingGuideUntilRetrySucceeds() = runBlocking {
        val first = repository.createProfile("김하늘") as ProfileMutationResult.Success
        failGuideWrites()
        assertEquals(ProfileMutationResult.StorageError, repository.completePermissionGuide())
        assertTrue(repository.observeProfiles().first().permissionGuidePending)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_guide_write")
        assertEquals(first, repository.completePermissionGuide())
        assertFalse(repository.observeProfiles().first().permissionGuidePending)
    }

    @Test
    fun completingWithoutAProfileDoesNotSkipFutureFirstRunGuide() = runBlocking {
        assertEquals(ProfileMutationResult.ProfileNotFound, repository.completePermissionGuide())
        assertTrue(repository.createProfile("김하늘") is ProfileMutationResult.Success)
        assertTrue(repository.observeProfiles().first().permissionGuidePending)
    }

    private fun failGuideWrites() {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_guide_write BEFORE INSERT ON local_onboarding_state
            BEGIN SELECT RAISE(ABORT, 'Injected guide storage failure'); END
            """.trimIndent(),
        )
    }
}
