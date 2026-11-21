package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
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
        val photoItem1 = ChecklistItem(
            id = "photo1",
            title = "Front Fridge Photo",
            quantity = "Need 2 cases",
            category = "Beverages",
            isChecked = false,
            imageBase64 = encoded
        )
        val photoItem2 = ChecklistItem(
            id = "photo2",
            title = "Side Cooler Photo",
            quantity = "1 tray",
            category = "Beverages",
            isChecked = false,
            imageBase64 = encoded
        )
        val textItem = ChecklistItem(
            id = "text1",
            title = "Sparkling Water",
            quantity = "6 packs",
            category = "Beverages",
            isChecked = false
        )
        var selectedState by mutableStateOf(false)
        var clickCount = 0

        composeRule.setContent {
            ShopKeepTheme {
                val dynamicItems = listOf(
                    photoItem1.copy(isChecked = selectedState),
                    photoItem2,
                    textItem
                )
                ChecklistScreen(
                    uiState = baseState.copy(checklistItems = dynamicItems),
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_photo_note_button").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_categories_button").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_note_card_photo1").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_select_circle_photo1").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_select_circle_photo1").performClick()

        // Verify selected state renders summary + Unselect All button AND moves photo1 to bottom "Marked (1)" section
        selectedState = true
        clickCount++
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("unselect_all_photos_button").assertIsDisplayed()
        composeRule.onNodeWithText("Marked (1)").performScrollTo()
        composeRule.onNodeWithText("Marked (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_note_card_photo1").assertExists()
        val markedLabelTop = composeRule.onNodeWithText("Marked (1)")
            .fetchSemanticsNode().boundsInRoot.top
        val textItemTop = composeRule.onNodeWithText("Sparkling Water")
            .fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(
            "Marked section ($markedLabelTop) must sit below text item ($textItemTop)",
            markedLabelTop > textItemTop
        )

        // Unmarking removes "Marked (1)" and moves photo1 back up alongside unmarked photo2
        selectedState = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Marked (1)").assertDoesNotExist()
        composeRule.onNodeWithTag("photo_note_card_photo1").performScrollTo()
        composeRule.onNodeWithTag("photo_note_card_photo1").assertIsDisplayed()
        val restoredPhotoTop = composeRule.onNodeWithTag("photo_note_card_photo1")
            .fetchSemanticsNode().boundsInRoot.top
        val photo2TopAfterRestore = composeRule.onNodeWithTag("photo_note_card_photo2")
            .fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertEquals(photo2TopAfterRestore, restoredPhotoTop, 1f)
        org.junit.Assert.assertEquals(1, clickCount)

        // Scroll back up to the compact header, open Edit Categories dialog and verify rename UI
        composeRule.onNodeWithTag("personal_checklist_list").performScrollToIndex(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_categories_button").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_categories_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manage_new_category_input").assertIsDisplayed()
        composeRule.onNodeWithTag("manage_rename_category_Beverages").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manage_rename_input").assertIsDisplayed()
    }

    @Test
    fun categoryPhotoOrdering_movesMarkedPhotosToBottom() {
        val personal = com.example.ui.checklist.orderPersonalCategoryItems(
            listOf(
                ChecklistItem(id = "p1", title = "Photo A", isChecked = true, imageBase64 = "abc", updatedAt = 200L),
                ChecklistItem(id = "p2", title = "Photo B", isChecked = false, imageBase64 = "def", updatedAt = 50L),
                ChecklistItem(id = "t1", title = "Milk", isChecked = false),
                ChecklistItem(id = "p3", title = "Photo C", isChecked = true, imageBase64 = "ghi", updatedAt = 100L)
            )
        )
        org.junit.Assert.assertEquals(listOf("p2"), personal.unmarkedPhotos.map { it.id })
        org.junit.Assert.assertEquals(listOf("t1"), personal.orderedText.map { it.id })
        org.junit.Assert.assertEquals(listOf("p3", "p1"), personal.markedPhotos.map { it.id })

        val shared = com.example.ui.checklist.orderSharedCategoryItems(
            listOf(
                SharedChecklistItem(id = "sp1", title = "Shared A", isChecked = true, imageBase64 = "abc", updatedAt = 300L),
                SharedChecklistItem(id = "sp2", title = "Shared B", isChecked = false, imageBase64 = "def", updatedAt = 10L)
            )
        )
        org.junit.Assert.assertEquals(listOf("sp2"), shared.unmarkedPhotos.map { it.id })
        org.junit.Assert.assertEquals(listOf("sp1"), shared.markedPhotos.map { it.id })
    }

    @Test
    fun tasksAndMatrixAndPhotosAndHabitsAndShareDialog_composeCleanly() {
        val (state, vm) = makeStateAndVm()
        val targetBoard = state.activeSharedBoard!!
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val msg = com.example.ui.components.ShareUtils.buildSharedBoardShareMessage(
            context = ctx,
            board = targetBoard,
            items = state.activeSharedItems
        )
        org.junit.Assert.assertTrue(
            "Share message must include exact board code ?checklist=${targetBoard.shareCode}",
            msg.contains("?checklist=${targetBoard.shareCode}")
        )
        composeRule.setContent {
            ShopKeepTheme {
                TasksScreen(uiState = state, viewModel = vm)
                HabitsScreen(uiState = state, viewModel = vm)
                PhotosScreen(uiState = state, viewModel = vm)
                EisenhowerMatrixScreen(uiState = state, viewModel = vm)
                ShareLinkReadyDialog(
                    board = targetBoard,
                    items = state.activeSharedItems,
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copy_web_url_button").assertIsDisplayed()
        composeRule.onNodeWithText(
            com.example.ui.components.ShareUtils.buildLiveWebUrl(ctx, targetBoard.shareCode)
        ).assertIsDisplayed()
    }

    @Test
    fun multiPhotoUploadDialog_andPhotosGrid_composeCleanly() {
        val tinyBitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val encoded = com.example.ui.components.ImageUtils.encodeToBase64(tinyBitmap)
        val drafts = listOf(
            com.example.ui.checklist.DraftMultiPhoto(id = "d1", base64 = encoded),
            com.example.ui.checklist.DraftMultiPhoto(id = "d2", base64 = encoded),
            com.example.ui.checklist.DraftMultiPhoto(id = "d3", base64 = encoded)
        )
        val (baseState, vm) = makeStateAndVm()
        val stateWithPhotos = baseState.copy(
            photos = listOf(
                com.example.data.model.AreaPhoto(id = "ap1", title = "Aisle 1", category = "Display Shelves", imageBase64 = encoded),
                com.example.data.model.AreaPhoto(id = "ap2", title = "Aisle 2", category = "Display Shelves", imageBase64 = encoded)
            )
        )

        composeRule.setContent {
            ShopKeepTheme {
                PhotosScreen(uiState = stateWithPhotos, viewModel = vm)
                com.example.ui.checklist.MultiPhotoUploadDialog(
                    title = "Add Photo Notes",
                    photos = drafts,
                    availableCategories = listOf("Beverages", "Snacks"),
                    initialCategory = "Beverages",
                    onAddMoreClick = {},
                    onRemovePhoto = {},
                    onSaveAll = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("photos_grid").assertExists()
        composeRule.onNodeWithTag("multi_photo_add_more_button").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_photo_category_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_save_multi_photos_button").assertIsDisplayed()
        composeRule.onNodeWithText("Save All (3)").assertIsDisplayed()
    }

    @Test
    fun deviceAlbumBrowser_sortsAlbumsAndPhotosAlphabetically_andFiltersByName() {
        val photos = listOf(
            com.example.ui.components.PickerPhotoItem(id = "1", displayName = "Zebra Gum", albumName = "Snacks", lastModified = 10L),
            com.example.ui.components.PickerPhotoItem(id = "2", displayName = "Apple Juice", albumName = "Beverages", lastModified = 30L),
            com.example.ui.components.PickerPhotoItem(id = "3", displayName = "Cola Can", albumName = "Beverages", lastModified = 20L),
            com.example.ui.components.PickerPhotoItem(id = "4", displayName = "Almond Milk", albumName = "Dairy", lastModified = 40L)
        )
        val albums = com.example.ui.components.DeviceAlbumBrowser.buildSortedAlbumSummaries(photos)
        org.junit.Assert.assertEquals(listOf("Beverages", "Dairy", "Snacks"), albums.map { it.name })

        val beveragesSorted = com.example.ui.components.DeviceAlbumBrowser.filterAndSortPickerPhotos(
            photos = photos,
            selectedAlbum = "Beverages",
            searchQuery = "",
            sortOrder = com.example.ui.components.PhotoPickerSortOrder.NAME_ASC
        )
        org.junit.Assert.assertEquals(listOf("Apple Juice", "Cola Can"), beveragesSorted.map { it.displayName })

        val searched = com.example.ui.components.DeviceAlbumBrowser.filterAndSortPickerPhotos(
            photos = photos,
            selectedAlbum = null,
            searchQuery = "cola",
            sortOrder = com.example.ui.components.PhotoPickerSortOrder.NAME_ASC
        )
        org.junit.Assert.assertEquals(listOf("Cola Can"), searched.map { it.displayName })
    }

    @Test
    fun smartPhotoSourceDialog_supportsSearchByName_andAlphabeticalAlbumSelection() {
        val tinyBitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val encoded = com.example.ui.components.ImageUtils.encodeToBase64(tinyBitmap)
        val savedPhotos = listOf(
            com.example.data.model.AreaPhoto(id = "p1", title = "Zebra Bar", category = "Snacks", imageBase64 = encoded),
            com.example.data.model.AreaPhoto(id = "p2", title = "Coke Zero", category = "Beverages", imageBase64 = encoded),
            com.example.data.model.AreaPhoto(id = "p3", title = "Apple Soda", category = "Beverages", imageBase64 = encoded)
        )
        var pickedCount = 0

        composeRule.setContent {
            ShopKeepTheme {
                com.example.ui.checklist.SmartPhotoSourceDialog(
                    savedAreaPhotos = savedPhotos,
                    onOpenFileSearchPicker = {},
                    onOpenGalleryPicker = {},
                    onLaunchLinkAlbumFolder = {},
                    onConfirmPickedPhotos = { pickedCount = it.size },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_search_files_button").assertIsDisplayed()
        composeRule.onNodeWithTag("picker_gallery_button").assertIsDisplayed()
        composeRule.onNodeWithTag("picker_link_album_folder_button").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_picker_search_input").assertIsDisplayed()

        // Switch to Albums A-Z tab and verify Beverages appears before Snacks
        composeRule.onNodeWithTag("picker_tab_albums").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("album_row_Beverages").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("album_row_Snacks").performScrollTo().assertIsDisplayed()
        val bevTop = composeRule.onNodeWithTag("album_row_Beverages").fetchSemanticsNode().boundsInRoot.top
        val snacksTop = composeRule.onNodeWithTag("album_row_Snacks").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("Beverages ($bevTop) must appear before Snacks ($snacksTop)", bevTop < snacksTop)

        // Tap Beverages album and verify Apple Soda appears before Coke Zero (alphabetical A-Z)
        composeRule.onNodeWithTag("album_row_Beverages").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_photo_card_shop_p3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("picker_photo_card_shop_p2").performScrollTo().assertIsDisplayed()
        val appleTop = composeRule.onNodeWithTag("picker_photo_card_shop_p3").fetchSemanticsNode().boundsInRoot.top
        val appleLeft = composeRule.onNodeWithTag("picker_photo_card_shop_p3").fetchSemanticsNode().boundsInRoot.left
        val cokeLeft = composeRule.onNodeWithTag("picker_photo_card_shop_p2").fetchSemanticsNode().boundsInRoot.left
        org.junit.Assert.assertTrue("Apple Soda must be placed first in alphabetical 2-col grid", appleLeft < cokeLeft && appleTop >= 0f)

        // Select a photo and confirm
        composeRule.onNodeWithTag("picker_photo_card_shop_p3").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_pick_selected_photos_button").performClick()
        org.junit.Assert.assertEquals(1, pickedCount)
    }

    @Test
    fun manageSharedBoardsDialog_supportsReorderRenameAndDelete() {
        var boards by mutableStateOf(
            listOf(
                SharedChecklist(id = "SHOP-A1", shareCode = "SHOP-A1", title = "Morning Counter", sortOrder = 1),
                SharedChecklist(id = "SHOP-B2", shareCode = "SHOP-B2", title = "Saree Stock", sortOrder = 2)
            )
        )
        var deletedCode: String? = null

        composeRule.setContent {
            ShopKeepTheme {
                com.example.ui.checklist.ManageSharedBoardsDialog(
                    boards = boards,
                    activeShareCode = "SHOP-A1",
                    onSelectBoard = {},
                    onCreateNewBoard = {},
                    onMoveBoard = { code, dir ->
                        val mutable = boards.toMutableList()
                        val idx = mutable.indexOfFirst { it.shareCode == code }
                        if (idx >= 0) {
                            val target = (idx + dir).coerceIn(0, mutable.lastIndex)
                            if (target != idx) {
                                val moved = mutable.removeAt(idx)
                                mutable.add(target, moved)
                                boards = mutable.mapIndexed { i, b -> b.copy(sortOrder = i + 1) }
                            }
                        }
                    },
                    onRenameBoard = { code, newTitle ->
                        boards = boards.map { if (it.shareCode == code) it.copy(title = newTitle) else it }
                    },
                    onDeleteBoard = { code ->
                        deletedCode = code
                        boards = boards.filterNot { it.shareCode == code }
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manage_board_row_SHOP-A1").assertIsDisplayed()
        composeRule.onNodeWithTag("manage_board_row_SHOP-B2").assertIsDisplayed()

        // Move SHOP-B2 up so it becomes #1 above SHOP-A1
        composeRule.onNodeWithTag("move_board_up_SHOP-B2").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(listOf("SHOP-B2", "SHOP-A1"), boards.map { it.shareCode })

        // Rename SHOP-B2
        composeRule.onNodeWithTag("rename_board_SHOP-B2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rename_board_input").performTextReplacement("Fancy Sarees")
        composeRule.onNodeWithTag("confirm_rename_board_button").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("Fancy Sarees", boards.first().title)

        // Delete SHOP-A1 with confirmation
        composeRule.onNodeWithTag("delete_board_SHOP-A1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_delete_board_button").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("SHOP-A1", deletedCode)
        org.junit.Assert.assertEquals(listOf("SHOP-B2"), boards.map { it.shareCode })
    }

    @Test
    fun htmlExport_embedsImagesInResponsiveGrid_andMovesMarkedPhotosToBottom() {
        val fakeB64Unmarked = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"
        val fakeB64Marked = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
        val html = com.example.ui.components.ShareUtils.buildChecklistHtml(
            title = "Shop Stock Test",
            sections = listOf(
                "1.MED-1 RANGE" to listOf(
                    com.example.ui.components.HtmlChecklistItem(
                        title = "Saree Unmarked",
                        quantity = "2 pcs",
                        isChecked = false,
                        imageBase64 = fakeB64Unmarked
                    ),
                    com.example.ui.components.HtmlChecklistItem(
                        title = "Saree Marked",
                        quantity = "1 pc",
                        isChecked = true,
                        imageBase64 = fakeB64Marked,
                        updatedAt = 500L
                    ),
                    com.example.ui.components.HtmlChecklistItem(
                        title = "Tag Pins",
                        quantity = "1 box",
                        isChecked = false
                    )
                )
            )
        )
        org.junit.Assert.assertTrue(html.contains("src=\"data:image/jpeg;base64,$fakeB64Unmarked\""))
        org.junit.Assert.assertTrue(html.contains("src=\"data:image/jpeg;base64,$fakeB64Marked\""))
        org.junit.Assert.assertTrue(html.contains("Marked (1)"))
        val unmarkedIdx = html.indexOf("Saree Unmarked")
        val textIdx = html.indexOf("Tag Pins")
        val markedIdx = html.indexOf("Saree Marked")
        org.junit.Assert.assertTrue("Unmarked photo ($unmarkedIdx) should precede text ($textIdx)", unmarkedIdx < textIdx)
        org.junit.Assert.assertTrue("Text ($textIdx) should precede marked photo ($markedIdx)", textIdx < markedIdx)
    }

    @Test
    fun repositoryExtractShareCode_handlesLandingUrlsAndBoardQueryUrls() {
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
        val repo = TaskRepository(firestore)

        org.junit.Assert.assertEquals(
            "PUBLIC_DEFAULT",
            repo.extractShareCode("https://nandwanifamily242l-hash.github.io/SHOPKEEP/")
        )
        org.junit.Assert.assertEquals(
            "SHOP-QP9CN8",
            repo.extractShareCode("https://nandwanifamily242l-hash.github.io/SHOPKEEP/?checklist=SHOP-QP9CN8")
        )
        org.junit.Assert.assertEquals(
            "SHOP-ABC123",
            repo.extractShareCode("https://gen-lang-client-0080616262.web.app/checklist/shop-abc123")
        )
    }
}




