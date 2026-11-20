package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@Keep
@IgnoreExtraProperties
data class SharedChecklist(
    @DocumentId val id: String = "",
    val shareCode: String = "",
    val activeShareCode: String = "",
    val title: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val memberIds: List<String> = emptyList(),
    val categories: List<String> = listOf("Beverages", "Dairy & Cold", "Snacks", "Supplies"),
    @get:PropertyName("isPublicEditable")
    @PropertyName("isPublicEditable")
    val isPublicEditable: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class SharedChecklistItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val quantity: String = "",
    val category: String = "Supplies",
    val notes: String = "",
    @get:PropertyName("isChecked")
    @PropertyName("isChecked")
    val isChecked: Boolean = false,
    val imageBase64: String = "",
    val addedByUid: String = "",
    val addedByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
