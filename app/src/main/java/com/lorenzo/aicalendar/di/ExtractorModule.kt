package com.lorenzo.aicalendar.di

import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import com.lorenzo.aicalendar.data.auth.DevApiKeyProvider
import com.lorenzo.aicalendar.data.extract.HybridEventExtractor
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    // Hybrid = cloud (OpenRouter) first, on-device fallback.
    @Binds
    @Singleton
    abstract fun bindEventExtractor(impl: HybridEventExtractor): EventExtractor

    // MVP debug: dev key from BuildConfig. Slice 7b swaps in the PKCE per-user provider.
    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(impl: DevApiKeyProvider): ApiKeyProvider
}
