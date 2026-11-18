package ru.delivery.photo.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import ru.delivery.photo.util.await
import java.io.File

/**
 * Минимальная обёртка CameraX: предпросмотр и съёмка одного кадра в файл.
 */
class CameraController(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null

    /** Максимальный зум в текущем состоянии: аппаратный, но не больше ×5. */
    val maxZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value
            ?.let { MAX_ZOOM.coerceAtMost(it.maxZoomRatio) }
            ?: 1f

    /** Минимальный зум (обычно 1, но в линейных камерах может быть меньше). */
    private val minZoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value
            ?.let { maxOf(1f, it.minZoomRatio) }
            ?: 1f

    /** Есть ли встроенная вспышка/фонарик на текущей камере. */
    val hasFlashUnit: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Включить/выключить фонарик в режиме предпросмотра/съёмки. */
    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.previewView = previewView
        val provider = ProcessCameraProvider.getInstance(context).await(context)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
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
     * Устанавливает кратность зума и возвращает фактически применённое значение.
     * Ограничивает ×5 аппаратным максимумом: хакерский зум сверх камеры.
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
        // При разрешённом повороте экрана актуальный rotation берём в момент съёмки.
        capture.targetRotation = previewView?.display?.rotation ?: Surface.ROTATION_0
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    // Ориентация применяется при декодировании (WatermarkRenderer.decodeScaled)
                    // по EXIF, чтобы фото и водяной знак всегда были выровнены корректно.
                    onResult(Result.success(outputFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            },
        )
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}
