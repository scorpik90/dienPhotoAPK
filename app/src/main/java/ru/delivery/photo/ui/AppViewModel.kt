package ru.delivery.photo.ui

import android.app.Application
import android.graphics.Bitmap
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.delivery.photo.DeliveryPhotoApp
import ru.delivery.photo.data.DeliveryRepository
import ru.delivery.photo.data.PhotoStore
import ru.delivery.photo.data.db.CrewMemberEntity
import ru.delivery.photo.data.db.DeliveryPointEntity
import ru.delivery.photo.data.db.PackageEntity
import ru.delivery.photo.data.db.PhotoEntity
import ru.delivery.photo.data.db.TripEntity
import ru.delivery.photo.domain.GeoSource
import ru.delivery.photo.domain.PhotoKind
import ru.delivery.photo.domain.SerialPatterns
import ru.delivery.photo.domain.SerialSource
import ru.delivery.photo.domain.ShareStrategy
import ru.delivery.photo.domain.TimeFmt
import ru.delivery.photo.domain.StampData
import ru.delivery.photo.ui.common.CrewDraft
import ru.delivery.photo.domain.ThemeMode
import ru.delivery.photo.location.LocationProvider
import ru.delivery.photo.location.ReverseGeocoder
import ru.delivery.photo.serial.SerialExtractor
import ru.delivery.photo.share.CaptionBuilder
import ru.delivery.photo.share.CollageBuilder
import ru.delivery.photo.watermark.WatermarkRenderer
import java.io.File

/** Состояние подтверждения снимка: превью, модель, адрес и кандидаты серийного номера. */
data class ReviewState(
    val pointId: String,
    val tempFile: File,
    val previewFile: File,
    val kind: PhotoKind,
    val candidates: List<String>,
    val modelCandidates: List<String>,
    val rawText: String?,
    val barcodeValue: String?,
    val selected: String,
    val selectedModel: String,
    val extracting: Boolean,
    val shotAt: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val geoSource: GeoSource,
    val source: SerialSource,
    val photoAddress: String? = null,
)

