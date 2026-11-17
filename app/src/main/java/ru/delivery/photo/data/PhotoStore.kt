package ru.delivery.photo.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Хранилище помеченных фотографий во внутреннем хранилище приложения.
 *
 * Служебные кадры намеренно не попадают в общую галерею устройства:
 * пользователю не нужно их видеть и вычищать, а получать их наружу можно
 * только явным действием — отправкой в мессенджер.
 */
class PhotoStore(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "tmp").apply { if (!exists()) mkdirs() }

    fun newPhotoFile(id: String): File = File(photosDir, "$id.jpg")

    fun newTempFile(prefix: String): File = File(tempDir, "$prefix-${System.currentTimeMillis()}.jpg")

    fun photoFile(filePath: String): File = File(filePath)

    fun delete(filePath: String) {
        runCatching { File(filePath).delete() }
    }

    fun reportDir(): File = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }

    fun clearTemp() {
        runCatching { tempDir.listFiles()?.forEach { it.delete() } }
    }

    /**
     * Удаляет черновики (preview-*.jpg), оставшиеся после снятого, но не
     * подтверждённого кадра. Если приложение было убито на экране проверки,
     * черновик иначе остался бы в хранилище навсегда.
     */
    fun clearOrphanedPreviews() {
        runCatching {
            photosDir.listFiles { file -> file.name.startsWith("preview-") }
                ?.forEach { it.delete() }
        }
    }

    companion object {
        fun sha256(file: File): String? = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
