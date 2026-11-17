package ru.delivery.photo.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.delivery.photo.data.db.CrewMemberEntity
import ru.delivery.photo.data.db.DeliveryPointEntity
import ru.delivery.photo.data.db.PackageEntity
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.data.db.TripEntity
import ru.delivery.photo.domain.TimeFmt
import ru.delivery.photo.ui.AppViewModel
import ru.delivery.photo.ui.common.FullScreenPhotoViewer
import ru.delivery.photo.ui.common.SectionCard
import ru.delivery.photo.ui.common.rememberShareCoordinator

/**
 * История завершённых рейсов.
 *
 * Список показывает, когда была смена, кто и на чём работал, сколько точек
 * и фотографий. Отдельная запись раскрывает полные данные рейса и позволяет
 * отправить отчёт по всем фото одним пакетом — так же, как по точке разгрузки.
 */
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val summaries by vm.historySummaries.collectAsStateWithLifecycle()
    val openedTrip by vm.historyTrip.collectAsStateWithLifecycle()

    if (openedTrip != null) {
        TripDetail(vm = vm, trip = openedTrip!!, onBack = vm::closeHistoryTrip)
    } else {
        HistoryList(items = summaries, onOpen = vm::openHistoryTrip)
    }
}

@Composable
private fun HistoryList(
    items: List<AppViewModel.TripSummary>,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "История рейсов",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Завершённых рейсов пока нет.\nЗавершите смену на вкладке «Съёмка».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.trip.id }) { summary ->
                TripHistoryCard(summary = summary, onClick = { onOpen(summary.trip.id) })
            }
        }
    }
}

@Composable
private fun TripHistoryCard(
    summary: AppViewModel.TripSummary,
    onClick: () -> Unit,
) {
    val trip = summary.trip
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Рейс ${trip.routeNo}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                periodText(trip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val drivers = summary.drivers.joinToString(", ").ifBlank {
                listOf(trip.driverName, trip.vehicle).filter { it.isNotBlank() }.joinToString(", ")
            }
            if (drivers.isNotBlank()) {
                Text("Водители: $drivers", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Точек разгрузки: ${summary.points.size} · Фото: ${summary.photoCount}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TripDetail(
    vm: AppViewModel,
    trip: TripEntity,
    onBack: () -> Unit,
) {
    val crew by vm.historyCrew.collectAsStateWithLifecycle()
    val points by vm.historyPoints.collectAsStateWithLifecycle()
    val photos by vm.historyPhotos.collectAsStateWithLifecycle()
    val packages by vm.historyPackages.collectAsStateWithLifecycle()

    var viewingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    // Свой координатор отправки: готовит пакет по всему рейсу, а не по точке.
    val share = rememberShareCoordinator(
        vm = vm,
        prepare = { includeSent -> vm.prepareTripShare(trip.id, includeSent) },
    )

    val unsent = photos.count { it.sentAt == null }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(Modifier.weight(1f)) {
                Text("Рейс ${trip.routeNo}", style = MaterialTheme.typography.titleMedium)
                Text(periodText(trip), style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard(title = "Экипаж и маршрут") {
                    if (crew.isEmpty()) {
                        Text(
                            listOf(trip.driverName, trip.vehicle)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                                .ifBlank { "Данные отсутствуют" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        crew.forEach { member -> CrewLine(member) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Точки разгрузки:", style = MaterialTheme.typography.labelMedium)
                    if (points.isEmpty()) {
                        Text("Нет", style = MaterialTheme.typography.bodySmall)
                    } else {
                        points.forEachIndexed { index, point ->
                            Text(
                                "${index + 1}. ${point.name}" +
                                    if (point.address.isNullOrBlank()) "" else " · ${point.address}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        "Отправок: ${packages.size} · Фото: ${photos.size}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (photos.isEmpty()) {
                item {
                    Text(
                        "В этом рейсе нет фотографий",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            items(points, key = { it.id }) { point ->
                PointPhotosCard(
                    point = point,
                    photos = photos.filter { it.pointId == point.id },
                    onPhotoClick = { viewingPhoto = it },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { share.start?.invoke() },
                enabled = photos.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("  Отчёт по рейсу ($unsent)")
            }
            OutlinedButton(
                onClick = { share.resendAll?.invoke() },
                enabled = photos.isNotEmpty(),
            ) { Text("Заново") }
        }
    }

    viewingPhoto?.let { photo ->
        FullScreenPhotoViewer(
            filePath = photo.filePath,
            onDismiss = { viewingPhoto = null },
            // История — архив: удаление фото отсюда не допускается.
        )
    }
}

@Composable
private fun CrewLine(member: CrewMemberEntity) {
    Text(
        buildString {
            append(member.name.ifBlank { "Водитель не указан" })
            if (member.vehicle.isNotBlank()) append(" · ").append(member.vehicle)
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PointPhotosCard(
    point: DeliveryPointEntity,
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit,
) {
    SectionCard(title = point.name, modifier = Modifier.fillMaxWidth()) {
        if (!point.address.isNullOrBlank()) {
            Text(point.address, style = MaterialTheme.typography.bodySmall)
        }
        if (photos.isEmpty()) {
            Text("Фотографий нет", style = MaterialTheme.typography.bodySmall)
            return@SectionCard
        }
        Text("Фото: ${photos.size}", style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.id }) { photo -> PhotoThumb(photo, onPhotoClick) }
        }
    }
}

@Composable
private fun PhotoThumb(photo: PhotoEntity, onClick: (PhotoEntity) -> Unit) {
    val bitmap = remember(photo.filePath) { BitmapFactory.decodeFile(photo.filePath) }
    Box(
        Modifier
            .size(72.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(photo) },
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
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun periodText(trip: TripEntity): String {
    val start = "${TimeFmt.date(trip.startedAt)} ${TimeFmt.time(trip.startedAt)}"
    val end = trip.finishedAt?.let { "${TimeFmt.date(it)} ${TimeFmt.time(it)}" }
    return if (end == null) "Начало: $start" else "$start — $end"
}
