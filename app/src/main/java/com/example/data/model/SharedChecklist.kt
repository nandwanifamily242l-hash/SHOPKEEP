package com.example.data.model

import com.google.firebase.firestore.DocumentId

data class SharedChecklist(
    @DocumentId val id: String = "",
    val shareCode: String = "",
    val title: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberIds: List<String> = emptyList(),
    val categories: List<String> = listOf("Beverages", "Dairy & Cold", "Snacks", "Supplies"),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SharedChecklistItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val quantity: String = "",
    val category: String = "Supplies",
    val notes: String = "",
    val isChecked: Boolean = false,
    val addedByUid: String = "",
    val addedByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
