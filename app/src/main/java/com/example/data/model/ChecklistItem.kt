package com.example.data.model

import com.google.firebase.firestore.DocumentId

data class ChecklistItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val quantity: String = "",
    val category: String = "General Stock",
    val notes: String = "",
    val isChecked: Boolean = false,
    val isDeleted: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)
