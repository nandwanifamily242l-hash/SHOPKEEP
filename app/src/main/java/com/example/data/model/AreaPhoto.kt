package com.example.data.model

import com.google.firebase.firestore.DocumentId

data class AreaPhoto(
    @DocumentId val id: String = "",
    val title: String = "",
    val category: String = "Display Shelves",
    val notes: String = "",
    val imageUri: String = "",
    val imageBase64: String = "",
    val mediaType: String = "IMAGE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)
