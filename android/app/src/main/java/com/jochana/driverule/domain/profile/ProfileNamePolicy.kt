package com.jochana.driverule.domain.profile

object ProfileNamePolicy {
    const val MAX_CODE_POINTS = 20
    const val MAX_PROFILES = 3

    fun validate(rawName: String): ProfileNameValidation {
        val name = rawName.trim()
        return when {
            name.isEmpty() -> ProfileNameValidation.Invalid(ProfileNameError.Empty)
            name.codePointCount(0, name.length) > MAX_CODE_POINTS ->
                ProfileNameValidation.Invalid(ProfileNameError.TooLong)
            else -> ProfileNameValidation.Valid(name)
        }
    }
}

enum class ProfileNameError {
    Empty,
    TooLong,
}

sealed interface ProfileNameValidation {
    data class Valid(val normalizedName: String) : ProfileNameValidation
    data class Invalid(val error: ProfileNameError) : ProfileNameValidation
}
