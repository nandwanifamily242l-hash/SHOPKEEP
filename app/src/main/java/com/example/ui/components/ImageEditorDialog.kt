package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.PhotoFilter
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditorTab(val label: String) {
    CROP_ROTATE("Crop & Rotate"),
    ADJUST("Adjust"),
    FILTERS("Filters"),
    DRAW("Markup")
}

private enum class CropAspectPreset(val label: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    FREE("Full", 0f, 0f, 1f, 1f),
    SQUARE("1:1 Square", 0.1f, 0.1f, 0.9f, 0.9f),
    WIDE("4:3", 0.05f, 0.12f, 0.95f, 0.88f),
    TIGHT("Tight Center", 0.18f, 0.18f, 0.82f, 0.82f)
}

@Composable
fun ImageEditorDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    var spec by remember { mutableStateOf(PhotoEditSpec()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeStrokePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var selectedPenColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    val strokesList = remember { mutableStateListOf<NormalizedStroke>() }

    var previewBitmap by remember { mutableStateOf(sourceBitmap) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(spec) {
        previewBitmap = withContext(Dispatchers.Default) {
            ImageUtils.applyEdits(sourceBitmap, spec.copy(strokes = emptyList()))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E131F),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("editor_close_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close editor", tint = Color.White)
                    }
                    Text(
                        text = "Edit Shop Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                spec = PhotoEditSpec()
                                strokesList.clear()
                                activeStrokePoints = emptyList()
                            },
                            modifier = Modifier.testTag("editor_reset_button")
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset edits", tint = Color.White)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    val finalBitmap = withContext(Dispatchers.Default) {
                                        ImageUtils.applyEdits(
                                            sourceBitmap,
                                            spec.copy(strokes = strokesList.toList())
                                        )
                                    }
                                    isProcessing = false
                                    onApply(finalBitmap)
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("editor_save_button")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply")
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageAspect = previewBitmap.width.toFloat() / previewBitmap.height.coerceAtLeast(1).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF171E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentTab = EditorTab.entries[selectedTab]
                        val drawModifier = if (currentTab == EditorTab.DRAW) {
                            Modifier.pointerInput(selectedPenColor) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        activeStrokePoints = listOf(nx to ny)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val ny = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        activeStrokePoints = activeStrokePoints + (nx to ny)
                                    },
                                    onDragEnd = {
                                        if (activeStrokePoints.size >= 2) {
                                            strokesList.add(
                                                NormalizedStroke(
                                                    points = activeStrokePoints,
                                                    colorArgb = selectedPenColor.toArgb().toLong() and 0xFFFFFFFFL
                                                )
                                            )
                                        }
                                        activeStrokePoints = emptyList()
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(drawModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Photo preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val allStrokes = strokesList.toList() + if (activeStrokePoints.size >= 2) {
                                    listOf(
                                        NormalizedStroke(
                                            points = activeStrokePoints,
                                            colorArgb = selectedPenColor.toArgb().toLong() and 0xFFFFFFFFL
                                        )
                                    )
                                } else {
                                    emptyList()
                                }
                                allStrokes.forEach { stroke ->
                                    if (stroke.points.size >= 2) {
                                        val path = Path()
                                        path.moveTo(
                                            stroke.points.first().first * size.width,
                                            stroke.points.first().second * size.height
                                        )
                                        stroke.points.drop(1).forEach { pt ->
                                            path.lineTo(pt.first * size.width, pt.second * size.height)
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(stroke.colorArgb.toInt()),
                                            style = Stroke(width = 6f)
                                        )
                                    }
                                }

                                if (currentTab == EditorTab.CROP_ROTATE && (spec.cropLeft > 0f || spec.cropTop > 0f || spec.cropRight < 1f || spec.cropBottom < 1f)) {
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.85f),
                                        topLeft = Offset(0f, 0f),
                                        size = Size(size.width, size.height),
                                        style = Stroke(width = 3f)
                                    )
                                }
                            }
                        }
                        val _unused = imageAspect
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF171E2E),
                    contentColor = Color.White
                ) {
                    EditorTab.entries.forEachIndexed { idx, tab ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(tab.label, maxLines = 1) },
                            icon = {
                                val icon = when (tab) {
                                    EditorTab.CROP_ROTATE -> Icons.Rounded.Crop
                                    EditorTab.ADJUST -> Icons.Rounded.Tune
                                    EditorTab.FILTERS -> Icons.Rounded.PhotoFilter
                                    EditorTab.DRAW -> Icons.Rounded.Brush
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                Surface(
                    color = Color(0xFF121826),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (EditorTab.entries[selectedTab]) {
                            EditorTab.CROP_ROTATE -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(
                                        onClick = { spec = spec.copy(rotationQuarterTurns = (spec.rotationQuarterTurns + 3) % 4) },
                                        modifier = Modifier.testTag("rotate_left_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.RotateLeft, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("-90°", color = Color.White)
                                    }
                                    TextButton(
                                        onClick = { spec = spec.copy(rotationQuarterTurns = (spec.rotationQuarterTurns + 1) % 4) },
                                        modifier = Modifier.testTag("rotate_right_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("+90°", color = Color.White)
                                    }
                                    TextButton(
                                        onClick = { spec = spec.copy(flipHorizontal = !spec.flipHorizontal) },
                                        modifier = Modifier.testTag("flip_horizontal_button")
                                    ) {
                                        Icon(Icons.Rounded.Flip, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Flip H", color = Color.White)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CropAspectPreset.entries.forEach { preset ->
                                        val isSelected = spec.cropLeft == preset.left && spec.cropTop == preset.top
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                spec = spec.copy(
                                                    cropLeft = preset.left,
                                                    cropTop = preset.top,
                                                    cropRight = preset.right,
                                                    cropBottom = preset.bottom
                                                )
                                            },
                                            label = { Text(preset.label) }
                                        )
                                    }
                                }

                                Text(
                                    text = "Fine Crop Margin: ${(spec.cropLeft * 100).toInt()}% • Straighten: ${spec.straightenDegrees.toInt()}°",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.LightGray
                                )
                                Slider(
                                    value = spec.straightenDegrees,
                                    onValueChange = { spec = spec.copy(straightenDegrees = it) },
                                    valueRange = -25f..25f
                                )
                            }

                            EditorTab.ADJUST -> {
                                Text("Brightness (${(spec.brightness * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                                Slider(
                                    value = spec.brightness,
                                    onValueChange = { spec = spec.copy(brightness = it) },
                                    valueRange = -0.35f..0.35f
                                )
                                Text("Contrast (${String.format("%.2f", spec.contrast)}x)", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                                Slider(
                                    value = spec.contrast,
                                    onValueChange = { spec = spec.copy(contrast = it) },
                                    valueRange = 0.6f..1.6f
                                )
                                Text("Saturation (${String.format("%.2f", spec.saturation)}x)", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
                                Slider(
                                    value = spec.saturation,
                                    onValueChange = { spec = spec.copy(saturation = it) },
                                    valueRange = 0f..1.8f
                                )
                            }

                            EditorTab.FILTERS -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PhotoFilterPreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = spec.filterPreset == preset,
                                            onClick = { spec = spec.copy(filterPreset = preset) },
                                            label = { Text(preset.label) }
                                        )
                                    }
                                }
                            }

                            EditorTab.DRAW -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf(
                                            Color(0xFFEF4444),
                                            Color(0xFFFACC15),
                                            Color(0xFF22C55E),
                                            Color(0xFF38BDF8),
                                            Color.White
                                        ).forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (selectedPenColor == color) 3.dp else 1.dp,
                                                        color = if (selectedPenColor == color) Color.White else Color.Gray,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { selectedPenColor = color }
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            if (strokesList.isNotEmpty()) {
                                                strokesList.removeAt(strokesList.lastIndex)
                                            }
                                        },
                                        enabled = strokesList.isNotEmpty()
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Undo", color = Color.White)
                                    }
                                }
                                Text(
                                    text = "Drag over the photo above to circle low stock, highlight invoice totals, or mark shelves.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
