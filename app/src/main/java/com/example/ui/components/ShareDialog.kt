package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem

@Composable
fun ShareLinkReadyDialog(
    board: SharedChecklist,
    items: List<SharedChecklistItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var baseUrl by remember { mutableStateOf(ShareUtils.getWebBaseUrl(context)) }
    var showEditHostDialog by remember { mutableStateOf(false) }

    val webUrl = ShareUtils.buildLiveWebUrl(board.shareCode, baseUrl)
    val appLink = ShareUtils.buildAppLink(board.shareCode)
    val shareMessage = ShareUtils.buildSharedBoardShareMessage(context, board, items)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share Real-Time Checklist",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Anyone can open the Live Web URL in any browser to view & sync stock in real time without installing the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE WEB URL (NO DOWNLOAD NEEDED)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showEditHostDialog = true },
                                modifier = Modifier.testTag("edit_web_host_button")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Change Web Host URL")
                            }
                        }
                        Text(
                            text = webUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { ShareUtils.copyToClipboard(context, "Live Web URL", webUrl) },
                                modifier = Modifier.weight(1f).testTag("copy_web_url_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Copy URL")
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                },
                                modifier = Modifier.weight(1f).testTag("open_web_url_button")
                            ) {
                                Icon(Icons.Rounded.Language, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SHARE CODE & APP LINK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Code: ${board.shareCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = appLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { ShareUtils.copyToClipboard(context, "Share Code", board.shareCode) },
                            modifier = Modifier.fillMaxWidth().testTag("copy_share_code_button")
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Share Code (${board.shareCode})")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { ShareUtils.shareViaWhatsApp(context, shareMessage) },
                        modifier = Modifier.weight(1f).testTag("share_whatsapp_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("WhatsApp")
                    }
                    OutlinedButton(
                        onClick = { ShareUtils.shareViaSms(context, shareMessage) },
                        modifier = Modifier.weight(1f).testTag("share_sms_button")
                    ) {
                        Icon(Icons.Rounded.Sms, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("SMS")
                    }
                }

                Button(
                    onClick = { ShareUtils.shareText(context, "ShopKeep Checklist: ${board.title}", shareMessage) },
                    modifier = Modifier.fillMaxWidth().testTag("share_system_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Link via Apps")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showEditHostDialog) {
        var hostInput by remember { mutableStateOf(baseUrl) }
        AlertDialog(
            onDismissRequest = { showEditHostDialog = false },
            title = { Text("Set Custom Live Web URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hosted the web/ folder on Netlify Drop, GitHub Pages, or Vercel? Paste its site URL below (leave blank to reset to default).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Site URL (e.g. https://my-shopkeep.netlify.app)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_web_host_input")
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ShareUtils.setCustomWebBaseUrl(context, hostInput)
                    baseUrl = ShareUtils.getWebBaseUrl(context)
                    showEditHostDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditHostDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
