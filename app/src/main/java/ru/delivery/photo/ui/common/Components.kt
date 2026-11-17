package ru.delivery.photo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import ru.delivery.photo.data.db.CrewMemberEntity
import ru.delivery.photo.data.db.DeliveryPointEntity
import ru.delivery.photo.domain.PhotoKind
import ru.delivery.photo.domain.ShareStrategy
import ru.delivery.photo.domain.ThemeMode

/** Карточка с заголовком и содержимым. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Водитель рейса: ФИО и автомобиль. */
data class CrewDraft(
    val id: String? = null,
    val name: String = "",
    val vehicle: String = "",
)

/**
 * Состав экипажа рейса.
 *
 * Номер рейса пользователь не вводит — он создаётся автоматически из времени
 * начала смены. Остаётся только ФИО водителя и автомобиль.
 *
 * Диалог управляемый: состояние держит вызывающий код. Иначе начальные
 * значения запоминаются один раз, и данные, пришедшие позже, не подхватятся.
 */
@Composable
fun TripDialog(
    crew: List<CrewDraft>,
    onCrewChange: (List<CrewDraft>) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Водители и автомобили") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Водители", style = MaterialTheme.typography.titleSmall)

                crew.forEachIndexed { index, member ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = member.name,
                            onValueChange = { value ->
                                onCrewChange(
                                    crew.toMutableList().also { it[index] = member.copy(name = value) }
                                )
                            },
                            label = { Text("ФИО") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = member.vehicle,
                            onValueChange = { value ->
                                onCrewChange(
                                    crew.toMutableList().also { it[index] = member.copy(vehicle = value) }
                                )
                            },
                            label = { Text("Автомобиль") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (crew.size > 1) {
                            IconButton(onClick = {
                                onCrewChange(crew.filterIndexed { i, _ -> i != index })
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Убрать водителя",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onCrewChange(crew + CrewDraft()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Добавить водителя")
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Диалог создания точки разгрузки. */
@Composable
fun DeliveryPointDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая точка разгрузки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Точка — место, где вы разгрузили груз и сфотографировали выгруженное.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название точки / получатель") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес (необязательно)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, address.takeIf { it.isNotBlank() }) }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/**
 * Выбор точки разгрузки: выпадающий список с возможностью
 * переименовать и удалить точку.
 *
 * Список сделан на DropdownMenu, а не на ExposedDropdownMenuBox: последний
 * требует особого якоря и при ошибке приводит к падению приложения.
 */
@Composable
fun PointSelector(
    points: List<DeliveryPointEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (DeliveryPointEntity) -> Unit,
    onDelete: (DeliveryPointEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = points.firstOrNull { it.id == selectedId }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Точка разгрузки",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    selected?.name ?: "Не выбрана",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            points.forEach { point ->
                DropdownMenuItem(
                    text = { Text(point.name) },
                    onClick = {
                        onSelect(point.id)
                        expanded = false
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                expanded = false
                                onRename(point)
                            }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Переименовать",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = {
                                expanded = false
                                onDelete(point)
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Новая точка") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCreate()
                },
            )
        }
    }
}

/**
 * Сегментированный переключатель в духе iOS.
 *
 * Используется вместо набора чипов: на iOS это стандартный способ выбрать
 * один вариант из небольшого списка.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Переключатель водителя в рейсе.
 *
 * Показывается, только когда в рейсе больше одного человека: переключаться
 * между единственным водителем незачем.
 */
@Composable
fun CrewSelector(
    members: List<CrewMemberEntity>,
    currentId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (members.size <= 1) return

    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(members, key = { it.id }) { member ->
            val selected = member.id == currentId || (currentId == null && member == members.first())
            FilterChip(
                selected = selected,
                onClick = { onSelect(member.id) },
                label = {
                    Text(
                        if (member.vehicle.isBlank()) member.name
                        else "${member.name} · ${member.vehicle}"
                    )
                },
            )
        }
    }
}

/** Переименование точки разгрузки. */
@Composable
fun RenamePointDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать точку") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Название точки / получатель") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Удаление точки разгрузки вместе с её фотографиями. */
@Composable
fun DeletePointDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить точку?") },
        text = {
            Text(
                "Точка «$name» и все сделанные в ней фотографии будут удалены безвозвратно.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор типа фотографии. */
@Composable
fun PhotoKindSelector(
    selected: PhotoKind,
    onSelect: (PhotoKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = PhotoKind.entries
    SegmentedControl(
        options = kinds.map { it.title },
        selectedIndex = kinds.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(kinds[it]) },
        modifier = modifier,
    )
}

/** Настройки оформления и способа отправки. */
@Composable
fun ShareSettingsDialog(
    strategy: ShareStrategy,
    chunkSize: Int,
    companyName: String,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onStrategy: (ShareStrategy) -> Unit,
    onChunkSize: (Int) -> Unit,
    onCompanyName: (String) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    var localStrategy by remember { mutableStateOf(strategy) }
    var localChunk by remember { mutableStateOf(chunkSize.toString()) }
    var localCompany by remember { mutableStateOf(companyName) }
    var localTheme by remember { mutableStateOf(themeMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Оформление", style = MaterialTheme.typography.titleSmall)
                SegmentedControl(
                    options = ThemeMode.entries.map { it.title },
                    selectedIndex = ThemeMode.entries.indexOf(localTheme),
                    onSelect = { localTheme = ThemeMode.entries[it] },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = localCompany,
                    onValueChange = { localCompany = it },
                    label = { Text("Организация в водяном знаке") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Отправка фото", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Идёт через системное меню «Поделиться». Пользователь сам выбирает " +
                        "приложение MAX и чат.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SegmentedControl(
                    options = ShareStrategy.entries.map { it.shortTitle },
                    selectedIndex = ShareStrategy.entries.indexOf(localStrategy),
                    onSelect = { localStrategy = ShareStrategy.entries[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (localStrategy == ShareStrategy.CHUNKS) {
                    OutlinedTextField(
                        value = localChunk,
                        onValueChange = { localChunk = it.filter(Char::isDigit) },
                        label = { Text("Фото в одной части") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onStrategy(localStrategy)
                localChunk.toIntOrNull()?.takeIf { it in 1..50 }?.let(onChunkSize)
                onCompanyName(localCompany)
                onThemeMode(localTheme)
                onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Подпись типа фотографии на русском. */
val PhotoKind.title: String
    get() = when (this) {
        PhotoKind.OBJECT -> "Объект"
        PhotoKind.LABEL -> "Этикетка"
    }

/** Подпись стратегии отправки. */
val ShareStrategy.title: String
    get() = when (this) {
        ShareStrategy.ALBUM -> "Альбом: все фото одним сообщением"
        ShareStrategy.CHUNKS -> "Частями: порциями по N фото"
        ShareStrategy.COLLAGE -> "Коллаж: одно изображение со всеми кадрами"
    }

/** Короткая подпись стратегии — для сегментированного переключателя. */
val ShareStrategy.shortTitle: String
    get() = when (this) {
        ShareStrategy.ALBUM -> "Альбом"
        ShareStrategy.CHUNKS -> "Частями"
        ShareStrategy.COLLAGE -> "Коллаж"
    }

/** Подпись режима оформления. */
val ThemeMode.title: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Как в системе"
        ThemeMode.LIGHT -> "Светлая"
        ThemeMode.DARK -> "Тёмная"
    }
