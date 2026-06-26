package com.lorenzo.aicalendar.domain.profile

import kotlinx.coroutines.flow.Flow

/** Stores the user profile and the "onboarding completed" flag (local, DataStore-backed). */
interface ProfileRepository {
    val profile: Flow<UserProfile>
    val onboardingCompleted: Flow<Boolean>
    suspend fun save(profile: UserProfile)
    suspend fun setOnboardingCompleted(done: Boolean)
}
