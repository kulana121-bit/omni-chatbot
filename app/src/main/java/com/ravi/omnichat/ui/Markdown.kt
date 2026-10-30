package com.ravi.omnichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lightweight Markdown renderer: headings, bullets, numbered lists, bold, italic, inline code, code blocks. */
@Composable
fun MarkdownText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { splitBlocks(text) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryC = MaterialTheme.colorScheme.primary
    // Parsed lines are cached, so while streaming only the newest line is rebuilt (smooth on low-end phones).
    val cache = androidx.compose.runtime.remember(codeBg, primaryC) { HashMap<String, AnnotatedString>() }
    fun inline(s: String): AnnotatedString = cache.getOrPut(s) { inlineOf(s, codeBg, primaryC) }.also { if (cache.size > 600) cache.clear() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { bi, b ->
            androidx.compose.runtime.key(bi) {
            if (b.isCode) CodeBlock(b.text, b.lang) else {
                val all = b.text.lines()
                var li = 0
                while (li < all.size) {
                    // Markdown tables: a run of lines starting with '|'
                    if (all[li].trimStart().startsWith("|")) {
                        val rows = mutableListOf<String>()
                        while (li < all.size && all[li].trimStart().startsWith("|")) { rows += all[li].trim(); li++ }
                        MdTable(rows, ::inline, color)
                        continue
                    }
                    val line = all[li].trimEnd()
                    li++
                    when {
                        line.isBlank() -> {}
                        line.startsWith("### ") -> Text(inline(line.drop(4)), style = MaterialTheme.typography.titleSmall, color = color)
                        line.startsWith("## ") -> Text(inline(line.drop(3)), style = MaterialTheme.typography.titleMedium, color = color)
                        line.startsWith("# ") -> Text(inline(line.drop(2)), style = MaterialTheme.typography.titleLarge, color = color)
                        line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                            val indent = (line.length - line.trimStart().length) / 2
                            Row(Modifier.padding(start = (indent * 14).dp)) {
                                Text("•  ", color = color, style = MaterialTheme.typography.bodyLarge)
                                Text(inline(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyLarge, color = color)
                            }
                        }
                        NUMBERED.matches(line) -> {
                            val num = line.trimStart().substringBefore(' ')
                            Row {
                                Text("$num ", color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(inline(line.trimStart().substringAfter(' ')), style = MaterialTheme.typography.bodyLarge, color = color)
                            }
                        }
                        line.startsWith("> ") -> Text(
                            inline(line.drop(2)),
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        RULE.matches(line) -> Box(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.outlineVariant).padding(top = 1.dp)
                        )
                        else -> Text(inline(line), style = MaterialTheme.typography.bodyLarge, color = color)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MdTable(rows: List<String>, inline: (String) -> AnnotatedString, color: Color) {
    val cells = rows.filterNot { Regex("^\\|?\\s*:?-{2,}").containsMatchIn(it) && it.replace(Regex("[|:\\-\\s]"), "").isEmpty() }
        .map { r -> r.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
    if (cells.isEmpty()) return
    val cols = cells.maxOf { it.size }
    val border = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, border, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            cells.forEachIndexed { ri, row ->
                Row(Modifier.background(if (ri == 0) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)) {
                    for (c in 0 until cols) {
                        Text(
                            inline(row.getOrElse(c) { "" }),
                            style = if (ri == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.width(150.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                if (ri < cells.lastIndex) Box(Modifier.width((150 * cols).dp).height(1.dp).background(border))
            }
        }
    }
}

private val NUMBERED = Regex("^\\s*\\d+[.)] .*")
private val RULE = Regex("^-{3,}$")
private val CITE = Regex("^\\[\\d+]")

@Composable
private fun <T> remember(key: Any, calc: () -> T): T = androidx.compose.runtime.remember(key) { calc() }

@Composable
private fun CodeBlock(code: String, lang: String) {
    val clip = LocalClipboardManager.current
    val copied = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied.value) { if (copied.value) { kotlinx.coroutines.delay(1500); copied.value = false } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(lang.ifBlank { "code" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { clip.setText(AnnotatedString(code)); copied.value = true }) {
                Icon(if (copied.value) Icons.Filled.Check else Icons.Outlined.ContentCopy, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(if (copied.value) "Copied" else "Copy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)
        )
    }
}

private data class Block(val text: String, val isCode: Boolean, val lang: String = "")

private fun splitBlocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    val parts = text.split("```")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) {
            val lang = p.substringBefore('\n').trim()
            val code = if (p.contains('\n')) p.substringAfter('\n') else p
            out += Block(code.trimEnd(), true, lang)
        } else if (p.isNotBlank()) out += Block(p, false)
    }
    return out
}

private fun inlineOf(s: String, codeBg: Color, primary: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("**", i) && s.indexOf("**", i + 2) > i -> {
                    val end = s.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                }
                s[i] == '`' && s.indexOf('`', i + 1) > i -> {
                    val end = s.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) { append(" ${s.substring(i + 1, end)} ") }
                    i = end + 1
                }
                (s[i] == '*' || s[i] == '_') && i + 1 < s.length && s[i + 1] != ' ' && s.indexOf(s[i], i + 1) > i + 1 &&
                    (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    val end = s.indexOf(s[i], i + 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                }
                s[i] == '[' && CITE.containsMatchIn(s.substring(i)) -> {
                    val end = s.indexOf(']', i)
                    withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) { append(s.substring(i, end + 1)) }
                    i = end + 1
                }
                else -> { append(s[i]); i++ }
            }
        }
    }
}
