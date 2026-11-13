package com.jochana.driverule.domain.profile

import com.jochana.driverule.core.model.DriverProfile

sealed interface ProfileMutationResult {
    data class Success(val profile: DriverProfile) : ProfileMutationResult
    data class InvalidName(val error: ProfileNameError) : ProfileMutationResult
    data object LimitReached : ProfileMutationResult
    data object ProfileNotFound : ProfileMutationResult
    data object PermissionGuideRequired : ProfileMutationResult
    data object StorageError : ProfileMutationResult
}
