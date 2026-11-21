package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
    onTogglePublicEditable: (Boolean) -> Unit = {},
    onSetAsPublicDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var baseUrl by remember { mutableStateOf(ShareUtils.getWebBaseUrl(context)) }
    var showEditHostDialog by remember { mutableStateOf(false) }

    val publicUrl = ShareUtils.buildPublicLandingUrl(baseUrl)
    val boardSpecificUrl = ShareUtils.buildLiveWebUrl(board.shareCode, baseUrl)
    val shareMessage = ShareUtils.buildSharedBoardShareMessage(
        context = context,
        board = board,
        items = items,
        usePublicLandingLink = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share Public Live Link",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Anyone can open your public link in any browser immediately — no code and no app download required.",
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
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "PUBLIC LINK FOR \"${board.title.uppercase()}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showEditHostDialog = true },
                                modifier = Modifier.testTag("edit_web_host_button")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Change GitHub Pages or Web URL")
                            }
                        }
                        Text(
                            text = boardSpecificUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { ShareUtils.copyToClipboard(context, "${board.title} Link", boardSpecificUrl) },
                                modifier = Modifier.weight(1f).testTag("copy_web_url_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Copy Link")
                            }
                            OutlinedButton(
                                onClick = {
                                    ShareUtils.openWebUrlInBrowser(context, boardSpecificUrl)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Public View & Mark/Unmark",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (board.isPublicEditable) {
                                    "Public link is view-only for stock; visitors can only mark & unmark items"
                                } else {
                                    "Strictly view-only — Google Sign-In required even to mark items"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = board.isPublicEditable,
                            onCheckedChange = onTogglePublicEditable,
                            modifier = Modifier.testTag("toggle_public_editable_switch")
                        )
                    }
                }

                if (onSetAsPublicDefault != null) {
                    OutlinedButton(
                        onClick = onSetAsPublicDefault,
                        modifier = Modifier.fillMaxWidth().testTag("set_public_default_button")
                    ) {
                        Text("Use \"${board.title}\" for Main Public Link")
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

                TextButton(
                    onClick = { ShareUtils.copyToClipboard(context, "Main Public Home URL", publicUrl) },
                    modifier = Modifier.fillMaxWidth().testTag("copy_share_code_button")
                ) {
                    Text("Copy Main Home URL (Default Board)")
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
            title = { Text("Set Your Live Website URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste your GitHub Pages URL (e.g. https://nandwanifamily242l-hash.github.io/your-repo) so the Copy & Share buttons always use your live link.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("GitHub Pages / Web URL") },
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
