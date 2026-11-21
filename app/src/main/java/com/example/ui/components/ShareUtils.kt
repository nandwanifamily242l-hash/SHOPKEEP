package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ChecklistItem
import com.example.data.model.SharedChecklist
import com.example.data.model.SharedChecklistItem
import java.io.File
import java.net.URLEncoder

data class HtmlChecklistItem(
    val title: String,
    val quantity: String = "",
    val notes: String = "",
    val isChecked: Boolean = false,
    val imageBase64: String = "",
    val updatedAt: Long = 0L
)

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

    fun openWebUrlInBrowser(context: Context, url: String) {
        val uri = Uri.parse(url)
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val pm = context.packageManager
        val resolvedBrowsers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(browserProbe, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)
        }.mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName }
            .distinct()

        if (resolvedBrowsers.isNotEmpty()) {
            val browserPackage = resolvedBrowsers.firstOrNull { pkg ->
                pkg.contains("chrome", ignoreCase = true) ||
                    pkg.contains("browser", ignoreCase = true) ||
                    pkg.contains("firefox", ignoreCase = true) ||
                    pkg.contains("edge", ignoreCase = true) ||
                    pkg.contains("opera", ignoreCase = true) ||
                    pkg.contains("brave", ignoreCase = true)
            } ?: resolvedBrowsers.first()

            val explicitBrowserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                `package` = browserPackage
            }
            try {
                context.startActivity(explicitBrowserIntent)
                return
            } catch (_: Exception) {
                // Fall back to generic VIEW intent below
            }
        }
        context.startActivity(viewIntent)
    }

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

    fun buildChecklistHtml(
        title: String,
        sections: List<Pair<String, List<HtmlChecklistItem>>>
    ): String {
        val totalItems = sections.sumOf { it.second.size }
        val pendingItems = sections.sumOf { (_, list) -> list.count { !it.isChecked } }
        val photoCount = sections.sumOf { (_, list) -> list.count { it.imageBase64.isNotBlank() } }

        return buildString(65536) {
            append(
                """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>${escapeHtml(title)}</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f4f6fb; color: #121826; margin: 0; padding: 14px; }
                    .container { max-width: 960px; margin: 0 auto; display: grid; gap: 14px; }
                    .header-card { background: #1a3878; color: #fff; border-radius: 16px; padding: 16px 18px; box-shadow: 0 6px 24px rgba(18,24,38,0.12); }
                    .header-card h1 { margin: 0; font-size: 20px; }
                    .header-meta { margin: 6px 0 0; font-size: 13px; opacity: 0.88; }
                    .cat-card { background: #fff; border-radius: 14px; padding: 14px; border: 1px solid #e2e8f0; box-shadow: 0 4px 14px rgba(18,24,38,0.05); }
                    .cat-header { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 10px; }
                    .cat-header h2 { margin: 0; font-size: 15px; color: #1a3878; text-transform: uppercase; letter-spacing: 0.04em; }
                    .cat-badge { font-size: 11px; font-weight: 700; color: #475569; background: #f1f5f9; padding: 3px 9px; border-radius: 999px; }
                    .photo-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 10px; }
                    @media (max-width: 900px) { .photo-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
                    @media (max-width: 600px) { body { padding: 10px; } .cat-card { padding: 12px; } .photo-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; } }
                    .photo-card { border: 2px solid #e2e8f0; border-radius: 12px; overflow: hidden; background: #f8fafc; display: flex; flex-direction: column; min-width: 0; }
                    .photo-card.checked { border-color: #1a3878; background: #eef2ff; }
                    .photo-wrap { position: relative; width: 100%; aspect-ratio: 4 / 3; background: #0f172a; overflow: hidden; }
                    .photo-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: pointer; }
                    .mark-circle { position: absolute; top: 6px; left: 6px; width: 28px; height: 28px; border-radius: 999px; border: 2px solid #fff; background: rgba(0,0,0,0.48); color: #fff; font-weight: 800; font-size: 14px; display: grid; place-items: center; cursor: pointer; box-shadow: 0 2px 6px rgba(0,0,0,0.35); }
                    .photo-card.checked .mark-circle { background: #1a3878; }
                    .photo-info { padding: 7px 8px; min-width: 0; }
                    .photo-title { font-size: 12px; font-weight: 600; color: #121826; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                    .photo-card.checked .photo-title { text-decoration: line-through; color: #475569; }
                    .photo-sub { font-size: 11px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
                    .text-row { display: flex; align-items: center; gap: 10px; padding: 8px 4px; border-top: 1px solid #eef1f8; cursor: pointer; }
                    .text-row input[type="checkbox"] { width: 18px; height: 18px; flex-shrink: 0; cursor: pointer; }
                    .text-main { flex: 1; min-width: 0; }
                    .text-title { font-size: 14px; font-weight: 500; }
                    .text-row.checked .text-title { text-decoration: line-through; color: #64748b; }
                    .text-notes { font-size: 12px; color: #64748b; }
                    .qty { font-size: 12px; color: #1a3878; background: #eef1f8; padding: 3px 8px; border-radius: 999px; font-weight: 600; flex-shrink: 0; }
                    .marked-label { font-size: 12px; font-weight: 700; color: #64748b; margin: 10px 0 6px; }
                    .hidden { display: none !important; }
                    .lightbox { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.85); display: grid; place-items: center; padding: 16px; z-index: 999; }
                    .lightbox-box { background: #fff; border-radius: 16px; max-width: 560px; width: 100%; overflow: hidden; }
                    .lightbox-box img { width: 100%; max-height: 72vh; object-fit: contain; background: #0f172a; display: block; }
                    .lightbox-bar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 16px; font-weight: 600; }
                    .lightbox-btn { border: none; background: #1a3878; color: #fff; border-radius: 8px; padding: 8px 14px; font-weight: 600; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <header class="header-card">
                      <h1>${escapeHtml(title)}</h1>
                      <p class="header-meta" id="overallMeta">$pendingItems pending &bull; $totalItems items ($photoCount photos)</p>
                    </header>
                """.trimIndent()
            )

            sections.forEach { (category, list) ->
                val unmarkedPhotos = list.filter { it.imageBase64.isNotBlank() && !it.isChecked }
                val unmarkedText = list.filter { it.imageBase64.isBlank() && !it.isChecked }
                val markedText = list.filter { it.imageBase64.isBlank() && it.isChecked }.sortedBy { it.updatedAt }
                val markedPhotos = list.filter { it.imageBase64.isNotBlank() && it.isChecked }.sortedBy { it.updatedAt }
                val catPending = list.count { !it.isChecked }

                append("<section class=\"cat-card\">")
                append("<div class=\"cat-header\"><h2>${escapeHtml(category)}</h2><span class=\"cat-badge\">$catPending pending &bull; ${list.size}</span></div>")

                append("<div class=\"photo-grid unmarked-grid${if (unmarkedPhotos.isEmpty()) " hidden" else ""}\">")
                unmarkedPhotos.forEach { item ->
                    appendPhotoCardHtml(this, item, checked = false)
                }
                append("</div>")

                val orderedText = unmarkedText + markedText
                if (orderedText.isNotEmpty()) {
                    append("<div class=\"text-list\">")
                    orderedText.forEach { item ->
                        val checkedClass = if (item.isChecked) " checked" else ""
                        val checkedAttr = if (item.isChecked) "checked" else ""
                        append("<label class=\"text-row$checkedClass\">")
                        append("<input type=\"checkbox\" $checkedAttr />")
                        append("<span class=\"text-main\"><span class=\"text-title\">${escapeHtml(item.title)}</span>")
                        if (item.notes.isNotBlank()) {
                            append("<span class=\"text-notes\"> &bull; ${escapeHtml(item.notes)}</span>")
                        }
                        append("</span>")
                        if (item.quantity.isNotBlank()) {
                            append("<span class=\"qty\">${escapeHtml(item.quantity)}</span>")
                        }
                        append("</label>")
                    }
                    append("</div>")
                }

                append("<div class=\"marked-block${if (markedPhotos.isEmpty()) " hidden" else ""}\">")
                append("<div class=\"marked-label\">Marked (${markedPhotos.size})</div>")
                append("<div class=\"photo-grid marked-grid\">")
                markedPhotos.forEach { item ->
                    appendPhotoCardHtml(this, item, checked = true)
                }
                append("</div></div>")
                append("</section>")
            }

            append(
                """
                  </div>
                  <div id="lightbox" class="lightbox hidden">
                    <div class="lightbox-box">
                      <img id="lbImg" alt="Photo Preview" />
                      <div class="lightbox-bar">
                        <span id="lbTitle">Photo Note</span>
                        <button id="lbClose" type="button" class="lightbox-btn">Close</button>
                      </div>
                    </div>
                  </div>
                  <script>
                    (function () {
                      const lb = document.getElementById("lightbox");
                      const lbImg = document.getElementById("lbImg");
                      const lbTitle = document.getElementById("lbTitle");
                      document.getElementById("lbClose").addEventListener("click", () => lb.classList.add("hidden"));
                      lb.addEventListener("click", (e) => { if (e.target === lb) lb.classList.add("hidden"); });

                      function refreshCounts() {
                        let total = 0, pending = 0, photos = 0;
                        document.querySelectorAll(".cat-card").forEach((cat) => {
                          const pCards = cat.querySelectorAll(".photo-card");
                          const tRows = cat.querySelectorAll(".text-row");
                          const catTotal = pCards.length + tRows.length;
                          let catPending = 0;
                          pCards.forEach((c) => { if (!c.classList.contains("checked")) catPending++; });
                          tRows.forEach((r) => { if (!r.classList.contains("checked")) catPending++; });
                          total += catTotal;
                          pending += catPending;
                          photos += pCards.length;
                          const badge = cat.querySelector(".cat-badge");
                          if (badge) badge.textContent = catPending + " pending \u2022 " + catTotal;
                          const uGrid = cat.querySelector(".unmarked-grid");
                          const mBlock = cat.querySelector(".marked-block");
                          const mGrid = cat.querySelector(".marked-grid");
                          const mLabel = cat.querySelector(".marked-label");
                          if (uGrid) uGrid.classList.toggle("hidden", uGrid.children.length === 0);
                          if (mBlock && mGrid) {
                            const mCount = mGrid.children.length;
                            mBlock.classList.toggle("hidden", mCount === 0);
                            if (mLabel) mLabel.textContent = "Marked (" + mCount + ")";
                          }
                        });
                        const meta = document.getElementById("overallMeta");
                        if (meta) meta.textContent = pending + " pending \u2022 " + total + " items (" + photos + " photos)";
                      }

                      document.querySelectorAll(".photo-card").forEach((card) => {
                        const img = card.querySelector("img");
                        const titleEl = card.querySelector(".photo-title");
                        const circle = card.querySelector(".mark-circle");
                        if (img) {
                          img.addEventListener("click", () => {
                            lbImg.src = img.src;
                            lbTitle.textContent = titleEl ? titleEl.textContent : "Photo Note";
                            lb.classList.remove("hidden");
                          });
                        }
                        if (circle) {
                          circle.addEventListener("click", (e) => {
                            e.stopPropagation();
                            const cat = card.closest(".cat-card");
                            const nextChecked = !card.classList.contains("checked");
                            card.classList.toggle("checked", nextChecked);
                            circle.textContent = nextChecked ? "\u2713" : "";
                            const target = nextChecked ? cat.querySelector(".marked-grid") : cat.querySelector(".unmarked-grid");
                            if (target) target.appendChild(card);
                            refreshCounts();
                          });
                        }
                      });

                      document.querySelectorAll(".text-row input[type='checkbox']").forEach((cb) => {
                        cb.addEventListener("change", () => {
                          const row = cb.closest(".text-row");
                          if (row) row.classList.toggle("checked", cb.checked);
                          refreshCounts();
                        });
                      });
                    })();
                  </script>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }

    private fun appendPhotoCardHtml(sb: StringBuilder, item: HtmlChecklistItem, checked: Boolean) {
        val rawB64 = item.imageBase64.trim()
        val dataSrc = if (rawB64.startsWith("data:")) rawB64 else "data:image/jpeg;base64,$rawB64"
        val sub = listOf(item.quantity, item.notes).filter { it.isNotBlank() }.joinToString(" • ")
        sb.append("<div class=\"photo-card${if (checked) " checked" else ""}\">")
        sb.append("<div class=\"photo-wrap\">")
        sb.append("<img src=\"${escapeHtml(dataSrc)}\" alt=\"${escapeHtml(item.title)}\" loading=\"lazy\" />")
        sb.append("<button type=\"button\" class=\"mark-circle\" title=\"Mark or Unmark\">${if (checked) "✓" else ""}</button>")
        sb.append("</div>")
        sb.append("<div class=\"photo-info\">")
        sb.append("<div class=\"photo-title\">${escapeHtml(item.title.ifBlank { "Photo Note" })}</div>")
        if (sub.isNotBlank()) {
            sb.append("<div class=\"photo-sub\">${escapeHtml(sub)}</div>")
        }
        sb.append("</div></div>")
    }

    fun exportChecklistHtmlFile(
        context: Context,
        title: String,
        sections: List<Pair<String, List<HtmlChecklistItem>>>
    ) {
        val dir = File(context.cacheDir, "checklists").apply { mkdirs() }
        val file = File(dir, "shopkeep_checklist_${System.currentTimeMillis()}.html")
        val html = buildChecklistHtml(title = title, sections = sections)
        file.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, title)
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
