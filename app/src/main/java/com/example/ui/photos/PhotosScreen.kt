package com.example.ui.photos

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AreaPhoto
import com.example.ui.ai.ShopAiAssistantDialog
import com.example.ui.checklist.DraftMultiPhoto
import com.example.ui.checklist.MultiPhotoUploadDialog
import com.example.ui.checklist.SmartPhotoSourceDialog
import com.example.ui.components.DeviceAlbumBrowser
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.ImageUtils
import com.example.ui.components.PickerPhotoItem
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun PhotosScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var pendingEditorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingSinglePhotoTitle by remember { mutableStateOf("") }
    var editingExistingPhoto by remember { mutableStateOf<AreaPhoto?>(null) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var draftBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var draftMultiPhotos by remember { mutableStateOf<List<DraftMultiPhoto>>(emptyList()) }
    var isPreparingPhotos by remember { mutableStateOf(false) }
    var showSmartPhotoSourceDialog by remember { mutableStateOf(false) }
    var albumRefreshKey by remember { mutableIntStateOf(0) }
    var aiPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renamingPhotoCategory by remember { mutableStateOf<String?>(null) }

    fun handleAreaPhotoUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1 && draftMultiPhotos.isEmpty()) {
            val uri = uris.first()
            val bmp = ImageUtils.decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                editingExistingPhoto = null
                pendingSinglePhotoTitle = DeviceAlbumBrowser.queryDisplayName(context, uri)
                pendingEditorBitmap = bmp
            }
        } else {
            scope.launch {
                isPreparingPhotos = true
                val decoded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        val bmp = ImageUtils.decodeBitmapFromUri(context, uri, maxDimension = 1024)
                        bmp?.let {
                            val b64 = ImageUtils.encodeToBase64(it, maxWidth = 840, quality = 76)
                            val cachedUri = ImageUtils.saveBitmapToCacheFile(context, it, "shopkeep_multi").toString()
                            val name = DeviceAlbumBrowser.queryDisplayName(context, uri)
                            if (b64.isNotBlank()) {
                                DraftMultiPhoto(
                                    id = UUID.randomUUID().toString(),
                                    base64 = b64,
                                    cacheUri = cachedUri,
                                    displayName = name
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
                isPreparingPhotos = false
                if (decoded.isNotEmpty()) {
                    draftMultiPhotos = draftMultiPhotos + decoded
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> handleAreaPhotoUris(uris) }

    val fileSearchPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> handleAreaPhotoUris(uris) }

    val linkAlbumFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            DeviceAlbumBrowser.savePersistedTreeUri(context, treeUri)
            albumRefreshKey += 1
        }
    }

    val availableCategories = remember(uiState.photos) {
        (viewModel.defaultPhotoCategories + uiState.photos.map { it.category })
            .distinct()
            .sortedBy { it.lowercase() }
    }

    val filteredPhotos = remember(uiState.photos, selectedCategoryFilter) {
        if (selectedCategoryFilter == "All") {
            uiState.photos
        } else {
            uiState.photos.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSmartPhotoSourceDialog = true },
                modifier = Modifier.testTag("add_shop_photo_fab")
            ) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = "Pick and edit shop photo")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isPreparingPhotos) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == "All",
                    onClick = { selectedCategoryFilter = "All" },
                    label = { Text("All (${uiState.photos.size})") }
                )
                availableCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategoryFilter == "All") "All Photo Categories" else selectedCategoryFilter,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        renamingPhotoCategory = if (selectedCategoryFilter == "All") {
                            availableCategories.firstOrNull()
                        } else {
                            selectedCategoryFilter
                        }
                    },
                    modifier = Modifier.testTag("rename_photo_category_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit Category")
                }
            }

            if (filteredPhotos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("No shop photos in this category", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Tap the camera button to pick one or multiple shelf, display, or invoice photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 158.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().testTag("photos_grid")
                ) {
                    items(filteredPhotos, key = { it.id }) { photo ->
                        val decodedBitmap = remember(photo.imageBase64) {
                            ImageUtils.decodeBitmapFromBase64(photo.imageBase64)
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                if (decodedBitmap != null) {
                                    Image(
                                        bitmap = decodedBitmap.asImageBitmap(),
                                        contentDescription = photo.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(132.dp)
                                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                            .clickable {
                                                editingExistingPhoto = photo
                                                pendingEditorBitmap = decodedBitmap
                                            }
                                    )
                                }
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = photo.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = photo.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (photo.notes.isNotBlank()) {
                                        Text(
                                            text = photo.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (decodedBitmap != null) {
                                            IconButton(
                                                onClick = {
                                                    editingExistingPhoto = photo
                                                    pendingEditorBitmap = decodedBitmap
                                                },
                                                modifier = Modifier.size(34.dp).testTag("edit_photo_${photo.id}")
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CropRotate,
                                                    contentDescription = "Crop and edit photo",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { aiPhotoBitmap = decodedBitmap },
                                                modifier = Modifier.size(34.dp).testTag("ai_scan_photo_${photo.id}")
                                            ) {
                                                Icon(
                                                    Icons.Rounded.AutoAwesome,
                                                    contentDescription = "Scan photo with AI",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.deletePhoto(photo.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete photo",
                                                modifier = Modifier.size(18.dp)
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

    pendingEditorBitmap?.let { bmp ->
        ImageEditorDialog(
            sourceBitmap = bmp,
            onDismiss = { pendingEditorBitmap = null },
            onApply = { edited ->
                pendingEditorBitmap = null
                draftBitmap = edited
                showMetadataDialog = true
            }
        )
    }

    if (showMetadataDialog && draftBitmap != null) {
        val existing = editingExistingPhoto
        var title by remember(existing?.id, pendingSinglePhotoTitle) {
            mutableStateOf(existing?.title ?: pendingSinglePhotoTitle)
        }
        var category by remember {
            mutableStateOf(
                existing?.category
                    ?: if (selectedCategoryFilter != "All") selectedCategoryFilter else availableCategories.first()
            )
        }
        var notes by remember { mutableStateOf(existing?.notes ?: "") }
        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            title = { Text(if (existing == null) "Save Edited Photo" else "Update Shop Photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Photo Title (e.g. Beverage Shelf)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("photo_title_input")
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
                        label = { Text("Notes / Invoice Total") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            pendingEditorBitmap = draftBitmap
                            showMetadataDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CropRotate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Re-open Photo Editor")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalBmp = draftBitmap ?: return@Button
                        val base64 = ImageUtils.encodeToBase64(finalBmp)
                        val cachedUri = ImageUtils.saveBitmapToCacheFile(context, finalBmp).toString()
                        viewModel.savePhoto(
                            id = existing?.id ?: "",
                            title = title.ifBlank { "Shop Photo" },
                            category = category.ifBlank { "Display Shelves" },
                            notes = notes,
                            imageUri = cachedUri,
                            imageBase64 = base64
                        )
                        draftBitmap = null
                        editingExistingPhoto = null
                        showMetadataDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_photo_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showMetadataDialog = false }) { Text("Cancel") }
            }
        )
    }

    renamingPhotoCategory?.let { oldCat ->
        var newCat by remember(oldCat) { mutableStateOf(oldCat) }
        AlertDialog(
            onDismissRequest = { renamingPhotoCategory = null },
            title = { Text("Rename Photo Category") },
            text = {
                OutlinedTextField(
                    value = newCat,
                    onValueChange = { newCat = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCat.isNotBlank()) {
                        viewModel.renamePhotoCategory(oldCat, newCat)
                        if (selectedCategoryFilter == oldCat) {
                            selectedCategoryFilter = newCat.trim()
                        }
                        renamingPhotoCategory = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPhotoCategory = null }) { Text("Cancel") }
            }
        )
    }

    aiPhotoBitmap?.let { bmp ->
        ShopAiAssistantDialog(
            contextSummary = "Analyzing shop photo log",
            bitmapToAnalyze = bmp,
            onDismiss = { aiPhotoBitmap = null },
            onAddChecklistSuggestions = { items ->
                items.forEach { line ->
                    viewModel.saveChecklistItem(title = line, quantity = "", category = "General Stock")
                }
            }
        )
    }

    if (draftMultiPhotos.isNotEmpty()) {
        MultiPhotoUploadDialog(
            title = "Add Shop Photos",
            photos = draftMultiPhotos,
            availableCategories = availableCategories,
            initialCategory = if (selectedCategoryFilter != "All") {
                selectedCategoryFilter
            } else {
                availableCategories.firstOrNull() ?: "Display Shelves"
            },
            showQuantityField = false,
            onAddMoreClick = { showSmartPhotoSourceDialog = true },
            onRemovePhoto = { id ->
                draftMultiPhotos = draftMultiPhotos.filterNot { it.id == id }
            },
            onSaveAll = { baseTitle, _, cat, notes ->
                viewModel.saveMultipleAreaPhotos(
                    encodedPhotos = draftMultiPhotos.map { it.cacheUri to it.base64 },
                    baseTitle = baseTitle,
                    category = cat,
                    notes = notes,
                    photoTitles = draftMultiPhotos.map { it.displayName }
                )
                draftMultiPhotos = emptyList()
            },
            onDismiss = { draftMultiPhotos = emptyList() }
        )
    }

    if (showSmartPhotoSourceDialog) {
        SmartPhotoSourceDialog(
            title = "Select Shop Photo(s)",
            savedAreaPhotos = uiState.photos,
            folderRefreshKey = albumRefreshKey,
            onOpenFileSearchPicker = {
                showSmartPhotoSourceDialog = false
                fileSearchPicker.launch(arrayOf("image/*"))
            },
            onOpenGalleryPicker = {
                showSmartPhotoSourceDialog = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onLaunchLinkAlbumFolder = {
                linkAlbumFolderLauncher.launch(null)
            },
            onConfirmPickedPhotos = { picked ->
                showSmartPhotoSourceDialog = false
                val uriList = picked.mapNotNull { it.uri }
                if (uriList.size == picked.size) {
                    handleAreaPhotoUris(uriList)
                } else {
                    scope.launch {
                        isPreparingPhotos = true
                        val converted = withContext(Dispatchers.IO) {
                            picked.mapNotNull { item ->
                                val bmp = when {
                                    item.uri != null -> ImageUtils.decodeBitmapFromUri(context, item.uri, maxDimension = 1024)
                                    item.base64.isNotBlank() -> ImageUtils.decodeBitmapFromBase64(item.base64)
                                    else -> null
                                }
                                bmp?.let {
                                    val b64 = item.base64.ifBlank { ImageUtils.encodeToBase64(it, maxWidth = 840, quality = 76) }
                                    val cachedUri = ImageUtils.saveBitmapToCacheFile(context, it, "shopkeep_multi").toString()
                                    DraftMultiPhoto(
                                        id = UUID.randomUUID().toString(),
                                        base64 = b64,
                                        cacheUri = cachedUri,
                                        displayName = item.displayName
                                    )
                                }
                            }
                        }
                        isPreparingPhotos = false
                        if (converted.size == 1 && draftMultiPhotos.isEmpty()) {
                            val bmp = ImageUtils.decodeBitmapFromBase64(converted.first().base64)
                            if (bmp != null) {
                                editingExistingPhoto = null
                                pendingSinglePhotoTitle = converted.first().displayName
                                pendingEditorBitmap = bmp
                            }
                        } else if (converted.isNotEmpty()) {
                            draftMultiPhotos = draftMultiPhotos + converted
                        }
                    }
                }
            },
            onDismiss = { showSmartPhotoSourceDialog = false }
        )
    }
}
