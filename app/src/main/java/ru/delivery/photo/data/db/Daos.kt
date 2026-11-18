package ru.delivery.photo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripEntity?

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("UPDATE trips SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: String, finishedAt: Long)
}

@Dao
interface DeliveryPointDao {
    @Query("SELECT * FROM delivery_points WHERE tripId = :tripId ORDER BY startedAt ASC")
    fun observeForTrip(tripId: String): Flow<List<DeliveryPointEntity>>

    @Query("SELECT * FROM delivery_points ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<DeliveryPointEntity>>

    @Query("SELECT * FROM delivery_points WHERE id = :id")
    suspend fun get(id: String): DeliveryPointEntity?

    @Upsert
    suspend fun upsert(point: DeliveryPointEntity)

    @Query("UPDATE delivery_points SET name = :name, address = :address WHERE id = :id")
    suspend fun update(id: String, name: String, address: String?)

    @Query("UPDATE delivery_points SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: String, finishedAt: Long)

    @Query("UPDATE delivery_points SET finishedAt = :finishedAt WHERE tripId = :tripId AND finishedAt IS NULL")
    suspend fun finishForTrip(tripId: String, finishedAt: Long)

    @Query("SELECT * FROM delivery_points ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<DeliveryPointEntity>>

    @Query("DELETE FROM delivery_points WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CrewDao {
    @Query("SELECT * FROM crew_members WHERE tripId = :tripId ORDER BY position ASC")
    fun observeForTrip(tripId: String): Flow<List<CrewMemberEntity>>

    @Query("SELECT * FROM crew_members WHERE tripId = :tripId ORDER BY position ASC")
    suspend fun getForTrip(tripId: String): List<CrewMemberEntity>

    @Query("SELECT * FROM crew_members ORDER BY tripId, position ASC")
    fun observeAll(): Flow<List<CrewMemberEntity>>

    @Query("SELECT * FROM crew_members WHERE id = :id")
    suspend fun get(id: String): CrewMemberEntity?

    @Upsert
    suspend fun upsert(member: CrewMemberEntity)

    @Query("DELETE FROM crew_members WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE pointId = :pointId ORDER BY shotAt ASC")
    fun observeForPoint(pointId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE pointId = :pointId ORDER BY shotAt ASC")
    suspend fun getForPoint(pointId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE tripId = :tripId ORDER BY shotAt ASC")
    fun observeForTrip(tripId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE pointId = :pointId AND sentAt IS NULL ORDER BY shotAt ASC")
    fun observeUnsentForPoint(pointId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT * FROM photos ORDER BY shotAt DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM photos WHERE pointId = :pointId")
    suspend fun deleteForPoint(pointId: String)

    @Query("UPDATE photos SET sentAt = :sentAt, packageId = :packageId WHERE id IN (:ids)")
    suspend fun markSent(ids: List<String>, sentAt: Long, packageId: String)
}

@Dao
interface PackageDao {
    @Query("SELECT * FROM packages WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeForTrip(tripId: String): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE pointId = :pointId ORDER BY createdAt DESC")
    suspend fun getForPoint(pointId: String): List<PackageEntity>

    @Upsert
    suspend fun upsert(pkg: PackageEntity)

    @Query("UPDATE packages SET sentAt = :sentAt, photoCount = :photoCount WHERE id = :id")
    suspend fun markSent(id: String, sentAt: Long, photoCount: Int)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(setting: SettingEntity)
}
