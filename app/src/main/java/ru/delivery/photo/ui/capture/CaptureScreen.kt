package ru.delivery.photo.ui.capture

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.delivery.photo.camera.CameraController
import ru.delivery.photo.data.db.DeliveryPointEntity
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.domain.PhotoKind
import ru.delivery.photo.ui.AppViewModel
import ru.delivery.photo.ui.ReviewState
import ru.delivery.photo.ui.common.AppSettingsDialog
import ru.delivery.photo.ui.common.CrewDraft
import ru.delivery.photo.ui.common.CrewSelector
import ru.delivery.photo.ui.common.PointSelector
import ru.delivery.photo.ui.common.SectionCard
import ru.delivery.photo.ui.common.ShareCoordinator
import ru.delivery.photo.ui.common.title
import ru.delivery.photo.ui.theme.Spacing
import ru.delivery.photo.util.hasCameraPermission
import ru.delivery.photo.util.hasLocationPermission
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    vm: AppViewModel,
    share: ShareCoordinator,
    onOpenPhotos: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val trip by vm.activeTrip.collectAsStateWithLifecycle()
    val points by vm.currentTripPoints.collectAsStateWithLifecycle()
    val pointId by vm.currentPointId.collectAsStateWithLifecycle()
    val photos by vm.currentPointPhotos.collectAsStateWithLifecycle()
    val kind by vm.currentKind.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val currentCrew by vm.currentCrew.collectAsStateWithLifecycle()

    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    var hasLocation by remember { mutableStateOf(context.hasLocationPermission()) }
    var showTripDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var tripCrew by remember { mutableStateOf(emptyList<CrewDraft>()) }
    var pendingCrew by remember { mutableStateOf(emptyList<CrewDraft>()) }

    // Состав экипажа для диалога подгружаем заранее — открывать диалог
    // с пустым списком нельзя.
    LaunchedEffect(trip?.id) {
        pendingCrew = vm.crewDrafts().ifEmpty {
            listOf(CrewDraft(name = trip?.driverName.orEmpty(), vehicle = trip?.vehicle.orEmpty()))
        }
    }
    var showPointDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<DeliveryPointEntity?>(null) }
    var deletingPoint by remember { mutableStateOf<DeliveryPointEntity?>(null) }
    var viewingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocation = granted }

    LaunchedEffect(Unit) {
        if (!context.hasCameraPermission()) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        if (!context.hasLocationPermission()) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val controller = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    // Зум и фонарик. Диапазон/наличие вспышки уточняются после привязки камеры.
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }

    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            runCatching {
                controller.bind(lifecycleOwner, previewView)
                maxZoom = controller.maxZoomRatio
                zoom = controller.setZoomRatio(1f)
                hasFlash = controller.hasFlashUnit
                torchOn = false
                controller.setTorch(false)
            }.onFailure { vm.notify("Камера недоступна: ${it.message}") }
        } else {
            torchOn = false
            hasFlash = false
        }
    }

    // Координаты берутся из системы автоматически — показываем их, чтобы
    // водитель видел, что геометка пишется.
    var gpsText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val locationProvider = (context.applicationContext as? ru.delivery.photo.DeliveryPhotoApp)
            ?.locationProvider
        while (true) {
            val location = locationProvider?.lastKnown()
            gpsText = if (location != null) {
                val acc = location.accuracy?.let { " ±${it.toInt()} м" } ?: ""
                "${String.format(Locale.US, "%.5f", location.latitude)}, " +
                    "${String.format(Locale.US, "%.5f", location.longitude)}$acc"
            } else {
                "Определение координат…"
            }
            delay(3_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------------- шапка
        SectionCard(
            title = "Рейс",
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trip == null) {
                        TextButton(onClick = {
                            tripCrew = pendingCrew
                            showTripDialog = true
                        }) { Text("Начать") }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                        }
                    } else {
                        TextButton(onClick = {
                            tripCrew = pendingCrew
                            showTripDialog = true
                        }) { Text("Изменить") }
                        TextButton(onClick = { showFinishDialog = true }) { Text("Завершить") }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                        }
                    }
                }
            },
        ) {
            val tripEntity = trip
            if (tripEntity == null) {
                Text("Рейс не начат", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    buildString {
                        append(tripEntity.routeNo)
                        val name = currentCrew?.name ?: tripEntity.driverName
                        val vehicle = currentCrew?.vehicle ?: tripEntity.vehicle
                        if (name.isNotBlank()) append(" · ").append(name)
                        if (vehicle.isNotBlank()) append(" · ").append(vehicle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                CrewSelector(
                    members = crew,
                    currentId = currentCrew?.id,
                    onSelect = vm::switchCrew,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        // ------------------------------------------------- точка разгрузки
        SectionCard(
            title = null,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        ) {
            PointSelector(
                points = points,
                selectedId = pointId,
                onSelect = vm::selectPoint,
                onCreate = { showPointDialog = true },
                onRename = { point -> editingPoint = point },
                onDelete = { point -> deletingPoint = point },
            )
            if (!hasLocation) {
                Text(
                    "Геолокация не разрешена — координаты в штампе будут недоступны",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // --------------------------------------------------- камера
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCamera) {
                // Жест «щипок» меняет кратность; кнопки ниже — запасной способ.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, factor, _ ->
                                zoom = controller.setZoomRatio(zoom * factor)
                            }
                        }
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет доступа к камере", color = Color.White)
                    Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                        Text("Разрешить")
                    }
                }
            }

            // Рамка распознавания — под контролами, чтобы кнопки не выглядели неактивными.
            if (kind == PhotoKind.LABEL) {
                LabelGuideFrame(Modifier.fillMaxSize())
            }

            // Управление зумом и фонариком.
            if (hasCamera && (maxZoom > 1f || hasFlash)) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (hasFlash) {
                        IconButton(
                            onClick = {
                                val next = !torchOn
                                torchOn = next
                                controller.setTorch(next)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (torchOn) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    } else {
                                        Color.Black.copy(alpha = 0.4f)
                                    },
                                ),
                        ) {
                            Icon(
                                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = if (torchOn) "Выключить фонарик" else "Включить фонарик",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    if (maxZoom > 1f) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                IconButton(
                                    onClick = { zoom = controller.setZoomRatio(zoom - 0.5f) },
                                    enabled = zoom > 1f,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Уменьшить",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f×", zoom),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                IconButton(
                                    onClick = { zoom = controller.setZoomRatio(zoom + 0.5f) },
                                    enabled = zoom < maxZoom,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Увеличить",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Индикатор геометки поверх превью.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.md),
                color = Color.Black.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = gpsText ?: "Определение координат…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Нижняя панель: тип слева, затвор по центру, тип справа.
            CameraBottomBar(
                kind = kind,
                onSelectKind = vm::setKind,
                onCapture = {
                    val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg")
                    controller.capture(file) { result ->
                        result
                            .onSuccess { vm.processCapture(it) }
                            .onFailure { vm.notify("Не удалось сделать снимок: ${it.message}") }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (busy) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // ------------------------------------------------------ снимки
        SectionCard(
            title = "Снимки точки: ${photos.size}",
            modifier = Modifier.padding(horizontal = Spacing.screen),
            trailing = {
                Row {
                    TextButton(onClick = onOpenPhotos) { Text("Все") }
                    IconButton(onClick = { share.start?.invoke() }, enabled = photos.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить в MAX")
                    }
                }
            },
        ) {
            if (photos.isEmpty()) {
                Text("В этой точке ещё нет фотографий", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(photos, key = { it.id }) { photo ->
                        val bitmap = remember(photo.filePath) {
                            BitmapFactory.decodeFile(photo.filePath)
                        }
                        Box(
                            Modifier
                                .size(Spacing.thumb)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewingPhoto = photo },
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (photo.sentAt != null) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(Spacing.badge)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }

    if (showTripDialog) {
        ru.delivery.photo.ui.common.TripDialog(
            crew = tripCrew,
            onCrewChange = { tripCrew = it },
            onDismiss = { showTripDialog = false },
            onSave = {
                val crew = tripCrew.filter { it.name.isNotBlank() }
                if (trip == null) {
                    vm.startTrip(crew)
                } else {
                    vm.updateTrip(crew)
                }
                showTripDialog = false
            },
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Завершить смену?") },
            text = {
                Text(
                    "Рейс и все фотографии будут перенесены в раздел «История рейсов». " +
                        "Продолжить съёмку можно будет в новом рейсе.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.finishTrip()
                    showFinishDialog = false
                }) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Отмена") }
            },
        )
    }

    editingPoint?.let { point ->
        var name by remember(point.id) { mutableStateOf(point.name) }
        var address by remember(point.id) { mutableStateOf(point.address.orEmpty()) }
        ru.delivery.photo.ui.common.EditPointDialog(
            name = name,
            address = address,
            onNameChange = { name = it },
            onAddressChange = { address = it },
            onDismiss = { editingPoint = null },
            onConfirm = {
                vm.updatePoint(point.id, name, address)
                editingPoint = null
            },
            onMessage = vm::notify,
        )
    }

    deletingPoint?.let { point ->
        ru.delivery.photo.ui.common.DeletePointDialog(
            name = point.name,
            onDismiss = { deletingPoint = null },
            onConfirm = {
                vm.deletePoint(point.id)
                deletingPoint = null
            },
        )
    }

    viewingPhoto?.let { photo ->
        ru.delivery.photo.ui.common.FullScreenPhotoViewer(
            filePath = photo.filePath,
            onDismiss = { viewingPhoto = null },
            onDelete = {
                vm.deletePhoto(photo)
                viewingPhoto = null
            },
        )
    }

    if (showPointDialog) {
        ru.delivery.photo.ui.common.DeliveryPointDialog(
            onDismiss = { showPointDialog = false },
            onConfirm = { name, address ->
                vm.createPoint(name, address)
                showPointDialog = false
            },
            onMessage = vm::notify,
        )
    }

    if (showSettings) {
        AppSettingsDialog(vm = vm, onDismiss = { showSettings = false })
    }

    review?.let { state ->
        ReviewDialog(
            state = state,
            onModelChange = vm::setReviewModel,
            onSerialChange = vm::setReviewSerial,
            onConfirm = vm::confirmReview,
            onSkip = vm::confirmWithoutSerial,
            onDiscard = vm::discardReview,
        )
    }
}

/**
 * Панель камеры: слева «Объект», в центре затвор, справа «Этикетка».
 * Выбранный тип подсвечен, чтобы переключение было явным.
 */
@Composable
private fun CameraBottomBar(
    kind: PhotoKind,
    onSelectKind: (PhotoKind) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraKindButton(
            text = PhotoKind.OBJECT.title,
            selected = kind == PhotoKind.OBJECT,
            onClick = { onSelectKind(PhotoKind.OBJECT) },
        )
        IconButton(
            onClick = onCapture,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f)),
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = "Снять",
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
            )
        }
        CameraKindButton(
            text = PhotoKind.LABEL.title,
            selected = kind == PhotoKind.LABEL,
            onClick = { onSelectKind(PhotoKind.LABEL) },
        )
    }
}

@Composable
private fun CameraKindButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        modifier = Modifier.width(100.dp),
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/**
 * Рамка-подсказка для съёмки шильдика.
 *
 * Шильдик занимает на кадре небольшую долю, а фон (небо, асфальт) мешает
 * распознаванию. Рамка показывает область, которая будет вырезана для OCR.
 */
@Composable
private fun LabelGuideFrame(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val frameWidth = size.width * 0.92f
        val frameHeight = size.height * 0.62f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f

        val shade = Color.Black.copy(alpha = 0.28f)
        drawRect(color = shade, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(
            color = shade,
            topLeft = Offset(0f, top + frameHeight),
            size = Size(size.width, size.height - top - frameHeight),
        )
        drawRect(color = shade, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(
            color = shade,
            topLeft = Offset(left + frameWidth, top),
            size = Size(size.width - left - frameWidth, frameHeight),
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3f),
        )
    }
}

/**
 * Диалог подтверждения снимка: предпросмотр, кандидаты модели и серийного номера
 * из распознавания и ручной ввод.
 */
@Composable
private fun ReviewDialog(
    state: ReviewState,
    onModelChange: (String) -> Unit,
    onSerialChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onDiscard: () -> Unit,
) {
    val bitmap = remember(state.previewFile.path, state.photoAddress) {
        BitmapFactory.decodeFile(state.previewFile.path)
    }

    AlertDialog(
        onDismissRequest = { /* требуется явное решение — не закрываем случайно */ },
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Проверьте снимок")
                IconButton(onClick = onDiscard) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить снимок")
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Fit,
                    )
                }

                Text(
                    "Тип: ${state.kind.title}",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (state.kind == PhotoKind.LABEL) {
                    if (state.extracting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Распознаём этикетку…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "Модель",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.modelCandidates.isEmpty()) {
                            Text(
                                "Модель не распознана. Введите её вручную или пропустите.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                items(state.modelCandidates) { candidate ->
                                    FilterChip(
                                        selected = candidate == state.selectedModel,
                                        onClick = { onModelChange(candidate) },
                                        label = {
                                            Text(candidate, fontFamily = FontFamily.Monospace)
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.selectedModel,
                            onValueChange = onModelChange,
                            label = { Text("Модель") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            "Серийный номер",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.candidates.isEmpty()) {
                            Text(
                                "Серийный номер не распознан. Введите его вручную или пропустите.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                items(state.candidates) { candidate ->
                                    FilterChip(
                                        selected = candidate == state.selected,
                                        onClick = { onSerialChange(candidate) },
                                        label = {
                                            Text(candidate, fontFamily = FontFamily.Monospace)
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.selected,
                            onValueChange = onSerialChange,
                            label = { Text("Серийный номер") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Text(
                    buildString {
                        append("Время: ").append(
                            ru.delivery.photo.domain.TimeFmt.stamp(state.shotAt)
                        )
                        append('\n')
                        if (state.lat != null && state.lon != null) {
                            append("Координаты: ")
                            append(String.format(Locale.US, "%.5f", state.lat))
                            append(", ")
                            append(String.format(Locale.US, "%.5f", state.lon))
                            state.accuracyM?.let { append(" ±").append(it.toInt()).append(" м") }
                        } else {
                            append("Координаты недоступны")
                        }
                        state.photoAddress?.takeIf { it.isNotBlank() }?.let {
                            append('\n').append("Адрес: ").append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.extracting,
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (state.kind == PhotoKind.LABEL) {
                    TextButton(onClick = onSkip, enabled = !state.extracting) { Text("Пропустить") }
                }
                TextButton(onClick = onDiscard) { Text("Удалить") }
            }
        },
    )
}
