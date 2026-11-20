package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.UUID

enum class PhotoPickerSortOrder(val label: String) {
    NAME_ASC("A → Z"),
    NAME_DESC("Z → A"),
    NEWEST("Newest")
}

data class PickerPhotoItem(
    val id: String,
    val displayName: String,
    val albumName: String,
    val uri: Uri? = null,
    val base64: String = "",
    val lastModified: Long = 0L
)

data class PickerAlbumSummary(
    val name: String,
    val photoCount: Int
)

data class LinkedAlbumScanResult(
    val albums: List<PickerAlbumSummary>,
    val photos: List<PickerPhotoItem>
)

object DeviceAlbumBrowser {
    private const val PREFS_NAME = "shopkeep_album_prefs"
    private const val KEY_LINKED_TREE_URIS = "linked_tree_uris"

    fun getPersistedTreeUris(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedStrings = prefs.getStringSet(KEY_LINKED_TREE_URIS, emptySet()) ?: emptySet()
        val systemPersisted = try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
        } catch (_: Exception) {
            emptyList()
        }
        return (savedStrings + systemPersisted)
            .distinct()
            .mapNotNull { raw ->
                try {
                    Uri.parse(raw)
                } catch (_: Exception) {
                    null
                }
            }
    }

    fun savePersistedTreeUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some OEM document providers allow session-only grants
        } catch (_: Exception) {
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_LINKED_TREE_URIS, emptySet()) ?: emptySet()).toMutableSet()
        current.add(uri.toString())
        prefs.edit().putStringSet(KEY_LINKED_TREE_URIS, current).apply()
    }

    fun removePersistedTreeUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_LINKED_TREE_URIS, emptySet()) ?: emptySet()).toMutableSet()
        current.remove(uri.toString())
        prefs.edit().putStringSet(KEY_LINKED_TREE_URIS, current).apply()
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        val rawName = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""

        return cleanPhotoDisplayTitle(rawName)
    }

    fun cleanPhotoDisplayTitle(rawFileName: String): String {
        val trimmed = rawFileName.trim()
        if (trimmed.isBlank()) return ""
        val withoutExt = trimmed.replace(
            Regex("\\.(jpe?g|png|webp|heic|heif|bmp|gif)$", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val lower = withoutExt.lowercase()
        return if (lower == "image" || lower == "photo" || lower == "temp") "" else withoutExt
    }

    fun scanLinkedFolders(
        context: Context,
        treeUris: List<Uri>,
        maxDepth: Int = 3,
        maxFolders: Int = 80,
        maxPhotos: Int = 1200
    ): LinkedAlbumScanResult {
        if (treeUris.isEmpty()) return LinkedAlbumScanResult(emptyList(), emptyList())

        val collectedPhotos = mutableListOf<PickerPhotoItem>()
        val seenDocUris = HashSet<String>()
        var foldersVisited = 0

        for (treeUri in treeUris) {
            val rootDocId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                continue
            }
            val rootAlbumName = querySingleDocumentName(context, treeUri, rootDocId)
                ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Selected Album"

            val queue = ArrayDeque<Triple<String, String, Int>>()
            queue.add(Triple(rootDocId, rootAlbumName, 0))

            while (queue.isNotEmpty() && foldersVisited < maxFolders && collectedPhotos.size < maxPhotos) {
                val (folderDocId, albumLabel, depth) = queue.removeFirst()
                foldersVisited += 1

                val childrenUri = try {
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
                } catch (_: Exception) {
                    continue
                }

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )

                try {
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                        while (cursor.moveToNext() && collectedPhotos.size < maxPhotos) {
                            val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                            val childName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null) ?: continue
                            val mime = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null) ?: ""
                            val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (!childName.startsWith(".") && depth < maxDepth) {
                                    queue.add(Triple(childId, childName.trim(), depth + 1))
                                }
                            } else if (mime.startsWith("image/")) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                val uriKey = docUri.toString()
                                if (seenDocUris.add(uriKey)) {
                                    val cleanTitle = cleanPhotoDisplayTitle(childName).ifBlank { childName }
                                    collectedPhotos.add(
                                        PickerPhotoItem(
                                            id = "doc_${UUID.nameUUIDFromBytes(uriKey.toByteArray())}",
                                            displayName = cleanTitle,
                                            albumName = albumLabel.ifBlank { "Selected Album" },
                                            uri = docUri,
                                            lastModified = modified
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    // Folder permission was revoked by user
                } catch (_: Exception) {
                }
            }
        }

        val sortedPhotos = filterAndSortPickerPhotos(
            photos = collectedPhotos,
            selectedAlbum = null,
            searchQuery = "",
            sortOrder = PhotoPickerSortOrder.NAME_ASC
        )
        val sortedAlbums = buildSortedAlbumSummaries(sortedPhotos)
        return LinkedAlbumScanResult(albums = sortedAlbums, photos = sortedPhotos)
    }

    private fun querySingleDocumentName(context: Context, treeUri: Uri, docId: String): String? {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildSortedAlbumSummaries(
        photos: List<PickerPhotoItem>,
        searchQuery: String = ""
    ): List<PickerAlbumSummary> {
        val q = searchQuery.trim()
        return photos
            .groupBy { it.albumName.trim().ifBlank { "Other" } }
            .entries
            .map { (album, list) ->
                val canonical = list.first().albumName.trim().ifBlank { album }
                PickerAlbumSummary(name = canonical, photoCount = list.size)
            }
            .filter { summary ->
                q.isBlank() || summary.name.contains(q, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun filterAndSortPickerPhotos(
        photos: List<PickerPhotoItem>,
        selectedAlbum: String?,
        searchQuery: String,
        sortOrder: PhotoPickerSortOrder
    ): List<PickerPhotoItem> {
        val q = searchQuery.trim()
        val filtered = photos.filter { item ->
            val albumMatches = selectedAlbum.isNullOrBlank() ||
                item.albumName.equals(selectedAlbum.trim(), ignoreCase = true)
            val queryMatches = q.isBlank() ||
                item.displayName.contains(q, ignoreCase = true) ||
                item.albumName.contains(q, ignoreCase = true)
            albumMatches && queryMatches
        }

        return when (sortOrder) {
            PhotoPickerSortOrder.NAME_ASC -> filtered.sortedWith(
                compareBy<PickerPhotoItem> { it.displayName.lowercase() }
                    .thenBy { it.albumName.lowercase() }
            )
            PhotoPickerSortOrder.NAME_DESC -> filtered.sortedWith(
                compareByDescending<PickerPhotoItem> { it.displayName.lowercase() }
                    .thenBy { it.albumName.lowercase() }
            )
            PhotoPickerSortOrder.NEWEST -> filtered.sortedWith(
                compareByDescending<PickerPhotoItem> { it.lastModified }
                    .thenBy { it.displayName.lowercase() }
            )
        }
    }
}
