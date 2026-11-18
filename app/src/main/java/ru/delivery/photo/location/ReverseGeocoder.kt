package ru.delivery.photo.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.delivery.photo.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Обратное геокодирование координат в текстовый адрес.
 *
 * Предпочтителен Яндекс Геокодер (лучше для российских адресов),
 * при недоступности/ошибке используется системный [Geocoder] устройства.
 */
object ReverseGeocoder {

    private const val TAG = "ReverseGeocoder"

    private val yandexApiKey: String by lazy { decodeKey() }

    private fun decodeKey(): String = runCatching {
        val enc = BuildConfig.YANDEX_GEOCODER_KEY_ENC
        if (enc.isBlank()) return ""
        val bytes = Base64.decode(enc, Base64.DEFAULT)
        val mask = BuildConfig.YANDEX_GEOCODER_KEY_MASK
        val decoded = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor mask).toByte() }
        String(decoded, Charsets.UTF_8)
    }.getOrDefault("")

    suspend fun geocode(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        geocodeWithYandex(lat, lon)?.let { return@withContext it }
        geocodeWithSystem(context, lat, lon)
    }

    private fun geocodeWithYandex(lat: Double, lon: Double): String? {
        val apiKey = yandexApiKey
        if (apiKey.isBlank()) return null
        return runCatching {
            val geocode = URLEncoder.encode("$lon,$lat", Charsets.UTF_8.name())
            val query = "https://geocode-maps.yandex.ru/1.x/?" +
                "apikey=$apiKey&geocode=$geocode&format=json&lang=ru_RU&results=1&kind=house"
            val connection = (URL(query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "DeliveryPhoto/1.0")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "Yandex geocoder HTTP $code")
                    return@runCatching null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val members = JSONObject(body)
                    .optJSONObject("response")
                    ?.optJSONObject("GeoObjectCollection")
                    ?.optJSONArray("featureMember") ?: return@runCatching null
                if (members.length() == 0) return@runCatching null
                val geo = members.optJSONObject(0)?.optJSONObject("GeoObject") ?: return@runCatching null
                val meta = geo.optJSONObject("metaDataProperty")?.optJSONObject("GeocoderMetaData")
                val shortened = shortenYandexAddress(meta)
                if (!shortened.isNullOrBlank()) return@runCatching shortened
                meta?.optString("text")?.takeIf { it.isNotBlank() }
                    ?: meta?.optJSONObject("Address")?.optString("formatted")?.takeIf { it.isNotBlank() }
                    ?: geo.optString("name")?.takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }.onFailure { e ->
            Log.w(TAG, "Yandex geocoder request failed", e)
        }.getOrNull()
    }

    /**
     * Сокращённый адрес без страны и области/региона (province).
     * Оставляет населённый пункт/район, улицу, дом и т.п.
     */
    private fun shortenYandexAddress(meta: JSONObject?): String? {
        val components = meta?.optJSONObject("Address")?.optJSONArray("Components") ?: return null
        val parts = ArrayList<String>()
        val seen = HashSet<String>()
        for (i in 0 until components.length()) {
            val comp = components.optJSONObject(i) ?: continue
            val kind = comp.optString("kind")
            val name = comp.optString("name").trim()
            if (name.isBlank()) continue
            if (kind == "country" || kind == "province") continue
            if (seen.add(name)) parts.add(name)
        }
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeWithSystem(context: Context, lat: Double, lon: Double): String? {
        return runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) {
                                continuation.resume(addresses.firstOrNull())
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "System geocoder error: $errorMessage")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    continuation.invokeOnCancellation { }
                }
            } else {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            } ?: return@runCatching null

            // Сокращённый адрес: без страны и области/региона.
            val parts = linkedSetOf<String>()
            listOfNotNull(
                address.locality,
                address.subLocality,
                address.thoroughfare,
                address.subThoroughfare,
                address.premises,
                address.featureName,
            ).forEach { part ->
                val value = part.trim()
                if (value.isNotBlank()) parts.add(value)
            }
            parts.joinToString(", ").takeIf { it.isNotBlank() }
        }.onFailure { e ->
            Log.w(TAG, "System geocoder failed", e)
        }.getOrNull()
    }
}
