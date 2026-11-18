package com.example.data.model

import com.google.firebase.firestore.DocumentId

data class ChecklistCategory(
    @DocumentId val id: String = "",
    val name: String = "",
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
