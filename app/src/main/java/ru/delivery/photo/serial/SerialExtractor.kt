package ru.delivery.photo.serial

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.delivery.photo.domain.SerialPatterns
import kotlin.coroutines.resume

/**
 * Результат извлечения кандидатов серийного номера из фотографии этикетки.
 *
 * [candidates] — предложения пользователю, а не готовый ответ: серийник
 * попадает в водяной знак только после ручного подтверждения.
 */
data class SerialExtraction(
    val candidates: List<String>,
    val rawText: String?,
    val barcodeValue: String?,
) {
    val isEmpty: Boolean get() = candidates.isEmpty()
}

/**
 * Извлечение серийного номера из фотографии этикетки.
 *
 * Используются модели ML Kit, поставляемые внутри APK (bundled): Google Play
 * Services на устройствах парка отсутствуют.
 *
 * Распознаётся латинская модель. Она покрывает не только латиницу, но и
 * кириллицу, поэтому отдельная русская модель не нужна. Шильдики изделий
 * англоязычные («DIESEL GENERATING SETS», «TYPE», «NO.»), так что латинской
 * модели достаточно.
 */
class SerialExtractor {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Штрихкод, если на кадре распознан машиночитаемый код. */
    suspend fun readBarcode(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                if (continuation.isActive) continuation.resume(value)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    /** Текст, распознанный на кадре. */
    suspend fun readText(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text?.trim()
                if (continuation.isActive) continuation.resume(text?.takeIf { it.isNotEmpty() })
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    /**
     * Полное извлечение: сначала штрихкод, затем текст этикетки.
     * Штрихкод приоритетнее — он не зависит от качества распознавания текста.
     */
    suspend fun extract(bitmap: Bitmap): SerialExtraction {
        val barcode = readBarcode(bitmap)
        val rawText = runCatching { readText(bitmap) }.getOrNull()

        val candidates = LinkedHashSet<String>()
        barcode?.let { candidates += SerialPatterns.normalize(it) }
        rawText?.let { candidates += SerialPatterns.extract(it) }

        return SerialExtraction(
            candidates = candidates.toList(),
            rawText = rawText,
            barcodeValue = barcode,
        )
    }

    fun close() {
        runCatching { barcodeScanner.close() }
        runCatching { textRecognizer.close() }
    }
}
