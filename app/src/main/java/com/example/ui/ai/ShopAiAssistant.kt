package com.example.ui.ai

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "ShopAiAssistant"

sealed interface AiDialogState {
    data object Idle : AiDialogState
    data object Loading : AiDialogState
    data class Streaming(val text: String) : AiDialogState
    data class Success(val text: String) : AiDialogState
    data class Error(val message: String) : AiDialogState
}

object ShopAiEngine {
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun streamShopAdvice(
        prompt: String,
        onChunk: (String) -> Unit
    ): Result<String> {
        val sb = StringBuilder()
        return try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        onChunk(sb.toString())
                    }
                }
            }
            val result = sb.toString().trim()
            if (result.isEmpty()) {
                Result.failure(IllegalStateException("Gemini returned an empty response."))
            } else {
                Result.success(result)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI stream timed out", e)
            Result.failure(Exception("AI request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI request failed."))
        }
    }

    suspend fun analyzeShopPhoto(
        bitmap: Bitmap,
        userPrompt: String
    ): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            userPrompt.ifBlank {
                                "Analyze this shop photo (shelf, invoice, or counter). List visible items, stock gaps, or receipt totals in crisp bullet points."
                            }
                        )
                    }
                )
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Model returned an empty analysis."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo AI timed out", e)
            Result.failure(Exception("Photo analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo AI failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Photo analysis failed."))
        }
    }
}

@Composable
fun ShopAiAssistantDialog(
    contextSummary: String,
    bitmapToAnalyze: Bitmap? = null,
    onDismiss: () -> Unit,
    onAddChecklistSuggestions: ((List<String>) -> Unit)? = null
) {
    var prompt by remember {
        mutableStateOf(
            if (bitmapToAnalyze != null) {
                "List items to restock or key details from this shop photo."
            } else {
                "Suggest 5 high-priority shop tasks or restock checks for today."
            }
        )
    }
    var state by remember { mutableStateOf<AiDialogState>(AiDialogState.Idle) }
    val scope = rememberCoroutineScope()

    fun runPrompt(customPrompt: String) {
        scope.launch {
            state = AiDialogState.Loading
            if (bitmapToAnalyze != null) {
                val res = ShopAiEngine.analyzeShopPhoto(bitmapToAnalyze, customPrompt)
                state = res.fold(
                    onSuccess = { AiDialogState.Success(it) },
                    onFailure = { AiDialogState.Error(it.message ?: "Analysis failed.") }
                )
            } else {
                val fullPrompt = """
                    You are a practical retail store operations assistant inside ShopKeep.
                    Current shop context: $contextSummary
                    User request: $customPrompt
                    Provide a concise, actionable response with bullet points.
                """.trimIndent()
                val res = ShopAiEngine.streamShopAdvice(fullPrompt) { partial ->
                    state = AiDialogState.Streaming(partial)
                }
                state = res.fold(
                    onSuccess = { AiDialogState.Success(it) },
                    onFailure = { AiDialogState.Error(it.message ?: "Request failed.") }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (bitmapToAnalyze != null) "AI Photo & Receipt Lens" else "ShopKeep AI Helper",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bitmapToAnalyze == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val p = "Create an opening & closing shop shift checklist."
                                prompt = p
                                runPrompt(p)
                            },
                            label = { Text("Shift Plan") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val p = "List fast-moving shop items to restock before weekend."
                                prompt = p
                                runPrompt(p)
                            },
                            label = { Text("Restock Ideas") }
                        )
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Ask about your shop") },
                    modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input"),
                    maxLines = 3
                )

                Button(
                    onClick = { runPrompt(prompt) },
                    enabled = state !is AiDialogState.Loading && prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("ai_run_button")
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (bitmapToAnalyze != null) "Analyze Photo" else "Ask ShopKeep AI")
                }

                when (val s = state) {
                    AiDialogState.Idle -> Unit
                    AiDialogState.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is AiDialogState.Streaming -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = s.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    is AiDialogState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = s.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (onAddChecklistSuggestions != null) {
                            OutlinedButton(
                                onClick = {
                                    val lines = s.text.lines()
                                        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
                                        .filter { it.length in 3..70 }
                                        .take(6)
                                    if (lines.isNotEmpty()) {
                                        onAddChecklistSuggestions(lines)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Lines to Stock Checklist")
                            }
                        }
                    }
                    is AiDialogState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = s.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