/** Запрос на отправку: набор файлов, подпись и стратегия. */
data class ShareRequest(
    val tripId: String,
    val pointId: String,
    val photoIds: List<String>,
    val files: List<File>,
    val caption: String,
    val strategy: ShareStrategy,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val deliveryApp: DeliveryPhotoApp = application as DeliveryPhotoApp
    private val repository: DeliveryRepository = deliveryApp.repository
    private val store: PhotoStore = deliveryApp.photoStore
    private val locationProvider: LocationProvider = deliveryApp.locationProvider
    private val serialExtractor: SerialExtractor = deliveryApp.serialExtractor

    val trips: StateFlow<List<TripEntity>> = repository.trips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeTrip: StateFlow<TripEntity?> = repository.activeTrip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val packages: StateFlow<List<PackageEntity>> = repository.allPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Map<String, String>> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _currentPointId = MutableStateFlow<String?>(null)
    val currentPointId: StateFlow<String?> = _currentPointId.asStateFlow()

    private val _currentKind = MutableStateFlow(PhotoKind.OBJECT)
    val currentKind: StateFlow<PhotoKind> = _currentKind.asStateFlow()

    private val _review = MutableStateFlow<ReviewState?>(null)
    val review: StateFlow<ReviewState?> = _review.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    /** Точки разгрузки активного рейса. */
    val currentTripPoints: StateFlow<List<DeliveryPointEntity>> = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else repository.pointsForTrip(trip.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Экипаж активного рейса: один или несколько водителей с машинами. */
    val crew: StateFlow<List<CrewMemberEntity>> = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else repository.crewForTrip(trip.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentCrewId = MutableStateFlow<String?>(null)
    val currentCrewId: StateFlow<String?> = _currentCrewId.asStateFlow()

    /** Текущий водитель: выбранный, иначе первый из состава экипажа. */
    val currentCrew: StateFlow<CrewMemberEntity?> = combine(crew, _currentCrewId) { members, id ->
        members.firstOrNull { it.id == id } ?: members.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Фотографии выбранной точки разгрузки. */
    val currentPointPhotos: StateFlow<List<PhotoEntity>> = _currentPointId
        .flatMapLatest { pointId ->
            if (pointId == null) flowOf(emptyList()) else repository.photosForPoint(pointId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Все фотографии активного рейса — для вкладки «Фото». */
    val currentTripPhotos: StateFlow<List<PhotoEntity>> = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(emptyList()) else repository.photosForTrip(trip.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------- история

    private val _historyTripId = MutableStateFlow<String?>(null)

    /** Завершённые смены, от новых к старым. */
    val finishedTrips: StateFlow<List<TripEntity>> = trips
        .map { list -> list.filter { it.finishedAt != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Данные рейса для списка истории. */
    data class TripSummary(
        val trip: TripEntity,
        val drivers: List<String>,
        val points: List<DeliveryPointEntity>,
        val photoCount: Int,
    )

    val historySummaries: StateFlow<List<TripSummary>> = combine(
        finishedTrips,
        repository.allCrew,
        repository.allPoints,
        repository.allPhotos,
    ) { trips, crew, points, photos ->
        trips.map { trip ->
            TripSummary(
                trip = trip,
                drivers = crew.filter { it.tripId == trip.id }.map { member ->
                    listOf(member.name, member.vehicle)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                },
                points = points.filter { it.tripId == trip.id },
                photoCount = photos.count { it.tripId == trip.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Рейс, открытый в истории. */
    val historyTrip: StateFlow<TripEntity?> = combine(finishedTrips, _historyTripId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historyCrew: StateFlow<List<CrewMemberEntity>> = _historyTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.crewForTrip(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyPoints: StateFlow<List<DeliveryPointEntity>> = _historyTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.pointsForTrip(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyPhotos: StateFlow<List<PhotoEntity>> = _historyTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.photosForTrip(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyPackages: StateFlow<List<PackageEntity>> = _historyTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.packagesForTrip(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openHistoryTrip(id: String) {
        _historyTripId.value = id
    }

    fun closeHistoryTrip() {
        _historyTripId.value = null
    }

    val shareStrategy: ShareStrategy
        get() = ShareStrategy.from(settings.value[KEY_SHARE_STRATEGY])

    val chunkSize: Int
        get() = settings.value[KEY_CHUNK_SIZE]?.toIntOrNull() ?: DEFAULT_CHUNK_SIZE

    /** Название организации для водяного знака. */
    val companyName: String
        get() = settings.value[KEY_COMPANY_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_NAME

    /** Режим оформления: как в системе, светлое или тёмное. */
    val themeMode: StateFlow<ThemeMode> = settings
        .map { ThemeMode.from(it[KEY_THEME_MODE]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    init {
        // Выбранная точка разгрузки и текущий водитель хранятся в базе,
        // а не только в памяти: иначе после перезапуска приложения рейс виден
        // в шапке, а съёмка не работает — некуда привязать фотографию.
        viewModelScope.launch {
            val saved = repository.getSetting(KEY_CURRENT_POINT)?.takeIf { it.isNotBlank() }
            if (saved != null) {
                val point = repository.getPoint(saved)
                val trip = repository.trips.first().firstOrNull { it.finishedAt == null }
                if (point != null && trip != null && point.tripId == trip.id) {
                    _currentPointId.value = point.id
                }
            }
            val savedCrew = repository.getSetting(KEY_CURRENT_CREW)?.takeIf { it.isNotBlank() }
            if (savedCrew != null) _currentCrewId.value = savedCrew
        }
    }

    // ---------------------------------------------------------------- рейс

    fun startTrip(crew: List<CrewDraft>) {
        if (crew.isEmpty() || crew.first().name.isBlank()) {
            emit("Укажите ФИО хотя бы одного водителя")
            return
        }
        viewModelScope.launch {
            val first = crew.first()
            // Номер рейса не вводится: он создаётся из времени начала смены
            // и служит подписью в водяном знаке и в истории рейсов.
            val routeNo = TimeFmt.shortStamp(System.currentTimeMillis())
            val id = repository.startTrip(routeNo, first.name, first.vehicle)
            emit("Рейс начат")

            var firstCrewId: String? = null
            crew.forEach { member ->
                val crewId = repository.addCrewMember(id, member.name, member.vehicle)
                if (firstCrewId == null) firstCrewId = crewId
            }
            firstCrewId?.let {
                _currentCrewId.value = it
                repository.putSetting(KEY_CURRENT_CREW, it)
            }

            _currentPointId.value = null
            repository.putSetting(KEY_CURRENT_POINT, "")
        }
    }

    /** Изменение состава экипажа. Номер рейса создаётся автоматически и не меняется. */
    fun updateTrip(crew: List<CrewDraft>) {
        val trip = activeTrip.value ?: return
        if (crew.isEmpty() || crew.first().name.isBlank()) {
            emit("Укажите ФИО хотя бы одного водителя")
            return
        }
        viewModelScope.launch {
            repository.getCrew(trip.id).forEach { repository.deleteCrewMember(it.id) }

            var firstCrewId: String? = null
            crew.forEach { member ->
                val crewId = repository.addCrewMember(trip.id, member.name, member.vehicle)
                if (firstCrewId == null) firstCrewId = crewId
            }
            val current = _currentCrewId.value
            // Состав пересоздан — идентификаторы новые, поэтому прежнего
            // выбора сохранить нельзя: остаётся первый водитель.
            _currentCrewId.value = firstCrewId
            repository.putSetting(KEY_CURRENT_CREW, firstCrewId.orEmpty())
            emit("Состав экипажа обновлён")
        }
    }

    /** Состав экипажа для предзаполнения диалога рейса. */
    suspend fun crewDrafts(): List<CrewDraft> {
        val trip = activeTrip.value ?: return emptyList()
        return repository.getCrew(trip.id).map {
            CrewDraft(id = it.id, name = it.name, vehicle = it.vehicle)
        }
    }

    fun finishTrip() {
        val trip = activeTrip.value ?: return
        viewModelScope.launch {
            repository.finishTrip(trip.id)
            _currentPointId.value = null
            _currentCrewId.value = null
            repository.putSetting(KEY_CURRENT_POINT, "")
            repository.putSetting(KEY_CURRENT_CREW, "")
            emit("Рейс завершён и перенесён в историю")
        }
    }

    // -------------------------------------------------------------- экипаж

    fun addCrewMember(name: String, vehicle: String) {
        val trip = activeTrip.value ?: run {
            emit("Сначала начните рейс")
            return
        }
        if (name.isBlank()) {
            emit("Укажите ФИО водителя")
            return
        }
        viewModelScope.launch {
            val id = repository.addCrewMember(trip.id, name, vehicle)
            switchCrew(id)
            emit("Водитель $name добавлен в рейс")
        }
    }

    fun deleteCrewMember(id: String) {
        viewModelScope.launch {
            repository.deleteCrewMember(id)
            if (_currentCrewId.value == id) {
                _currentCrewId.value = null
                repository.putSetting(KEY_CURRENT_CREW, "")
            }
        }
    }

    fun switchCrew(id: String) {
        _currentCrewId.value = id
        viewModelScope.launch { repository.putSetting(KEY_CURRENT_CREW, id) }
    }

    // ----------------------------------------------------- точки разгрузки

    fun selectPoint(pointId: String) {
        _currentPointId.value = pointId
        viewModelScope.launch { repository.putSetting(KEY_CURRENT_POINT, pointId) }
    }

    fun createPoint(name: String, address: String?) {
        val trip = activeTrip.value ?: run {
            emit("Сначала начните рейс")
            return
        }
        if (name.isBlank()) {
            emit("Укажите название точки разгрузки")
            return
        }
        viewModelScope.launch {
            val id = repository.createPoint(trip.id, name, address)
            selectPoint(id)
            emit("Точка разгрузки «$name» создана")
        }
    }

    fun setKind(kind: PhotoKind) {
        _currentKind.value = kind
    }

    /** Обновление названия и адреса точки разгрузки. */
    fun updatePoint(id: String, name: String, address: String?) {
        if (name.isBlank()) {
            emit("Название точки не может быть пустым")
            return
        }
        viewModelScope.launch { repository.updatePoint(id, name, address) }
    }

    /** Удаление точки разгрузки вместе с её фотографиями. */
    fun deletePoint(id: String) {
        viewModelScope.launch {
            val photos = repository.deletePoint(id)
            photos.forEach { store.delete(it.filePath) }
            if (_currentPointId.value == id) {
                _currentPointId.value = null
                repository.putSetting(KEY_CURRENT_POINT, "")
            }
            emit("Точка разгрузки удалена")
        }
    }

    // ------------------------------------------------------------- съёмка

    /** Обрабатывает только что снятый кадр: штамп, геометка, адрес, подготовка к подтверждению. */
    fun processCapture(tempFile: File) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val trip = activeTrip.value
                val pointId = _currentPointId.value

                if (trip == null || pointId == null) {
                    emit("Сначала начните рейс и выберите точку разгрузки")
                    store.delete(tempFile.path)
                    return@launch
                }

                val point = repository.getPoint(pointId)
                val location = locationProvider.current()
                val provider = location?.provider
                val geoSource = when (provider) {
                    null -> GeoSource.NONE
                    LocationManager.GPS_PROVIDER -> GeoSource.GPS
                    else -> GeoSource.NETWORK
                }

                val shotAt = System.currentTimeMillis()
                val baseStamp = StampData(
                    companyName = companyName,
                    routeNo = trip.routeNo,
                    pointName = point?.name.orEmpty(),
                    model = null,
                    serial = null,
                    shotAtMillis = shotAt,
                    lat = location?.latitude,
                    lon = location?.longitude,
                    accuracyM = location?.accuracy,
                    driverName = currentCrew.value?.name ?: trip.driverName,
                    vehicle = currentCrew.value?.vehicle ?: trip.vehicle,
                    address = null,
                )

                val previewFile = store.newPhotoFile("preview-${System.currentTimeMillis()}")
                val previewOk = withContext(Dispatchers.Default) {
                    val source = WatermarkRenderer.decodeScaled(tempFile) ?: return@withContext false
                    try {
                        val previewBitmap = WatermarkRenderer.render(source, baseStamp)
                        try {
                            WatermarkRenderer.save(previewFile, previewBitmap, baseStamp)
                        } finally {
                            previewBitmap.recycle()
                        }
                        true
                    } finally {
                        source.recycle()
                    }
                }
                if (!previewOk) {
                    emit("Не удалось прочитать снимок")
                    store.delete(tempFile.path)
                    return@launch
                }

                val kind = _currentKind.value

                _review.value = ReviewState(
                    pointId = pointId,
                    tempFile = tempFile,
                    previewFile = previewFile,
                    kind = kind,
                    candidates = emptyList(),
                    modelCandidates = emptyList(),
                    rawText = null,
                    barcodeValue = null,
                    selected = "",
                    selectedModel = "",
                    extracting = kind == PhotoKind.LABEL,
                    shotAt = shotAt,
                    lat = location?.latitude,
                    lon = location?.longitude,
                    accuracyM = location?.accuracy,
                    geoSource = geoSource,
                    source = SerialSource.NONE,
                    photoAddress = null,
                )
                _busy.value = false

                val extractionDeferred = if (kind == PhotoKind.LABEL) {
                    viewModelScope.async(Dispatchers.Default) {
                        val bitmap = WatermarkRenderer.decodeScaled(tempFile, maxDim = 1600)
                        try {
                            if (bitmap == null) {
                                null
                            } else {
                                // Шильдик занимает небольшую часть кадра, а фон
                                // (небо, асфальт) мешает распознаванию. Для OCR берём
                                // только центральную область, куда водитель наводил
                                // рамку по подсказке.
                                val crop = cropForOcr(bitmap)
                                try {
                                    serialExtractor.extract(crop)
                                } finally {
                                    if (crop !== bitmap) crop.recycle()
                                }
                            }
                        } finally {
                            bitmap?.recycle()
                        }
                    }
                } else {
                    null
                }

                val addressDeferred = if (location != null) {
                    viewModelScope.async(Dispatchers.IO) {
                        ReverseGeocoder.geocode(
                            getApplication(),
                            location.latitude,
                            location.longitude,
                        )
                    }
                } else {
                    null
                }

                extractionDeferred?.let { deferred ->
                    val extraction = runCatching { deferred.await() }.getOrNull()
                    if (extraction != null) {
                        _review.value = _review.value?.takeIf { it.tempFile == tempFile }?.copy(
                            candidates = extraction.candidates,
                            modelCandidates = extraction.modelCandidates,
                            rawText = extraction.rawText,
                            barcodeValue = extraction.barcodeValue,
                            selected = extraction.candidates.firstOrNull().orEmpty(),
                            selectedModel = extraction.modelCandidates.firstOrNull().orEmpty(),
                            extracting = false,
                            source = if (extraction.barcodeValue != null) {
                                SerialSource.BARCODE
                            } else {
                                SerialSource.OCR
                            },
                        ) ?: _review.value
                    } else {
                        _review.value = _review.value?.takeIf { it.tempFile == tempFile }?.copy(extracting = false)
                            ?: _review.value
                    }
                }

                addressDeferred?.let { deferred ->
                    val photoAddress = runCatching { deferred.await() }.getOrNull()?.takeIf { it.isNotBlank() }
                    if (photoAddress != null) {
                        val current = _review.value
                        if (current != null && current.tempFile == tempFile) {
                            withContext(Dispatchers.Default) {
                                val source = WatermarkRenderer.decodeScaled(tempFile) ?: return@withContext
                                try {
                                    val previewBitmap = WatermarkRenderer.render(source, baseStamp.copy(address = photoAddress))
                                    try {
                                        WatermarkRenderer.save(previewFile, previewBitmap, baseStamp.copy(address = photoAddress))
                                    } finally {
                                        previewBitmap.recycle()
                                    }
                                } finally {
                                    source.recycle()
                                }
                            }
                            _review.value = _review.value?.takeIf { it.tempFile == tempFile }?.copy(photoAddress = photoAddress)
                                ?: _review.value
                        }
                    }
                }
            } catch (e: Exception) {
                emit("Ошибка обработки снимка: ${e.message}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun setReviewSerial(value: String) {
        _review.value = _review.value?.copy(selected = value)
    }

    fun setReviewModel(value: String) {
        _review.value = _review.value?.copy(selectedModel = value)
    }

    /** Подтверждает серийный номер и сохраняет финальный помеченный снимок. */
    fun confirmReview() {
        val state = _review.value ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val trip = activeTrip.value ?: return@launch
                val point = repository.getPoint(state.pointId)

                val model = state.selectedModel.trim().takeIf { it.isNotEmpty() }
                val serial = state.selected.trim().takeIf { it.isNotEmpty() }
                // Источник фиксируется честно: штрихкод, найденный кандидат
                // распознавания или ручной ввод. Это попадает в отчёт и позволяет
                // потом разобрать спорный случай.
                val serialSource = when {
                    serial == null -> SerialSource.NONE
                    state.barcodeValue != null &&
                        serial == SerialPatterns.normalize(state.barcodeValue) -> SerialSource.BARCODE
                    state.candidates.any { it == serial } -> SerialSource.OCR
                    else -> SerialSource.MANUAL
                }

                val stamp = StampData(
                    companyName = companyName,
                    routeNo = trip.routeNo,
                    pointName = point?.name.orEmpty(),
                    model = model,
                    serial = serial,
                    shotAtMillis = state.shotAt,
                    lat = state.lat,
                    lon = state.lon,
                    accuracyM = state.accuracyM,
                    driverName = currentCrew.value?.name ?: trip.driverName,
                    vehicle = currentCrew.value?.vehicle ?: trip.vehicle,
                    address = state.photoAddress,
                )

                val source = WatermarkRenderer.decodeScaled(state.tempFile)
                if (source == null) {
                    emit("Не удалось прочитать снимок")
                    return@launch
                }

                val finalFile = store.newPhotoFile("photo-${System.currentTimeMillis()}")
                val bitmap = WatermarkRenderer.render(source, stamp)
                val (w, h) = WatermarkRenderer.save(finalFile, bitmap, stamp)
                bitmap.recycle()
                source.recycle()

                repository.savePhoto(
                    tripId = trip.id,
                    pointId = state.pointId,
                    filePath = finalFile.path,
                    kind = state.kind,
                    model = model,
                    serial = serial,
                    serialSource = serialSource,
                    serialRawText = state.rawText,
                    shotAt = state.shotAt,
                    lat = state.lat,
                    lon = state.lon,
                    accuracyM = state.accuracyM,
                    geoSource = state.geoSource,
                    width = w,
                    height = h,
                    sha256 = PhotoStore.sha256(finalFile),
                )

                store.delete(state.previewFile.path)
                store.delete(state.tempFile.path)
                _review.value = null
                emit("Снимок сохранён")
            } catch (e: Exception) {
                emit("Ошибка сохранения: ${e.message}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Пропускает подтверждение модели и серийного номера. */
    fun confirmWithoutSerial() {
        _review.value = _review.value?.copy(selected = "", selectedModel = "")
        confirmReview()
    }

    /** Отменяет снимок целиком. */
    fun discardReview() {
        val state = _review.value ?: return
        store.delete(state.previewFile.path)
        store.delete(state.tempFile.path)
        _review.value = null
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            store.delete(photo.filePath)
            emit("Фото удалено")
        }
    }

    // ---------------------------------------------------------- отправка

    /**
     * Готовит набор файлов к отправке. Возвращает null, если отправлять нечего.
     *
     * @param includeSent повторная отправка — берём все фотографии точки,
     *   а не только ещё не отправленные.
     */
    suspend fun prepareShare(includeSent: Boolean = false): ShareRequest? {
        val trip = activeTrip.value ?: return null
        val pointId = _currentPointId.value ?: return null
        val point = repository.getPoint(pointId) ?: return null

        val all = repository.photosForPoint(pointId).first()
        val unsent = if (includeSent) all else all.filter { it.sentAt == null }
        if (unsent.isEmpty()) {
            emit("Все фотографии этой точки уже отправлены")
            return null
        }

        val caption = CaptionBuilder.build(
            routeNo = trip.routeNo,
            pointName = point.name,
            driverName = currentCrew.value?.name ?: trip.driverName,
            vehicle = currentCrew.value?.vehicle ?: trip.vehicle,
            photos = unsent,
        )

        val files = unsent.map { File(it.filePath) }.filter { it.exists() }
        if (files.isEmpty()) {
            emit("Файлы фотографий не найдены")
            return null
        }

        return ShareRequest(
            tripId = trip.id,
            pointId = pointId,
            photoIds = unsent.map { it.id },
            files = files,
            caption = caption,
            strategy = shareStrategy,
        )
    }

    /**
     * Готовит отчёт по всему рейсу из истории: все фотографии всех точек
     * одним пакетом. pointId пустой — пакет относится ко всему рейсу.
     */
    suspend fun prepareTripShare(tripId: String, includeSent: Boolean = false): ShareRequest? {
        val trip = repository.getTrip(tripId) ?: return null
        val all = repository.photosForTrip(tripId).first()
        val selected = if (includeSent) all else all.filter { it.sentAt == null }
        if (selected.isEmpty()) {
            emit("Все фотографии рейса уже отправлены")
            return null
        }

        val points = repository.pointsForTrip(tripId).first()
        val drivers = repository.getCrew(tripId).map { member ->
            listOf(member.name, member.vehicle).filter { it.isNotBlank() }.joinToString(", ")
        }
        val caption = CaptionBuilder.buildTrip(
            routeNo = trip.routeNo,
            drivers = drivers,
            pointNames = points.map { it.name },
            photos = selected,
        )

        val files = selected.map { File(it.filePath) }.filter { it.exists() }
        if (files.isEmpty()) {
            emit("Файлы фотографий не найдены")
            return null
        }

        return ShareRequest(
            tripId = tripId,
            pointId = "",
            photoIds = selected.map { it.id },
            files = files,
            caption = caption,
            strategy = shareStrategy,
        )
    }

    /**
     * Разбивает запрос на порции — когда мессенджер не собирает несколько файлов
     * в одно сообщение. Подпись каждой части содержит «часть N из M».
     */
    fun chunkRequest(request: ShareRequest): List<ShareRequest> {
        if (request.strategy != ShareStrategy.CHUNKS) return listOf(request)
        val parts = request.files.chunked(chunkSize.coerceAtLeast(1))
        return parts.mapIndexed { index, chunk ->
            request.copy(
                files = chunk,
                caption = "${request.caption}\nЧасть ${index + 1} из ${parts.size}",
            )
        }
    }

    /** Собирает коллаж — аварийный вариант, когда альбом недоступен. */
    suspend fun buildCollage(request: ShareRequest): ShareRequest? = withContext(Dispatchers.Default) {
        val target = File(store.reportDir(), "collage-${System.currentTimeMillis()}.jpg")
        val collage = CollageBuilder.build(request.files, target)
        if (collage == null) {
            emit("Не удалось собрать коллаж")
            null
        } else {
            request.copy(files = listOf(collage), caption = request.caption)
        }
    }

    /** Фиксирует факт отправки пакета. */
    suspend fun completeShare(request: ShareRequest): String {
        val packageId = repository.registerPackage(
            tripId = request.tripId,
            pointId = request.pointId,
            photoIds = request.photoIds,
            strategy = request.strategy,
            caption = request.caption,
            sentAt = System.currentTimeMillis(),
        )
        emit("Фото отправлены")
        return packageId
    }

    // ------------------------------------------------------------- прочее

    fun setShareStrategy(strategy: ShareStrategy) {
        viewModelScope.launch { repository.putSetting(KEY_SHARE_STRATEGY, strategy.code) }
    }

    fun setChunkSize(size: Int) {
        viewModelScope.launch { repository.putSetting(KEY_CHUNK_SIZE, size.toString()) }
    }

    fun setCompanyName(name: String) {
        viewModelScope.launch {
            repository.putSetting(KEY_COMPANY_NAME, name.trim().ifEmpty { DEFAULT_COMPANY_NAME })
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.putSetting(KEY_THEME_MODE, mode.code) }
    }

    /**
     * Центральная область кадра для распознавания шильдика.
     * Фон (небо, асфальт) не несёт полезной информации и мешает OCR.
     */
    private fun cropForOcr(bitmap: Bitmap): Bitmap {
        val width = (bitmap.width * OCR_CROP_WIDTH).toInt().coerceAtLeast(1)
        val height = (bitmap.height * OCR_CROP_HEIGHT).toInt().coerceAtLeast(1)
        if (width >= bitmap.width && height >= bitmap.height) return bitmap
        val x = ((bitmap.width - width) / 2).coerceAtLeast(0)
        val y = ((bitmap.height - height) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    /** Показать сообщение пользователю. */
    fun notify(message: String) {
        emit(message)
    }

    override fun onCleared() {
        super.onCleared()
        serialExtractor.close()
    }

    companion object {
        const val KEY_SHARE_STRATEGY = "share_strategy"
        const val KEY_CHUNK_SIZE = "chunk_size"
        const val KEY_COMPANY_NAME = "company_name"
        const val KEY_CURRENT_POINT = "current_point_id"
        const val KEY_CURRENT_CREW = "current_crew_id"
        const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_CHUNK_SIZE = 10
        const val DEFAULT_COMPANY_NAME = "ООО Интех"

        /** Доля кадра, оставляемая при обрезке для распознавания шильдика. */
        private const val OCR_CROP_WIDTH = 0.92f
        private const val OCR_CROP_HEIGHT = 0.62f
    }
}