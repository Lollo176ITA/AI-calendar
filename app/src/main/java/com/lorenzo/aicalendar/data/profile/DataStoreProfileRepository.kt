package com.lorenzo.aicalendar.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lorenzo.aicalendar.domain.profile.Profession
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.profile.Sex
import com.lorenzo.aicalendar.domain.profile.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    override val profile: Flow<UserProfile> = dataStore.data.map { p ->
        UserProfile(
            firstName = p[FIRST_NAME].orEmpty(),
            lastName = p[LAST_NAME].orEmpty(),
            sex = p[SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.UNSPECIFIED,
            birthDate = p[BIRTH_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            city = p[CITY].orEmpty(),
            profession = p[PROFESSION]?.let { runCatching { Profession.valueOf(it) }.getOrNull() }
                ?: Profession.UNSPECIFIED,
            routine = p[ROUTINE].orEmpty(),
        )
    }

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    override suspend fun save(profile: UserProfile) {
        dataStore.edit { p ->
            p[FIRST_NAME] = profile.firstName
            p[LAST_NAME] = profile.lastName
            p[SEX] = profile.sex.name
            profile.birthDate?.let { p[BIRTH_EPOCH_DAY] = it.toEpochDay() } ?: p.remove(BIRTH_EPOCH_DAY)
            p[CITY] = profile.city
            p[PROFESSION] = profile.profession.name
            p[ROUTINE] = profile.routine
        }
    }

    override suspend fun setOnboardingCompleted(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    private companion object {
        val FIRST_NAME = stringPreferencesKey("first_name")
        val LAST_NAME = stringPreferencesKey("last_name")
        val SEX = stringPreferencesKey("sex")
        val BIRTH_EPOCH_DAY = longPreferencesKey("birth_epoch_day")
        val CITY = stringPreferencesKey("city")
        val PROFESSION = stringPreferencesKey("profession")
        val ROUTINE = stringPreferencesKey("routine")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
