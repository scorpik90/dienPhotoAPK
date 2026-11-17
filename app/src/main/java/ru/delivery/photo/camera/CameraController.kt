package ru.delivery.photo.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import ru.delivery.photo.util.await
import java.io.File
import java.io.FileOutputStream

/**
 * Управление камерой: привязка к жизненному циклу и съёмка кадра в файл.
 */
class CameraController(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    /** Максимальный зум с учётом ограничений конкретной камеры: не больше ×5. */
    val maxZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value
            ?.let { MAX_ZOOM.coerceAtMost(it.maxZoomRatio) }
            ?: 1f

    /** Минимальный зум обычно равен 1, но у некоторых камер он больше. */
    private val minZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value
            ?.let { maxOf(1f, it.minZoomRatio) }
            ?: 1f

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = ProcessCameraProvider.getInstance(context).await(context)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
            .build()

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
        imageCapture = capture
    }

    /**
     * Устанавливает кратность зума и возвращает реально применённое значение.
     * Ограничение ×5 накладывается поверх диапазона, который сообщает камера.
     */
    fun setZoomRatio(value: Float): Float {
        val active = camera ?: return 1f
        val applied = value.coerceIn(minZoomRatio, maxZoomRatio)
        active.cameraControl.setZoomRatio(applied)
        return applied
    }

    fun capture(outputFile: File, onResult: (Result<File>) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(Result.failure(IllegalStateException("Камера не готова")))
            return
        }
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    // Некоторые устройства пишут JPEG с EXIF-тегом ориентации, но
                    // часть просмотрщиков его не учитывает — кадр выходит
                    // повёрнутым на 90°. Поэтому ориентация применяется к
                    // пикселям сразу, а тег сбрасывается.
                    normalizeOrientation(outputFile)
                    onResult(Result.success(outputFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            },
        )
    }

    /**
     * Применяет EXIF-ориентацию к пикселям и сбрасывает тег.
     * После этого картинка одинаково отображается в любом просмотрщике.
     */
    private fun normalizeOrientation(file: File) {
        runCatching {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return@runCatching

            val source = BitmapFactory.decodeFile(file.path) ?: return@runCatching
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true,
            )
            if (rotated !== source) source.recycle()

            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            rotated.recycle()

            ExifInterface(file).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                saveAttributes()
            }
        }
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}
