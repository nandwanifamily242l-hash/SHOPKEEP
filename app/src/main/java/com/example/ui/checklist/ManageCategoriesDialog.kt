package com.example.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class EditableCategoryEntry(
    val name: String,
    val itemCount: Int
)

@Composable
fun ManageCategoriesDialog(
    title: String,
    categories: List<EditableCategoryEntry>,
    fallbackMoveTarget: String,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (oldName: String, newName: String) -> Unit,
    onDeleteCategory: (name: String, moveItemsTo: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<EditableCategoryEntry?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New category name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("manage_new_category_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = newCategoryName.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddCategory(trimmed)
                                newCategoryName = ""
                            }
                        },
                        modifier = Modifier.testTag("manage_add_category_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (categories.isEmpty()) {
                    Text(
                        text = "No categories yet. Add one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (entry.itemCount == 0) {
                                                "Empty"
                                            } else {
                                                "${entry.itemCount} item(s)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { renameTarget = entry.name },
                                        modifier = Modifier.testTag("manage_rename_category_${entry.name}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "Rename ${entry.name}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (entry.itemCount == 0) {
                                                onDeleteCategory(entry.name, null)
                                            } else {
                                                deleteTarget = entry
                                            }
                                        },
                                        modifier = Modifier.testTag("manage_delete_category_${entry.name}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete ${entry.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("manage_categories_done_button")
            ) {
                Text("Done")
            }
        }
    )

    renameTarget?.let { oldName ->
        var updatedName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Category") },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manage_rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = updatedName.trim()
                        if (trimmed.isNotEmpty() && trimmed != oldName) {
                            onRenameCategory(oldName, trimmed)
                        }
                        renameTarget = null
                    },
                    modifier = Modifier.testTag("manage_confirm_rename_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        val otherCategories = categories
            .map { it.name }
            .filterNot { it.equals(target.name, ignoreCase = true) }
        val moveTarget = otherCategories.firstOrNull() ?: fallbackMoveTarget
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove \"${target.name}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This category currently has ${target.itemCount} item(s). Choose whether to move them to \"$moveTarget\" or remove them as well.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            onDeleteCategory(target.name, moveTarget)
                            deleteTarget = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("move_items_and_delete_category_button")
                    ) {
                        Text("Move items to \"$moveTarget\" & Remove")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(target.name, null)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_category_with_items_button")
                ) { Text("Delete Category & Items") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
