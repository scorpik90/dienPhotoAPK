package ru.delivery.photo.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.delivery.photo.domain.ShareStrategy
import ru.delivery.photo.share.ShareDispatcher
import ru.delivery.photo.ui.AppViewModel
import ru.delivery.photo.ui.ShareRequest

/**
 * Координатор отправки через системный share-sheet.
 *
 * Поддерживает стратегии: альбом (все фото одним сообщением), части (порциями)
 * и коллаж (одно изображение). При стратегии «части» share-sheet вызывается
 * последовательно для каждой порции — пользователь в каждый раз выбирает чат.
 *
 * [start] и [resendAll] назначаются композитной функцией; подготовку пакета
 * задаёт вызывающий экран: точки разгрузки или весь рейс из истории.
 */
@Stable
class ShareCoordinator {
    var chunks by mutableStateOf<List<ShareRequest>>(emptyList())
    var index by mutableStateOf(0)
    var busy by mutableStateOf(false)

    /** Запуск отправки; назначается композитной функцией при создании координатора. */
    var start: (() -> Unit)? = null

    /** Повторная отправка уже отправленных фотографий. */
    var resendAll: (() -> Unit)? = null

    val current: ShareRequest? get() = chunks.getOrNull(index)
    val isEmpty: Boolean get() = chunks.isEmpty()
}

/**
 * Создаёт координатор отправки, связанный с системным диалогом выбора приложения.
 *
 * @param prepare подготовка пакета. По умолчанию — фотографии текущей точки;
 *   экран истории передаёт подготовку отчёта по всему рейсу.
 */
@Composable
fun rememberShareCoordinator(
    vm: AppViewModel,
    prepare: suspend (includeSent: Boolean) -> ShareRequest? = { includeSent ->
        vm.prepareShare(includeSent)
    },
): ShareCoordinator {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dispatcher = remember { ShareDispatcher(context) }
    val coordinator = remember { ShareCoordinator() }

    var launch: ((ShareRequest) -> Unit)? = null

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val chunks = coordinator.chunks
        if (chunks.isEmpty()) return@rememberLauncherForActivityResult

        if (coordinator.index < chunks.lastIndex) {
            coordinator.index += 1
            launch?.invoke(chunks[coordinator.index])
        } else {
            val request = chunks.last()
            coordinator.chunks = emptyList()
            coordinator.index = 0
            coordinator.busy = false
            scope.launch { vm.completeShare(request) }
        }
    }

    launch = { request ->
        val intent = if (request.strategy == ShareStrategy.COLLAGE && request.files.size == 1) {
            dispatcher.buildSingle(request.files.first(), ShareDispatcher.MIME_JPEG, request.caption)
        } else {
            dispatcher.buildMultiple(request.files, request.caption)
        }
        launcher.launch(Intent.createChooser(intent, "Отправить фото в MAX"))
    }

    /**
     * Подготовка и запуск отправки: коллаж при необходимости, разбиение на части,
     * открытие системного share-sheet.
     */
    suspend fun prepareAndLaunch(includeSent: Boolean) {
        val request = prepare(includeSent) ?: return
        val prepared = if (request.strategy == ShareStrategy.COLLAGE) {
            vm.buildCollage(request) ?: return
        } else {
            request
        }
        val parts = vm.chunkRequest(prepared)
        if (parts.isEmpty()) return
        coordinator.chunks = parts
        coordinator.index = 0
        coordinator.busy = true
        launch?.invoke(parts.first())
    }

    coordinator.start = { scope.launch { prepareAndLaunch(includeSent = false) } }
    coordinator.resendAll = { scope.launch { prepareAndLaunch(includeSent = true) } }

    return coordinator
}
