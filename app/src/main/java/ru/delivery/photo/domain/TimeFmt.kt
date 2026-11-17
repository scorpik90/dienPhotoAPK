package ru.delivery.photo.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирование времени.
 *
 * Во всех водяных знаках используется московское время (UTC+3): документы
 * приёмки-передачи оформляются по единому часовому поясу независимо от того,
 * в каком поясе находится устройство водителя.
 */
object TimeFmt {

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    /**
     * Сокращения месяцев по ГОСТ. Обратите внимание: для мая и июня —
     * без точки («12 мая 2026 г.»), для остальных — с точкой («12 нояб. 2026 г.»).
     */
    private val MONTHS = arrayOf(
        "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
        "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.",
    )

    private val STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
    private val SHORT_STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
    private val EXIF = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
    private val EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ROOT)

    /** 12.11.2026 14:32:07 */
    fun stamp(millis: Long): String = STAMP.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    /**
     * Короткий номер рейса: «12.11.2026 14:32». Пользователь его не вводит —
     * рейс получает его автоматически по времени начала (по московскому времени).
     */
    fun shortStamp(millis: Long): String = SHORT_STAMP.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    fun date(millis: Long): String = DATE.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    fun time(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    /**
     * Формат водяного знака: «12 нояб. 2026 г. 13:30:33».
     * Соответствует принятому в компании оформлению фотографий.
     */
    fun watermarkStamp(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZONE)
        val day = date.dayOfMonth
        val month = MONTHS[date.monthValue - 1]
        val year = date.year
        val time = TIME.format(date)
        return "$day $month $year г. $time"
    }

    /** Формат EXIF: 2026:11:12 14:32:07 */
    fun exifDateTime(millis: Long): String = EXIF.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    fun exifDate(millis: Long): String = EXIF_DATE.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    /** Смещение от московского времени в часах для выбранного устройством пояса. */
    fun offsetHoursFromMoscow(deviceZone: ZoneId): Int {
        val deviceOffset = deviceOffsetMinutes(deviceZone)
        val mskOffset = deviceOffsetMinutes(ZONE)
        return Math.round((deviceOffset - mskOffset) / 60f)
    }

    private fun deviceOffsetMinutes(zone: ZoneId): Int =
        zone.rules.getOffset(Instant.now()).totalSeconds / 60
}
