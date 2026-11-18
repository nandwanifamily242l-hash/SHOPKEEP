package com.example.ui.checklist

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.ChecklistItem
import com.example.data.model.ChecklistSortOption
import com.example.data.model.SharedChecklistItem
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
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = My Stock, 1 = Shared Live
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddPersonalItemDialog by remember { mutableStateOf(false) }
    var editingPersonalItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var preselectedCategory by remember { mutableStateOf("General Stock") }

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

    val activePersonalItems = remember(uiState.checklistItems, searchQuery) {
        uiState.checklistItems
            .filter { !it.isDeleted }
            .filter {
                searchQuery.isBlank() ||
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedSubTab == 0) {
                        editingPersonalItem = null
                        preselectedCategory = sortedCategories.firstOrNull() ?: "General Stock"
                        showAddPersonalItemDialog = true
                    } else if (uiState.activeSharedBoard != null) {
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
                                        text = "No items yet. Tap + to add stock to $category.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else {
                                    Spacer(Modifier.height(6.dp))
                                    catItems.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingPersonalItem = item
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
                                            text = "Code: ${activeBoard.shareCode} • ${activeBoard.memberIds.size} member(s)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = { showShareDialog = true },
                                        modifier = Modifier.testTag("open_share_link_dialog_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share URL")
                                    }
                                }
                            }
                        }

                        val sharedCategories = remember(activeBoard.categories, uiState.activeSharedItems) {
                            (activeBoard.categories + uiState.activeSharedItems.map { it.category })
                                .distinct()
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
                                        itemsInCat.forEach { item ->
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
        var title by remember { mutableStateOf(editingPersonalItem?.title ?: "") }
        var qty by remember { mutableStateOf(editingPersonalItem?.quantity ?: "") }
        var category by remember { mutableStateOf(preselectedCategory) }
        var notes by remember { mutableStateOf(editingPersonalItem?.notes ?: "") }
        AlertDialog(
            onDismissRequest = { showAddPersonalItemDialog = false },
            title = { Text(if (editingPersonalItem == null) "Add Stock Item" else "Edit Stock Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Item Name") },
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
                        if (title.isNotBlank()) {
                            viewModel.saveChecklistItem(
                                id = editingPersonalItem?.id ?: "",
                                title = title,
                                quantity = qty,
                                category = category,
                                notes = notes
                            )
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
        var title by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(board?.categories?.firstOrNull() ?: "Supplies") }
        AlertDialog(
            onDismissRequest = { showAddSharedItemDialog = false },
            title = { Text("Add to Shared Board") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Item Name") },
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        viewModel.saveSharedItem(title = title, quantity = qty, category = category)
                        showAddSharedItemDialog = false
                    }
                }) { Text("Add Item") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSharedItemDialog = false }) { Text("Cancel") }
            }
        )
    }

    val currentBoard = uiState.activeSharedBoard
    if (showShareDialog && currentBoard != null) {
        ShareLinkReadyDialog(
            board = currentBoard,
            items = uiState.activeSharedItems,
            onDismiss = { showShareDialog = false }
        )
    }
}
