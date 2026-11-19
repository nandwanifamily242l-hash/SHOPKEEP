package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class HouseArea(
    @DocumentId val id: String = "",
    val name: String = "",
    val iconName: String = "Storefront",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)
