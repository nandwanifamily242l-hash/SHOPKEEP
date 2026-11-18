package com.example.ui.photos

import android.graphics.Bitmap
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AreaPhoto
import com.example.ui.ai.ShopAiAssistantDialog
import com.example.ui.components.ImageEditorDialog
import com.example.ui.components.ImageUtils
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel

@Composable
fun PhotosScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var pendingEditorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editingExistingPhoto by remember { mutableStateOf<AreaPhoto?>(null) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var draftBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aiPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renamingPhotoCategory by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtils.decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                editingExistingPhoto = null
                pendingEditorBitmap = bmp
            }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            if (selectedCategoryFilter != "All") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCategoryFilter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { renamingPhotoCategory = selectedCategoryFilter },
                        modifier = Modifier.testTag("rename_photo_category_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rename Category")
                    }
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
                            text = "Tap the camera button to pick a shelf, display, or invoice photo — crop, rotate, adjust, mark up, and scan with AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredPhotos, key = { it.id }) { photo ->
                        val decodedBitmap = remember(photo.imageBase64) {
                            ImageUtils.decodeBitmapFromBase64(photo.imageBase64)
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (decodedBitmap != null) {
                                    Image(
                                        bitmap = decodedBitmap.asImageBitmap(),
                                        contentDescription = photo.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                editingExistingPhoto = photo
                                                pendingEditorBitmap = decodedBitmap
                                            }
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(photo.title, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = photo.category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (photo.notes.isNotBlank()) {
                                            Text(
                                                text = photo.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row {
                                        if (decodedBitmap != null) {
                                            IconButton(
                                                onClick = {
                                                    editingExistingPhoto = photo
                                                    pendingEditorBitmap = decodedBitmap
                                                },
                                                modifier = Modifier.testTag("edit_photo_${photo.id}")
                                            ) {
                                                Icon(Icons.Rounded.CropRotate, contentDescription = "Crop and edit photo")
                                            }
                                            IconButton(
                                                onClick = { aiPhotoBitmap = decodedBitmap },
                                                modifier = Modifier.testTag("ai_scan_photo_${photo.id}")
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Scan photo with AI")
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deletePhoto(photo.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete photo")
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
        var title by remember { mutableStateOf(existing?.title ?: "") }
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
}
