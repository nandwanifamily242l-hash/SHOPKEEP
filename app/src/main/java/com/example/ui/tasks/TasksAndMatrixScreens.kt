package com.example.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.HouseholdTask
import com.example.data.model.TaskPriority
import com.example.viewmodel.ShopKeepUiState
import com.example.viewmodel.TaskViewModel

@Composable
fun TasksScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    var filter by remember { mutableStateOf("Pending") } // Pending, Bills, Completed, Trash
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<HouseholdTask?>(null) }

    val filteredTasks = remember(uiState.tasks, filter) {
        when (filter) {
            "Pending" -> uiState.tasks.filter { !it.isDeleted && !it.isCompleted }
            "Bills" -> uiState.tasks.filter { !it.isDeleted && it.category.equals("Bill", ignoreCase = true) }
            "Completed" -> uiState.tasks.filter { !it.isDeleted && it.isCompleted }
            "Trash" -> uiState.tasks.filter { it.isDeleted }
            else -> uiState.tasks.filter { !it.isDeleted }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add shop task")
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
                listOf("Pending", "Bills", "Completed", "All", "Trash").forEach { tab ->
                    FilterChip(
                        selected = filter == tab,
                        onClick = { filter = tab },
                        label = { Text(tab) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!task.isDeleted) {
                                    editingTask = task
                                    showTaskDialog = true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!task.isDeleted) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { viewModel.toggleTaskCompletion(task) }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = "${task.category} • ${task.priorityEnum().quadrantTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (task.description.isNotBlank()) {
                                    Text(
                                        text = task.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (task.isDeleted) {
                                IconButton(onClick = { viewModel.restoreTask(task.id) }) {
                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Restore task")
                                }
                                IconButton(onClick = { viewModel.deleteTask(task.id, permanent = true) }) {
                                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete permanently")
                                }
                            } else {
                                IconButton(onClick = { viewModel.deleteTask(task.id, permanent = false) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Move to recycle bin")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        var title by remember { mutableStateOf(editingTask?.title ?: "") }
        var description by remember { mutableStateOf(editingTask?.description ?: "") }
        var category by remember { mutableStateOf(editingTask?.category ?: "Restock") }
        var priority by remember { mutableStateOf(editingTask?.priorityEnum() ?: TaskPriority.NOT_URGENT_IMPORTANT) }
        var reminderMinutes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text(if (editingTask == null) "New Shop Task" else "Edit Shop Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Details / Supplier / Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Restock", "Bill", "Maintenance", "Staff").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskPriority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p.quadrantTitle) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reminderMinutes,
                        onValueChange = { reminderMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Remind me in (minutes, optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val mins = reminderMinutes.toLongOrNull()
                            val reminderAt = mins?.let { System.currentTimeMillis() + it * 60_000L }
                            viewModel.saveTask(
                                id = editingTask?.id ?: "",
                                title = title,
                                description = description,
                                category = category,
                                areaId = editingTask?.areaId ?: "",
                                priority = priority,
                                dueDateMillis = editingTask?.dueDateMillis,
                                reminderTimeMillis = reminderAt ?: editingTask?.reminderTimeMillis
                            )
                            showTaskDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_task_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTaskDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EisenhowerMatrixScreen(
    uiState: ShopKeepUiState,
    viewModel: TaskViewModel
) {
    val activeTasks = remember(uiState.tasks) {
        uiState.tasks.filter { !it.isDeleted && !it.isCompleted }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Shop Priority Matrix",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Focus on urgent restocks, vendor bills, and high-impact store improvements first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(TaskPriority.entries, key = { it.name }) { quadrant ->
            val qTasks = activeTasks.filter { it.priorityEnum() == quadrant }
            val containerColor = when (quadrant) {
                TaskPriority.URGENT_IMPORTANT -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                TaskPriority.NOT_URGENT_IMPORTANT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                TaskPriority.URGENT_NOT_IMPORTANT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                TaskPriority.NOT_URGENT_NOT_IMPORTANT -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${quadrant.quadrantTitle} • ${quadrant.label} (${qTasks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (qTasks.isEmpty()) {
                        Text(
                            text = "No active tasks in this quadrant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        qTasks.forEach { task ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.isCompleted,
                                        onCheckedChange = { viewModel.toggleTaskCompletion(task) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(task.title, fontWeight = FontWeight.Medium)
                                        Text(task.category, style = MaterialTheme.typography.labelSmall)
                                    }
                                    val nextPriority = TaskPriority.entries[(quadrant.ordinal + 1) % TaskPriority.entries.size]
                                    TextButton(onClick = { viewModel.updateTaskPriority(task, nextPriority) }) {
                                        Text("Move")
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
