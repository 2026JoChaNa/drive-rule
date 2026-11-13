package com.jochana.driverule.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNamePolicyTest {
    @Test
    fun `blank unicode whitespace is rejected`() {
        assertEquals(
            ProfileNameValidation.Invalid(ProfileNameError.Empty),
            ProfileNamePolicy.validate(" \t\n\u00a0\u3000"),
        )
    }

    @Test
    fun `name is trimmed before checking the limit`() {
        val name = "가".repeat(ProfileNamePolicy.MAX_CODE_POINTS)
        assertEquals(
            ProfileNameValidation.Valid(name),
            ProfileNamePolicy.validate("  $name  "),
        )
    }

    @Test
    fun `names longer than twenty code points are rejected`() {
        assertEquals(
            ProfileNameValidation.Invalid(ProfileNameError.TooLong),
            ProfileNamePolicy.validate("가".repeat(21)),
        )
    }

    @Test
    fun `supplementary characters count as one code point each`() {
        val twentyCharacters = "\uD83D\uDE97".repeat(20)
        assertEquals(
            ProfileNameValidation.Valid(twentyCharacters),
            ProfileNamePolicy.validate(twentyCharacters),
        )
        assertEquals(
            ProfileNameValidation.Invalid(ProfileNameError.TooLong),
            ProfileNamePolicy.validate(twentyCharacters + "가"),
        )
    }
}
