package com.example.data.model

import com.google.firebase.firestore.DocumentId

data class HouseArea(
    @DocumentId val id: String = "",
    val name: String = "",
    val iconName: String = "Storefront",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)
