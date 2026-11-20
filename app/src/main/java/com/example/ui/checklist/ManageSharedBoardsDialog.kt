package com.example.ui.checklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SharedChecklist

@Composable
fun ManageSharedBoardsDialog(
    boards: List<SharedChecklist>,
    activeShareCode: String?,
    onSelectBoard: (String) -> Unit,
    onCreateNewBoard: () -> Unit,
    onMoveBoard: (shareCode: String, direction: Int) -> Unit,
    onRenameBoard: (shareCode: String, newTitle: String) -> Unit,
    onDeleteBoard: (shareCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<SharedChecklist?>(null) }
    var deleteTarget by remember { mutableStateOf<SharedChecklist?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manage Live Boards",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Arrange board order with the arrows, tap a board to open it, or rename/delete boards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onCreateNewBoard()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("manage_boards_create_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create New Board")
                }

                if (boards.isEmpty()) {
                    Text(
                        text = "No live boards yet. Create or join one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        boards.forEachIndexed { index, board ->
                            val isActive = activeShareCode.equals(board.shareCode, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = if (isActive) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectBoard(board.shareCode)
                                        onDismiss()
                                    }
                                    .testTag("manage_board_row_${board.shareCode}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${index + 1}. ${board.title.ifBlank { "Live Board" }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = if (isActive) {
                                                "${board.shareCode} • Active"
                                            } else {
                                                board.shareCode
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onMoveBoard(board.shareCode, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.testTag("move_board_up_${board.shareCode}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = "Move ${board.title} up"
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveBoard(board.shareCode, 1) },
                                        enabled = index < boards.lastIndex,
                                        modifier = Modifier.testTag("move_board_down_${board.shareCode}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            contentDescription = "Move ${board.title} down"
                                        )
                                    }
                                    IconButton(
                                        onClick = { renameTarget = board },
                                        modifier = Modifier.testTag("rename_board_${board.shareCode}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "Rename ${board.title}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteTarget = board },
                                        modifier = Modifier.testTag("delete_board_${board.shareCode}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete ${board.title}",
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
                modifier = Modifier.testTag("manage_boards_done_button")
            ) {
                Text("Done")
            }
        }
    )

    renameTarget?.let { target ->
        var updatedTitle by remember(target.shareCode) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Live Board") },
            text = {
                OutlinedTextField(
                    value = updatedTitle,
                    onValueChange = { updatedTitle = it },
                    label = { Text("Board Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_board_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = updatedTitle.trim()
                        if (trimmed.isNotEmpty() && trimmed != target.title) {
                            onRenameBoard(target.shareCode, trimmed)
                        }
                        renameTarget = null
                    },
                    modifier = Modifier.testTag("confirm_rename_board_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = {
                Text(
                    text = "This removes \"${target.title}\" (${target.shareCode}) and its shared items if you own this board.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBoard(target.shareCode)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_board_button")
                ) { Text("Delete Board") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
