package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@Keep
@IgnoreExtraProperties
data class ChecklistCategory(
    @DocumentId val id: String = "",
    val name: String = "",
    @get:PropertyName("isHidden")
    @PropertyName("isHidden")
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)

enum class ChecklistSortOption(val label: String) {
    ALPHABETICAL_ASC("A -> Z"),
    ALPHABETICAL_DESC("Z -> A"),
    ITEM_COUNT_DESC("Most Items"),
    UNCHECKED_FIRST("Pending First")
}
