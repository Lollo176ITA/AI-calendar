package com.lorenzo.aicalendar.domain.profile

import java.time.LocalDate

enum class Sex { MALE, FEMALE, OTHER, UNSPECIFIED }

/** What fills the user's weeks. Multiple selections allowed (e.g. work AND study). */
enum class Occupation { STUDENT, WORKER, OTHER }

/** The user's profile, collected at onboarding and editable from Settings. */
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val birthDate: LocalDate? = null,
    val city: String = "",
    /** Empty set = not specified. */
    val occupations: Set<Occupation> = emptySet(),
    /** Free-text weekly routine (wake/work/lessons/fixed commitments) — fed to the assistant. */
    val routine: String = "",
)
