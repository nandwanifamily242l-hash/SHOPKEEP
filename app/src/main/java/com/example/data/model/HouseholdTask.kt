package com.example.data.model

import com.google.firebase.firestore.DocumentId

enum class TaskPriority(val label: String, val quadrantTitle: String) {
    URGENT_IMPORTANT("Urgent & Important", "Do First"),
    NOT_URGENT_IMPORTANT("Important, Not Urgent", "Schedule"),
    URGENT_NOT_IMPORTANT("Urgent, Low Impact", "Delegate"),
    NOT_URGENT_NOT_IMPORTANT("Low Priority", "Later");

    companion object {
        fun fromString(value: String?): TaskPriority =
            entries.firstOrNull { it.name == value } ?: NOT_URGENT_IMPORTANT
    }
}

data class HouseholdTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Restock",
    val areaId: String = "",
    val priority: String = TaskPriority.NOT_URGENT_IMPORTANT.name,
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val dueDateMillis: Long? = null,
    val reminderTimeMillis: Long? = null,
    val repeatRule: String = "NONE",
    val mediaUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userId: String = ""
) {
    fun priorityEnum(): TaskPriority = TaskPriority.fromString(priority)
}
