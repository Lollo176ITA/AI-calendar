package com.lorenzo.aicalendar.di

import com.lorenzo.aicalendar.data.repository.RoomEventRepository
import com.lorenzo.aicalendar.domain.repository.EventRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: RoomEventRepository): EventRepository
}
