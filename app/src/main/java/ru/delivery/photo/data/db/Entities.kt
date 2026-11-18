package ru.delivery.photo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Рейс — поездка водителя. Внутри рейса водитель разгружается в нескольких
 * точках, в каждой из которых фотографирует выгруженный груз.
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val routeNo: String,
    val driverName: String,
    val vehicle: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

/**
 * Точка разгрузки — место, где водитель выгрузил груз и сфотографировал
 * выгруженное. Фотографии привязываются именно к точке, а не к позиции груза.
 */
@Entity(tableName = "delivery_points")
data class DeliveryPointEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val address: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

/**
 * Член экипажа рейса. В рейсе может быть несколько водителей с разными
 * машинами; на экране съёмки водитель переключается между ними, и в водяном
 * знак попадают ФИО и автомобиль выбранного.
 */
@Entity(tableName = "crew_members")
data class CrewMemberEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val vehicle: String,
    val position: Int,
)

/** Фотография со штампом. Файл лежит во внутреннем хранилище приложения. */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val pointId: String,
    val filePath: String,
    val kind: String,
    val serial: String? = null,
    val serialSource: String? = null,
    /** Исходный распознанный текст — для разбора спорных случаев. */
    val serialRawText: String? = null,
    val shotAt: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val geoSource: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sha256: String? = null,
    val sentAt: Long? = null,
    val packageId: String? = null,
    /** Модель оборудования, распознанная на этикетке (при наличии). */
    val model: String? = null,
)

/** Факт отправки набора фотографий в мессенджер. */
@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val pointId: String,
    val createdAt: Long,
    val sentAt: Long? = null,
    val photoCount: Int,
    val strategy: String,
    val caption: String? = null,
)

/** Ключевые настройки приложения. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
