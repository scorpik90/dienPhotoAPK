package ru.delivery.photo.ui.photos

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.ui.AppViewModel
import ru.delivery.photo.ui.common.FullScreenPhotoViewer
import ru.delivery.photo.ui.common.SectionCard
import ru.delivery.photo.ui.common.rememberShareCoordinator
import ru.delivery.photo.ui.theme.Spacing

/**
 * Галерея всех фотографий текущего рейса, сгруппированных по точкам разгрузки.
 */
@Composable
fun PhotosScreen(
    vm: AppViewModel,
) {
    val trip by vm.activeTrip.collectAsStateWithLifecycle()
    val points by vm.currentTripPoints.collectAsStateWithLifecycle()
    val photos by vm.currentTripPhotos.collectAsStateWithLifecycle()

    var viewingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    val tripEntity = trip
    // Отправка с вкладки «Фото» — отчёт по всему рейсу, а не по одной точке.
    val tripShare = rememberShareCoordinator(vm) { includeSent ->
        tripEntity?.let { vm.prepareTripShare(it.id, includeSent) }
    }
    val groups = remember(points, photos) {
        points.map { point -> point to photos.filter { it.pointId == point.id } }
    }
    val ungrouped = remember(points, photos) {
        val known = points.map { it.id }.toSet()
        photos.filter { it.pointId !in known }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        ) {
            Text(
                text = "Фото по точкам",
                style = MaterialTheme.typography.titleMedium,
            )
            if (tripEntity != null) {
                Text(
                    "Рейс ${tripEntity.routeNo} · ${photos.size} шт.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Рейс не начат",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (photos.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "В этом рейсе пока нет фотографий. Снимайте на вкладке «Съёмка».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(groups, key = { it.first.id }) { (point, pointPhotos) ->
                    SectionCard(title = point.name) {
                        if (pointPhotos.isEmpty()) {
                            Text(
                                "Нет фото в этой точке",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            PhotoThumbRow(
                                photos = pointPhotos,
                                onPhotoClick = { viewingPhoto = it },
                            )
                        }
                    }
                }
                if (ungrouped.isNotEmpty()) {
                    item(key = "ungrouped") {
                        SectionCard(title = "Без точки") {
                            PhotoThumbRow(
                                photos = ungrouped,
                                onPhotoClick = { viewingPhoto = it },
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = { tripShare.start?.invoke() }, enabled = photos.isNotEmpty()) {
                Text("Отправить в MAX (${photos.count { it.sentAt == null }})")
            }
            TextButton(onClick = { tripShare.resendAll?.invoke() }, enabled = photos.isNotEmpty()) {
                Text("Отправить заново")
            }
        }
    }

    viewingPhoto?.let { photo ->
        FullScreenPhotoViewer(
            filePath = photo.filePath,
            onDismiss = { viewingPhoto = null },
            onDelete = {
                vm.deletePhoto(photo)
                viewingPhoto = null
            },
        )
    }
}

@Composable
private fun PhotoThumbRow(
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(photos, key = { it.id }) { photo ->
            val bitmap = remember(photo.filePath) { BitmapFactory.decodeFile(photo.filePath) }
            Box(
                Modifier
                    .size(Spacing.thumb)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPhotoClick(photo) },
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
