package com.lorenzo.aicalendar.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.lorenzo.aicalendar.data.profile.DataStoreProfileRepository
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

@Module
@InstallIn(SingletonComponent::class)
object ProfileDataModule {
    @Provides
    @Singleton
    fun provideProfileDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.profileDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileBindModule {
    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: DataStoreProfileRepository): ProfileRepository
}
