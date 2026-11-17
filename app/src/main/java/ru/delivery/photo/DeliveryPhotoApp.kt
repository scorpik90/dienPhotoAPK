package ru.delivery.photo

import android.app.Application
import androidx.work.WorkManager
import ru.delivery.photo.data.DeliveryRepository
import ru.delivery.photo.data.PhotoStore
import ru.delivery.photo.data.db.AppDatabase
import ru.delivery.photo.location.LocationProvider
import ru.delivery.photo.serial.SerialExtractor

/**
 * Приложение целиком работает на устройстве: собственный сервер не нужен,
 * сетевого доступа приложение не требует.
 */
class DeliveryPhotoApp : Application() {

    lateinit var repository: DeliveryRepository
    lateinit var photoStore: PhotoStore
    lateinit var locationProvider: LocationProvider
    lateinit var serialExtractor: SerialExtractor

    override fun onCreate() {
        super.onCreate()
        repository = DeliveryRepository(AppDatabase.get(this))
        photoStore = PhotoStore(this)
        locationProvider = LocationProvider(this)
        serialExtractor = SerialExtractor()

        // Черновики неподтверждённых кадров не должны копиться.
        photoStore.clearOrphanedPreviews()

        // Напоминания о подписании документов больше не используются.
        // Задача из прежних версий могла остаться в WorkManager — убираем её.
        WorkManager.getInstance(this).cancelUniqueWork("signoff_reminder")
    }
}
