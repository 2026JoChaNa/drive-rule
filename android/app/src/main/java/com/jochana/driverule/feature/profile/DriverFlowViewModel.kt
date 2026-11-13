package com.jochana.driverule.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.profile.ProfileMutationResult
import com.jochana.driverule.domain.profile.ProfileNameError
import com.jochana.driverule.domain.profile.ProfileNamePolicy
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DriverFlowViewModel(
    private val profiles: DriverProfileRepository,
    private val ux: UxRestrictionProvider,
    private val appearance: ThemePreferenceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverFlowUiState())
    val uiState = mutableState.asStateFlow()
    private var profileJob: Job? = null
    private var autoNameSpeechPending = true

    init {
        viewModelScope.launch {
            ux.state.collect { state ->
                mutableState.update { it.copy(vehicleUxState = state) }
            }
        }
        viewModelScope.launch {
            appearance.themeMode.catch {
                mutableState.update { it.copy(themeError = true) }
            }.collect { mode -> mutableState.update { it.copy(themeMode = mode, themeError = false) } }
        }
        retryProfiles()
    }

    fun retryProfiles() {
        profileJob?.cancel()
        mutableState.update { it.copy(loading = true, storageError = false) }
        profileJob = viewModelScope.launch {
            profiles.observeProfiles().catch {
                mutableState.update { it.copy(loading = false, storageError = true) }
            }.collect { snapshot ->
                mutableState.update {
                    it.copy(loading = false, storageError = false, profiles = snapshot.profiles,
                        activeProfile = snapshot.activeProfile,
                        permissionGuidePending = snapshot.permissionGuidePending)
                }
            }
        }
    }

    fun changeName(name: String) {
        if (!canManageProfiles() || uiState.value.saving) return
        mutableState.update { it.copy(name = name, error = null) }
    }

    /** One automatic start per name-entry visit within this ViewModel's lifetime. */
    fun consumeAutomaticNameSpeechStart(): Boolean {
        val state = uiState.value
        if (!autoNameSpeechPending || !canManageProfiles() || state.saving ||
            (state.activeProfile != null && !state.addingProfile)) return false
        autoNameSpeechPending = false
        // An existing draft, even whitespace, means the user has already started typing.
        return state.name.isEmpty()
    }

    fun addProfile() {
        if (!canManageProfiles() || uiState.value.saving || uiState.value.addingProfile) return
        if (uiState.value.profiles.size >= ProfileNamePolicy.MAX_PROFILES) {
            mutableState.update { it.copy(error = ProfileInputError.LimitReached) }
        } else {
            autoNameSpeechPending = true
            mutableState.update { it.copy(addingProfile = true, name = "", error = null) }
        }
    }

    fun cancelAdding() {
        if (!uiState.value.saving) {
            autoNameSpeechPending = false
            mutableState.update { it.copy(addingProfile = false, name = "", error = null) }
        }
    }

    fun saveName() = mutate { profiles.createProfile(uiState.value.name) }

    fun skip(defaultName: String) {
        if (uiState.value.activeProfile == null && !uiState.value.addingProfile) {
            mutate { profiles.createProfile(defaultName) }
        }
    }

    fun selectProfile(id: Long) = mutate { profiles.selectProfile(id) }

    fun completePermissionGuide() {
        if (!uiState.value.permissionGuidePending) return
        mutate(completingPermissionGuide = true) { profiles.completePermissionGuide() }
    }

    fun setTheme(mode: ThemeMode) {
        if (!canInteract()) return
        viewModelScope.launch {
            try {
                appearance.setThemeMode(mode)
                mutableState.update { it.copy(themeMode = mode, themeError = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(themeError = true) }
            }
        }
    }

    private fun canInteract(): Boolean = uiState.value.interactionAllowed &&
        ux.state.value == VehicleUxState.Unrestricted

    private fun canManageProfiles(): Boolean = canInteract() && !uiState.value.permissionGuidePending

    private fun mutate(
        completingPermissionGuide: Boolean = false,
        operation: suspend () -> ProfileMutationResult,
    ) {
        if (!canInteract() || uiState.value.saving) return
        if (!completingPermissionGuide && uiState.value.permissionGuidePending) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = operation()
            mutableState.update { state ->
                when (result) {
                    is ProfileMutationResult.Success -> state.copy(
                        saving = false, addingProfile = false, name = "", error = null,
                        permissionGuidePending = if (completingPermissionGuide) false
                            else state.permissionGuidePending,
                    )
                    is ProfileMutationResult.InvalidName -> state.copy(saving = false, error =
                        if (result.error == ProfileNameError.Empty) ProfileInputError.Empty else ProfileInputError.TooLong)
                    ProfileMutationResult.LimitReached -> state.copy(saving = false, error = ProfileInputError.LimitReached)
                    ProfileMutationResult.ProfileNotFound -> state.copy(saving = false, error = ProfileInputError.NotFound)
                    ProfileMutationResult.PermissionGuideRequired -> state.copy(
                        saving = false, addingProfile = false, permissionGuidePending = true, error = null,
                    )
                    ProfileMutationResult.StorageError -> state.copy(saving = false, error = ProfileInputError.Storage)
                }
            }
        }
    }
}
