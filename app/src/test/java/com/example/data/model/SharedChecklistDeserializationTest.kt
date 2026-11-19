package com.example.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedChecklistDeserializationTest {

    @Test
    fun customClassMapper_deserializesSharedChecklistAndItem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.applet.abgygd")
                .setProjectId("demo-no-project")
                .setApiKey("fake-api-key")
                .build()
        )
        val firestore = FirebaseFirestore.getInstance(app, "ai-studio-android-homekeep-8502e84b-2339-4853-b108-eaf44e3bbe94")
        val boardRef = firestore.collection("shared_checklists").document("SHOP-123456")

        val boardMap = mapOf(
            "shareCode" to "SHOP-123456",
            "activeShareCode" to "",
            "title" to "My Shop Board",
            "ownerId" to "uid1",
            "ownerName" to "Owner",
            "memberIds" to listOf("uid1"),
            "categories" to listOf("Supplies"),
            "isPublicEditable" to false,
            "createdAt" to 1000L,
            "updatedAt" to 2000L
        )
        val board = CustomClassMapper.convertToCustomClass(boardMap, SharedChecklist::class.java, boardRef)
        assertThat(board.id).isEqualTo("SHOP-123456")
        assertThat(board.shareCode).isEqualTo("SHOP-123456")
        assertThat(board.isPublicEditable).isFalse()

        val itemRef = boardRef.collection("items").document("item_1")
        val itemMap = mapOf(
            "title" to "Tea",
            "quantity" to "2 boxes",
            "category" to "Beverages",
            "notes" to "",
            "isChecked" to true,
            "imageBase64" to "abc123base64",
            "addedByUid" to "uid1",
            "addedByName" to "Owner",
            "createdAt" to 1000L,
            "updatedAt" to 2000L
        )
        val item = CustomClassMapper.convertToCustomClass(itemMap, SharedChecklistItem::class.java, itemRef)
        assertThat(item.id).isEqualTo("item_1")
        assertThat(item.title).isEqualTo("Tea")
        assertThat(item.isChecked).isTrue()
        assertThat(item.imageBase64).isEqualTo("abc123base64")
    }

    @Test
    fun customClassMapper_deserializesAllPersonalShopModels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.applet.abgygd")
                .setProjectId("demo-no-project")
                .setApiKey("fake-api-key")
                .build()
        )
        val firestore = FirebaseFirestore.getInstance(app, "ai-studio-android-homekeep-8502e84b-2339-4853-b108-eaf44e3bbe94")
        val userDoc = firestore.collection("users").document("uid1")

        val task = CustomClassMapper.convertToCustomClass(
            mapOf(
                "title" to "Pay electric bill",
                "isCompleted" to true,
                "isDeleted" to false,
                "priority" to "URGENT_IMPORTANT",
                "createdAt" to 100L
            ),
            HouseholdTask::class.java,
            userDoc.collection("tasks").document("task1")
        )
        assertThat(task.id).isEqualTo("task1")
        assertThat(task.isCompleted).isTrue()

        val checklistItem = CustomClassMapper.convertToCustomClass(
            mapOf(
                "title" to "Whole Milk",
                "quantity" to "4 crates",
                "category" to "Dairy & Cold",
                "isChecked" to true,
                "isDeleted" to false,
                "imageBase64" to "img987"
            ),
            ChecklistItem::class.java,
            userDoc.collection("checklist_items").document("ci1")
        )
        assertThat(checklistItem.id).isEqualTo("ci1")
        assertThat(checklistItem.isChecked).isTrue()
        assertThat(checklistItem.imageBase64).isEqualTo("img987")

        val cat = CustomClassMapper.convertToCustomClass(
            mapOf("name" to "Spices", "isHidden" to true, "sortOrder" to 2),
            ChecklistCategory::class.java,
            userDoc.collection("checklist_categories").document("cat1")
        )
        assertThat(cat.id).isEqualTo("cat1")
        assertThat(cat.isHidden).isTrue()

        val area = CustomClassMapper.convertToCustomClass(
            mapOf("name" to "Front Counter", "description" to "POS terminal"),
            HouseArea::class.java,
            userDoc.collection("areas").document("area1")
        )
        assertThat(area.id).isEqualTo("area1")
        assertThat(area.name).isEqualTo("Front Counter")

        val habit = CustomClassMapper.convertToCustomClass(
            mapOf("title" to "Lock Back Door", "streakCount" to 5, "completedDates" to listOf("2026-11-18")),
            HouseholdHabit::class.java,
            userDoc.collection("habits").document("hab1")
        )
        assertThat(habit.id).isEqualTo("hab1")
        assertThat(habit.streakCount).isEqualTo(5)

        val photo = CustomClassMapper.convertToCustomClass(
            mapOf("title" to "Shelf A", "category" to "Display Shelves", "notes" to "Restock needed"),
            AreaPhoto::class.java,
            userDoc.collection("photos").document("ph1")
        )
        assertThat(photo.id).isEqualTo("ph1")
        assertThat(photo.title).isEqualTo("Shelf A")
    }
}
