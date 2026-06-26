package com.lorenzo.aicalendar.domain.profile

import java.time.LocalDate

enum class Sex { MALE, FEMALE, OTHER, UNSPECIFIED }

enum class Profession { STUDENT, WORKER, OTHER, UNSPECIFIED }

/** The user's profile, collected at onboarding and editable from Settings. */
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val birthDate: LocalDate? = null,
    val city: String = "",
    val profession: Profession = Profession.UNSPECIFIED,
)
