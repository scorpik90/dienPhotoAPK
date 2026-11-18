package ru.delivery.photo.ui.common

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ru.delivery.photo.ui.theme.Spacing

/**
 * Полноэкранный просмотр фотографии.
 *
 * Открывается тапом по миниатюре — на вкладках «Съёмка», «Фото» и в истории.
 * Системный жест «назад» закрывает только просмотр, не меняя вкладку.
 */
@Composable
fun FullScreenPhotoViewer(
    filePath: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val bitmap = remember(filePath) { BitmapFactory.decodeFile(filePath) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Пока открыт диалог подтверждения, первый «назад» закрывает его.
    BackHandler(enabled = !confirmDelete) { onDismiss() }

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

        if (onDelete != null) {
            IconButton(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.md)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить фото", tint = Color.White)
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.md)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.medium),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить фото?") },
            text = {
                Text(
                    "Фотография будет удалена из отчёта и с устройства. " +
                        "Отменить это действие нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}
