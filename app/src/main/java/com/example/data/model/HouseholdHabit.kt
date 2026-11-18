package com.example.data.model

import com.google.firebase.firestore.DocumentId

enum class HabitFrequency(val label: String) {
    DAILY("Daily Routine"),
    WEEKLY("Weekly Routine");

    companion object {
        fun fromString(value: String?): HabitFrequency =
            entries.firstOrNull { it.name == value } ?: DAILY
    }
}

data class HouseholdHabit(
    @DocumentId val id: String = "",
    val title: String = "",
    val frequency: String = HabitFrequency.DAILY.name,
    val category: String = "Opening & Closing",
    val streakCount: Int = 0,
    val completedDates: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
) {
    fun frequencyEnum(): HabitFrequency = HabitFrequency.fromString(frequency)
}
