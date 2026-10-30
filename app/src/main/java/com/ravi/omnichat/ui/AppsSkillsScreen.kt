package com.ravi.omnichat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravi.omnichat.ChatViewModel
import com.ravi.omnichat.data.Connectors
import com.ravi.omnichat.data.PremadeSkill
import com.ravi.omnichat.data.SkillLibrary
import com.ravi.omnichat.ui.theme.Pill

/** Apps (connectors), the user's skills and the ready-made skill library, opened from the side panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSkillsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps & Skills", fontFamily = FontFamily.Serif) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Segmented tabs
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceContainerHigh).padding(3.dp)
            ) {
                val apps = vm.connectors.count { it.value }
                listOf("Apps · $apps", "My skills · ${vm.skills.size}", "Library").forEachIndexed { i, l ->
                    val sel = tab == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                            .background(if (sel) cs.surfaceContainerLowest else Color.Transparent)
                            .clickable { tab = i }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(l, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            color = if (sel) cs.onSurface else cs.onSurfaceVariant, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (t) {
                        0 -> {
                            Hint("Turn on the apps the AI may use. In chat, type @ to call one, like @weather or @phone.")
                            ConnectorsSection(vm)
                        }
                        1 -> {
                            Hint("Your skills are reusable instructions. In chat, type / to use one, like /shorts-script.")
                            SkillsSection(vm)
                            if (vm.skills.isEmpty()) OutlinedButton(onClick = { tab = 2 }, shape = Pill) { Text("Browse the skill library") }
                        }
                        else -> LibraryTab(vm)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun LibraryTab(vm: ChatViewModel) {
    var cat by remember { mutableStateOf("All") }
    var preview by remember { mutableStateOf<PremadeSkill?>(null) }
    val cs = MaterialTheme.colorScheme
    val added = vm.skills.map { it.name.lowercase() }.toSet()
    Hint("Ready-made skills, tuned for great results. Tap one to preview, or add it in one tap.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("All") + SkillLibrary.categories).forEach { c ->
            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }, shape = Pill)
        }
    }
    val list = SkillLibrary.all.filter { cat == "All" || it.category == cat }
    if (list.any { it.name.lowercase() !in added }) {
        TextButton(onClick = {
            list.filter { it.name.lowercase() !in added }.forEach { vm.upsertSkill(it.toSkill()) }
            vm.toast = "Added ${list.count { it.name.lowercase() !in added }} skills"
        }) { Text(if (cat == "All") "Add all" else "Add all $cat skills") }
    }
    list.forEach { p ->
        val isAdded = p.name.lowercase() in added
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cs.surfaceContainerLowest,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { preview = p }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(p.emoji, style = MaterialTheme.typography.titleLarge) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("/" + p.toSkill().slug + "  ·  " + p.category, style = MaterialTheme.typography.labelSmall, color = cs.primary, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                if (isAdded) {
                    Surface(shape = Pill, color = cs.secondaryContainer) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Added", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    FilledTonalIconButton(onClick = { vm.upsertSkill(p.toSkill()); vm.toast = "${p.name} added. Use /${p.toSkill().slug}" }, modifier = Modifier.bounce()) {
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            }
        }
    }

    preview?.let { p ->
        val isAdded = p.name.lowercase() in added
        AlertDialog(
            onDismissRequest = { preview = null },
            icon = { Text(p.emoji, style = MaterialTheme.typography.headlineMedium) },
            title = { Text(p.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(p.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh) {
                        Text(p.instructions, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !isAdded, onClick = { vm.upsertSkill(p.toSkill()); preview = null; vm.toast = "${p.name} added" }) {
                    Text(if (isAdded) "Added" else "Add skill")
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Close") } }
        )
    }
}
