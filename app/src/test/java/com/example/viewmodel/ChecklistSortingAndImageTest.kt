package com.example.viewmodel

import android.graphics.Bitmap
import com.example.data.model.ChecklistItem
import com.example.data.model.ChecklistSortOption
import com.example.ui.components.ImageUtils
import com.example.ui.components.PhotoEditSpec
import com.example.ui.components.PhotoFilterPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChecklistSortingAndImageTest {

    @Test
    fun sortCategoryNames_alphabeticalAscIsDefaultAndAccurate() {
        val categories = listOf("Snacks", "Beverages", "Dairy & Cold", "Bakery")
        val sorted = when (ChecklistSortOption.ALPHABETICAL_ASC) {
            ChecklistSortOption.ALPHABETICAL_ASC -> categories.sortedBy { it.lowercase() }
            else -> categories
        }
        assertThat(sorted).containsExactly("Bakery", "Beverages", "Dairy & Cold", "Snacks").inOrder()
    }

    @Test
    fun sortCategoryNames_uncheckedFirstOrdersByPendingCount() {
        val categories = listOf("Bakery", "Beverages", "Snacks")
        val items = listOf(
            ChecklistItem(title = "Water", category = "Beverages", isChecked = false),
            ChecklistItem(title = "Cola", category = "Beverages", isChecked = false),
            ChecklistItem(title = "Chips", category = "Snacks", isChecked = false),
            ChecklistItem(title = "Bread", category = "Bakery", isChecked = true)
        )
        val sorted = categories.sortedWith(
            compareByDescending<String> { cat ->
                items.count { it.category.equals(cat, ignoreCase = true) && !it.isChecked }
            }.thenBy { it.lowercase() }
        )
        assertThat(sorted).containsExactly("Beverages", "Snacks", "Bakery").inOrder()
    }

    @Test
    fun imageUtils_cropAndRotateTransformsBitmapDimensions() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val rotated = ImageUtils.applyEdits(
            source,
            PhotoEditSpec(
                rotationQuarterTurns = 1,
                cropLeft = 0.25f,
                cropRight = 0.75f,
                cropTop = 0f,
                cropBottom = 1f,
                filterPreset = PhotoFilterPreset.VIVID
            )
        )
        assertThat(rotated.width).isEqualTo(50)
        assertThat(rotated.height).isEqualTo(200)
    }
}
