package com.lorenzo.aicalendar.di

import android.content.Context
import androidx.room.Room
import com.lorenzo.aicalendar.data.local.AiCalendarDatabase
import com.lorenzo.aicalendar.data.local.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AiCalendarDatabase =
        Room.databaseBuilder(context, AiCalendarDatabase::class.java, "aicalendar.db").build()

    @Provides
    fun provideEventDao(db: AiCalendarDatabase): EventDao = db.eventDao()
}
