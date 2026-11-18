package ru.delivery.photo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Определение координат.
 *
 * Используется штатный [LocationManager], а не Google Play Services:
 * в российских сборках Android сервисы Google отсутствуют, и
 * FusedLocationProviderClient на таких устройствах не работает.
 */
class LocationProvider(private val context: Context) {

    private val manager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Доступен ли хотя бы один провайдер геолокации. */
    fun isProviderAvailable(): Boolean {
        val lm = manager ?: return false
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any { provider ->
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    /** Последнее известное положение, без запроса. */
    fun lastKnown(): Location? {
        val lm = manager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    /**
     * Актуальная позиция. Сначала пробуем кэш, затем запрашиваем у провайдера
     * с ограничением по времени.
     */
    suspend fun current(timeoutMs: Long = 8_000): Location? {
        lastKnown()?.let { cached ->
            // Кэш считаем пригодным, только если он свежий.
            if (System.currentTimeMillis() - cached.time < 60_000) return cached
        }
        val lm = manager ?: return null
        return withTimeoutOrNull(timeoutMs) {
            requestSingle(lm, LocationManager.GPS_PROVIDER)
                ?: requestSingle(lm, LocationManager.NETWORK_PROVIDER)
        } ?: lastKnown()
    }

    @Suppress("DEPRECATION")
    private suspend fun requestSingle(lm: LocationManager, provider: String): Location? {
        if (!lm.allProviders.contains(provider)) return null
        if (!lm.isProviderEnabled(provider)) return null

        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                }

                @Deprecated("Сигнатура обязательна для API < 30")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            var signal: CancellationSignal? = null
            var failed = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    signal = CancellationSignal()
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive && location != null) continuation.resume(location)
                    }
                } else {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                failed = true
            } catch (_: IllegalArgumentException) {
                failed = true
            }

            if (failed) {
                if (continuation.isActive) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            // Регистрация обработчика отмены должна быть ровно одна:
            // на одну корутину вешается только один CancelHandler, иначе
            // kotlinx.coroutines бросает IllegalStateException.
            continuation.invokeOnCancellation {
                runCatching { signal?.cancel() }
                runCatching { lm.removeUpdates(listener) }
            }
        }
    }
}
