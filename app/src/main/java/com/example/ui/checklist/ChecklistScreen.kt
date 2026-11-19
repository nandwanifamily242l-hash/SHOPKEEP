package com.example.ui.checklist

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.ImageUtils
import com.example.ui.components.ShareLinkReadyDialog
import com.example.ui.components.ShareUtils
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel

@Composable
fun ChecklistScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = My Stock, 1 = Shared Live
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddPersonalItemDialog by remember { mutableStateOf(false) }
    var editingPersonalItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var preselectedCategory by remember { mutableStateOf("General Stock") }

    var draftPersonalPhotoBase64 by remember { mutableStateOf("") }
    var draftSharedPhotoBase64 by remember { mutableStateOf("") }
    var editorTargetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editorForShared by remember { mutableStateOf(false) }
    var editorDirectSavePersonal by remember { mutableStateOf<ChecklistItem?>(null) }
    var editorDirectSaveShared by remember { mutableStateOf<SharedChecklistItem?>(null) }

    var previewPersonalPhotoItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var previewSharedPhotoItem by remember { mutableStateOf<SharedChecklistItem?>(null) }
    var editingSharedItem by remember { mutableStateOf<SharedChecklistItem?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var renamingCategoryTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // name to isShared

    var showCreateBoardDialog by remember { mutableStateOf(false) }
    var showJoinBoardDialog by remember { mutableStateOf(false) }
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
    val checklistPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.decodeBitmapFromUri(context, uri, maxDimension = 1024)
            if (bmp != null) {
                val encoded = ImageUtils.encodeToBase64(bmp, maxWidth = 720, quality = 74)
                if (pickForSharedBoard) {
                    draftSharedPhotoBase64 = encoded
                    if (!showAddSharedItemDialog && uiState.activeSharedBoard != null) {
                        editingSharedItem = null
                        showAddSharedItemDialog = true
                    }
                } else {
                    draftPersonalPhotoBase64 = encoded
                    if (!showAddPersonalItemDialog) {
                        editingPersonalItem = null
                        preselectedCategory = sortedCategories.firstOrNull() ?: "General Stock"
                        showAddPersonalItemDialog = true
                    }
                }
            } else {
                Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
            }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedSubTab == 0) {
                        editingPersonalItem = null
                        draftPersonalPhotoBase64 = ""
                        preselectedCategory = sortedCategories.firstOrNull() ?: "General Stock"
                        showAddPersonalItemDialog = true
                    } else if (uiState.activeSharedBoard != null) {
                        editingSharedItem = null
                        draftSharedPhotoBase64 = ""
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
                    text = { Text("My Stock Lists") }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Shared Live Boards") }
                )
            }

            if (selectedSubTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search items or categories") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("checklist_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("checklist_sort_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sort: ${uiState.checklistSortOption.label}")
                            }
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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showAddCategoryDialog = true },
                                modifier = Modifier.testTag("add_category_button")
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("+ Category")
                            }
                            IconButton(
                                onClick = {
                                    val grouped = sortedCategories.map { cat ->
                                        cat to activePersonalItems
                                            .filter { it.category.equals(cat, ignoreCase = true) }
                                            .map { Triple(it.title, it.quantity, it.isChecked) }
                                    }.filter { it.second.isNotEmpty() }
                                    ShareUtils.exportChecklistHtmlFile(
                                        context = context,
                                        title = "ShopKeep Restock Checklist",
                                        items = grouped
                                    )
                                },
                                modifier = Modifier.testTag("export_html_checklist_button")
                            ) {
                                Icon(Icons.Rounded.Html, contentDescription = "Export interactive HTML checklist")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pickForSharedBoard = false
                                checklistPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("add_photo_note_button")
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+ Photo Note")
                        }
                        Button(
                            onClick = {
                                viewModel.publishPersonalListToPublicBoard {
                                    selectedSubTab = 1
                                    showShareDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("publish_public_link_button")
                        ) {
                            Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Public Link")
                        }
                    }

                    if (selectedPersonalPhotoCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$selectedPersonalPhotoCount photo note(s) selected",
                                    style = MaterialTheme.typography.labelLarge,
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedCategories, key = { it }) { category ->
                        val catItems = activePersonalItems.filter {
                            it.category.equals(category, ignoreCase = true)
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                    val photoItems = catItems.filter { it.imageBase64.isNotBlank() }
                                    val textItems = catItems.filter { it.imageBase64.isBlank() }

                                    if (photoItems.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            photoItems.chunked(2).forEach { pair ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    pair.forEach { photoItem ->
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
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    if (pair.size == 1) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (textItems.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        textItems.forEach { item ->
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
                                }
                            }
                        }
                    }
                }
            } else {
                // SHARED REAL-TIME BOARDS TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCreateBoardDialog = true },
                            modifier = Modifier.weight(1f).testTag("create_shared_board_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Live Board")
                        }
                        OutlinedButton(
                            onClick = { showJoinBoardDialog = true },
                            modifier = Modifier.weight(1f).testTag("join_shared_board_button")
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Join by Code")
                        }
                    }

                    if (uiState.sharedChecklists.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.sharedChecklists.forEach { board ->
                                FilterChip(
                                    selected = uiState.activeSharedCode == board.shareCode,
                                    onClick = { viewModel.selectSharedChecklist(board.shareCode) },
                                    label = { Text("${board.title} (${board.shareCode})") }
                                )
                            }
                        }
                    }

                    val activeBoard = uiState.activeSharedBoard
                    if (activeBoard == null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Real-Time Live Web Sharing", fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Create a live board to get a shareable Web URL. Staff or suppliers can open the URL in any browser to view & sync updates instantly without downloading an app.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(activeBoard.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (activeBoard.isPublicEditable) {
                                                "Public Link • View & Mark/Unmark only"
                                            } else {
                                                "Restricted • Strictly view-only in public"
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = { showShareDialog = true },
                                        modifier = Modifier.testTag("open_share_link_dialog_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Public Link")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        pickForSharedBoard = true
                                        checklistPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("add_shared_photo_note_button")
                                ) {
                                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("+ Add Photo Note to Live Board")
                                }
                            }
                        }

                        if (selectedSharedPhotoCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedSharedPhotoCount photo note(s) selected",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { viewModel.unselectAllSharedPhotos() }) {
                                        Text("Unselect All")
                                    }
                                }
                            }
                        }

                        val sharedCategories = remember(activeBoard.categories, uiState.activeSharedItems) {
                            (activeBoard.categories + uiState.activeSharedItems.map { it.category })
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .distinctBy { it.lowercase() }
                                .sortedBy { it.lowercase() }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

                                        val sharedPhotoItems = itemsInCat.filter { it.imageBase64.isNotBlank() }
                                        val sharedTextItems = itemsInCat.filter { it.imageBase64.isBlank() }

                                        if (sharedPhotoItems.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                sharedPhotoItems.chunked(2).forEach { pair ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        pair.forEach { photoItem ->
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
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        if (pair.size == 1) {
                                                            Spacer(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        sharedTextItems.forEach { item ->
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
        var title by remember(editingPersonalItem?.id) { mutableStateOf(editingPersonalItem?.title ?: "") }
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
                                checklistPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("attach_checklist_photo_button")
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Photo Note")
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

    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Checklist Category") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_category_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCatName.isNotBlank()) {
                        viewModel.addChecklistCategory(newCatName)
                        showAddCategoryDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") }
            }
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
        var title by remember(editingSharedItem?.id) { mutableStateOf(editingSharedItem?.title ?: "") }
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
                                checklistPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Photo Note")
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
}
