package ru.delivery.photo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.delivery.photo.data.db.AppDatabase
import ru.delivery.photo.data.db.CrewMemberEntity
import ru.delivery.photo.data.db.DeliveryPointEntity
import ru.delivery.photo.data.db.PackageEntity
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.data.db.SettingEntity
import ru.delivery.photo.data.db.TripEntity
import ru.delivery.photo.domain.GeoSource
import ru.delivery.photo.domain.PhotoKind
import ru.delivery.photo.domain.SerialSource
import ru.delivery.photo.domain.ShareStrategy
import java.util.UUID

/**
 * Единственная точка доступа к данным. Сервера нет, поэтому хранилище полностью локальное:
 * вся информация о рейсах, точках разгрузки и фотографиях живёт на устройстве
 * до выгрузки отчёта.
 */
class DeliveryRepository(private val db: AppDatabase) {

    private val tripDao = db.tripDao()
    private val pointDao = db.deliveryPointDao()
    private val crewDao = db.crewDao()
    private val photoDao = db.photoDao()
    private val packageDao = db.packageDao()
    private val settingDao = db.settingDao()

    val trips: Flow<List<TripEntity>> = tripDao.observeAll()
    val activeTrip: Flow<TripEntity?> = tripDao.observeActive()
    val allPoints: Flow<List<DeliveryPointEntity>> = pointDao.observeAll()
    val allPackages: Flow<List<PackageEntity>> = packageDao.observeAll()
    val allCrew: Flow<List<CrewMemberEntity>> = crewDao.observeAll()
    val allPhotos: Flow<List<PhotoEntity>> = photoDao.observeAll()

    fun pointsForTrip(tripId: String): Flow<List<DeliveryPointEntity>> = pointDao.observeForTrip(tripId)

    fun crewForTrip(tripId: String): Flow<List<CrewMemberEntity>> = crewDao.observeForTrip(tripId)

    fun packagesForTrip(tripId: String): Flow<List<PackageEntity>> = packageDao.observeForTrip(tripId)

    suspend fun getCrew(tripId: String): List<CrewMemberEntity> = crewDao.getForTrip(tripId)

    fun photosForPoint(pointId: String): Flow<List<PhotoEntity>> = photoDao.observeForPoint(pointId)
    fun photosForTrip(tripId: String): Flow<List<PhotoEntity>> = photoDao.observeForTrip(tripId)
    fun unsentPhotosForPoint(pointId: String): Flow<List<PhotoEntity>> = photoDao.observeUnsentForPoint(pointId)

    val settings: Flow<Map<String, String>> = settingDao.observeAll().map { list -> list.associate { it.key to it.value } }

    suspend fun getSetting(key: String): String? = settingDao.get(key)
    suspend fun putSetting(key: String, value: String) = settingDao.upsert(SettingEntity(key, value))

    suspend fun startTrip(routeNo: String, driverName: String, vehicle: String): String {
        val id = UUID.randomUUID().toString()
        tripDao.upsert(
            TripEntity(
                id = id,
                routeNo = routeNo.trim(),
                driverName = driverName.trim(),
                vehicle = vehicle.trim(),
                startedAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    /** Завершение смены: рейс и все его точки попадают в историю. */
    suspend fun finishTrip(tripId: String) {
        val now = System.currentTimeMillis()
        tripDao.finish(tripId, now)
        pointDao.finishForTrip(tripId, now)
    }

    suspend fun getTrip(id: String): TripEntity? = tripDao.get(id)

    suspend fun createPoint(tripId: String, name: String, address: String?): String {
        val id = UUID.randomUUID().toString()
        pointDao.upsert(
            DeliveryPointEntity(
                id = id,
                tripId = tripId,
                name = name.trim(),
                address = address?.trim()?.takeIf { it.isNotEmpty() },
                startedAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    suspend fun getPoint(id: String): DeliveryPointEntity? = pointDao.get(id)

    /** Переименование точки разгрузки. */
    suspend fun renamePoint(id: String, name: String) {
        if (name.isBlank()) return
        pointDao.rename(id, name.trim())
    }

    /**
     * Удаление точки разгрузки вместе с её фотографиями.
     * Возвращает удалённые фотографии — их файлы нужно стереть с диска.
     */
    suspend fun deletePoint(id: String): List<PhotoEntity> {
        val photos = photoDao.getForPoint(id)
        photoDao.deleteForPoint(id)
        pointDao.delete(id)
        return photos
    }

    // ------------------------------------------------------------ экипаж

    suspend fun addCrewMember(tripId: String, name: String, vehicle: String): String {
        val existing = crewDao.getForTrip(tripId)
        val id = UUID.randomUUID().toString()
        crewDao.upsert(
            CrewMemberEntity(
                id = id,
                tripId = tripId,
                name = name.trim(),
                vehicle = vehicle.trim(),
                position = existing.size,
            )
        )
        return id
    }

    suspend fun deleteCrewMember(id: String) = crewDao.delete(id)

    suspend fun savePhoto(
        tripId: String,
        pointId: String,
        filePath: String,
        kind: PhotoKind,
        model: String?,
        serial: String?,
        serialSource: SerialSource,
        serialRawText: String?,
        shotAt: Long,
        lat: Double?,
        lon: Double?,
        accuracyM: Float?,
        geoSource: GeoSource,
        width: Int,
        height: Int,
        sha256: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        photoDao.upsert(
            PhotoEntity(
                id = id,
                tripId = tripId,
                pointId = pointId,
                filePath = filePath,
                kind = kind.code,
                serial = serial,
                serialSource = serialSource.code,
                serialRawText = serialRawText,
                shotAt = shotAt,
                lat = lat,
                lon = lon,
                accuracyM = accuracyM,
                geoSource = geoSource.code,
                width = width,
                height = height,
                sha256 = sha256,
                model = model,
            )
        )
        return id
    }

    suspend fun deletePhoto(photo: PhotoEntity) {
        photoDao.delete(photo.id)
    }

    suspend fun getPhoto(id: String): PhotoEntity? = photoDao.get(id)

    /** Создаёт пакет и помечает входящие в него фотографии как отправленные. */
    suspend fun registerPackage(
        tripId: String,
        pointId: String,
        photoIds: List<String>,
        strategy: ShareStrategy,
        caption: String,
        sentAt: Long,
    ): String {
        val id = UUID.randomUUID().toString()
        packageDao.upsert(
            PackageEntity(
                id = id,
                tripId = tripId,
                pointId = pointId,
                createdAt = sentAt,
                sentAt = sentAt,
                photoCount = photoIds.size,
                strategy = strategy.code,
                caption = caption,
            )
        )
        if (photoIds.isNotEmpty()) {
            photoDao.markSent(photoIds, sentAt, id)
        }
        return id
    }
}
