package ru.delivery.photo.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.domain.TimeFmt
import java.io.File
import java.io.FileOutputStream

/**
 * Отправка помеченных фотографий в мессенджер через системный share-sheet.
 *
 * Пользователь самостоятельно выбирает приложение (MAX) и чат.
 * Сервер, бот и токены для этого не требуются.
 *
 * Количество файлов, которое мессенджер соберёт в одно сообщение-альбом,
 * зависит от реализации получателя, поэтому поддерживаются три стратегии
 * (см. [ru.delivery.photo.domain.ShareStrategy]).
 */
class ShareDispatcher(private val context: Context) {

    private val authority: String = "${context.packageName}.fileprovider"

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    /** Все фотографии одним сообщением. */
    fun buildMultiple(files: List<File>, caption: String): Intent {
        val uris = ArrayList(files.map { uriFor(it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = MIME_JPEG
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Один файл — используется для коллажа и для выгрузки отчёта. */
    fun buildSingle(file: File, mimeType: String, caption: String? = null): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            if (caption != null) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_CSV = "text/csv"
    }
}

/**
 * Формирование текстовой подписи к пакету фотографий.
 */
object CaptionBuilder {

    fun build(
        routeNo: String,
        pointName: String,
        driverName: String,
        vehicle: String,
        photos: List<PhotoEntity>,
        part: Int? = null,
        parts: Int? = null,
    ): String = buildString {
        append("Отчёт по точке разгрузки «").append(pointName.ifBlank { "—" }).append("»")
        append(", рейс ").append(routeNo.ifBlank { "—" })
        appendLine()

        val serial = photos.mapNotNull { it.serial }.distinct()
        if (serial.isNotEmpty()) {
            append("Серийные номера: ").append(serial.joinToString(", ")).appendLine()
        }

        if (driverName.isNotBlank() || vehicle.isNotBlank()) {
            append("Водитель: ")
            append(listOf(driverName, vehicle).filter { it.isNotBlank() }.joinToString(", "))
            appendLine()
        }

        if (photos.isNotEmpty()) {
            append("Фото: ").append(photos.size).append(" шт., ")
            append(TimeFmt.time(photos.first().shotAt)).append("–").append(TimeFmt.time(photos.last().shotAt))
            appendLine()
        }

        if (part != null && parts != null && parts > 1) {
            append("Часть ").append(part).append(" из ").append(parts)
        }
    }

    /**
     * Подпись к отчёту по всему завершённому рейсу.
     *
     * В отличие от отчёта по точке, здесь перечисляются все водители и все
     * точки разгрузки — по ним можно сверить пакет фотографий с маршрутом.
     */
    fun buildTrip(
        routeNo: String,
        drivers: List<String>,
        pointNames: List<String>,
        photos: List<PhotoEntity>,
    ): String = buildString {
        append("Отчёт по рейсу ").append(routeNo.ifBlank { "—" }).appendLine()

        if (drivers.isNotEmpty()) {
            append("Водители: ").append(drivers.joinToString("; ")).appendLine()
        }
        if (pointNames.isNotEmpty()) {
            append("Точки разгрузки: ").append(pointNames.joinToString(", ")).appendLine()
        }

        val serial = photos.mapNotNull { it.serial }.distinct()
        if (serial.isNotEmpty()) {
            append("Серийные номера: ").append(serial.joinToString(", ")).appendLine()
        }

        if (photos.isNotEmpty()) {
            append("Фото: ").append(photos.size).append(" шт., ")
            append(TimeFmt.time(photos.first().shotAt)).append("–").append(TimeFmt.time(photos.last().shotAt))
        }
    }
}

/**
 * Сборка всех кадров в одно изображение-сетку.
 *
 * Аварийный вариант на случай, если получатель не собирает несколько файлов
 * в одно сообщение: альбом всегда остаётся одним вложением.
 */
object CollageBuilder {

    private const val COLUMNS = 3
    private const val CELL_WIDTH = 900
    private const val GAP = 10

    fun build(files: List<File>, target: File): File? {
        if (files.isEmpty()) return null

        val bitmaps = files.mapNotNull { decode(it) }
        if (bitmaps.isEmpty()) return null

        val columns = COLUMNS.coerceAtMost(bitmaps.size)
        val rows = (bitmaps.size + columns - 1) / columns
        val cellHeight = CELL_WIDTH * 3 / 4
        val width = columns * CELL_WIDTH + (columns + 1) * GAP
        val height = rows * cellHeight + (rows + 1) * GAP

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(24, 24, 24))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        bitmaps.forEachIndexed { index, bitmap ->
            val col = index % columns
            val row = index / columns
            val left = GAP + col * (CELL_WIDTH + GAP)
            val top = GAP + row * (cellHeight + GAP)

            val scale = maxOf(
                CELL_WIDTH.toFloat() / bitmap.width,
                cellHeight.toFloat() / bitmap.height,
            )
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale

            val dst = Rect(
                (left + (CELL_WIDTH - drawW) / 2).toInt(),
                (top + (cellHeight - drawH) / 2).toInt(),
                (left + (CELL_WIDTH + drawW) / 2).toInt(),
                (top + (cellHeight + drawH) / 2).toInt(),
            )
            canvas.drawBitmap(bitmap, null, dst, paint)
            bitmap.recycle()
        }

        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        output.recycle()
        return target
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > CELL_WIDTH * 2 || bounds.outHeight / sample > CELL_WIDTH * 2) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
