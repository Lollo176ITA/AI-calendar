package com.lorenzo.aicalendar.data.auth

import com.lorenzo.aicalendar.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the OpenRouter API key. The MVP debug build reads a dev key from BuildConfig;
 * the production path (PKCE per-user login, slice 7b) implements this same interface so the
 * extractor never changes.
 */
interface ApiKeyProvider {
    suspend fun currentKey(): String?
}

@Singleton
class DevApiKeyProvider @Inject constructor() : ApiKeyProvider {
    override suspend fun currentKey(): String? =
        BuildConfig.OPENROUTER_DEV_KEY.takeIf { it.isNotBlank() }
}
