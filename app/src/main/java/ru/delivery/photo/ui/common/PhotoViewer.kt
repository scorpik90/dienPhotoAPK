package ru.delivery.photo.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory

/**
 * Полноэкранный просмотр фотографии.
 *
 * Открывается тапом по миниатюре — на вкладках «Съёмка» и «Фото».
 */
@Composable
fun FullScreenPhotoViewer(
    filePath: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val bitmap = remember(filePath) { BitmapFactory.decodeFile(filePath) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
        }

        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить фото", tint = Color.White)
            }
        }
    }
}
