package com.lorenzo.aicalendar.di

import com.lorenzo.aicalendar.data.extract.OnDeviceEventExtractor
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    // Slice 6: on-device only. Slice 7 swaps this for the hybrid (on-device + OpenRouter) extractor.
    @Binds
    @Singleton
    abstract fun bindEventExtractor(impl: OnDeviceEventExtractor): EventExtractor
}
