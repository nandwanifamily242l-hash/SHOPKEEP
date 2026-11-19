package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AreaPhoto
import com.example.data.model.ChecklistCategory
import com.example.data.model.ChecklistItem
import com.example.data.model.ChecklistSortOption
import com.example.data.model.HouseArea
import com.example.data.model.HouseholdHabit
import com.example.data.model.HouseholdTask
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem
import com.example.data.model.TaskPriority
import com.example.data.repository.FirestoreError
import com.example.data.repository.TaskRepository
import com.example.reminder.TaskReminderManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShopKeepUiState(
    val isLoading: Boolean = false,
    val tasks: List<HouseholdTask> = emptyList(),
    val areas: List<HouseArea> = emptyList(),
    val habits: List<HouseholdHabit> = emptyList(),
    val photos: List<AreaPhoto> = emptyList(),
    val checklistItems: List<ChecklistItem> = emptyList(),
    val customCategories: List<ChecklistCategory> = emptyList(),
    val checklistSortOption: ChecklistSortOption = ChecklistSortOption.ALPHABETICAL_ASC,
    val sharedChecklists: List<SharedChecklist> = emptyList(),
    val activeSharedCode: String? = null,
    val activeSharedBoard: SharedChecklist? = null,
    val activeSharedItems: List<SharedChecklistItem> = emptyList(),
    val pendingDeepLinkShareCode: String? = null,
    val errorMessage: String? = null,
    val statusBanner: String? = null
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopKeepUiState())
    val uiState: StateFlow<ShopKeepUiState> = _uiState.asStateFlow()

    private var syncJobs: List<Job> = emptyList()
    private var sharedBoardJob: Job? = null
    private var sharedItemsJob: Job? = null

    val defaultChecklistCategories = listOf(
        "Beverages",
        "Cleaning Supplies",
        "Counter & Packaging",
        "Dairy & Cold",
        "Dry Goods & Snacks",
        "General Stock"
    )

    val defaultPhotoCategories = listOf(
        "Counter & POS",
        "Display Shelves",
        "Invoices & Bills",
        "Stockroom",
        "Supplier Deliveries"
    )

    fun startSyncForCurrentUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        stopSync()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            repository.syncUserProfile()
        }

        val jobs = listOf(
            viewModelScope.launch {
                repository.observeTasks()
                    .catch { handleFlowError(it) }
                    .collect { tasks ->
                        _uiState.update { it.copy(tasks = tasks, isLoading = false) }
                    }
            },
            viewModelScope.launch {
                repository.observeAreas()
                    .catch { handleFlowError(it) }
                    .collect { areas ->
                        _uiState.update { it.copy(areas = areas) }
                    }
            },
            viewModelScope.launch {
                repository.observeHabits()
                    .catch { handleFlowError(it) }
                    .collect { habits ->
                        _uiState.update { it.copy(habits = habits) }
                    }
            },
            viewModelScope.launch {
                repository.observePhotos()
                    .catch { handleFlowError(it) }
                    .collect { photos ->
                        _uiState.update { it.copy(photos = photos) }
                    }
            },
            viewModelScope.launch {
                repository.observeChecklistItems()
                    .catch { handleFlowError(it) }
                    .collect { items ->
                        _uiState.update { it.copy(checklistItems = items) }
                    }
            },
            viewModelScope.launch {
                repository.observeChecklistCategories()
                    .catch { handleFlowError(it) }
                    .collect { cats ->
                        _uiState.update { it.copy(customCategories = cats) }
                    }
            },
            viewModelScope.launch {
                repository.observeMySharedChecklists()
                    .catch { handleFlowError(it) }
                    .collect { boards ->
                        _uiState.update { it.copy(sharedChecklists = boards) }
                        if (_uiState.value.activeSharedCode == null && boards.isNotEmpty()) {
                            selectSharedChecklist(boards.first().shareCode)
                        }
                    }
            }
        )
        syncJobs = jobs

        _uiState.value.pendingDeepLinkShareCode?.let { pendingCode ->
            _uiState.update { it.copy(pendingDeepLinkShareCode = null) }
            joinSharedChecklist(pendingCode)
        }
        val _unused = uid
    }

    fun stopSync() {
        syncJobs.forEach { it.cancel() }
        syncJobs = emptyList()
        sharedBoardJob?.cancel()
        sharedItemsJob?.cancel()
        _uiState.value = ShopKeepUiState()
    }

    fun setPendingDeepLinkCode(rawCodeOrUrl: String?) {
        if (rawCodeOrUrl.isNullOrBlank()) return
        val extracted = repository.extractShareCode(rawCodeOrUrl)
        if (extracted.isBlank()) return
        if (FirebaseAuth.getInstance().currentUser != null) {
            joinSharedChecklist(extracted)
        } else {
            _uiState.update { it.copy(pendingDeepLinkShareCode = extracted) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    private fun handleFlowError(throwable: Throwable) {
        val err = FirestoreError.fromThrowable(throwable)
        _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
    }

    // --- TASKS ---
    fun saveTask(
        id: String = "",
        title: String,
        description: String,
        category: String,
        areaId: String,
        priority: TaskPriority,
        dueDateMillis: Long?,
        reminderTimeMillis: Long?,
        repeatRule: String = "NONE",
        mediaUris: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.tasks.firstOrNull { it.id == id }
            val task = (existing ?: HouseholdTask()).copy(
                id = id,
                title = title,
                description = description,
                category = category,
                areaId = areaId,
                priority = priority.name,
                dueDateMillis = dueDateMillis,
                reminderTimeMillis = reminderTimeMillis,
                repeatRule = repeatRule,
                mediaUris = mediaUris
            )
            repository.upsertTask(task)
                .onSuccess { savedId ->
                    if (reminderTimeMillis != null && !task.isCompleted) {
                        TaskReminderManager.scheduleReminder(
                            context = appContext,
                            taskId = savedId,
                            title = title,
                            description = description,
                            triggerAtMillis = reminderTimeMillis
                        )
                    } else {
                        TaskReminderManager.cancelReminder(appContext, savedId)
                    }
                    _uiState.update { it.copy(statusBanner = "Shop task saved") }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun toggleTaskCompletion(task: HouseholdTask) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.upsertTask(updated).onSuccess {
                if (updated.isCompleted) {
                    TaskReminderManager.cancelReminder(appContext, task.id)
                }
            }.onFailure { t ->
                _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
            }
        }
    }

    fun updateTaskPriority(task: HouseholdTask, priority: TaskPriority) {
        viewModelScope.launch {
            repository.upsertTask(task.copy(priority = priority.name))
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun deleteTask(taskId: String, permanent: Boolean = false) {
        viewModelScope.launch {
            TaskReminderManager.cancelReminder(appContext, taskId)
            repository.deleteTask(taskId, permanent)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun restoreTask(taskId: String) {
        viewModelScope.launch {
            repository.restoreTask(taskId)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Task restored") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    // --- ZONES / AREAS ---
    fun saveArea(id: String = "", name: String, description: String, iconName: String = "Storefront") {
        viewModelScope.launch {
            val existing = _uiState.value.areas.firstOrNull { it.id == id }
            val area = (existing ?: HouseArea()).copy(
                id = id,
                name = name,
                description = description,
                iconName = iconName
            )
            repository.upsertArea(area)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Shop zone saved") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun deleteArea(areaId: String) {
        viewModelScope.launch {
            repository.deleteArea(areaId)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    // --- ROUTINES / HABITS ---
    fun saveHabit(id: String = "", title: String, frequency: String, category: String) {
        viewModelScope.launch {
            val existing = _uiState.value.habits.firstOrNull { it.id == id }
            val habit = (existing ?: HouseholdHabit()).copy(
                id = id,
                title = title,
                frequency = frequency,
                category = category
            )
            repository.upsertHabit(habit)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Routine saved") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun toggleHabitToday(habit: HouseholdHabit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val updatedDates = if (today in habit.completedDates) {
            habit.completedDates - today
        } else {
            habit.completedDates + today
        }
        val newStreak = if (today in updatedDates) {
            maxOf(habit.streakCount + 1, updatedDates.size)
        } else {
            (habit.streakCount - 1).coerceAtLeast(0)
        }
        viewModelScope.launch {
            repository.upsertHabit(
                habit.copy(completedDates = updatedDates, streakCount = newStreak)
            ).onFailure { t ->
                _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    // --- SHOP PHOTOS ---
    fun savePhoto(
        id: String = "",
        title: String,
        category: String,
        notes: String,
        imageUri: String,
        imageBase64: String,
        mediaType: String = "IMAGE"
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.photos.firstOrNull { it.id == id }
            val photo = (existing ?: AreaPhoto()).copy(
                id = id,
                title = title,
                category = category,
                notes = notes,
                imageUri = imageUri,
                imageBase64 = imageBase64,
                mediaType = mediaType
            )
            repository.upsertPhoto(photo)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Photo saved") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun renamePhotoCategory(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName.trim()) return
        viewModelScope.launch {
            repository.renamePhotoCategory(oldName, newName)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Category renamed to ${newName.trim()}") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    // --- PERSONAL CHECKLISTS + CATEGORY SORTING & RENAMING ---
    fun setChecklistSortOption(option: ChecklistSortOption) {
        _uiState.update { it.copy(checklistSortOption = option) }
    }

    fun visibleChecklistCategories(): List<String> {
        val custom = _uiState.value.customCategories
        val hiddenNames = custom.filter { it.isHidden }.map { it.name }.toSet()
        val activeCustomNames = custom.filter { !it.isHidden }.map { it.name }
        val itemCategoryNames = _uiState.value.checklistItems
            .filter { !it.isDeleted }
            .map { it.category }

        val combined = (defaultChecklistCategories.filter { it !in hiddenNames } + activeCustomNames + itemCategoryNames)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }

        return sortCategoryNames(
            categories = combined,
            items = _uiState.value.checklistItems.filter { !it.isDeleted },
            sortOption = _uiState.value.checklistSortOption
        )
    }

    fun sortCategoryNames(
        categories: List<String>,
        items: List<ChecklistItem>,
        sortOption: ChecklistSortOption
    ): List<String> {
        return when (sortOption) {
            ChecklistSortOption.ALPHABETICAL_ASC -> categories.sortedBy { it.lowercase(Locale.getDefault()) }
            ChecklistSortOption.ALPHABETICAL_DESC -> categories.sortedByDescending { it.lowercase(Locale.getDefault()) }
            ChecklistSortOption.ITEM_COUNT_DESC -> categories.sortedWith(
                compareByDescending<String> { cat -> items.count { it.category.equals(cat, ignoreCase = true) } }
                    .thenBy { it.lowercase(Locale.getDefault()) }
            )
            ChecklistSortOption.UNCHECKED_FIRST -> categories.sortedWith(
                compareByDescending<String> { cat ->
                    items.count { it.category.equals(cat, ignoreCase = true) && !it.isChecked }
                }.thenBy { it.lowercase(Locale.getDefault()) }
            )
        }
    }

    fun addChecklistCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.upsertChecklistCategory(ChecklistCategory(name = trimmed, isHidden = false))
                .onSuccess { _uiState.update { it.copy(statusBanner = "Category \"$trimmed\" added") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun renameChecklistCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        val isDefault = defaultChecklistCategories.any { it.equals(oldName, ignoreCase = true) }
        viewModelScope.launch {
            repository.renameChecklistCategory(oldName, trimmed, isDefault)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Category renamed to \"$trimmed\"") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun saveChecklistItem(
        id: String = "",
        title: String,
        quantity: String,
        category: String,
        notes: String = "",
        imageBase64: String? = null
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.checklistItems.firstOrNull { it.id == id }
            val resolvedPhoto = imageBase64 ?: existing?.imageBase64 ?: ""
            val resolvedTitle = title.trim().ifBlank { if (resolvedPhoto.isNotBlank()) "Photo Note" else "" }
            if (resolvedTitle.isBlank()) return@launch
            val item = (existing ?: ChecklistItem()).copy(
                id = id,
                title = resolvedTitle,
                quantity = quantity,
                category = category,
                notes = notes,
                imageBase64 = resolvedPhoto
            )
            repository.upsertChecklistItem(item)
                .onSuccess {
                    if (resolvedPhoto.isNotBlank()) {
                        _uiState.update { it.copy(statusBanner = "Photo note saved under \"$category\"") }
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun unselectAllChecklistPhotos() {
        viewModelScope.launch {
            val selectedPhotos = _uiState.value.checklistItems.filter {
                !it.isDeleted && it.imageBase64.isNotBlank() && it.isChecked
            }
            selectedPhotos.forEach { item ->
                repository.upsertChecklistItem(item.copy(isChecked = false))
                    .onFailure { t ->
                        _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                    }
            }
        }
    }

    fun toggleChecklistItem(item: ChecklistItem) {
        viewModelScope.launch {
            repository.upsertChecklistItem(item.copy(isChecked = !item.isChecked))
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun deleteChecklistItem(itemId: String, permanent: Boolean = false) {
        viewModelScope.launch {
            repository.deleteChecklistItem(itemId, permanent)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun restoreChecklistItem(itemId: String) {
        viewModelScope.launch {
            repository.restoreChecklistItem(itemId)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    // --- SHARED REAL-TIME CHECKLISTS ---
    fun createSharedChecklist(title: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.createSharedChecklist(title)
                .onSuccess { code ->
                    selectSharedChecklist(code)
                    _uiState.update { it.copy(statusBanner = "Public live board $code ready to share") }
                    onCreated(code)
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun publishPersonalListToPublicBoard(
        title: String = "Shop Stock Checklist",
        onReady: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.publishPersonalChecklistToPublicBoard(
                items = _uiState.value.checklistItems,
                title = title
            )
                .onSuccess { code ->
                    selectSharedChecklist(code)
                    _uiState.update { it.copy(statusBanner = "Public live link is ready!") }
                    onReady(code)
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun toggleSharedBoardPublicEditable(shareCode: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setSharedBoardPublicEditable(shareCode, enabled)
                .onSuccess {
                    val msg = if (enabled) {
                        "Public mark/unmark ON — visitors can view & check items only"
                    } else {
                        "Strictly view-only — sign-in required even to mark items"
                    }
                    _uiState.update { it.copy(statusBanner = msg) }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun setBoardAsPublicDefault(board: SharedChecklist) {
        viewModelScope.launch {
            repository.setPublicDefaultBoard(board.shareCode, board.title, board.isPublicEditable)
                .onSuccess {
                    _uiState.update { it.copy(statusBanner = "\"${board.title}\" set as your main Public Link") }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun joinSharedChecklist(codeOrUrl: String) {
        viewModelScope.launch {
            repository.joinSharedChecklist(codeOrUrl)
                .onSuccess { board ->
                    selectSharedChecklist(board.shareCode)
                    _uiState.update { it.copy(statusBanner = "Joined shared board: ${board.title}") }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun selectSharedChecklist(shareCode: String?) {
        sharedBoardJob?.cancel()
        sharedItemsJob?.cancel()
        if (shareCode.isNullOrBlank()) {
            _uiState.update { it.copy(activeSharedCode = null, activeSharedBoard = null, activeSharedItems = emptyList()) }
            return
        }
        val code = shareCode.trim().uppercase()
        _uiState.update { it.copy(activeSharedCode = code) }
        sharedBoardJob = viewModelScope.launch {
            repository.observeSharedChecklist(code)
                .catch { handleFlowError(it) }
                .collect { board ->
                    _uiState.update { it.copy(activeSharedBoard = board) }
                }
        }
        sharedItemsJob = viewModelScope.launch {
            repository.observeSharedChecklistItems(code)
                .catch { handleFlowError(it) }
                .collect { items ->
                    _uiState.update { it.copy(activeSharedItems = items) }
                }
        }
    }

    fun saveSharedItem(
        id: String = "",
        title: String,
        quantity: String,
        category: String,
        notes: String = "",
        imageBase64: String? = null
    ) {
        val code = _uiState.value.activeSharedCode ?: return
        viewModelScope.launch {
            val existing = _uiState.value.activeSharedItems.firstOrNull { it.id == id }
            val resolvedPhoto = imageBase64 ?: existing?.imageBase64 ?: ""
            val resolvedTitle = title.trim().ifBlank { if (resolvedPhoto.isNotBlank()) "Photo Note" else "" }
            if (resolvedTitle.isBlank()) return@launch
            val item = (existing ?: SharedChecklistItem()).copy(
                id = id,
                title = resolvedTitle,
                quantity = quantity,
                category = category,
                notes = notes,
                imageBase64 = resolvedPhoto
            )
            repository.upsertSharedItem(code, item)
                .onSuccess {
                    if (resolvedPhoto.isNotBlank()) {
                        _uiState.update { it.copy(statusBanner = "Photo note synced to live board") }
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun unselectAllSharedPhotos() {
        val code = _uiState.value.activeSharedCode ?: return
        viewModelScope.launch {
            val selectedPhotos = _uiState.value.activeSharedItems.filter {
                it.imageBase64.isNotBlank() && it.isChecked
            }
            selectedPhotos.forEach { item ->
                repository.upsertSharedItem(code, item.copy(isChecked = false))
                    .onFailure { t ->
                        _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                    }
            }
        }
    }

    fun toggleSharedItem(item: SharedChecklistItem) {
        val code = _uiState.value.activeSharedCode ?: return
        viewModelScope.launch {
            repository.upsertSharedItem(code, item.copy(isChecked = !item.isChecked))
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun deleteSharedItem(itemId: String) {
        val code = _uiState.value.activeSharedCode ?: return
        viewModelScope.launch {
            repository.deleteSharedItem(code, itemId)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun renameSharedCategory(oldName: String, newName: String) {
        val code = _uiState.value.activeSharedCode ?: return
        if (newName.isBlank() || oldName == newName.trim()) return
        viewModelScope.launch {
            repository.renameSharedCategory(code, oldName, newName)
                .onSuccess { _uiState.update { it.copy(statusBanner = "Shared category renamed to ${newName.trim()}") } }
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    fun addSharedCategory(name: String) {
        val code = _uiState.value.activeSharedCode ?: return
        val board = _uiState.value.activeSharedBoard ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.updateSharedCategories(code, board.categories + trimmed)
                .onFailure { t ->
                    _uiState.update { it.copy(errorMessage = FirestoreError.fromThrowable(t).message) }
                }
        }
    }

    class Factory(
        private val repository: TaskRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskViewModel(repository, context.applicationContext) as T
        }
    }
}
