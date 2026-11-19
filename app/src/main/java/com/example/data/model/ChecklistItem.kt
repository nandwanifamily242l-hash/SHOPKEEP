package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@Keep
@IgnoreExtraProperties
data class ChecklistItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val quantity: String = "",
    val category: String = "General Stock",
    val notes: String = "",
    @get:PropertyName("isChecked")
    @PropertyName("isChecked")
    val isChecked: Boolean = false,
    @get:PropertyName("isDeleted")
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false,
    val imageBase64: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)
