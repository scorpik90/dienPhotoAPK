package ru.delivery.photo.domain

/** Тип фотографии. Определяет, участвует ли снимок в извлечении серийного номера. */
enum class PhotoKind(val code: String) {
    OBJECT("object"),
    LABEL("label");

    companion object {
        fun from(code: String?): PhotoKind = entries.firstOrNull { it.code == code } ?: OBJECT
    }
}

/** Откуда взят серийный номер — для аудита и разбора спорных случаев. */
enum class SerialSource(val code: String) {
    BARCODE("barcode"),
    OCR("ocr"),
    MANUAL("manual"),
    NONE("none");

    companion object {
        fun from(code: String?): SerialSource = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** Источник координат. */
enum class GeoSource(val code: String) {
    GPS("gps"),
    NETWORK("network"),
    NONE("none");

    companion object {
        fun from(code: String?): GeoSource = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * Стратегия отправки пакета фотографий в мессенджер.
 *
 * [ALBUM] — один ACTION_SEND_MULTIPLE со всеми фотографиями (мессенджер собирает альбом).
 * [CHUNKS] — последовательная отправка порциями, в подписи указывается «часть N из M».
 * [COLLAGE] — одно изображение-сетка со всеми кадрами (аварийный вариант).
 */
enum class ShareStrategy(val code: String) {
    ALBUM("album"),
    CHUNKS("chunks"),
    COLLAGE("collage");

    companion object {
        fun from(code: String?): ShareStrategy = entries.firstOrNull { it.code == code } ?: ALBUM
    }
}

/** Режим оформления приложения: как в системе, светлое или тёмное. */
enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Данные водяного знака. Формируются в момент съёмки и запекаются одновременно
 * в пиксели изображения и в EXIF.
 *
 * @param companyName название организации — выводится первой строкой штампа.
 * @param accuracyM точность GPS. В штамп попадает только когда она плохая:
 *   точные координаты без указания погрешности вводят в заблуждение.
 */
data class StampData(
    val companyName: String,
    val routeNo: String,
    val pointName: String,
    val serial: String?,
    val shotAtMillis: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val driverName: String,
    val vehicle: String,
) {
    val hasGeo: Boolean get() = lat != null && lon != null

    /** Порог, ниже которого точность считается сомнительной. */
    val lowAccuracy: Boolean get() = hasGeo && (accuracyM == null || accuracyM > LOW_ACCURACY_M)

    companion object {
        const val LOW_ACCURACY_M = 30f
    }
}
