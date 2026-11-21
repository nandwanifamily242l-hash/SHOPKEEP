package com.example.data.repository

import android.content.Context
import android.util.Log
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val REPO_TAG = "ShopKeepRepo"

class TaskRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private inline fun <reified T : Any> DocumentSnapshot.toObjectSafe(): T? =
        runCatching { toObject(T::class.java) }
            .onFailure { e ->
                Log.e(REPO_TAG, "Skipping malformed ${T::class.java.simpleName} doc id=$id", e)
            }
            .getOrNull()

    companion object {
        const val PUBLIC_DEFAULT_BOARD_ID = "PUBLIC_DEFAULT"

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
                    doc.toObjectSafe<HouseholdTask>()?.copy(id = doc.id)
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
        snap.toObjectSafe<HouseholdTask>()?.copy(id = snap.id)
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
                    doc.toObjectSafe<HouseArea>()?.copy(id = doc.id)
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
                    doc.toObjectSafe<HouseholdHabit>()?.copy(id = doc.id)
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
                    doc.toObjectSafe<AreaPhoto>()?.copy(id = doc.id)
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

    suspend fun addAreaPhotos(photos: List<AreaPhoto>): Result<Int> = runCatching {
        val uid = requireUid()
        if (photos.isEmpty()) return@runCatching 0
        val baseTime = System.currentTimeMillis()
        var savedCount = 0
        photos.chunked(5).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { photo ->
                val id = photo.id.ifBlank { UUID.randomUUID().toString() }
                val ts = baseTime + savedCount
                val payload = mapOf(
                    "title" to photo.title.trim().ifBlank { "Shop Photo" },
                    "category" to photo.category.trim().ifBlank { "Display Shelves" },
                    "notes" to photo.notes.trim(),
                    "imageUri" to photo.imageUri,
                    "imageBase64" to photo.imageBase64,
                    "mediaType" to photo.mediaType,
                    "createdAt" to ts,
                    "updatedAt" to ts,
                    "userId" to uid
                )
                batch.set(photosCol(uid).document(id), payload)
                savedCount++
            }
            batch.commit().await()
        }
        savedCount
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
                    doc.toObjectSafe<ChecklistItem>()?.copy(id = doc.id)
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
            "title" to item.title.trim().ifBlank { if (item.imageBase64.isNotBlank()) "Photo Note" else "" },
            "quantity" to item.quantity.trim(),
            "category" to item.category.trim().ifBlank { "General Stock" },
            "notes" to item.notes.trim(),
            "isChecked" to item.isChecked,
            "isDeleted" to item.isDeleted,
            "imageBase64" to item.imageBase64,
            "orderIndex" to item.orderIndex,
            "createdAt" to if (item.id.isBlank()) now else item.createdAt,
            "updatedAt" to now,
            "userId" to uid
        )
        checklistItemsCol(uid).document(id).set(payload).await()
        id
    }

    suspend fun addChecklistPhotoNotes(items: List<ChecklistItem>): Result<Int> = runCatching {
        val uid = requireUid()
        if (items.isEmpty()) return@runCatching 0
        val baseTime = System.currentTimeMillis()
        var savedCount = 0
        items.chunked(5).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { item ->
                val id = item.id.ifBlank { UUID.randomUUID().toString() }
                val ts = baseTime + savedCount
                val payload = mapOf(
                    "title" to item.title.trim().ifBlank { "Photo Note" },
                    "quantity" to item.quantity.trim(),
                    "category" to item.category.trim().ifBlank { "General Stock" },
                    "notes" to item.notes.trim(),
                    "isChecked" to item.isChecked,
                    "isDeleted" to false,
                    "imageBase64" to item.imageBase64,
                    "orderIndex" to item.orderIndex,
                    "createdAt" to ts,
                    "updatedAt" to ts,
                    "userId" to uid
                )
                batch.set(checklistItemsCol(uid).document(id), payload)
                savedCount++
            }
            batch.commit().await()
        }
        savedCount
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
                    doc.toObjectSafe<ChecklistCategory>()?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertChecklistCategory(category: ChecklistCategory): Result<String> = runCatching {
        val uid = requireUid()
        val trimmed = category.name.trim()
        require(trimmed.isNotEmpty()) { "Category name cannot be empty" }
        val id = category.id.ifBlank { categoryKey(trimmed) }
        val payload = mapOf(
            "name" to trimmed,
            "isHidden" to category.isHidden,
            "sortOrder" to category.sortOrder,
            "createdAt" to if (category.id.isBlank()) System.currentTimeMillis() else category.createdAt,
            "userId" to uid
        )
        checklistCatsCol(uid).document(id).set(payload, SetOptions.merge()).await()
        if (!category.isHidden) {
            runCatching {
                checklistCatsCol(uid).document("hidden_${categoryKey(trimmed)}").delete().await()
            }
        }
        id
    }

    suspend fun renameChecklistCategory(
        oldName: String,
        newName: String,
        isDefaultCategory: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        require(trimmedNew.isNotEmpty()) { "Category name cannot be empty" }

        val allItemsSnap = checklistItemsCol(uid).get().await()
        val matchingItems = allItemsSnap.documents.filter { doc ->
            doc.getString("category")?.trim()?.equals(trimmedOld, ignoreCase = true) == true
        }
        val allCatsSnap = checklistCatsCol(uid).get().await()
        val matchingCats = allCatsSnap.documents.filter { doc ->
            val catName = doc.getString("name")?.trim() ?: ""
            val isHiddenFlag = doc.getBoolean("isHidden") == true
            !isHiddenFlag && catName.equals(trimmedOld, ignoreCase = true)
        }

        val now = System.currentTimeMillis()
        matchingItems.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.update(doc.reference, mapOf("category" to trimmedNew, "updatedAt" to now))
            }
            batch.commit().await()
        }

        val catBatch = firestore.batch()
        matchingCats.forEach { doc ->
            catBatch.delete(doc.reference)
        }
        // Clear any old hidden marker for the target category name so it stays visible
        catBatch.delete(checklistCatsCol(uid).document("hidden_${categoryKey(trimmedNew)}"))

        if (isDefaultCategory && !trimmedOld.equals(trimmedNew, ignoreCase = true)) {
            val hiddenOldRef = checklistCatsCol(uid).document("hidden_${categoryKey(trimmedOld)}")
            catBatch.set(
                hiddenOldRef,
                mapOf(
                    "name" to trimmedOld,
                    "isHidden" to true,
                    "sortOrder" to 0,
                    "createdAt" to now,
                    "userId" to uid
                )
            )
        }

        val newCatRef = checklistCatsCol(uid).document(categoryKey(trimmedNew))
        catBatch.set(
            newCatRef,
            mapOf(
                "name" to trimmedNew,
                "isHidden" to false,
                "sortOrder" to 0,
                "createdAt" to now,
                "userId" to uid
            )
        )
        catBatch.commit().await()
    }

    suspend fun deleteChecklistCategory(
        categoryName: String,
        isDefaultCategory: Boolean,
        moveItemsTo: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = categoryName.trim()
        require(trimmed.isNotEmpty()) { "Category name cannot be empty" }

        val allItemsSnap = checklistItemsCol(uid).get().await()
        val matchingItems = allItemsSnap.documents.filter { doc ->
            doc.getString("category")?.trim()?.equals(trimmed, ignoreCase = true) == true &&
                doc.getBoolean("isDeleted") != true
        }
        val now = System.currentTimeMillis()
        val targetCategory = moveItemsTo?.trim()?.takeIf { it.isNotEmpty() }

        matchingItems.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                if (targetCategory != null) {
                    batch.update(doc.reference, mapOf("category" to targetCategory, "updatedAt" to now))
                } else {
                    batch.update(doc.reference, mapOf("isDeleted" to true, "updatedAt" to now))
                }
            }
            batch.commit().await()
        }

        val allCatsSnap = checklistCatsCol(uid).get().await()
        val matchingCats = allCatsSnap.documents.filter { doc ->
            doc.getString("name")?.trim()?.equals(trimmed, ignoreCase = true) == true &&
                doc.getBoolean("isHidden") != true
        }

        val catBatch = firestore.batch()
        matchingCats.forEach { doc -> catBatch.delete(doc.reference) }
        if (isDefaultCategory) {
            catBatch.set(
                checklistCatsCol(uid).document("hidden_${categoryKey(trimmed)}"),
                mapOf(
                    "name" to trimmed,
                    "isHidden" to true,
                    "sortOrder" to 0,
                    "createdAt" to now,
                    "userId" to uid
                )
            )
        }
        if (targetCategory != null) {
            catBatch.delete(checklistCatsCol(uid).document("hidden_${categoryKey(targetCategory)}"))
        }
        catBatch.commit().await()
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
                    doc.toObjectSafe<SharedChecklist>()?.copy(id = doc.id)
                }?.filter {
                    it.id != PUBLIC_DEFAULT_BOARD_ID && it.shareCode != PUBLIC_DEFAULT_BOARD_ID
                }?.sortedWith(
                    compareBy<SharedChecklist>({ it.sortOrder }, { -it.updatedAt })
                ) ?: emptyList()
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
                val board = snapshot?.toObjectSafe<SharedChecklist>()?.copy(id = snapshot.id)
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
                    doc.toObjectSafe<SharedChecklistItem>()?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createSharedChecklist(
        title: String,
        initialCategories: List<String> = listOf("Beverages", "Dairy & Cold", "Snacks", "Supplies"),
        isPublicEditable: Boolean = true
    ): Result<String> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val code = "SHOP-" + UUID.randomUUID().toString().take(6).uppercase()
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "Shared Shop List" }
        val payload = mapOf(
            "shareCode" to code,
            "title" to cleanTitle,
            "ownerId" to user.uid,
            "ownerName" to (user.displayName ?: "Shop Owner"),
            "memberIds" to listOf(user.uid),
            "categories" to initialCategories,
            "isPublicEditable" to isPublicEditable,
            "sortOrder" to 0,
            "createdAt" to now,
            "updatedAt" to now
        )
        sharedChecklistsCol().document(code).set(payload).await()
        setPublicDefaultBoard(code, cleanTitle, isPublicEditable).getOrThrow()
        code
    }

    suspend fun setPublicDefaultBoard(
        shareCode: String,
        title: String,
        isPublicEditable: Boolean = true
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val code = shareCode.trim().uppercase()
        require(code.isNotBlank()) { "Share code cannot be blank" }
        val now = System.currentTimeMillis()
        val pointerRef = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID)
        val existingSnap = pointerRef.get().await()
        if (existingSnap.exists()) {
            pointerRef.update(
                mapOf(
                    "activeShareCode" to code,
                    "title" to title.trim().ifBlank { "Shop Stock Checklist" },
                    "isPublicEditable" to isPublicEditable,
                    "updatedAt" to now
                )
            ).await()
        } else {
            val payload = mapOf(
                "shareCode" to PUBLIC_DEFAULT_BOARD_ID,
                "activeShareCode" to code,
                "title" to title.trim().ifBlank { "Shop Stock Checklist" },
                "ownerId" to user.uid,
                "ownerName" to (user.displayName ?: "Shop Owner"),
                "memberIds" to listOf(user.uid),
                "isPublicEditable" to isPublicEditable,
                "createdAt" to now,
                "updatedAt" to now
            )
            pointerRef.set(payload).await()
        }
    }

    suspend fun setSharedBoardPublicEditable(
        shareCode: String,
        isPublicEditable: Boolean
    ): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        val now = System.currentTimeMillis()
        sharedChecklistsCol().document(code).update(
            mapOf("isPublicEditable" to isPublicEditable, "updatedAt" to now)
        ).await()
        val pointerRef = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID)
        val pointerSnap = pointerRef.get().await()
        if (pointerSnap.exists() && pointerSnap.getString("activeShareCode") == code) {
            pointerRef.update(
                mapOf("isPublicEditable" to isPublicEditable, "updatedAt" to now)
            ).await()
        }
    }

    suspend fun publishPersonalChecklistToPublicBoard(
        items: List<ChecklistItem>,
        title: String = "Shop Stock Checklist"
    ): Result<String> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val uidSlug = user.uid.replace(Regex("[^A-Za-z0-9]"), "").take(6).uppercase().ifBlank { "PUBLIC" }
        val code = "SHOP-$uidSlug"
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "Shop Stock Checklist" }
        val activeItems = items.filter { !it.isDeleted }
        val categories = (activeItems.map { it.category.trim() }.filter { it.isNotBlank() } +
            listOf("Beverages", "Dairy & Cold", "Snacks", "Supplies")).distinct()

        val boardRef = sharedChecklistsCol().document(code)
        val existingBoard = boardRef.get().await()
        if (existingBoard.exists()) {
            boardRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "categories" to categories,
                    "isPublicEditable" to true,
                    "updatedAt" to now
                )
            ).await()
        } else {
            val payload = mapOf(
                "shareCode" to code,
                "title" to cleanTitle,
                "ownerId" to user.uid,
                "ownerName" to (user.displayName ?: "Shop Owner"),
                "memberIds" to listOf(user.uid),
                "categories" to categories,
                "isPublicEditable" to true,
                "createdAt" to now,
                "updatedAt" to now
            )
            boardRef.set(payload).await()
        }

        if (activeItems.isNotEmpty()) {
            activeItems.chunked(5).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { item ->
                    val itemId = item.id.ifBlank { UUID.randomUUID().toString() }
                    val itemRef = boardRef.collection("items").document(itemId)
                    batch.set(
                        itemRef,
                        mapOf(
                            "title" to item.title.trim().ifBlank { if (item.imageBase64.isNotBlank()) "Photo Note" else "Stock Item" },
                            "quantity" to item.quantity.trim(),
                            "category" to item.category.trim().ifBlank { "General Stock" },
                            "notes" to item.notes.trim(),
                            "isChecked" to item.isChecked,
                            "imageBase64" to item.imageBase64,
                            "addedByUid" to user.uid,
                            "addedByName" to (user.displayName ?: "Shop Owner"),
                            "createdAt" to item.createdAt,
                            "updatedAt" to now
                        )
                    )
                }
                batch.commit().await()
            }
        }

        setPublicDefaultBoard(code, cleanTitle, isPublicEditable = true).getOrThrow()
        code
    }

    suspend fun joinSharedChecklist(rawCodeOrUrl: String): Result<SharedChecklist> = runCatching {
        val uid = requireUid()
        var code = extractShareCode(rawCodeOrUrl)
        require(code.isNotBlank()) { "Invalid share code or link" }
        if (code == PUBLIC_DEFAULT_BOARD_ID) {
            val pointerSnap = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID).get().await()
            val activeCode = pointerSnap.getString("activeShareCode")?.trim()?.uppercase().orEmpty()
            if (activeCode.isBlank()) {
                throw NoSuchElementException("No public live board has been published yet")
            }
            code = activeCode
        }
        val docRef = sharedChecklistsCol().document(code)
        val snap = docRef.get().await()
        val board = snap.toObjectSafe<SharedChecklist>()?.copy(id = snap.id)
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
            "title" to item.title.trim().ifBlank { if (item.imageBase64.isNotBlank()) "Photo Note" else "" },
            "quantity" to item.quantity.trim(),
            "category" to item.category.trim().ifBlank { "Supplies" },
            "notes" to item.notes.trim(),
            "isChecked" to item.isChecked,
            "imageBase64" to item.imageBase64,
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

    suspend fun addSharedPhotoNotes(shareCode: String, items: List<SharedChecklistItem>): Result<Int> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val code = shareCode.trim().uppercase()
        if (items.isEmpty()) return@runCatching 0
        val baseTime = System.currentTimeMillis()
        val boardRef = sharedChecklistsCol().document(code)
        var savedCount = 0
        items.chunked(5).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { item ->
                val id = item.id.ifBlank { UUID.randomUUID().toString() }
                val ts = baseTime + savedCount
                val payload = mapOf(
                    "title" to item.title.trim().ifBlank { "Photo Note" },
                    "quantity" to item.quantity.trim(),
                    "category" to item.category.trim().ifBlank { "Supplies" },
                    "notes" to item.notes.trim(),
                    "isChecked" to item.isChecked,
                    "imageBase64" to item.imageBase64,
                    "addedByUid" to user.uid,
                    "addedByName" to (user.displayName ?: "Shop Owner"),
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
                batch.set(boardRef.collection("items").document(id), payload)
                savedCount++
            }
            batch.commit().await()
        }
        boardRef.update("updatedAt", baseTime + savedCount).await()
        savedCount
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
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        require(trimmedNew.isNotEmpty()) { "Category name cannot be empty" }
        val boardRef = sharedChecklistsCol().document(code)
        val boardSnap = boardRef.get().await()
        val board = boardSnap.toObjectSafe<SharedChecklist>()
            ?: throw NoSuchElementException("Shared checklist not found")
        val updatedCats = board.categories
            .map { if (it.trim().equals(trimmedOld, ignoreCase = true)) trimmedNew else it.trim() }
            .let { if (it.none { c -> c.equals(trimmedNew, ignoreCase = true) }) it + trimmedNew else it }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val allItemsSnap = boardRef.collection("items").get().await()
        val matchingItems = allItemsSnap.documents.filter { doc ->
            doc.getString("category")?.trim()?.equals(trimmedOld, ignoreCase = true) == true
        }
        val now = System.currentTimeMillis()
        boardRef.update(mapOf("categories" to updatedCats, "updatedAt" to now)).await()
        matchingItems.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.update(doc.reference, mapOf("category" to trimmedNew, "updatedAt" to now))
            }
            batch.commit().await()
        }
    }

    suspend fun deleteSharedCategory(
        shareCode: String,
        categoryName: String,
        moveItemsTo: String? = null
    ): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        val trimmed = categoryName.trim()
        require(trimmed.isNotEmpty()) { "Category name cannot be empty" }
        val boardRef = sharedChecklistsCol().document(code)
        val boardSnap = boardRef.get().await()
        val board = boardSnap.toObjectSafe<SharedChecklist>()
            ?: throw NoSuchElementException("Shared checklist not found")
        val targetCategory = moveItemsTo?.trim()?.takeIf { it.isNotEmpty() }
        val updatedCats = board.categories
            .filterNot { it.trim().equals(trimmed, ignoreCase = true) }
            .let { list ->
                if (targetCategory != null && list.none { it.equals(targetCategory, ignoreCase = true) }) {
                    list + targetCategory
                } else {
                    list
                }
            }
            .distinctBy { it.lowercase() }

        val allItemsSnap = boardRef.collection("items").get().await()
        val matchingItems = allItemsSnap.documents.filter { doc ->
            doc.getString("category")?.trim()?.equals(trimmed, ignoreCase = true) == true
        }
        val now = System.currentTimeMillis()
        boardRef.update(mapOf("categories" to updatedCats, "updatedAt" to now)).await()
        matchingItems.chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                if (targetCategory != null) {
                    batch.update(doc.reference, mapOf("category" to targetCategory, "updatedAt" to now))
                } else {
                    batch.delete(doc.reference)
                }
            }
            batch.commit().await()
        }
    }

    suspend fun updateSharedCategories(shareCode: String, categories: List<String>): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        val cleaned = categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        sharedChecklistsCol().document(code).update(
            mapOf("categories" to cleaned, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    suspend fun renameSharedChecklist(shareCode: String, newTitle: String): Result<Unit> = runCatching {
        requireUid()
        val code = shareCode.trim().uppercase()
        val cleanTitle = newTitle.trim().take(120)
        require(code.isNotBlank() && cleanTitle.isNotEmpty()) { "Board name cannot be empty" }
        val now = System.currentTimeMillis()
        sharedChecklistsCol().document(code).update(
            mapOf("title" to cleanTitle, "updatedAt" to now)
        ).await()
        val pointerRef = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID)
        val pointerSnap = pointerRef.get().await()
        if (pointerSnap.exists() && pointerSnap.getString("activeShareCode") == code) {
            pointerRef.update(
                mapOf("title" to cleanTitle, "updatedAt" to now)
            ).await()
        }
    }

    suspend fun deleteSharedChecklist(shareCode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val code = shareCode.trim().uppercase()
        require(code.isNotBlank()) { "Invalid board code" }
        val boardRef = sharedChecklistsCol().document(code)
        val boardSnap = boardRef.get().await()
        val ownerId = boardSnap.getString("ownerId").orEmpty()
        if (ownerId == uid) {
            val itemDocs = boardRef.collection("items").get().await().documents
            itemDocs.chunked(350).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            boardRef.delete().await()
            val pointerRef = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID)
            val pointerSnap = pointerRef.get().await()
            if (pointerSnap.exists() && pointerSnap.getString("activeShareCode") == code) {
                pointerRef.delete().await()
            }
        } else {
            boardRef.update(
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(uid),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    suspend fun reorderSharedChecklists(orderedShareCodes: List<String>): Result<Unit> = runCatching {
        requireUid()
        val cleanedCodes = orderedShareCodes
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != PUBLIC_DEFAULT_BOARD_ID }
            .distinct()
        if (cleanedCodes.isEmpty()) return@runCatching
        cleanedCodes.withIndex().chunked(350).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (idx, code) ->
                batch.update(sharedChecklistsCol().document(code), "sortOrder", idx + 1)
            }
            batch.commit().await()
        }
    }

    suspend fun syncPublicBoardsDirectory(boards: List<SharedChecklist>): Result<Unit> = runCatching {
        val user = auth.currentUser ?: return@runCatching
        val filtered = boards.filter {
            it.id != PUBLIC_DEFAULT_BOARD_ID &&
                it.shareCode != PUBLIC_DEFAULT_BOARD_ID &&
                it.shareCode.isNotBlank()
        }
        val directoryPayload = filtered.map { board ->
            mapOf(
                "shareCode" to board.shareCode.trim().uppercase(),
                "title" to board.title.trim().ifBlank { "Shop Checklist" }.take(120),
                "sortOrder" to board.sortOrder
            )
        }
        val pointerRef = sharedChecklistsCol().document(PUBLIC_DEFAULT_BOARD_ID)
        val snap = pointerRef.get().await()
        val now = System.currentTimeMillis()
        if (!snap.exists()) {
            val first = filtered.firstOrNull() ?: return@runCatching
            pointerRef.set(
                mapOf(
                    "shareCode" to PUBLIC_DEFAULT_BOARD_ID,
                    "activeShareCode" to first.shareCode.trim().uppercase(),
                    "title" to first.title.trim().ifBlank { "Shop Stock Checklist" }.take(120),
                    "ownerId" to user.uid,
                    "ownerName" to (user.displayName ?: "Shop Owner"),
                    "memberIds" to listOf(user.uid),
                    "isPublicEditable" to first.isPublicEditable,
                    "boardsDirectory" to directoryPayload,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()
            return@runCatching
        }

        val currentActive = snap.getString("activeShareCode").orEmpty().trim().uppercase()
        val activeExists = currentActive.isNotEmpty() &&
            filtered.any { it.shareCode.equals(currentActive, ignoreCase = true) }
        val existingDir = (snap.get("boardsDirectory") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            Triple(
                m["shareCode"]?.toString()?.trim()?.uppercase().orEmpty(),
                m["title"]?.toString()?.trim().orEmpty(),
                (m["sortOrder"] as? Number)?.toInt() ?: 0
            )
        } ?: emptyList()
        val desiredDir = filtered.map {
            Triple(
                it.shareCode.trim().uppercase(),
                it.title.trim().ifBlank { "Shop Checklist" }.take(120),
                it.sortOrder
            )
        }

        if (existingDir == desiredDir && (activeExists || filtered.isEmpty())) {
            return@runCatching
        }

        val updates = mutableMapOf<String, Any>(
            "boardsDirectory" to directoryPayload,
            "updatedAt" to now
        )
        if (!activeExists && filtered.isNotEmpty()) {
            val fallback = filtered.first()
            updates["activeShareCode"] = fallback.shareCode.trim().uppercase()
            updates["title"] = fallback.title.trim().ifBlank { "Shop Stock Checklist" }.take(120)
            updates["isPublicEditable"] = fallback.isPublicEditable
        }
        pointerRef.update(updates).await()
    }

    private fun categoryKey(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "cat_${System.currentTimeMillis()}" }

    fun extractShareCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("checklist=")) {
            val after = trimmed.substringAfter("checklist=").substringBefore("&").substringBefore("#")
            val decoded = after.trim().trim('/').uppercase()
            if (decoded.isNotEmpty()) return decoded
        }
        if (trimmed.contains("/checklist/")) {
            val after = trimmed.substringAfter("/checklist/").substringBefore("?").substringBefore("#").substringBefore("/")
            val decoded = after.trim().uppercase()
            if (decoded.isNotEmpty()) return decoded
        }
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return PUBLIC_DEFAULT_BOARD_ID
        }
        return trimmed.uppercase()
    }
}
