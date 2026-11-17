package ru.delivery.photo.ui.photos

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.domain.ThemeMode
import ru.delivery.photo.ui.AppViewModel
import ru.delivery.photo.ui.common.FullScreenPhotoViewer
import ru.delivery.photo.ui.common.ShareCoordinator
import ru.delivery.photo.ui.common.ShareSettingsDialog

/**
 * Галерея фотографий выбранной точки разгрузки.
 *
 * Списка точек здесь нет: он живёт на вкладке «Съёмка», где точку можно
 * выбрать, переименовать и удалить.
 */
@Composable
fun PhotosScreen(
    vm: AppViewModel,
    share: ShareCoordinator,
) {
    val trip by vm.activeTrip.collectAsStateWithLifecycle()
    val points by vm.currentTripPoints.collectAsStateWithLifecycle()
    val pointId by vm.currentPointId.collectAsStateWithLifecycle()
    val photos by vm.currentPointPhotos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    val tripEntity = trip
    val currentStrategy = ru.delivery.photo.domain.ShareStrategy.from(settings[AppViewModel.KEY_SHARE_STRATEGY])
    val chunkSize = settings[AppViewModel.KEY_CHUNK_SIZE]?.toIntOrNull() ?: AppViewModel.DEFAULT_CHUNK_SIZE
    val companyName = settings[AppViewModel.KEY_COMPANY_NAME] ?: AppViewModel.DEFAULT_COMPANY_NAME
    val pointName = points.firstOrNull { it.id == pointId }?.name

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = pointName ?: "Точка не выбрана",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (tripEntity != null) {
                    Text(
                        "Рейс ${tripEntity.routeNo}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                PhotoCell(photo = photo, onClick = { viewingPhoto = photo })
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { share.start?.invoke() }, enabled = photos.isNotEmpty()) {
                Text("Отправить в MAX (${photos.count { it.sentAt == null }})")
            }
            TextButton(onClick = { share.resendAll?.invoke() }, enabled = photos.isNotEmpty()) {
                Text("Отправить заново")
            }
        }
    }

    if (showSettings) {
        ShareSettingsDialog(
            strategy = currentStrategy,
            chunkSize = chunkSize,
            companyName = companyName,
            themeMode = ThemeMode.from(settings[AppViewModel.KEY_THEME_MODE]),
            onDismiss = { showSettings = false },
            onStrategy = vm::setShareStrategy,
            onChunkSize = vm::setChunkSize,
            onCompanyName = vm::setCompanyName,
            onThemeMode = vm::setThemeMode,
        )
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
private fun PhotoCell(photo: PhotoEntity, onClick: () -> Unit) {
    val bitmap = remember(photo.filePath) { BitmapFactory.decodeFile(photo.filePath) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
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
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
