package com.lorenzo.aicalendar.data.extract

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.extract.ExtractionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the best extractor per call: cloud (OpenRouter) when online and a key is available,
 * otherwise the on-device tier. Cloud failures fall back to on-device so the app never blocks.
 */
@Singleton
class HybridEventExtractor @Inject constructor(
    private val onDevice: OnDeviceEventExtractor,
    private val cloud: CloudEventExtractor,
    private val keyProvider: ApiKeyProvider,
    @param:ApplicationContext private val context: Context,
) : EventExtractor {

    override suspend fun extract(input: ExtractionInput): ExtractionResult {
        if (isOnline() && keyProvider.currentKey() != null) {
            runCatching { return cloud.extract(input) }
                .onFailure { Log.w(TAG, "Cloud extraction failed; falling back on-device", it) }
        }
        return onDevice.extract(input)
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "HybridEventExtractor"
    }
}
