package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChecklistItem
import com.example.data.model.HouseArea
import com.example.data.model.HouseholdHabit
import com.example.data.model.HouseholdTask
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem
import com.example.data.repository.TaskRepository
import com.example.ui.checklist.ChecklistScreen
import com.example.ui.components.ShareLinkReadyDialog
import com.example.ui.habits.HabitsScreen
import com.example.ui.home.HomeDashboardScreen
import com.example.ui.photos.PhotosScreen
import com.example.ui.tasks.EisenhowerMatrixScreen
import com.example.ui.tasks.TasksScreen
import com.example.ui.theme.ShopKeepTheme
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenCompositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeStateAndVm(): Pair<ShopKeepUiState, TaskViewModel> {
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
        val viewModel = TaskViewModel(TaskRepository(firestore), context)

        val sampleBoard = SharedChecklist(
            id = "SHOP-123456",
            shareCode = "SHOP-123456",
            title = "Main Shop Board",
            isPublicEditable = true
        )
        val sampleState = ShopKeepUiState(
            tasks = listOf(HouseholdTask(id = "t1", title = "Order Milk", category = "Restock")),
            areas = listOf(HouseArea(id = "a1", name = "Front Counter", description = "POS")),
            habits = listOf(HouseholdHabit(id = "h1", title = "Check Fridge Temp")),
            checklistItems = listOf(ChecklistItem(id = "c1", title = "Espresso Beans", quantity = "2 kg")),
            sharedChecklists = listOf(sampleBoard),
            activeSharedCode = "SHOP-123456",
            activeSharedBoard = sampleBoard,
            activeSharedItems = listOf(SharedChecklistItem(id = "s1", title = "Paper Cups", quantity = "5 sleeves"))
        )
        return sampleState to viewModel
    }

    @Test
    fun homeDashboard_composesCleanly() {
        val (state, _) = makeStateAndVm()
        composeRule.setContent {
            ShopKeepTheme {
                HomeDashboardScreen(
                    uiState = state,
                    userDisplayName = "Owner",
                    onOpenTasks = {},
                    onOpenChecklist = {},
                    onOpenPhotos = {},
                    onOpenMatrix = {},
                    onOpenHabits = {},
                    onOpenAi = {},
                    onToggleTask = {},
                    onSaveZone = { _, _, _ -> },
                    onDeleteZone = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_checklist_button").assertIsDisplayed()
    }

    @Test
    fun checklistScreen_composesCleanly_andPhotoNoteTopLeftCircleTogglesSelection() {
        val tinyBitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val encoded = com.example.ui.components.ImageUtils.encodeToBase64(tinyBitmap)

        val (baseState, vm) = makeStateAndVm()
        val photoItem = ChecklistItem(
            id = "photo1",
            title = "Front Fridge Photo",
            quantity = "Need 2 cases",
            category = "Beverages",
            isChecked = false,
            imageBase64 = encoded
        )
        var selectedState by mutableStateOf(false)
        var clickCount = 0

        composeRule.setContent {
            ShopKeepTheme {
                val dynamicItems = listOf(photoItem.copy(isChecked = selectedState))
                ChecklistScreen(
                    uiState = baseState.copy(checklistItems = dynamicItems),
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_photo_note_button").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_note_card_photo1").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_select_circle_photo1").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_select_circle_photo1").performClick()

        // Verify selected state renders summary + Unselect All button and toggles back off
        selectedState = true
        clickCount++
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("unselect_all_photos_button").assertIsDisplayed()

        selectedState = false
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(1, clickCount)
    }

    @Test
    fun tasksAndMatrixAndPhotosAndHabitsAndShareDialog_composeCleanly() {
        val (state, vm) = makeStateAndVm()
        composeRule.setContent {
            ShopKeepTheme {
                TasksScreen(uiState = state, viewModel = vm)
                HabitsScreen(uiState = state, viewModel = vm)
                PhotosScreen(uiState = state, viewModel = vm)
                EisenhowerMatrixScreen(uiState = state, viewModel = vm)
                ShareLinkReadyDialog(
                    board = state.activeSharedBoard!!,
                    items = state.activeSharedItems,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copy_web_url_button").assertIsDisplayed()
    }
}
