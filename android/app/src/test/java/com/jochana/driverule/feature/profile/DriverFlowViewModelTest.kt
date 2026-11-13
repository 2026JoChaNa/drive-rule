package com.jochana.driverule.feature.profile

import androidx.lifecycle.ViewModelStore
import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.profile.ProfileMutationResult
import com.jochana.driverule.domain.profile.ProfileNameError
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverFlowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `automatic name speech starts once after loading and stays consumed on UX recovery`() = runTest {
        Fixture().use { fixture ->
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            runCurrent()
            assertTrue(fixture.viewModel.consumeAutomaticNameSpeechStart())
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())

            fixture.ux.current.value = VehicleUxState.Restricted
            runCurrent()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            fixture.ux.current.value = VehicleUxState.Unrestricted
            runCurrent()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
        }
    }

    @Test
    fun `unknown and restricted states do not consume automatic name speech opportunity`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            listOf(VehicleUxState.Unknown, VehicleUxState.Restricted).forEach { restricted ->
                fixture.ux.current.value = restricted
                // Check the provider directly before its collector updates UiState.
                assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
                runCurrent()
                assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            }
            fixture.ux.current.value = VehicleUxState.Unrestricted
            runCurrent()
            assertTrue(fixture.viewModel.consumeAutomaticNameSpeechStart())
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
        }
    }

    @Test
    fun `home never starts name speech and each new add profile visit starts at most once`() = runTest {
        Fixture(ProfileSnapshot(listOf(drivers.first()), drivers.first().id)).use { fixture ->
            runCurrent()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            fixture.viewModel.addProfile()
            assertTrue(fixture.viewModel.consumeAutomaticNameSpeechStart())
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            fixture.viewModel.addProfile()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())

            fixture.viewModel.cancelAdding()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            fixture.viewModel.addProfile()
            assertTrue(fixture.viewModel.consumeAutomaticNameSpeechStart())
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
        }
    }

    @Test
    fun `permission guide never starts automatic name speech`() = runTest {
        val pending = ProfileSnapshot(listOf(drivers.first()), drivers.first().id, permissionGuidePending = true)
        Fixture(pending).use { fixture ->
            runCurrent()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
        }
    }

    @Test
    fun `typed draft consumes automatic speech without starting even if later cleared`() = runTest {
        listOf("김하늘", " ").forEach { draft ->
            Fixture().use { fixture ->
                runCurrent()
                fixture.viewModel.changeName(draft)
                assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
                assertEquals(draft, fixture.viewModel.uiState.value.name)
                fixture.viewModel.changeName("")
                assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            }
        }
    }

    @Test
    fun `saving does not consume automatic speech before a failed registration can be retried`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            fixture.profiles.createGate = gate
            fixture.profiles.createResult = ProfileMutationResult.StorageError
            fixture.viewModel.skip("운전자 1")
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertTrue(fixture.viewModel.consumeAutomaticNameSpeechStart())
            assertFalse(fixture.viewModel.consumeAutomaticNameSpeechStart())
        }
    }

    @Test
    fun `empty name result keeps setup open and permits correction`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            fixture.profiles.createResult = ProfileMutationResult.InvalidName(ProfileNameError.Empty)
            fixture.viewModel.changeName("   ")
            fixture.viewModel.saveName()
            runCurrent()

            assertEquals(ProfileInputError.Empty, fixture.viewModel.uiState.value.error)
            assertNull(fixture.viewModel.uiState.value.activeProfile)
            assertFalse(fixture.viewModel.uiState.value.permissionGuidePending)
            assertFalse(fixture.viewModel.uiState.value.saving)

            fixture.viewModel.changeName("김하늘")
            assertNull(fixture.viewModel.uiState.value.error)
            assertEquals("김하늘", fixture.viewModel.uiState.value.name)
        }
    }

    @Test
    fun `skip creates the supplied default once and observes the selected profile`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            fixture.viewModel.skip("운전자 1")
            runCurrent()

            assertEquals(listOf("운전자 1"), fixture.profiles.createdNames)
            assertEquals("운전자 1", fixture.viewModel.uiState.value.activeProfile?.name)
            assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
            assertFalse(fixture.viewModel.uiState.value.saving)

            fixture.viewModel.skip("운전자 1")
            runCurrent()
            assertEquals(1, fixture.profiles.createdNames.size)
        }
    }

    @Test
    fun `permission guide is restored and blocks profile changes until completed`() = runTest {
        val pending = ProfileSnapshot(listOf(drivers.first()), drivers.first().id, permissionGuidePending = true)
        Fixture(pending).use { fixture ->
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
            fixture.viewModel.changeName("새 이름")
            fixture.viewModel.addProfile()
            fixture.viewModel.saveName()
            fixture.viewModel.selectProfile(drivers.first().id)
            fixture.viewModel.skip("운전자 1")
            runCurrent()

            assertTrue(fixture.profiles.createdNames.isEmpty())
            assertTrue(fixture.profiles.selectedIds.isEmpty())
            assertEquals("", fixture.viewModel.uiState.value.name)
            assertFalse(fixture.viewModel.uiState.value.addingProfile)

            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.permissionGuidePending)
            assertEquals(drivers.first(), fixture.viewModel.uiState.value.activeProfile)
            assertEquals(1, fixture.profiles.completionCalls)
            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertEquals(1, fixture.profiles.completionCalls)
        }
    }

    @Test
    fun `failed permission completion remains pending and retry is guarded against duplicate clicks`() = runTest {
        val pending = ProfileSnapshot(listOf(drivers.first()), drivers.first().id, permissionGuidePending = true)
        Fixture(pending).use { fixture ->
            runCurrent()
            fixture.profiles.completionResult = ProfileMutationResult.StorageError
            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
            assertFalse(fixture.viewModel.uiState.value.saving)
            assertEquals(ProfileInputError.Storage, fixture.viewModel.uiState.value.error)

            fixture.profiles.completionResult = null
            val gate = CompletableDeferred<Unit>()
            fixture.profiles.completionGate = gate
            fixture.viewModel.completePermissionGuide()
            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.saving)
            assertEquals(2, fixture.profiles.completionCalls)
            gate.complete(Unit)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.saving)
            assertFalse(fixture.viewModel.uiState.value.permissionGuidePending)
            assertNull(fixture.viewModel.uiState.value.error)
        }
    }

    @Test
    fun `permission completion checks live UX and remains pending through profile read retry`() = runTest {
        val pending = ProfileSnapshot(listOf(drivers.first()), drivers.first().id, permissionGuidePending = true)
        Fixture(pending).use { fixture ->
            runCurrent()
            listOf(VehicleUxState.Restricted, VehicleUxState.Unknown).forEach { restricted ->
                fixture.ux.current.value = restricted
                fixture.viewModel.completePermissionGuide()
                runCurrent()
                assertEquals(0, fixture.profiles.completionCalls)
                assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
                fixture.ux.current.value = VehicleUxState.Unrestricted
                runCurrent()
            }

            fixture.profiles.remainingReadFailures = 1
            fixture.viewModel.retryProfiles()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.storageError)
            fixture.viewModel.completePermissionGuide()
            runCurrent()
            assertEquals(0, fixture.profiles.completionCalls)

            fixture.viewModel.retryProfiles()
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.storageError)
            assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
        }
    }

    @Test
    fun `adding another profile after onboarding does not reopen permission guide`() = runTest {
        Fixture(ProfileSnapshot(listOf(drivers.first()), drivers.first().id)).use { fixture ->
            runCurrent()
            fixture.viewModel.addProfile()
            fixture.viewModel.changeName("이바다")
            fixture.viewModel.saveName()
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.permissionGuidePending)
            assertFalse(fixture.viewModel.uiState.value.addingProfile)
            assertEquals(2, fixture.viewModel.uiState.value.profiles.size)
        }
    }

    @Test
    fun `repository pending guard returns the flow to permission guide without an input error`() = runTest {
        Fixture(ProfileSnapshot(listOf(drivers.first()), drivers.first().id)).use { fixture ->
            runCurrent()
            fixture.profiles.createResult = ProfileMutationResult.PermissionGuideRequired
            fixture.viewModel.addProfile()
            fixture.viewModel.changeName("이바다")
            fixture.viewModel.saveName()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.permissionGuidePending)
            assertFalse(fixture.viewModel.uiState.value.addingProfile)
            assertFalse(fixture.viewModel.uiState.value.saving)
            assertNull(fixture.viewModel.uiState.value.error)
        }
    }

    @Test
    fun `profile limit prevents adding and handles repository limit race`() = runTest {
        Fixture(ProfileSnapshot(drivers, drivers.first().id)).use { fixture ->
            runCurrent()
            fixture.viewModel.addProfile()
            assertFalse(fixture.viewModel.uiState.value.addingProfile)
            assertEquals(ProfileInputError.LimitReached, fixture.viewModel.uiState.value.error)
            assertTrue(fixture.profiles.createdNames.isEmpty())
        }

        Fixture().use { fixture ->
            runCurrent()
            fixture.profiles.createResult = ProfileMutationResult.LimitReached
            fixture.viewModel.changeName("추가 운전자")
            fixture.viewModel.saveName()
            runCurrent()

            assertEquals(ProfileInputError.LimitReached, fixture.viewModel.uiState.value.error)
            assertFalse(fixture.viewModel.uiState.value.saving)
            assertNull(fixture.viewModel.uiState.value.activeProfile)
        }
    }

    @Test
    fun `selecting a stored driver updates the home profile from repository state`() = runTest {
        Fixture(ProfileSnapshot(drivers, drivers.first().id)).use { fixture ->
            runCurrent()
            fixture.viewModel.selectProfile(drivers[1].id)
            runCurrent()

            assertEquals(listOf(drivers[1].id), fixture.profiles.selectedIds)
            assertEquals(drivers[1], fixture.viewModel.uiState.value.activeProfile)
            assertFalse(fixture.viewModel.uiState.value.saving)
        }
    }

    @Test
    fun `pending save ignores duplicate submission and preserves the submitted name`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            fixture.profiles.createGate = gate
            fixture.viewModel.changeName("김하늘")
            fixture.viewModel.saveName()
            // These actions occur before the launched save coroutine has run.
            fixture.viewModel.changeName("다른 이름")
            fixture.viewModel.saveName()
            fixture.viewModel.skip("운전자 1")
            fixture.viewModel.selectProfile(42)
            runCurrent()

            assertTrue(fixture.viewModel.uiState.value.saving)
            assertEquals(listOf("김하늘"), fixture.profiles.createdNames)
            assertTrue(fixture.profiles.selectedIds.isEmpty())
            fixture.viewModel.cancelAdding()
            assertEquals("김하늘", fixture.viewModel.uiState.value.name)

            gate.complete(Unit)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.saving)
            assertEquals("김하늘", fixture.viewModel.uiState.value.activeProfile?.name)
        }
    }

    @Test
    fun `live unknown or restricted UX blocks input and mutations before collection catches up`() = runTest {
        listOf(VehicleUxState.Unknown, VehicleUxState.Restricted).forEach { restrictedState ->
            Fixture().use { fixture ->
                runCurrent()
                fixture.viewModel.changeName("기존 입력")
                fixture.ux.current.value = restrictedState
                assertEquals(VehicleUxState.Unrestricted, fixture.viewModel.uiState.value.vehicleUxState)

                fixture.viewModel.changeName("새 입력")
                fixture.viewModel.addProfile()
                fixture.viewModel.saveName()
                fixture.viewModel.skip("운전자 1")
                fixture.viewModel.selectProfile(42)
                fixture.viewModel.setTheme(ThemeMode.Dark)
                runCurrent()

                assertEquals("기존 입력", fixture.viewModel.uiState.value.name)
                assertFalse(fixture.viewModel.uiState.value.addingProfile)
                assertFalse(fixture.viewModel.uiState.value.interactionAllowed)
                assertTrue(fixture.profiles.createdNames.isEmpty())
                assertTrue(fixture.profiles.selectedIds.isEmpty())
                assertTrue(fixture.appearance.writtenModes.isEmpty())
            }
        }
    }

    @Test
    fun `failed profile read blocks setup until explicit retry restores stored driver`() = runTest {
        val profiles = FakeProfiles(ProfileSnapshot(drivers, drivers.last().id)).apply {
            remainingReadFailures = 1
        }
        Fixture(profiles = profiles).use { fixture ->
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.storageError)
            assertFalse(fixture.viewModel.uiState.value.loading)
            fixture.viewModel.skip("운전자 1")
            runCurrent()
            assertTrue(profiles.createdNames.isEmpty())

            fixture.viewModel.retryProfiles()
            assertTrue(fixture.viewModel.uiState.value.loading)
            runCurrent()

            assertEquals(2, profiles.observationCount)
            assertFalse(fixture.viewModel.uiState.value.storageError)
            assertFalse(fixture.viewModel.uiState.value.loading)
            assertEquals(drivers.last(), fixture.viewModel.uiState.value.activeProfile)
        }
    }

    @Test
    fun `failed theme write retains theme and a later successful choice clears error`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            fixture.appearance.writeFailure = IOException("test storage unavailable")
            fixture.viewModel.setTheme(ThemeMode.Dark)
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.themeError)
            assertEquals(ThemeMode.System, fixture.viewModel.uiState.value.themeMode)

            fixture.appearance.writeFailure = null
            fixture.viewModel.setTheme(ThemeMode.Light)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.themeError)
            assertEquals(ThemeMode.Light, fixture.viewModel.uiState.value.themeMode)
        }
    }

    @Test
    fun `theme choice recovers after the initial theme read failed`() = runTest {
        val appearance = FakeAppearance().apply { remainingReadFailures = 1 }
        Fixture(appearance = appearance).use { fixture ->
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.themeError)

            fixture.viewModel.setTheme(ThemeMode.Dark)
            runCurrent()

            assertFalse(fixture.viewModel.uiState.value.themeError)
            assertEquals(ThemeMode.Dark, fixture.viewModel.uiState.value.themeMode)
        }
    }

    @Test
    fun `cancelled theme write is not presented as a storage failure`() = runTest {
        Fixture().use { fixture ->
            runCurrent()
            fixture.appearance.writeFailure = CancellationException("test cancellation")
            fixture.viewModel.setTheme(ThemeMode.Dark)
            runCurrent()

            assertFalse(fixture.viewModel.uiState.value.themeError)
            assertEquals(ThemeMode.System, fixture.viewModel.uiState.value.themeMode)
        }
    }

    private class Fixture(
        initialSnapshot: ProfileSnapshot = ProfileSnapshot(emptyList(), null),
        val profiles: FakeProfiles = FakeProfiles(initialSnapshot),
        val appearance: FakeAppearance = FakeAppearance(),
    ) : AutoCloseable {
        val ux = FakeUx()
        val viewModel = DriverFlowViewModel(profiles, ux, appearance)
        private val store = ViewModelStore().apply { put("driverFlow", viewModel) }

        override fun close() = store.clear()
    }

    private class FakeProfiles(initialSnapshot: ProfileSnapshot) : DriverProfileRepository {
        val snapshot = MutableStateFlow(initialSnapshot)
        val createdNames = mutableListOf<String>()
        val selectedIds = mutableListOf<Long>()
        var createResult: ProfileMutationResult? = null
        var createGate: CompletableDeferred<Unit>? = null
        var remainingReadFailures = 0
        var observationCount = 0
        var completionCalls = 0
        var completionResult: ProfileMutationResult? = null
        var completionGate: CompletableDeferred<Unit>? = null

        override fun observeProfiles(): Flow<ProfileSnapshot> = flow {
            observationCount++
            if (remainingReadFailures > 0) {
                remainingReadFailures--
                throw IOException("test read failure")
            }
            emitAll(snapshot)
        }

        override suspend fun createProfile(name: String): ProfileMutationResult {
            createdNames += name
            createGate?.await()
            createResult?.let { return it }
            val nextId = (snapshot.value.profiles.maxOfOrNull { it.id } ?: 0) + 1
            val profile = DriverProfile(nextId, name)
            snapshot.value = ProfileSnapshot(
                snapshot.value.profiles + profile,
                profile.id,
                permissionGuidePending = snapshot.value.profiles.isEmpty(),
            )
            return ProfileMutationResult.Success(profile)
        }

        override suspend fun completePermissionGuide(): ProfileMutationResult {
            completionCalls++
            completionGate?.await()
            completionResult?.let { return it }
            val profile = snapshot.value.activeProfile ?: return ProfileMutationResult.ProfileNotFound
            snapshot.value = snapshot.value.copy(permissionGuidePending = false)
            return ProfileMutationResult.Success(profile)
        }

        override suspend fun selectProfile(id: Long): ProfileMutationResult {
            selectedIds += id
            val profile = snapshot.value.profiles.firstOrNull { it.id == id }
                ?: return ProfileMutationResult.ProfileNotFound
            snapshot.value = snapshot.value.copy(activeProfileId = id)
            return ProfileMutationResult.Success(profile)
        }
    }

    private class FakeUx : UxRestrictionProvider {
        val current = MutableStateFlow(VehicleUxState.Unrestricted)
        override val state: StateFlow<VehicleUxState> = current.asStateFlow()
    }

    private class FakeAppearance : ThemePreferenceRepository {
        val currentMode = MutableStateFlow(ThemeMode.System)
        val writtenModes = mutableListOf<ThemeMode>()
        var writeFailure: Exception? = null
        var remainingReadFailures = 0

        override val themeMode: Flow<ThemeMode> = flow {
            if (remainingReadFailures > 0) {
                remainingReadFailures--
                throw IOException("test theme read failure")
            }
            emitAll(currentMode)
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            writtenModes += mode
            writeFailure?.let { throw it }
            currentMode.value = mode
        }
    }

    private companion object {
        val drivers = listOf(
            DriverProfile(1, "김하늘"),
            DriverProfile(2, "이바다"),
            DriverProfile(3, "박산"),
        )
    }
}
