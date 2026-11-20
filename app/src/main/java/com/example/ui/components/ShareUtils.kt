package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ChecklistItem
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem
import java.io.File
import java.net.URLEncoder

object ShareUtils {
    const val DEFAULT_WEB_BASE_URL = "https://nandwanifamily242l-hash.github.io/SHOPKEEP"
    private const val PREFS_NAME = "shopkeep_share_prefs"
    private const val KEY_CUSTOM_WEB_URL = "custom_web_base_url"

    fun getWebBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_WEB_URL, null)?.trim()?.trimEnd('/')
        return if (!saved.isNullOrBlank()) saved else DEFAULT_WEB_BASE_URL
    }

    fun setCustomWebBaseUrl(context: Context, rawUrl: String) {
        val normalized = rawUrl.trim().trimEnd('/').let { url ->
            if (url.isBlank()) ""
            else if (url.startsWith("http://") || url.startsWith("https://")) url
            else "https://$url"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_WEB_URL, normalized)
            .apply()
    }

    fun buildPublicLandingUrl(baseUrl: String = DEFAULT_WEB_BASE_URL): String {
        val cleanBase = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_WEB_BASE_URL }
        return "$cleanBase/"
    }

    fun buildPublicLandingUrl(context: Context): String =
        buildPublicLandingUrl(getWebBaseUrl(context))

    fun buildLiveWebUrl(shareCode: String, baseUrl: String = DEFAULT_WEB_BASE_URL): String {
        val cleanBase = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_WEB_BASE_URL }
        return "$cleanBase/?checklist=${URLEncoder.encode(shareCode.trim().uppercase(), "UTF-8")}"
    }

    fun buildLiveWebUrl(context: Context, shareCode: String): String =
        buildLiveWebUrl(shareCode, getWebBaseUrl(context))

    fun buildAppLink(shareCode: String): String =
        "shopkeep://checklist/${shareCode.trim().uppercase()}"

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    fun shareViaWhatsApp(context: Context, text: String) {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            shareText(context, "ShopKeep Checklist", text)
        }
    }

    fun shareViaSms(context: Context, text: String) {
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
            putExtra("sms_body", text)
        }
        try {
            context.startActivity(smsIntent)
        } catch (_: Exception) {
            shareText(context, "ShopKeep Checklist", text)
        }
    }

    fun buildPersonalChecklistText(items: List<ChecklistItem>): String {
        val grouped = items.filter { !it.isDeleted }.groupBy { it.category }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        return buildString {
            appendLine("ShopKeep Restock Checklist")
            appendLine("========================")
            grouped.forEach { (cat, list) ->
                appendLine()
                appendLine("[$cat]")
                list.forEach { item ->
                    val mark = if (item.isChecked) "[x]" else "[ ]"
                    val qty = if (item.quantity.isNotBlank()) " (${item.quantity})" else ""
                    appendLine("$mark ${item.title}$qty")
                }
            }
        }
    }

    fun buildSharedBoardShareMessage(
        context: Context,
        board: SharedChecklist,
        items: List<SharedChecklistItem>,
        usePublicLandingLink: Boolean = false
    ): String {
        val webUrl = if (usePublicLandingLink) {
            buildPublicLandingUrl(context)
        } else {
            buildLiveWebUrl(context, board.shareCode)
        }
        val pendingCount = items.count { !it.isChecked }
        return buildString {
            appendLine("ShopKeep Live Checklist: ${board.title}")
            appendLine("$pendingCount pending • ${items.size} total items")
            appendLine()
            appendLine("Open \"${board.title}\" live in browser (no app needed):")
            appendLine(webUrl)
        }
    }

    fun exportChecklistHtmlFile(
        context: Context,
        title: String,
        items: List<Pair<String, List<Triple<String, String, Boolean>>>>
    ) {
        val dir = File(context.cacheDir, "checklists").apply { mkdirs() }
        val file = File(dir, "shopkeep_checklist_${System.currentTimeMillis()}.html")
        val html = buildString {
            append(
                """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>${escapeHtml(title)}</title>
                  <style>
                    body { font-family: system-ui, -apple-system, sans-serif; background: #f7f8fc; color: #121826; margin: 0; padding: 20px; }
                    .card { max-width: 680px; margin: 0 auto; background: #fff; border-radius: 16px; padding: 20px; box-shadow: 0 6px 24px rgba(18,24,38,0.08); }
                    h1 { margin-top: 0; color: #1a3878; font-size: 22px; }
                    h2 { margin: 18px 0 8px; font-size: 15px; color: #d95d39; text-transform: uppercase; letter-spacing: 0.04em; }
                    label { display: flex; align-items: center; gap: 10px; padding: 8px 6px; border-bottom: 1px solid #eef1f8; cursor: pointer; }
                    input:checked + span { text-decoration: line-through; color: #64748b; }
                    .qty { margin-left: auto; font-size: 13px; color: #1a3878; font-weight: 600; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>${escapeHtml(title)}</h1>
                """.trimIndent()
            )
            items.forEach { (category, list) ->
                append("<h2>${escapeHtml(category)}</h2>")
                list.forEach { (name, qty, checked) ->
                    val attr = if (checked) "checked" else ""
                    append("<label><input type=\"checkbox\" $attr /><span>${escapeHtml(name)}</span>")
                    if (qty.isNotBlank()) {
                        append("<span class=\"qty\">${escapeHtml(qty)}</span>")
                    }
                    append("</label>")
                }
            }
            append("</div></body></html>")
        }
        file.writeText(html)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Interactive Checklist"))
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
