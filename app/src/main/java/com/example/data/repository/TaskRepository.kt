package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AreaPhoto
import com.example.data.model.ChecklistCategory
import com.example.data.model.ChecklistItem
import com.example.data.model.HouseArea
import com.example.data.model.HouseholdHabit
import com.example.data.model.HouseholdTask
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TaskRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        fun create(context: Context): TaskRepository {
            val databaseId = context.getString(R.string.firestore_database_id)
            return TaskRepository(
                firestore = FirebaseFirestore.getInstance(databaseId),
                auth = FirebaseAuth.getInstance()
            )
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in")

    private fun userDoc(uid: String = requireUid()) = firestore.collection("users").document(uid)
    private fun tasksCol(uid: String = requireUid()) = userDoc(uid).collection("tasks")
    private fun areasCol(uid: String = requireUid()) = userDoc(uid).collection("areas")
    private fun habitsCol(uid: String = requireUid()) = userDoc(uid).collection("habits")
    private fun photosCol(uid: String = requireUid()) = userDoc(uid).collection("photos")
    private fun checklistItemsCol(uid: String = requireUid()) = userDoc(uid).collection("checklist_items")
    private fun checklistCatsCol(uid: String = requireUid()) = userDoc(uid).collection("checklist_categories")
    private fun sharedChecklistsCol() = firestore.collection("shared_checklists")

    suspend fun syncUserProfile(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        userDoc(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: "Shop Owner"),
                "email" to (user.email ?: ""),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    // --- TASKS ---
    fun observeTasks(): Flow<List<HouseholdTask>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = tasksCol(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HouseholdTask::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertTask(task: HouseholdTask): Result<String> = runCatching {
        val uid = requireUid()
        val id = task.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val payload = hashMapOf<String, Any?>(
            "title" to task.title.trim(),
            "description" to task.description.trim(),
            "category" to task.category.ifBlank { "Restock" },
            "areaId" to task.areaId,
            "priority" to task.priority,
            "isCompleted" to task.isCompleted,
            "isDeleted" to task.isDeleted,
            "dueDateMillis" to task.dueDateMillis,
            "reminderTimeMillis" to task.reminderTimeMillis,
            "repeatRule" to task.repeatRule,
            "mediaUris" to task.mediaUris,
            "createdAt" to if (task.id.isBlank()) now else task.createdAt,
            "updatedAt" to now,
            "userId" to uid
        )
        tasksCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun getTaskById(taskId: String): Result<HouseholdTask?> = runCatching {
        val uid = requireUid()
        val snap = tasksCol(uid).document(taskId).get().await()
        snap.toObject(HouseholdTask::class.java)?.copy(id = snap.id)
    }

    suspend fun deleteTask(taskId: String, permanent: Boolean = false): Result<Unit> = runCatching {
        val uid = requireUid()
        if (permanent) {
            tasksCol(uid).document(taskId).delete().await()
        } else {
            tasksCol(uid).document(taskId).update(
                mapOf("isDeleted" to true, "updatedAt" to System.currentTimeMillis())
            ).await()
        }
    }

    suspend fun restoreTask(taskId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        tasksCol(uid).document(taskId).update(
            mapOf("isDeleted" to false, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    // --- SHOP ZONES / AREAS ---
    fun observeAreas(): Flow<List<HouseArea>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = areasCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HouseArea::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertArea(area: HouseArea): Result<String> = runCatching {
        val uid = requireUid()
        val id = area.id.ifBlank { UUID.randomUUID().toString() }
        val payload = mapOf(
            "name" to area.name.trim(),
            "iconName" to area.iconName,
            "description" to area.description.trim(),
            "createdAt" to if (area.id.isBlank()) System.currentTimeMillis() else area.createdAt,
            "userId" to uid
        )
        areasCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun deleteArea(areaId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        areasCol(uid).document(areaId).delete().await()
    }

    // --- SHOP ROUTINES / HABITS ---
    fun observeHabits(): Flow<List<HouseholdHabit>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = habitsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HouseholdHabit::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertHabit(habit: HouseholdHabit): Result<String> = runCatching {
        val uid = requireUid()
        val id = habit.id.ifBlank { UUID.randomUUID().toString() }
        val payload = mapOf(
            "title" to habit.title.trim(),
            "frequency" to habit.frequency,
            "category" to habit.category.ifBlank { "Opening & Closing" },
            "streakCount" to habit.streakCount.coerceAtLeast(0),
            "completedDates" to habit.completedDates,
            "createdAt" to if (habit.id.isBlank()) System.currentTimeMillis() else habit.createdAt,
            "userId" to uid
        )
        habitsCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        habitsCol(uid).document(habitId).delete().await()
    }

    // --- SHOP PHOTOS ---
    fun observePhotos(): Flow<List<AreaPhoto>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = photosCol(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AreaPhoto::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertPhoto(photo: AreaPhoto): Result<String> = runCatching {
        val uid = requireUid()
        val id = photo.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "title" to photo.title.trim().ifBlank { "Shop Photo" },
            "category" to photo.category.trim().ifBlank { "Display Shelves" },
            "notes" to photo.notes.trim(),
            "imageUri" to photo.imageUri,
            "imageBase64" to photo.imageBase64,
            "mediaType" to photo.mediaType,
            "createdAt" to if (photo.id.isBlank()) now else photo.createdAt,
            "updatedAt" to now,
            "userId" to uid
        )
        photosCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        photosCol(uid).document(photoId).delete().await()
    }

    suspend fun renamePhotoCategory(oldName: String, newName: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmedNew = newName.trim()
        require(trimmedNew.isNotEmpty()) { "Category name cannot be empty" }
        val matching = photosCol(uid).whereEqualTo("category", oldName).get().await()
        val batch = firestore.batch()
        val now = System.currentTimeMillis()
        matching.documents.forEach { doc ->
            batch.update(doc.reference, mapOf("category" to trimmedNew, "updatedAt" to now))
        }
        batch.commit().await()
    }

    // --- PERSONAL CHECKLIST ITEMS & CATEGORIES ---
    fun observeChecklistItems(): Flow<List<ChecklistItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = checklistItemsCol(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChecklistItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertChecklistItem(item: ChecklistItem): Result<String> = runCatching {
        val uid = requireUid()
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "title" to item.title.trim(),
            "quantity" to item.quantity.trim(),
            "category" to item.category.trim().ifBlank { "General Stock" },
            "notes" to item.notes.trim(),
            "isChecked" to item.isChecked,
            "isDeleted" to item.isDeleted,
            "orderIndex" to item.orderIndex,
            "createdAt" to if (item.id.isBlank()) now else item.createdAt,
            "updatedAt" to now,
            "userId" to uid
        )
        checklistItemsCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun deleteChecklistItem(itemId: String, permanent: Boolean = false): Result<Unit> = runCatching {
        val uid = requireUid()
        if (permanent) {
            checklistItemsCol(uid).document(itemId).delete().await()
        } else {
            checklistItemsCol(uid).document(itemId).update(
                mapOf("isDeleted" to true, "updatedAt" to System.currentTimeMillis())
            ).await()
        }
    }

    suspend fun restoreChecklistItem(itemId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        checklistItemsCol(uid).document(itemId).update(
            mapOf("isDeleted" to false, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    fun observeChecklistCategories(): Flow<List<ChecklistCategory>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = checklistCatsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChecklistCategory::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertChecklistCategory(category: ChecklistCategory): Result<String> = runCatching {
        val uid = requireUid()
        val id = category.id.ifBlank { categoryKey(category.name) }
        val payload = mapOf(
            "name" to category.name.trim(),
            "isHidden" to category.isHidden,
            "sortOrder" to category.sortOrder,
            "createdAt" to if (category.id.isBlank()) System.currentTimeMillis() else category.createdAt,
            "userId" to uid
        )
        checklistCatsCol(uid).document(id).set(payload, SetOptions.merge()).await()
        id
    }

    suspend fun renameChecklistCategory(
        oldName: String,
        newName: String,
        isDefaultCategory: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmedNew = newName.trim()
        require(trimmedNew.isNotEmpty()) { "Category name cannot be empty" }
        val itemsSnap = checklistItemsCol(uid).whereEqualTo("category", oldName).get().await()
        val catsSnap = checklistCatsCol(uid).whereEqualTo("name", oldName).get().await()
        val batch = firestore.batch()
        val now = System.currentTimeMillis()

        itemsSnap.documents.forEach { doc ->
            batch.update(doc.reference, mapOf("category" to trimmedNew, "updatedAt" to now))
        }

        catsSnap.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        if (isDefaultCategory) {
            val hiddenOldRef = checklistCatsCol(uid).document("hidden_${categoryKey(oldName)}")
            batch.set(
                hiddenOldRef,
                mapOf(
                    "name" to oldName,
                    "isHidden" to true,
                    "sortOrder" to 0,
                    "createdAt" to now,
                    "userId" to uid
                )
            )
        }

        val newCatRef = checklistCatsCol(uid).document(categoryKey(trimmedNew))
        batch.set(
            newCatRef,
            mapOf(
                "name" to trimmedNew,
                "isHidden" to false,
                "sortOrder" to 0,
                "createdAt" to now,
                "userId" to uid
            )
        )
        batch.commit().await()
    }

    // --- SHARED COLLABORATIVE CHECKLISTS ---
    fun observeMySharedChecklists(): Flow<List<SharedChecklist>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close(IllegalStateException("Unauthenticated"))
            return@callbackFlow
        }
        val listener = sharedChecklistsCol()
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SharedChecklist::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeSharedChecklist(shareCode: String): Flow<SharedChecklist?> = callbackFlow {
        val normalized = shareCode.trim().uppercase()
        if (normalized.isEmpty()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = sharedChecklistsCol().document(normalized)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val board = snapshot?.toObject(SharedChecklist::class.java)?.copy(id = snapshot.id)
                trySend(board)
            }
        awaitClose { listener.remove() }
    }

    fun observeSharedChecklistItems(shareCode: String): Flow<List<SharedChecklistItem>> = callbackFlow {
        val normalized = shareCode.trim().uppercase()
        if (normalized.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = sharedChecklistsCol().document(normalized)
            .collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(SharedChecklistItem::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createSharedChecklist(
        title: String,
        initialCategories: List<String> = listOf("Beverages", "Dairy & Cold", "Snacks", "Supplies")
    ): Result<String> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val code = "SHOP-" + UUID.randomUUID().toString().take(6).uppercase()
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "shareCode" to code,
            "title" to title.trim().ifBlank { "Shared Shop List" },
            "ownerId" to user.uid,
            "ownerName" to (user.displayName ?: "Shop Owner"),
            "memberIds" to listOf(user.uid),
            "categories" to initialCategories,
            "createdAt" to now,
            "updatedAt" to now
        )
        sharedChecklistsCol().document(code).set(payload).await()
        code
    }

    suspend fun joinSharedChecklist(rawCodeOrUrl: String): Result<SharedChecklist> = runCatching {
        val uid = requireUid()
        val code = extractShareCode(rawCodeOrUrl)
        require(code.isNotBlank()) { "Invalid share code or link" }
        val docRef = sharedChecklistsCol().document(code)
        val snap = docRef.get().await()
        val board = snap.toObject(SharedChecklist::class.java)?.copy(id = snap.id)
            ?: throw NoSuchElementException("Shared checklist '$code' not found")
        if (!board.memberIds.contains(uid)) {
            docRef.update(
                mapOf(
                    "memberIds" to FieldValue.arrayUnion(uid),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        }
        board
    }

    suspend fun upsertSharedItem(shareCode: String, item: SharedChecklistItem): Result<String> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val code = shareCode.trim().uppercase()
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "title" to item.title.trim(),
            "quantity" to item.quantity.trim(),
            "category" to item.category.trim().ifBlank { "Supplies" },
            "notes" to item.notes.trim(),
            "isChecked" to item.isChecked,
            "addedByUid" to (item.addedByUid.ifBlank { user.uid }),
            "addedByName" to (item.addedByName.ifBlank { user.displayName ?: "Staff" }),
            "createdAt" to if (item.id.isBlank()) now else item.createdAt,
            "updatedAt" to now
        )
        val boardRef = sharedChecklistsCol().document(code)
        boardRef.collection("items").document(id).set(payload).await()
        boardRef.update("updatedAt", now).await()
        id
    }

    suspend fun deleteSharedItem(shareCode: String, itemId: String): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        sharedChecklistsCol().document(code).collection("items").document(itemId).delete().await()
    }

    suspend fun renameSharedCategory(
        shareCode: String,
        oldName: String,
        newName: String
    ): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        val trimmedNew = newName.trim()
        require(trimmedNew.isNotEmpty()) { "Category name cannot be empty" }
        val boardRef = sharedChecklistsCol().document(code)
        val boardSnap = boardRef.get().await()
        val board = boardSnap.toObject(SharedChecklist::class.java)
            ?: throw NoSuchElementException("Shared checklist not found")
        val updatedCats = board.categories.map { if (it == oldName) trimmedNew else it }
            .let { if (trimmedNew !in it) it + trimmedNew else it }
            .distinct()
        val itemsSnap = boardRef.collection("items").whereEqualTo("category", oldName).get().await()
        val batch = firestore.batch()
        val now = System.currentTimeMillis()
        batch.update(boardRef, mapOf("categories" to updatedCats, "updatedAt" to now))
        itemsSnap.documents.forEach { doc ->
            batch.update(doc.reference, mapOf("category" to trimmedNew, "updatedAt" to now))
        }
        batch.commit().await()
    }

    suspend fun updateSharedCategories(shareCode: String, categories: List<String>): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        sharedChecklistsCol().document(code).update(
            mapOf("categories" to categories.distinct(), "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    private fun categoryKey(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "cat_${System.currentTimeMillis()}" }

    fun extractShareCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("checklist=")) {
            val after = trimmed.substringAfter("checklist=").substringBefore("&").substringBefore("#")
            return after.trim().uppercase()
        }
        if (trimmed.contains("/checklist/")) {
            val after = trimmed.substringAfter("/checklist/").substringBefore("?").substringBefore("/")
            return after.trim().uppercase()
        }
        return trimmed.uppercase()
    }
}
