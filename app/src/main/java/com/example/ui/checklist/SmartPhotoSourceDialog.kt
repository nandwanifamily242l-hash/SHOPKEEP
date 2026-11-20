package com.example.ui.checklist

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AreaPhoto
import com.example.ui.components.DeviceAlbumBrowser
import com.example.ui.components.ImageUtils
import com.example.ui.components.PhotoPickerSortOrder
import com.example.ui.components.PickerPhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SmartPhotoSourceDialog(
    title: String = "Select Photo(s)",
    savedAreaPhotos: List<AreaPhoto>,
    folderRefreshKey: Int = 0,
    maxSelection: Int = 20,
    onOpenFileSearchPicker: () -> Unit,
    onOpenGalleryPicker: () -> Unit,
    onLaunchLinkAlbumFolder: () -> Unit,
    onConfirmPickedPhotos: (List<PickerPhotoItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var viewAlbumsTab by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(PhotoPickerSortOrder.NAME_ASC) }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var visibleLimit by remember { mutableIntStateOf(60) }

    var isScanningFolders by remember { mutableStateOf(false) }
    var devicePhotos by remember { mutableStateOf<List<PickerPhotoItem>>(emptyList()) }
    var linkedFolderCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(folderRefreshKey) {
        val treeUris = DeviceAlbumBrowser.getPersistedTreeUris(context)
        linkedFolderCount = treeUris.size
        if (treeUris.isNotEmpty()) {
            isScanningFolders = true
            val scanned = withContext(Dispatchers.IO) {
                DeviceAlbumBrowser.scanLinkedFolders(context, treeUris)
            }
            devicePhotos = scanned.photos
            isScanningFolders = false
        } else {
            devicePhotos = emptyList()
        }
    }

    val allPhotos = remember(devicePhotos, savedAreaPhotos) {
        val savedItems = savedAreaPhotos
            .filter { it.imageBase64.isNotBlank() }
            .map { area ->
                PickerPhotoItem(
                    id = "shop_${area.id}",
                    displayName = area.title.ifBlank { "Shop Photo" },
                    albumName = area.category.ifBlank { "Shop Photos" },
                    uri = null,
                    base64 = area.imageBase64,
                    lastModified = area.createdAt
                )
            }
        devicePhotos + savedItems
    }

    val sortedAlbums = remember(allPhotos, searchQuery) {
        DeviceAlbumBrowser.buildSortedAlbumSummaries(
            photos = allPhotos,
            searchQuery = if (viewAlbumsTab) searchQuery else ""
        )
    }

    val filteredSortedPhotos = remember(allPhotos, selectedAlbum, searchQuery, sortOrder) {
        DeviceAlbumBrowser.filterAndSortPickerPhotos(
            photos = allPhotos,
            selectedAlbum = selectedAlbum,
            searchQuery = searchQuery,
            sortOrder = sortOrder
        )
    }

    val photoLookup = remember(allPhotos) {
        allPhotos.associateBy { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = "Search by name, browse albums A–Z, or open Gallery",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Zero-Permission System Pickers + Link Device Album Folder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenFileSearchPicker,
                        modifier = Modifier.weight(1f).testTag("picker_search_files_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search Files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenGalleryPicker,
                        modifier = Modifier.weight(1f).testTag("picker_gallery_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedButton(
                    onClick = onLaunchLinkAlbumFolder,
                    modifier = Modifier.fillMaxWidth().testTag("picker_link_album_folder_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (linkedFolderCount == 0) {
                            "Link Device Album Folder (for A–Z Browse)"
                        } else {
                            "+ Add Another Device Album Folder ($linkedFolderCount linked)"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isScanningFolders) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        visibleLimit = 60
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    label = { Text("Search photo or album by name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("photo_picker_search_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !viewAlbumsTab,
                        onClick = { viewAlbumsTab = false },
                        label = { Text("Photos (${filteredSortedPhotos.size})") },
                        modifier = Modifier.testTag("picker_tab_photos")
                    )
                    FilterChip(
                        selected = viewAlbumsTab,
                        onClick = {
                            selectedAlbum = null
                            viewAlbumsTab = true
                        },
                        label = { Text("Albums A–Z (${sortedAlbums.size})") },
                        modifier = Modifier.testTag("picker_tab_albums")
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            sortOrder = when (sortOrder) {
                                PhotoPickerSortOrder.NAME_ASC -> PhotoPickerSortOrder.NAME_DESC
                                PhotoPickerSortOrder.NAME_DESC -> PhotoPickerSortOrder.NEWEST
                                PhotoPickerSortOrder.NEWEST -> PhotoPickerSortOrder.NAME_ASC
                            }
                        },
                        modifier = Modifier.testTag("picker_sort_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(sortOrder.label, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (selectedAlbum != null && !viewAlbumsTab) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Album: $selectedAlbum (${sortOrder.label})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedAlbum = null
                                    viewAlbumsTab = true
                                },
                                modifier = Modifier.testTag("clear_selected_album_button")
                            ) {
                                Text("All Albums")
                            }
                        }
                    }
                }

                if (viewAlbumsTab) {
                    if (sortedAlbums.isEmpty()) {
                        EmptyBrowserNotice(linkedFolderCount = linkedFolderCount)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sortedAlbums.forEach { album ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedAlbum = album.name
                                            viewAlbumsTab = false
                                            visibleLimit = 60
                                        }
                                        .testTag("album_row_${album.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${album.photoCount} photo(s) • Sorted A–Z",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "Open album ${album.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (filteredSortedPhotos.isEmpty()) {
                        EmptyBrowserNotice(linkedFolderCount = linkedFolderCount)
                    } else {
                        val visiblePhotos = filteredSortedPhotos.take(visibleLimit)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visiblePhotos.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { photo ->
                                        val isSelected = selectedIds.contains(photo.id)
                                        PickerPhotoGridCard(
                                            photo = photo,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedIds = if (isSelected) {
                                                    selectedIds - photo.id
                                                } else if (selectedIds.size < maxSelection) {
                                                    selectedIds + photo.id
                                                } else {
                                                    selectedIds
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            if (filteredSortedPhotos.size > visibleLimit) {
                                TextButton(
                                    onClick = { visibleLimit += 60 },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Show More (${filteredSortedPhotos.size - visibleLimit} remaining)")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val picked = selectedIds.mapNotNull { photoLookup[it] }
                    if (picked.isNotEmpty()) {
                        onConfirmPickedPhotos(picked)
                    }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("confirm_pick_selected_photos_button")
            ) {
                Text("Add Selected (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyBrowserNotice(linkedFolderCount: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (linkedFolderCount == 0) {
                    "Tip: Search files directly or link a device album folder"
                } else {
                    "No matching photos or albums found"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (linkedFolderCount == 0) {
                    "Tap 'Search Files' above to search any photo on your phone by filename right now, or tap 'Link Device Album Folder' (e.g., Pictures or DCIM) to browse all your albums sorted alphabetically A–Z."
                } else {
                    "Try a different name in the search bar or switch to Albums A–Z."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickerPhotoGridCard(
    photo: PickerPhotoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("picker_photo_card_${photo.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (photo.uri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photo.uri)
                            .size(320)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (photo.base64.isNotBlank()) {
                    val bmp: Bitmap? = remember(photo.base64) {
                        ImageUtils.decodeBitmapFromBase64(photo.base64)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.52f),
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = photo.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = photo.albumName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
