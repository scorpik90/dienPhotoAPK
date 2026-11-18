package ru.delivery.photo.watermark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import ru.delivery.photo.domain.StampData
import ru.delivery.photo.domain.TimeFmt
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Нанесение водяного знака.
 *
 * Штамп наносится в двух слоях:
 *  1. в пиксели изображения — видно человеку, переживает пересжатие в мессенджере;
 *  2. в EXIF — машиночитаемо, используется при сверке и разборе спорных случаев.
 *
 * Наносится на устройстве, до отправки в сеть: сырой кадр без маркировки
 * не должен покидать телефон.
 *
 * Оформление (правый нижний угол, белый текст с чёрной обводкой, запятая как
 * разделитель дробной части, русская дата) повторяет принятое в компании
 * оформление фотографий — чтобы кадры из приложения не выделялись среди
 * остальных материалов.
 */
object WatermarkRenderer {

    /** Максимальная длинная сторона итогового изображения, px. */
    const val MAX_DIMENSION = 3000

    /** Качество JPEG. */
    const val JPEG_QUALITY = 90

    private const val TEXT_SIZE_RATIO = 0.030f
    private const val MIN_TEXT_SIZE = 12f
    private const val MARGIN_RATIO = 0.022f
    private const val STROKE_RATIO = 0.16f

    /**
     * Строки водяного знака.
     *
     * Информация о смене (рейсе) не выводится; дата и время фиксации фото сохраняются.
     * При наличии показываются модель и (после неё) серийный номер.
     */
    fun buildLines(stamp: StampData): List<String> {
        val lines = ArrayList<String>(7)

        lines += if (stamp.hasGeo) {
            val lat = String.format(Locale.US, "%.5f", stamp.lat).replace('.', ',')
            val lon = String.format(Locale.US, "%.5f", stamp.lon).replace('.', ',')
            // Точность выводится только когда она плохая: «точные координаты»
            // без указания погрешности вводят в заблуждение.
            if (stamp.lowAccuracy) {
                val acc = stamp.accuracyM?.let { " ±${it.toInt()} м" } ?: " (точность неизвестна)"
                "$lat, $lon$acc"
            } else {
                "$lat, $lon"
            }
        } else {
            "Координаты недоступны"
        }

        lines += TimeFmt.watermarkStamp(stamp.shotAtMillis)

        if (stamp.pointName.isNotBlank()) lines += stamp.pointName

        val who = buildString {
            if (stamp.driverName.isNotBlank()) append(stamp.driverName)
            if (stamp.vehicle.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(stamp.vehicle)
            }
            if (stamp.companyName.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(stamp.companyName)
            }
        }
        if (who.isNotBlank()) lines += who

        stamp.model?.takeIf { it.isNotBlank() }?.let { lines += "Модель: $it" }
        stamp.serial?.takeIf { it.isNotBlank() }?.let { lines += "SN: $it" }

        return lines
    }

    /** Наложение штампа на копию исходного изображения. */
    fun render(source: Bitmap, stamp: StampData): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)

        val lines = buildLines(stamp)
        if (lines.isEmpty()) return output

        val width = output.width
        val height = output.height
        val margin = (width * MARGIN_RATIO).toInt().coerceAtLeast(10)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }

        // Подбираем размер шрифта так, чтобы самая длинная строка помещалась.
        var textSize = width * TEXT_SIZE_RATIO
        while (textSize > MIN_TEXT_SIZE) {
            paint.textSize = textSize
            val widest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            if (widest <= width - margin * 2) break
            textSize -= 1f
        }
        paint.textSize = textSize

        val fm = paint.fontMetrics
        val lineHeight = (fm.bottom - fm.top) * 1.18f

        // Блок прижат к правому нижнему углу.
        var baseline = height - margin - fm.bottom

        val outline = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = textSize * STROKE_RATIO
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val fill = Paint(paint).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        for (line in lines) {
            canvas.drawText(line, width - margin.toFloat(), baseline, outline)
            canvas.drawText(line, width - margin.toFloat(), baseline, fill)
            baseline -= lineHeight
        }

        return output
    }

    /**
     * Сохраняет помеченное изображение в JPEG и дописывает EXIF.
     * Возвращает пару «ширина × высота».
     */
    fun save(target: File, bitmap: Bitmap, stamp: StampData): Pair<Int, Int> {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        writeExif(target, stamp)
        return bitmap.width to bitmap.height
    }

    /** Записывает метаданные в EXIF-блок JPEG-файла. */
    fun writeExif(file: File, stamp: StampData) {
        runCatching {
            val exif = ExifInterface(file)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, TimeFmt.exifDateTime(stamp.shotAtMillis))
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, TimeFmt.exifDateTime(stamp.shotAtMillis))
            exif.setAttribute(ExifInterface.TAG_DATETIME, TimeFmt.exifDateTime(stamp.shotAtMillis))
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, descriptionFor(stamp))
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "DeliveryPhoto")
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, TimeFmt.exifDate(stamp.shotAtMillis))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, TimeFmt.time(stamp.shotAtMillis))
            if (stamp.lat != null && stamp.lon != null) {
                exif.setLatLong(stamp.lat, stamp.lon)
            }
            exif.saveAttributes()
        }
    }

    /** Текстовое описание кадра, попадающее в EXIF и в отчёт. */
    fun descriptionFor(stamp: StampData): String = buildString {
        append("Рейс ").append(stamp.routeNo.ifBlank { "—" })
        append("; точка ").append(stamp.pointName.ifBlank { "—" })
        stamp.model?.takeIf { it.isNotBlank() }?.let { append("; модель ").append(it) }
        stamp.serial?.takeIf { it.isNotBlank() }?.let { append("; SN ").append(it) }
        append("; ").append(TimeFmt.stamp(stamp.shotAtMillis))
        if (stamp.hasGeo) {
            append("; ")
            append(String.format(Locale.US, "%.6f", stamp.lat))
            append(", ")
            append(String.format(Locale.US, "%.6f", stamp.lon))
            stamp.accuracyM?.let { append(" +/-").append(it.toInt()).append("m") }
        } else {
            append("; координаты недоступны")
        }
        if (stamp.driverName.isNotBlank()) append("; ").append(stamp.driverName)
        if (stamp.vehicle.isNotBlank()) append("; ").append(stamp.vehicle)
    }

    /**
     * Декодирует JPEG с уменьшением до [maxDim] по длинной стороне,
     * чтобы не держать в памяти полный кадр камеры.
     */
    fun decodeScaled(file: File, maxDim: Int = MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
    }
}
