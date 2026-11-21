package com.example.ui.checklist

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.ChecklistItem
import com.example.data.model.ChecklistSortOption
import com.example.data.model.SharedChecklistItem
import com.example.ui.components.DeviceAlbumBrowser
import com.example.ui.components.HtmlChecklistItem
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.ImageUtils
import com.example.ui.components.PickerPhotoItem
import com.example.ui.components.ShareLinkReadyDialog
import com.example.ui.components.ShareUtils
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun ChecklistScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = My Stock, 1 = Shared Live
    LaunchedEffect(uiState.navigateToSharedTabEvent) {
        if (uiState.navigateToSharedTabEvent > 0) {
            selectedSubTab = 1
            viewModel.consumeSharedTabNavigation()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddPersonalItemDialog by remember { mutableStateOf(false) }
    var editingPersonalItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var preselectedCategory by remember { mutableStateOf("General Stock") }

    var draftPersonalPhotoBase64 by remember { mutableStateOf("") }
    var draftPersonalPhotoTitle by remember { mutableStateOf("") }
    var draftSharedPhotoBase64 by remember { mutableStateOf("") }
    var draftSharedPhotoTitle by remember { mutableStateOf("") }
    var draftMultiPersonalPhotos by remember { mutableStateOf<List<DraftMultiPhoto>>(emptyList()) }
    var draftMultiSharedPhotos by remember { mutableStateOf<List<DraftMultiPhoto>>(emptyList()) }
    var isPreparingPhotos by remember { mutableStateOf(false) }
    var showSmartPhotoSourceDialog by remember { mutableStateOf(false) }
    var albumRefreshKey by remember { mutableIntStateOf(0) }
    var editorTargetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editorForShared by remember { mutableStateOf(false) }
    var editorDirectSavePersonal by remember { mutableStateOf<ChecklistItem?>(null) }
    var editorDirectSaveShared by remember { mutableStateOf<SharedChecklistItem?>(null) }

    var previewPersonalPhotoItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var previewSharedPhotoItem by remember { mutableStateOf<SharedChecklistItem?>(null) }
    var editingSharedItem by remember { mutableStateOf<SharedChecklistItem?>(null) }

    var showManagePersonalCategories by remember { mutableStateOf(false) }
    var showManageSharedCategories by remember { mutableStateOf(false) }
    var renamingCategoryTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // name to isShared

    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var showJoinBoardDialog by remember { mutableStateOf(false) }
    var showManageSharedBoards by remember { mutableStateOf(false) }
    var showAddSharedItemDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    val sortedCategories = remember(
        uiState.checklistItems,
        uiState.customCategories,
        uiState.checklistSortOption
    ) {
        viewModel.visibleChecklistCategories()
    }

    var pickForSharedBoard by rememberSaveable { mutableStateOf(false) }

    fun applyEncodedPhotos(encodedList: List<DraftMultiPhoto>) {
        if (encodedList.isEmpty()) {
            Toast.makeText(context, "Could not load selected photo(s)", Toast.LENGTH_SHORT).show()
            return
        }
        if (pickForSharedBoard) {
            val alreadyMulti = draftMultiSharedPhotos.isNotEmpty()
            if (encodedList.size == 1 && !alreadyMulti) {
                draftSharedPhotoBase64 = encodedList.first().base64
                draftSharedPhotoTitle = encodedList.first().displayName
                if (!showAddSharedItemDialog && uiState.activeSharedBoard != null) {
                    editingSharedItem = null
                    showAddSharedItemDialog = true
                }
            } else {
                showAddSharedItemDialog = false
                draftMultiSharedPhotos = draftMultiSharedPhotos + encodedList
            }
        } else {
            val alreadyMulti = draftMultiPersonalPhotos.isNotEmpty()
            if (encodedList.size == 1 && !alreadyMulti) {
                draftPersonalPhotoBase64 = encodedList.first().base64
                draftPersonalPhotoTitle = encodedList.first().displayName
                if (!showAddPersonalItemDialog) {
                    editingPersonalItem = null
                    preselectedCategory = sortedCategories.firstOrNull() ?: "General Stock"
                    showAddPersonalItemDialog = true
                }
            } else {
                showAddPersonalItemDialog = false
                draftMultiPersonalPhotos = draftMultiPersonalPhotos + encodedList
            }
        }
    }

    fun handleSelectedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isPreparingPhotos = true
            val encodedList = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val bmp = ImageUtils.decodeBitmapFromUri(context, uri, maxDimension = 1024)
                    bmp?.let {
                        val b64 = ImageUtils.encodeToBase64(it, maxWidth = 720, quality = 74)
                        val name = DeviceAlbumBrowser.queryDisplayName(context, uri)
                        if (b64.isNotBlank()) {
                            DraftMultiPhoto(
                                id = UUID.randomUUID().toString(),
                                base64 = b64,
                                displayName = name
                            )
                        } else {
                            null
                        }
                    }
                }
            }
            isPreparingPhotos = false
            applyEncodedPhotos(encodedList)
        }
    }

    fun handlePickedBrowserPhotos(picked: List<PickerPhotoItem>) {
        if (picked.isEmpty()) return
        scope.launch {
            isPreparingPhotos = true
            val encodedList = withContext(Dispatchers.IO) {
                picked.mapNotNull { item ->
                    val resolvedB64 = when {
                        item.base64.isNotBlank() -> item.base64
                        item.uri != null -> {
                            val bmp = ImageUtils.decodeBitmapFromUri(context, item.uri, maxDimension = 1024)
                            bmp?.let { ImageUtils.encodeToBase64(it, maxWidth = 720, quality = 74) } ?: ""
                        }
                        else -> ""
                    }
                    if (resolvedB64.isNotBlank()) {
                        DraftMultiPhoto(
                            id = UUID.randomUUID().toString(),
                            base64 = resolvedB64,
                            displayName = item.displayName
                        )
                    } else {
                        null
                    }
                }
            }
            isPreparingPhotos = false
            applyEncodedPhotos(encodedList)
        }
    }

    val checklistPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> handleSelectedUris(uris) }

    val checklistFileSearchPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleSelectedUris(uris) }

    val linkAlbumFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            DeviceAlbumBrowser.savePersistedTreeUri(context, treeUri)
            albumRefreshKey += 1
        }
    }

    val activePersonalItems = remember(uiState.checklistItems, searchQuery) {
        uiState.checklistItems
            .filter { !it.isDeleted }
            .filter {
                searchQuery.isBlank() ||
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.notes.contains(searchQuery, ignoreCase = true)
            }
    }

    val selectedPersonalPhotoCount = remember(activePersonalItems) {
        activePersonalItems.count { it.imageBase64.isNotBlank() && it.isChecked }
    }
    val selectedSharedPhotoCount = remember(uiState.activeSharedItems) {
        uiState.activeSharedItems.count { it.imageBase64.isNotBlank() && it.isChecked }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedSubTab == 0) {
                        editingPersonalItem = null
                        draftPersonalPhotoBase64 = ""
                        draftPersonalPhotoTitle = ""
                        preselectedCategory = sortedCategories.firstOrNull() ?: "General Stock"
                        showAddPersonalItemDialog = true
                    } else if (uiState.activeSharedBoard != null) {
                        editingSharedItem = null
                        draftSharedPhotoBase64 = ""
                        draftSharedPhotoTitle = ""
                        showAddSharedItemDialog = true
                    } else {
                        showCreateBoardDialog = true
                    }
                },
                modifier = Modifier.testTag("checklist_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add checklist item")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("My Stock Lists", style = MaterialTheme.typography.labelLarge) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("Shared Live Boards", style = MaterialTheme.typography.labelLarge) }
                )
            }
            if (isPreparingPhotos) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (selectedSubTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("personal_checklist_list"),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "personal_compact_controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Compact single-line search bar (36dp height)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search items or categories...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("checklist_search_input")
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Single compact horizontally-scrollable action strip (32dp chips)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        pickForSharedBoard = false
                                        showSmartPhotoSourceDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("+ Photo(s)") },
                                    modifier = Modifier.height(32.dp).testTag("add_photo_note_button")
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        viewModel.publishPersonalListToPublicBoard {
                                            selectedSubTab = 1
                                            showShareDialog = true
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("Public Link") },
                                    modifier = Modifier.height(32.dp).testTag("publish_public_link_button")
                                )
                                Box {
                                    FilterChip(
                                        selected = false,
                                        onClick = { showSortMenu = true },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                                        },
                                        label = { Text(uiState.checklistSortOption.label) },
                                        modifier = Modifier.height(32.dp).testTag("checklist_sort_button")
                                    )
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        ChecklistSortOption.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.label) },
                                                onClick = {
                                                    viewModel.setChecklistSortOption(option)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = { showManagePersonalCategories = true },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("Categories") },
                                    modifier = Modifier.height(32.dp).testTag("edit_categories_button")
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val grouped = sortedCategories.map { cat ->
                                            cat to activePersonalItems
                                                .filter { it.category.equals(cat, ignoreCase = true) }
                                                .map {
                                                    HtmlChecklistItem(
                                                        title = it.title,
                                                        quantity = it.quantity,
                                                        notes = it.notes,
                                                        isChecked = it.isChecked,
                                                        imageBase64 = it.imageBase64,
                                                        updatedAt = it.updatedAt
                                                    )
                                                }
                                        }.filter { it.second.isNotEmpty() }
                                        ShareUtils.exportChecklistHtmlFile(
                                            context = context,
                                            title = "ShopKeep Restock Checklist",
                                            sections = grouped
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Html, contentDescription = "Export interactive HTML checklist", modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("HTML") },
                                    modifier = Modifier.height(32.dp).testTag("export_html_checklist_button")
                                )
                            }

                            if (selectedPersonalPhotoCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$selectedPersonalPhotoCount photo note(s) selected",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            onClick = { viewModel.unselectAllChecklistPhotos() },
                                            modifier = Modifier.testTag("unselect_all_photos_button")
                                        ) {
                                            Text("Unselect All")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(sortedCategories, key = { it }) { category ->
                        val catItems = activePersonalItems.filter {
                            it.category.equals(category, ignoreCase = true)
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${catItems.count { !it.isChecked }} pending • ${catItems.size} total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = { renamingCategoryTarget = category to false },
                                            modifier = Modifier.testTag("rename_category_${category}")
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit category name")
                                        }
                                        IconButton(
                                            onClick = {
                                                editingPersonalItem = null
                                                draftPersonalPhotoBase64 = ""
                                                draftPersonalPhotoTitle = ""
                                                preselectedCategory = category
                                                showAddPersonalItemDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = "Add item to $category")
                                        }
                                    }
                                }

                                if (catItems.isEmpty()) {
                                    Text(
                                        text = "No items yet. Tap + or + Photo Note to add to $category.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else {
                                    val ordered = orderPersonalCategoryItems(catItems)
                                    val unmarkedPhotos = ordered.unmarkedPhotos
                                    val orderedTextItems = ordered.orderedText
                                    val markedPhotos = ordered.markedPhotos

                                    if (unmarkedPhotos.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        ResponsivePhotoGrid(items = unmarkedPhotos) { photoItem, cardModifier ->
                                            ChecklistPhotoNoteCard(
                                                itemId = photoItem.id,
                                                title = photoItem.title,
                                                quantity = photoItem.quantity,
                                                notes = photoItem.notes,
                                                isSelected = photoItem.isChecked,
                                                imageBase64 = photoItem.imageBase64,
                                                onToggleSelect = { viewModel.toggleChecklistItem(photoItem) },
                                                onPreviewPhoto = { previewPersonalPhotoItem = photoItem },
                                                onEdit = {
                                                    editingPersonalItem = photoItem
                                                    draftPersonalPhotoBase64 = photoItem.imageBase64
                                                    preselectedCategory = photoItem.category
                                                    showAddPersonalItemDialog = true
                                                },
                                                onDelete = { viewModel.deleteChecklistItem(photoItem.id, permanent = false) },
                                                modifier = cardModifier
                                            )
                                        }
                                    }

                                    if (orderedTextItems.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        orderedTextItems.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        editingPersonalItem = item
                                                        draftPersonalPhotoBase64 = item.imageBase64
                                                        preselectedCategory = item.category
                                                        showAddPersonalItemDialog = true
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = item.isChecked,
                                                    onCheckedChange = { viewModel.toggleChecklistItem(item) }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (item.quantity.isNotBlank() || item.notes.isNotBlank()) {
                                                        Text(
                                                            text = listOf(item.quantity, item.notes).filter { it.isNotBlank() }.joinToString(" • "),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { viewModel.deleteChecklistItem(item.id, permanent = false) }) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = "Move to recycle bin",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (markedPhotos.isNotEmpty()) {
                                        if (unmarkedPhotos.isNotEmpty() || orderedTextItems.isNotEmpty()) {
                                            Text(
                                                text = "Marked (${markedPhotos.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                            )
                                        } else {
                                            Spacer(Modifier.height(8.dp))
                                        }
                                        ResponsivePhotoGrid(items = markedPhotos) { photoItem, cardModifier ->
                                            ChecklistPhotoNoteCard(
                                                itemId = photoItem.id,
                                                title = photoItem.title,
                                                quantity = photoItem.quantity,
                                                notes = photoItem.notes,
                                                isSelected = photoItem.isChecked,
                                                imageBase64 = photoItem.imageBase64,
                                                onToggleSelect = { viewModel.toggleChecklistItem(photoItem) },
                                                onPreviewPhoto = { previewPersonalPhotoItem = photoItem },
                                                onEdit = {
                                                    editingPersonalItem = photoItem
                                                    draftPersonalPhotoBase64 = photoItem.imageBase64
                                                    preselectedCategory = photoItem.category
                                                    showAddPersonalItemDialog = true
                                                },
                                                onDelete = { viewModel.deleteChecklistItem(photoItem.id, permanent = false) },
                                                modifier = cardModifier
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // SHARED REAL-TIME BOARDS TAB (compact scrollable header so photos & categories fill the screen)
                val activeBoard = uiState.activeSharedBoard
                val sharedCategories = remember(activeBoard?.categories, uiState.activeSharedItems) {
                    ((activeBoard?.categories ?: emptyList()) + uiState.activeSharedItems.map { it.category })
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("shared_checklist_list"),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "shared_compact_controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = { showCreateBoardDialog = true },
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("New Board") },
                                    modifier = Modifier.height(32.dp).testTag("create_shared_board_button")
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { showJoinBoardDialog = true },
                                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("Join Code") },
                                    modifier = Modifier.height(32.dp).testTag("join_shared_board_button")
                                )
                                if (activeBoard != null) {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            pickForSharedBoard = true
                                            showSmartPhotoSourceDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        label = { Text("+ Photo(s)") },
                                        modifier = Modifier.height(32.dp).testTag("add_shared_photo_note_button")
                                    )
                                    FilterChip(
                                        selected = true,
                                        onClick = { showShareDialog = true },
                                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        label = { Text("Public Link") },
                                        modifier = Modifier.height(32.dp).testTag("open_share_link_dialog_button")
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = { showManageSharedCategories = true },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        label = { Text("Categories") },
                                        modifier = Modifier.height(32.dp).testTag("edit_shared_categories_button")
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val grouped = sharedCategories.map { cat ->
                                                cat to uiState.activeSharedItems
                                                    .filter { it.category.equals(cat, ignoreCase = true) }
                                                    .map {
                                                        HtmlChecklistItem(
                                                            title = it.title,
                                                            quantity = it.quantity,
                                                            notes = it.notes,
                                                            isChecked = it.isChecked,
                                                            imageBase64 = it.imageBase64,
                                                            updatedAt = it.updatedAt
                                                        )
                                                    }
                                            }.filter { it.second.isNotEmpty() }
                                            ShareUtils.exportChecklistHtmlFile(
                                                context = context,
                                                title = activeBoard.title.ifBlank { "ShopKeep Live Board" },
                                                sections = grouped
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Html, contentDescription = "Export shared board HTML", modifier = Modifier.size(15.dp)) },
                                        label = { Text("HTML") },
                                        modifier = Modifier.height(32.dp).testTag("export_shared_html_checklist_button")
                                    )
                                }
                                if (uiState.sharedChecklists.isNotEmpty()) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { showManageSharedBoards = true },
                                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        label = { Text("Boards") },
                                        modifier = Modifier.height(32.dp).testTag("manage_shared_boards_button")
                                    )
                                }
                            }

                            if (uiState.sharedChecklists.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.sharedChecklists.forEach { board ->
                                        FilterChip(
                                            selected = uiState.activeSharedCode == board.shareCode,
                                            onClick = { viewModel.selectSharedChecklist(board.shareCode) },
                                            label = { Text("${board.title} (${board.shareCode})") },
                                            modifier = Modifier.height(30.dp)
                                        )
                                    }
                                }
                            }

                            if (activeBoard != null) {
                                Text(
                                    text = "${activeBoard.title} • " + if (activeBoard.isPublicEditable) {
                                        "Public: View & Mark/Unmark only"
                                    } else {
                                        "Public: Strictly view-only"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }

                            if (selectedSharedPhotoCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$selectedSharedPhotoCount photo note(s) selected",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = { viewModel.unselectAllSharedPhotos() }) {
                                            Text("Unselect All")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (activeBoard == null) {
                        item(key = "shared_empty_card") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Real-Time Live Web Sharing", fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "Create a live board to get a shareable Web URL. Visitors can open the URL in any browser to view & mark items.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    } else {
                        items(sharedCategories, key = { it }) { cat ->
                                val itemsInCat = uiState.activeSharedItems.filter {
                                    it.category.equals(cat, ignoreCase = true)
                                }
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { renamingCategoryTarget = cat to true },
                                                modifier = Modifier.testTag("rename_shared_category_$cat")
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Rename shared category")
                                            }
                                        }

                                        val orderedShared = orderSharedCategoryItems(itemsInCat)
                                        val unmarkedSharedPhotos = orderedShared.unmarkedPhotos
                                        val orderedSharedText = orderedShared.orderedText
                                        val markedSharedPhotos = orderedShared.markedPhotos

                                        if (unmarkedSharedPhotos.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            ResponsivePhotoGrid(items = unmarkedSharedPhotos) { photoItem, cardModifier ->
                                                ChecklistPhotoNoteCard(
                                                    itemId = photoItem.id,
                                                    title = photoItem.title,
                                                    quantity = photoItem.quantity,
                                                    notes = photoItem.notes,
                                                    isSelected = photoItem.isChecked,
                                                    imageBase64 = photoItem.imageBase64,
                                                    onToggleSelect = { viewModel.toggleSharedItem(photoItem) },
                                                    onPreviewPhoto = { previewSharedPhotoItem = photoItem },
                                                    onEdit = {
                                                        editingSharedItem = photoItem
                                                        draftSharedPhotoBase64 = photoItem.imageBase64
                                                        showAddSharedItemDialog = true
                                                    },
                                                    onDelete = { viewModel.deleteSharedItem(photoItem.id) },
                                                    modifier = cardModifier
                                                )
                                            }
                                        }

                                        orderedSharedText.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = item.isChecked,
                                                    onCheckedChange = { viewModel.toggleSharedItem(item) }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                                    )
                                                    if (item.quantity.isNotBlank()) {
                                                        Text(item.quantity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                IconButton(onClick = { viewModel.deleteSharedItem(item.id) }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove shared item")
                                                }
                                            }
                                        }

                                        if (markedSharedPhotos.isNotEmpty()) {
                                            if (unmarkedSharedPhotos.isNotEmpty() || orderedSharedText.isNotEmpty()) {
                                                Text(
                                                    text = "Marked (${markedSharedPhotos.size})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                                )
                                            } else {
                                                Spacer(Modifier.height(6.dp))
                                            }
                                            ResponsivePhotoGrid(items = markedSharedPhotos) { photoItem, cardModifier ->
                                                ChecklistPhotoNoteCard(
                                                    itemId = photoItem.id,
                                                    title = photoItem.title,
                                                    quantity = photoItem.quantity,
                                                    notes = photoItem.notes,
                                                    isSelected = photoItem.isChecked,
                                                    imageBase64 = photoItem.imageBase64,
                                                    onToggleSelect = { viewModel.toggleSharedItem(photoItem) },
                                                    onPreviewPhoto = { previewSharedPhotoItem = photoItem },
                                                    onEdit = {
                                                        editingSharedItem = photoItem
                                                        draftSharedPhotoBase64 = photoItem.imageBase64
                                                        showAddSharedItemDialog = true
                                                    },
                                                    onDelete = { viewModel.deleteSharedItem(photoItem.id) },
                                                    modifier = cardModifier
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    if (showAddPersonalItemDialog) {
        var title by remember(editingPersonalItem?.id, draftPersonalPhotoTitle) {
            mutableStateOf(editingPersonalItem?.title ?: draftPersonalPhotoTitle)
        }
        var qty by remember(editingPersonalItem?.id) { mutableStateOf(editingPersonalItem?.quantity ?: "") }
        var category by remember(editingPersonalItem?.id, preselectedCategory) { mutableStateOf(preselectedCategory) }
        var notes by remember(editingPersonalItem?.id) { mutableStateOf(editingPersonalItem?.notes ?: "") }
        val draftBitmap = remember(draftPersonalPhotoBase64) {
            ImageUtils.decodeBitmapFromBase64(draftPersonalPhotoBase64)
        }
        AlertDialog(
            onDismissRequest = { showAddPersonalItemDialog = false },
            title = {
                Text(
                    when {
                        editingPersonalItem != null -> "Edit Stock Item"
                        draftPersonalPhotoBase64.isNotBlank() -> "Add Photo Note"
                        else -> "Add Stock Item"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draftBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = draftBitmap.asImageBitmap(),
                                contentDescription = "Attached checklist photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editorForShared = false
                                    editorDirectSavePersonal = null
                                    editorTargetBitmap = draftBitmap
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Crop / Edit")
                            }
                            TextButton(onClick = { draftPersonalPhotoBase64 = "" }) {
                                Text("Remove")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                pickForSharedBoard = false
                                showSmartPhotoSourceDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("attach_checklist_photo_button")
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Photo(s)")
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = {
                            Text(if (draftBitmap != null) "Photo Title / Label (optional)" else "Item Name")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("checklist_item_title_input")
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantity (e.g. 2 crates, 10 kg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (sortedCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sortedCategories.forEach { catOption ->
                                FilterChip(
                                    selected = category.equals(catOption, ignoreCase = true),
                                    onClick = { category = catOption },
                                    label = { Text(catOption) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Supplier / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val effectiveTitle = title.ifBlank { if (draftBitmap != null) "Photo Note" else "" }
                        if (effectiveTitle.isNotBlank()) {
                            viewModel.saveChecklistItem(
                                id = editingPersonalItem?.id ?: "",
                                title = effectiveTitle,
                                quantity = qty,
                                category = category,
                                notes = notes,
                                imageBase64 = draftPersonalPhotoBase64
                            )
                            draftPersonalPhotoBase64 = ""
                            showAddPersonalItemDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_checklist_item_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPersonalItemDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showManagePersonalCategories) {
        val entries = sortedCategories.map { cat ->
            EditableCategoryEntry(
                name = cat,
                itemCount = activePersonalItems.count { it.category.equals(cat, ignoreCase = true) }
            )
        }
        ManageCategoriesDialog(
            title = "Edit Stock Categories",
            categories = entries,
            fallbackMoveTarget = "General Stock",
            onAddCategory = { viewModel.addChecklistCategory(it) },
            onRenameCategory = { oldName, newName -> viewModel.renameChecklistCategory(oldName, newName) },
            onDeleteCategory = { name, moveTo -> viewModel.deleteChecklistCategory(name, moveTo) },
            onDismiss = { showManagePersonalCategories = false }
        )
    }

    val currentSharedBoardForCategories = uiState.activeSharedBoard
    if (showManageSharedCategories && currentSharedBoardForCategories != null) {
        val sharedCatNames = (currentSharedBoardForCategories.categories + uiState.activeSharedItems.map { it.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        val entries = sharedCatNames.map { cat ->
            EditableCategoryEntry(
                name = cat,
                itemCount = uiState.activeSharedItems.count { it.category.equals(cat, ignoreCase = true) }
            )
        }
        ManageCategoriesDialog(
            title = "Edit Live Board Categories",
            categories = entries,
            fallbackMoveTarget = "Supplies",
            onAddCategory = { viewModel.addSharedCategory(it) },
            onRenameCategory = { oldName, newName -> viewModel.renameSharedCategory(oldName, newName) },
            onDeleteCategory = { name, moveTo -> viewModel.deleteSharedCategory(name, moveTo) },
            onDismiss = { showManageSharedCategories = false }
        )
    }

    renamingCategoryTarget?.let { (oldName, isShared) ->
        var updatedName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { renamingCategoryTarget = null },
            title = { Text("Rename Category") },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_category_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedName.isNotBlank()) {
                            if (isShared) {
                                viewModel.renameSharedCategory(oldName, updatedName)
                            } else {
                                viewModel.renameChecklistCategory(oldName, updatedName)
                            }
                            renamingCategoryTarget = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_category_button")
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingCategoryTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showManageSharedBoards) {
        ManageSharedBoardsDialog(
            boards = uiState.sharedChecklists,
            activeShareCode = uiState.activeSharedCode,
            onSelectBoard = { code -> viewModel.selectSharedChecklist(code) },
            onCreateNewBoard = { showCreateBoardDialog = true },
            onMoveBoard = { code, dir -> viewModel.moveSharedBoard(code, dir) },
            onRenameBoard = { code, newTitle -> viewModel.renameSharedBoard(code, newTitle) },
            onDeleteBoard = { code -> viewModel.deleteSharedBoard(code) },
            onDismiss = { showManageSharedBoards = false }
        )
    }

    if (showCreateBoardDialog) {
        var boardTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBoardDialog = false },
            title = { Text("Create Real-Time Shared Board") },
            text = {
                OutlinedTextField(
                    value = boardTitle,
                    onValueChange = { boardTitle = it },
                    label = { Text("Board Title (e.g. Morning Counter Restock)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shared_board_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createSharedChecklist(boardTitle.ifBlank { "Shop Restock Board" }) {
                            showShareDialog = true
                        }
                        showCreateBoardDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_board_button")
                ) { Text("Create & Get Link") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBoardDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinBoardDialog) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinBoardDialog = false },
            title = { Text("Join Shared Board") },
            text = {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Share Code or Web URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (codeInput.isNotBlank()) {
                        viewModel.joinSharedChecklist(codeInput)
                        showJoinBoardDialog = false
                    }
                }) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinBoardDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddSharedItemDialog) {
        val board = uiState.activeSharedBoard
        var title by remember(editingSharedItem?.id, draftSharedPhotoTitle) {
            mutableStateOf(editingSharedItem?.title ?: draftSharedPhotoTitle)
        }
        var qty by remember(editingSharedItem?.id) { mutableStateOf(editingSharedItem?.quantity ?: "") }
        var category by remember(editingSharedItem?.id, board?.shareCode) {
            mutableStateOf(editingSharedItem?.category ?: board?.categories?.firstOrNull() ?: "Supplies")
        }
        var notes by remember(editingSharedItem?.id) { mutableStateOf(editingSharedItem?.notes ?: "") }
        val draftBitmap = remember(draftSharedPhotoBase64) {
            ImageUtils.decodeBitmapFromBase64(draftSharedPhotoBase64)
        }
        AlertDialog(
            onDismissRequest = { showAddSharedItemDialog = false },
            title = {
                Text(
                    when {
                        editingSharedItem != null -> "Edit Shared Item"
                        draftBitmap != null -> "Add Photo Note to Board"
                        else -> "Add to Shared Board"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draftBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = draftBitmap.asImageBitmap(),
                                contentDescription = "Attached shared checklist photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editorForShared = true
                                    editorDirectSaveShared = null
                                    editorTargetBitmap = draftBitmap
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Crop / Edit")
                            }
                            TextButton(onClick = { draftSharedPhotoBase64 = "" }) {
                                Text("Remove")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                pickForSharedBoard = true
                                showSmartPhotoSourceDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Photo(s)")
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (draftBitmap != null) "Photo Title (optional)" else "Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val sharedCatChoices = ((board?.categories ?: emptyList()) + uiState.activeSharedItems.map { it.category })
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                    if (sharedCatChoices.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sharedCatChoices.forEach { catOption ->
                                FilterChip(
                                    selected = category.equals(catOption, ignoreCase = true),
                                    onClick = { category = catOption },
                                    label = { Text(catOption) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val effectiveTitle = title.ifBlank { if (draftBitmap != null) "Photo Note" else "" }
                    if (effectiveTitle.isNotBlank()) {
                        viewModel.saveSharedItem(
                            id = editingSharedItem?.id ?: "",
                            title = effectiveTitle,
                            quantity = qty,
                            category = category,
                            notes = notes,
                            imageBase64 = draftSharedPhotoBase64
                        )
                        draftSharedPhotoBase64 = ""
                        editingSharedItem = null
                        showAddSharedItemDialog = false
                    }
                }) { Text(if (editingSharedItem != null) "Save" else "Add Item") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSharedItemDialog = false }) { Text("Cancel") }
            }
        )
    }

    previewPersonalPhotoItem?.let { item ->
        val latest = uiState.checklistItems.firstOrNull { it.id == item.id } ?: item
        val bmp = remember(latest.imageBase64) { ImageUtils.decodeBitmapFromBase64(latest.imageBase64) }
        if (bmp != null) {
            ChecklistPhotoNotePreviewDialog(
                title = latest.title,
                quantity = latest.quantity,
                notes = latest.notes,
                isSelected = latest.isChecked,
                bitmap = bmp,
                onToggleSelect = { viewModel.toggleChecklistItem(latest) },
                onOpenEditor = {
                    editorForShared = false
                    editorDirectSavePersonal = latest
                    editorTargetBitmap = bmp
                    previewPersonalPhotoItem = null
                },
                onDismiss = { previewPersonalPhotoItem = null }
            )
        } else {
            previewPersonalPhotoItem = null
        }
    }

    previewSharedPhotoItem?.let { item ->
        val latest = uiState.activeSharedItems.firstOrNull { it.id == item.id } ?: item
        val bmp = remember(latest.imageBase64) { ImageUtils.decodeBitmapFromBase64(latest.imageBase64) }
        if (bmp != null) {
            ChecklistPhotoNotePreviewDialog(
                title = latest.title,
                quantity = latest.quantity,
                notes = latest.notes,
                isSelected = latest.isChecked,
                bitmap = bmp,
                onToggleSelect = { viewModel.toggleSharedItem(latest) },
                onOpenEditor = {
                    editorForShared = true
                    editorDirectSaveShared = latest
                    editorTargetBitmap = bmp
                    previewSharedPhotoItem = null
                },
                onDismiss = { previewSharedPhotoItem = null }
            )
        } else {
            previewSharedPhotoItem = null
        }
    }

    editorTargetBitmap?.let { bmp ->
        ImageEditorDialog(
            sourceBitmap = bmp,
            onDismiss = {
                editorTargetBitmap = null
                editorDirectSavePersonal = null
                editorDirectSaveShared = null
            },
            onApply = { edited ->
                val encoded = ImageUtils.encodeToBase64(edited, maxWidth = 720, quality = 74)
                val directPersonal = editorDirectSavePersonal
                val directShared = editorDirectSaveShared
                if (directPersonal != null) {
                    viewModel.saveChecklistItem(
                        id = directPersonal.id,
                        title = directPersonal.title,
                        quantity = directPersonal.quantity,
                        category = directPersonal.category,
                        notes = directPersonal.notes,
                        imageBase64 = encoded
                    )
                } else if (directShared != null) {
                    viewModel.saveSharedItem(
                        id = directShared.id,
                        title = directShared.title,
                        quantity = directShared.quantity,
                        category = directShared.category,
                        notes = directShared.notes,
                        imageBase64 = encoded
                    )
                } else if (editorForShared) {
                    draftSharedPhotoBase64 = encoded
                } else {
                    draftPersonalPhotoBase64 = encoded
                }
                editorTargetBitmap = null
                editorDirectSavePersonal = null
                editorDirectSaveShared = null
            }
        )
    }

    val currentBoard = uiState.activeSharedBoard
    if (showShareDialog && currentBoard != null) {
        ShareLinkReadyDialog(
            board = currentBoard,
            items = uiState.activeSharedItems,
            onTogglePublicEditable = { enabled ->
                viewModel.toggleSharedBoardPublicEditable(currentBoard.shareCode, enabled)
            },
            onSetAsPublicDefault = {
                viewModel.setBoardAsPublicDefault(currentBoard)
            },
            onDismiss = { showShareDialog = false }
        )
    }

    if (draftMultiPersonalPhotos.isNotEmpty()) {
        MultiPhotoUploadDialog(
            title = "Add Photo Notes",
            photos = draftMultiPersonalPhotos,
            availableCategories = sortedCategories,
            initialCategory = preselectedCategory.ifBlank { sortedCategories.firstOrNull() ?: "General Stock" },
            showQuantityField = true,
            onAddMoreClick = {
                pickForSharedBoard = false
                showSmartPhotoSourceDialog = true
            },
            onRemovePhoto = { id ->
                draftMultiPersonalPhotos = draftMultiPersonalPhotos.filterNot { it.id == id }
            },
            onSaveAll = { baseTitle, qty, cat, notes ->
                viewModel.saveMultipleChecklistPhotos(
                    photosBase64 = draftMultiPersonalPhotos.map { it.base64 },
                    baseTitle = baseTitle,
                    quantity = qty,
                    category = cat,
                    notes = notes,
                    photoTitles = draftMultiPersonalPhotos.map { it.displayName }
                )
                draftMultiPersonalPhotos = emptyList()
            },
            onDismiss = { draftMultiPersonalPhotos = emptyList() }
        )
    }

    if (draftMultiSharedPhotos.isNotEmpty()) {
        val board = uiState.activeSharedBoard
        val sharedCats = ((board?.categories ?: emptyList()) + uiState.activeSharedItems.map { it.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        MultiPhotoUploadDialog(
            title = "Add Live Board Photos",
            photos = draftMultiSharedPhotos,
            availableCategories = sharedCats,
            initialCategory = sharedCats.firstOrNull() ?: "Supplies",
            showQuantityField = true,
            onAddMoreClick = {
                pickForSharedBoard = true
                showSmartPhotoSourceDialog = true
            },
            onRemovePhoto = { id ->
                draftMultiSharedPhotos = draftMultiSharedPhotos.filterNot { it.id == id }
            },
            onSaveAll = { baseTitle, qty, cat, notes ->
                viewModel.saveMultipleSharedPhotos(
                    photosBase64 = draftMultiSharedPhotos.map { it.base64 },
                    baseTitle = baseTitle,
                    quantity = qty,
                    category = cat,
                    notes = notes,
                    photoTitles = draftMultiSharedPhotos.map { it.displayName }
                )
                draftMultiSharedPhotos = emptyList()
            },
            onDismiss = { draftMultiSharedPhotos = emptyList() }
        )
    }

    if (showSmartPhotoSourceDialog) {
        SmartPhotoSourceDialog(
            title = if (pickForSharedBoard) "Add Shared Photo Note(s)" else "Add Photo Note(s)",
            savedAreaPhotos = uiState.photos,
            folderRefreshKey = albumRefreshKey,
            onOpenFileSearchPicker = {
                showSmartPhotoSourceDialog = false
                checklistFileSearchPicker.launch(arrayOf("image/*"))
            },
            onOpenGalleryPicker = {
                showSmartPhotoSourceDialog = false
                checklistPhotoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onLaunchLinkAlbumFolder = {
                linkAlbumFolderLauncher.launch(null)
            },
            onConfirmPickedPhotos = { picked ->
                showSmartPhotoSourceDialog = false
                handlePickedBrowserPhotos(picked)
            },
            onDismiss = { showSmartPhotoSourceDialog = false }
        )
    }
}
