package ru.delivery.photo.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ожидание результата ListenableFuture в корутине. */
suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
