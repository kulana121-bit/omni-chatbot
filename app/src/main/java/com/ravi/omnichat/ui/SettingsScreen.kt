package com.ravi.omnichat.ui

import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ravi.omnichat.ChatViewModel
import com.ravi.omnichat.data.Protocol
import com.ravi.omnichat.data.Providers
import com.ravi.omnichat.data.Endpoint
import com.ravi.omnichat.data.ModelInfo
import com.ravi.omnichat.ui.theme.Pill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val prefs = vm.prefs
    val uri = LocalUriHandler.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var providerId by remember { mutableStateOf(vm.providerId) }
    var key by remember(providerId) { mutableStateOf(prefs.apiKey(providerId)) }
    var baseUrl by remember(providerId) { mutableStateOf(prefs.baseUrl(providerId)) }
    var protocol by remember { mutableStateOf(prefs.customProtocol) }
    var showKey by remember { mutableStateOf(false) }
    var system by remember { mutableStateOf(prefs.systemPrompt) }
    var webDefault by remember { mutableStateOf(prefs.webDefault) }
    var smartWeb by remember { mutableStateOf(prefs.smartWeb) }
    var saved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val provider = Providers.byId(providerId)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontFamily = androidx.compose.ui.text.font.FontFamily.Serif) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().hero("hero-model")
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.model.let { if (it == ChatViewModel.AUTO_MODEL) "Auto" else it.substringAfterLast('/') }.ifBlank { "No model selected" },
                            style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(vm.provider.name + " · " + vm.connectors.count { it.value } + " apps · " + vm.skills.size + " skills · " + vm.memories.size + " memories",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            SectionCard("AI provider", Icons.Outlined.Hub) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Providers.all.forEach { p ->
                        FilterChip(
                            selected = p.id == providerId,
                            onClick = { providerId = p.id; saved = false },
                            label = { Text(p.name) },
                            leadingIcon = if (p.id == providerId) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                            shape = Pill
                        )
                    }
                }
                if (providerId == "custom") {
                    Spacer(Modifier.height(12.dp))
                    Text("API format", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Protocol.entries.forEachIndexed { i, pr ->
                            SegmentedButton(
                                selected = protocol == pr,
                                onClick = { protocol = pr },
                                shape = SegmentedButtonDefaults.itemShape(i, Protocol.entries.size)
                            ) { Text(pr.label.substringBefore(' ').substringBefore('-'), maxLines = 1) }
                        }
                    }
                    Text(
                        "Most platforms (LM Studio, vLLM, Azure, etc.) use the OpenAI format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            SectionCard("Connection", Icons.Outlined.Key) {
                val urlError = remember(baseUrl, vm.allowLocalHttp) {
                    if (baseUrl.isBlank()) null else com.ravi.omnichat.security.EndpointPolicy.checkAiEndpoint(baseUrl, vm.allowLocalHttp)
                }
                vm.unreadableSecrets().takeIf { it.isNotEmpty() }?.let {
                    Text("Some saved keys couldn't be unlocked on this phone (security storage was reset). Please enter them again.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it; saved = false },
                    label = { Text("${provider.name} API key") },
                    placeholder = { Text(provider.keyHint) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show key")
                        }
                    },
                    shape = Pill,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it; saved = false },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = if (provider.defaultBaseUrl.isNotBlank() && baseUrl != provider.defaultBaseUrl) ({
                        IconButton(onClick = { baseUrl = provider.defaultBaseUrl }) { Icon(Icons.Outlined.RestartAlt, "Reset") }
                    }) else null,
                    shape = Pill,
                    isError = urlError != null,
                    supportingText = urlError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth()
                )
                if (baseUrl.trim().startsWith("http://", true) || providerId == "ollama") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow HTTP for local servers", style = MaterialTheme.typography.bodyMedium)
                            Text("Only for Ollama / LM Studio on this phone or your Wi-Fi network. Traffic isn't encrypted.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(vm.allowLocalHttp, { vm.updateAllowLocalHttp(it) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (provider.keyUrl.isNotBlank()) {
                        TextButton(onClick = { runCatching { uri.openUri(provider.keyUrl) } }) {
                            Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Get a key")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            vm.saveProvider(providerId, key, baseUrl, if (providerId == "custom") protocol else null)
                            saved = true
                        },
                        enabled = baseUrl.isNotBlank() && urlError == null
                    ) {
                        Icon(Icons.Outlined.CloudSync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & load models")
                    }
                }
                if (saved) {
                    Spacer(Modifier.height(8.dp))
                    when {
                        vm.modelsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.modelsError != null -> Text(vm.modelsError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        else -> Text(
                            "✓ Connected · ${vm.models.size} models available · using ${vm.model}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            SectionCard("Assistant", Icons.Outlined.Tune) {
                OutlinedTextField(
                    value = system,
                    onValueChange = { system = it; prefs.systemPrompt = it },
                    label = { Text("System prompt") },
                    minLines = 2, maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Internet access by default") },
                    supportingContent = { Text("Search the web and read pages before answering") },
                    leadingContent = { Icon(Icons.Outlined.Public, null) },
                    trailingContent = {
                        Switch(checked = webDefault, onCheckedChange = {
                            webDefault = it; prefs.webDefault = it; vm.webEnabled = it
                        })
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Smart web search") },
                    supportingContent = { Text("Auto-search for news, prices, scores and other fresh info, even when Web is off") },
                    leadingContent = { Icon(Icons.Outlined.AutoAwesome, null) },
                    trailingContent = { Switch(checked = smartWeb, onCheckedChange = { smartWeb = it; prefs.smartWeb = it }) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }

            UsageSection(vm)

            MemorySection(vm)

            AdvancedSection(vm)

            SectionCard("Appearance", Icons.Outlined.Palette) {
                ListItem(
                    headlineContent = { Text("Wallpaper colours") },
                    supportingContent = { Text("Off = soft pastel theme. On = Material You colours from your wallpaper (Android 12+)") },
                    leadingContent = { Icon(Icons.Outlined.ColorLens, null) },
                    trailingContent = { Switch(checked = vm.dynamicColor, onCheckedChange = { vm.setDynamic(it) }) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }

            SectionCard("Data", Icons.Outlined.Storage) {
                Text(
                    (if (vm.prefs.secrets.encrypted) "API keys are encrypted with a key kept in Android Keystore. "
                     else "This phone's secure key storage isn't working, so API keys are stored without encryption. ") +
                        "Chats and keys stay on this phone and are excluded from backups. Your messages, attachments, and app results " +
                        "you allow are sent to the AI provider you pick (and your fallback providers, if turned on); web search queries go to Bing, DuckDuckGo and Brave. " +
                        "Each provider's own policy decides what it keeps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear all chats")
                    }
                    OutlinedButton(onClick = { confirmWipe = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete all data")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text("Delete all OmniChat data?") },
            text = { Text("Deletes every chat, memory, skill, usage record, setting and saved API key from this phone. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.wipeAllData(); confirmWipe = false }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text("Clear all chats?") },
            text = { Text("This permanently deletes your chat history.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MemorySection(vm: ChatViewModel) {
    var adding by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val clear = androidx.compose.ui.graphics.Color.Transparent
    SectionCard("Memory", Icons.Outlined.Bookmark) {
        ListItem(
            headlineContent = { Text("Use memory") },
            supportingContent = { Text("AI remembers useful things about you across chats and uses them in replies. Off in incognito.") },
            leadingContent = { Icon(Icons.Outlined.Psychology, null) },
            trailingContent = { Switch(checked = vm.memoryOn, onCheckedChange = { vm.setMemoryEnabled(it) }) },
            colors = ListItemDefaults.colors(containerColor = clear)
        )
        androidx.compose.animation.AnimatedVisibility(vm.memoryOn) {
            Column {
                if (vm.memories.isEmpty()) Text(
                    "Nothing saved yet. Tell the AI \"remember that…\" or just chat, and it'll save what matters.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                vm.memories.asReversed().forEach { m ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            var editing by remember(m.id) { mutableStateOf(false) }
                            var draft by remember(m.id) { mutableStateOf(m.text) }
                            Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { draft = m.text; editing = true }.padding(vertical = 10.dp))
                            IconButton(onClick = { draft = m.text; editing = true }) { Icon(Icons.Outlined.Edit, "Edit memory", Modifier.size(18.dp)) }
                            IconButton(onClick = { vm.deleteMemory(m) }) { Icon(Icons.Outlined.Close, "Delete memory", Modifier.size(18.dp)) }
                            if (editing) androidx.compose.material3.AlertDialog(
                                onDismissRequest = { editing = false },
                                title = { Text("Edit memory") },
                                text = { OutlinedTextField(draft, { draft = it }, minLines = 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) },
                                confirmButton = { TextButton(onClick = { vm.editMemory(m, draft); editing = false }) { Text("Save") } },
                                dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(adding, { adding = it }, label = { Text("Add a memory") }, singleLine = true, shape = Pill, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { vm.addMemory(adding); adding = "" }, enabled = adding.isNotBlank()) { Text("Add") }
                }
                if (vm.memories.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all memories", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all memories?") },
        text = { Text("The AI will forget everything it saved about you.") },
        confirmButton = { TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AdvancedSection(vm: ChatViewModel) {
    var editing by remember { mutableStateOf<Endpoint?>(null) }
    var adding by remember { mutableStateOf(false) }
    var maxTok by remember { mutableStateOf(vm.prefs.maxTokens.toString()) }
    val clear = androidx.compose.ui.graphics.Color.Transparent

    SectionCard("Advanced", Icons.Outlined.Bolt) {
        ListItem(
            headlineContent = { Text("Auto fallback") },
            supportingContent = { Text("When a key hits its limit or fails, switch to the next key automatically") },
            leadingContent = { Icon(Icons.Outlined.AltRoute, null) },
            trailingContent = { Switch(checked = vm.autoFallback, onCheckedChange = { vm.setAutoFallbackOn(it) }) },
            colors = ListItemDefaults.colors(containerColor = clear)
        )
        ListItem(
            headlineContent = { Text("Never cut long replies") },
            supportingContent = { Text("If a reply hits the length limit or the connection drops, keep generating until it's complete") },
            leadingContent = { Icon(Icons.Outlined.AllInclusive, null) },
            trailingContent = { Switch(checked = vm.autoContinue, onCheckedChange = { vm.setAutoContinueOn(it) }) },
            colors = ListItemDefaults.colors(containerColor = clear)
        )
        OutlinedTextField(
            value = maxTok,
            onValueChange = { v ->
                maxTok = v.filter { it.isDigit() }.take(6)
                maxTok.toIntOrNull()?.takeIf { it >= 256 }?.let { vm.prefs.maxTokens = it }
            },
            label = { Text("Max output tokens per part (Claude)") },
            supportingText = { Text("Other providers use the model's maximum automatically") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = Pill,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("Fallback keys", style = MaterialTheme.typography.titleSmall)
        Text(
            "Order: your main key first (${vm.provider.name}), then these from top to bottom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        vm.fallbacks.forEachIndexed { i, e ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.clickable { editing = e }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = Pill, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${i + 1}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            (if (e.model.isBlank() || e.model == ChatViewModel.AUTO_MODEL) "Auto model" else e.model) + " · …" + e.key.takeLast(4),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.moveFallback(e, true) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Up") }
                    IconButton(onClick = { vm.moveFallback(e, false) }, enabled = i < vm.fallbacks.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Down") }
                    Switch(checked = e.enabled, onCheckedChange = { vm.toggleFallback(e) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { adding = true }, shape = Pill) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add API key")
        }
    }

    if (adding || editing != null) {
        FallbackDialog(vm, editing, onDismiss = { adding = false; editing = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FallbackDialog(vm: ChatViewModel, existing: Endpoint?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var providerId by remember { mutableStateOf(existing?.providerId ?: "openrouter") }
    var protocol by remember { mutableStateOf(existing?.protocol ?: Providers.byId(providerId).protocol) }
    var key by remember { mutableStateOf(existing?.key ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: Providers.byId(providerId).defaultBaseUrl) }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(36.dp),
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text(if (existing == null) "Add fallback key" else "Edit fallback key") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Providers.all.forEach { p ->
                        FilterChip(
                            selected = p.id == providerId,
                            onClick = {
                                providerId = p.id
                                if (p.id != "custom") protocol = p.protocol
                                baseUrl = p.defaultBaseUrl; models = emptyList(); model = ""; error = null
                            },
                            label = { Text(p.name) },
                            shape = Pill
                        )
                    }
                }
                if (providerId == "custom") {
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Protocol.entries.forEachIndexed { i, pr ->
                            SegmentedButton(
                                selected = protocol == pr, onClick = { protocol = pr },
                                shape = SegmentedButtonDefaults.itemShape(i, Protocol.entries.size)
                            ) { Text(pr.label.substringBefore(' ').substringBefore('-'), maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, singleLine = true, shape = Pill,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(baseUrl, { baseUrl = it.trim() }, label = { Text("Base URL") }, singleLine = true, shape = Pill,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = model == ChatViewModel.AUTO_MODEL,
                        onClick = { model = if (model == ChatViewModel.AUTO_MODEL) "" else ChatViewModel.AUTO_MODEL },
                        label = { Text("Auto model") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) },
                        shape = Pill
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Picks the best model, skips ones that fail", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedTextField(
                        model, { model = it.trim() }, label = { Text("Model") }, singleLine = true, shape = Pill,
                        trailingIcon = {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = {
                                scope.launch {
                                    loading = true; error = null
                                    runCatching { vm.fetchModels(protocol, baseUrl.trimEnd('/'), key) }
                                        .onSuccess { models = it; menu = it.isNotEmpty(); if (it.isEmpty()) error = "No models returned" }
                                        .onFailure { error = it.message }
                                    loading = false
                                }
                            }) { Icon(Icons.Outlined.CloudDownload, "Load models") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.heightIn(max = 360.dp)) {
                        OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, shape = Pill,
                            modifier = Modifier.padding(horizontal = 8.dp).width(240.dp))
                        models.filter { query.isBlank() || it.id.contains(query, true) }.take(200).forEach { m ->
                            DropdownMenuItem(text = { Text(m.id, maxLines = 1) }, onClick = { model = m.id; menu = false })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Name (optional)") }, placeholder = { Text("e.g. Groq key 2") },
                    singleLine = true, shape = Pill, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.upsertFallback(
                        Endpoint(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            providerId = providerId, protocol = protocol,
                            baseUrl = baseUrl.trimEnd('/'), key = key, model = model.ifBlank { ChatViewModel.AUTO_MODEL }, label = label.trim(),
                            enabled = existing?.enabled ?: true
                        )
                    )
                    onDismiss()
                },
                enabled = baseUrl.isNotBlank(),
                shape = Pill
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { vm.removeFallback(existing); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
internal fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    // Claude-style: small grey heading above a flat, outlined white group.
    Column {
        Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}

@Composable
internal fun ConnectorsSection(vm: ChatViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pendingId by remember { mutableStateOf<String?>(null) }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val id = pendingId ?: return@rememberLauncherForActivityResult
        if (res.values.all { it }) vm.setConnector(id, true) else vm.toast = "Permission needed for this app"
        pendingId = null
    }
    var token by remember { mutableStateOf(vm.prefs.githubToken) }
    SectionCard("Apps", Icons.Outlined.Apps) {
        Text("Let the AI use apps on your phone. Type @ in chat to call one directly.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        com.ravi.omnichat.data.Connectors.all.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(connectorIcon(c.id), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    Text(c.desc + "  ·  @" + c.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.connectors[c.id] == true, onCheckedChange = { on ->
                    val missing = c.permissions.filter { ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
                    if (on && missing.isNotEmpty()) { pendingId = c.id; perm.launch(missing.toTypedArray()) } else vm.setConnector(c.id, on)
                })
            }
            if (c.id == "github" && vm.connectors["github"] == true) {
                OutlinedTextField(
                    value = token, onValueChange = { token = it; vm.prefs.githubToken = it },
                    label = { Text("GitHub token (optional, for your private repos)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillsSection(vm: ChatViewModel) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<com.ravi.omnichat.data.Skill?>(null) }
    var creatingAi by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importSkill(u)
    }
    SectionCard("Skills", Icons.Outlined.AutoFixHigh) {
        Text("Reusable instructions. Type / in chat to use one, e.g. /email-writer.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        vm.skills.forEachIndexed { i, sk ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = sk }.padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("/" + sk.slug, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(sk.description.ifBlank { sk.instructions.take(80) }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                IconButton(onClick = { vm.deleteSkill(sk) }) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AssistChip(onClick = { editing = com.ravi.omnichat.data.Skill(name = "", instructions = "") }, label = { Text("Write") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) }, shape = Pill)
            AssistChip(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }, label = { Text("Upload .md") },
                leadingIcon = { Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)) }, shape = Pill)
            AssistChip(onClick = { creatingAi = true }, label = { Text("Create with AI") },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) }, shape = Pill)
        }
    }

    editing?.let { sk ->
        var name by remember(sk.id) { mutableStateOf(sk.name) }
        var desc by remember(sk.id) { mutableStateOf(sk.description) }
        var ins by remember(sk.id) { mutableStateOf(sk.instructions) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (sk.name.isBlank()) "New skill" else "Edit skill") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        supportingText = { if (name.isNotBlank()) Text("Call it with /" + sk.copy(name = name).slug) })
                    OutlinedTextField(desc, { desc = it }, label = { Text("When to use (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(ins, { ins = it }, label = { Text("Instructions") }, minLines = 5, maxLines = 12, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && ins.isNotBlank(), onClick = {
                    vm.upsertSkill(sk.copy(name = name.trim(), description = desc.trim(), instructions = ins.trim())); editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (creatingAi) {
        var idea by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) creatingAi = false },
            icon = { Icon(Icons.Outlined.AutoAwesome, null) },
            title = { Text("Create a skill with AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(idea, { idea = it }, label = { Text("What should it do?") },
                        placeholder = { Text("e.g. Write funny Hinglish YouTube Shorts scripts under 60s") },
                        minLines = 3, shape = RoundedCornerShape(14.dp), enabled = !busy)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = idea.isNotBlank() && !busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        runCatching { vm.generateSkill(idea.trim()) }
                            .onSuccess { creatingAi = false; editing = it }
                            .onFailure { err = it.message ?: "Failed" }
                        busy = false
                    }
                }) { Text(if (busy) "Creating…" else "Create") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { creatingAi = false }) { Text("Cancel") } }
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageSection(vm: ChatViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = vm.usageTick
    var tab by remember { mutableIntStateOf(0) }
    var editScope by remember { mutableStateOf<String?>(null) }
    // keep the countdown fresh
    var clock by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); clock++ } }
    val counts by androidx.compose.runtime.produceState(initialValue = emptyMap<String, com.ravi.omnichat.data.Counter>(), tick, clock) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.usage.todayCounts() }
    }
    val cs = MaterialTheme.colorScheme
    SectionCard("Usage", Icons.Outlined.DataUsage) {
        val total = counts.filterKeys { it.startsWith("k:") }.values.fold(com.ravi.omnichat.data.Counter()) { a, b -> a + b }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${total.requests}", style = MaterialTheme.typography.headlineMedium)
            Text(" requests  ·  ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Text(com.ravi.omnichat.data.UsageStore.fmt(total.tokens), style = MaterialTheme.typography.headlineMedium)
            Text(" tokens today", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        key(clock) {
            Text(vm.usage.resetLabel(), style = MaterialTheme.typography.bodySmall, color = cs.primary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Day resets at", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        val rm = vm.usage.resetMinutes
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "12:00 AM", 330 to "5:30 AM", 810 to "1:30 PM · Gemini").forEach { (m, l) ->
                FilterChip(selected = rm == m, onClick = { vm.setResetMinutes(m) }, label = { Text(l) }, shape = Pill)
            }
            val custom = rm !in listOf(0, 330, 810)
            FilterChip(selected = custom, shape = Pill, onClick = {
                android.app.TimePickerDialog(ctx, { _, h, mi -> vm.setResetMinutes(h * 60 + mi) }, rm / 60, rm % 60, false).show()
            }, label = { Text(if (custom) "%d:%02d %s".format(((rm / 60) + 11) % 12 + 1, rm % 60, if (rm < 720) "AM" else "PM") else "Custom…") })
        }
        Text("Gemini's free quota resets at midnight Pacific (1:30 PM IST); most others at midnight UTC (5:30 AM IST).",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(3.dp)) {
            listOf("API keys", "Models").forEachIndexed { i, l ->
                val sel = tab == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) cs.surfaceContainerLowest else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(l, style = MaterialTheme.typography.labelLarge, color = if (sel) cs.onSurface else cs.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (tab == 0) {
            val keys = vm.allKeys()
            if (keys.isEmpty()) Text("No API keys saved yet.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            keys.forEachIndexed { i, k ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                UsageRow(vm, k.scope, counts[k.scope], onEdit = { editScope = k.scope }) {
                    vm.usage.rate(k.scope)?.let { r -> RateLine(r) }
                    if (com.ravi.omnichat.data.BalanceApi.supported(k.providerId)) {
                        val b = vm.balances[k.scope]
                        b?.lines?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        vm.balanceErrors[k.scope]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                        TextButton(onClick = { vm.refreshBalance(k) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (b == null) "Check balance & credit limit" else "Refresh balance")
                        }
                    }
                }
            }
        } else {
            val cur = com.ravi.omnichat.data.Scopes.model(vm.providerId, vm.model)
            val scopes = (listOf(cur) + counts.keys.filter { it.startsWith("m:") } + vm.usage.limitedScopes().filter { it.startsWith("m:") })
                .distinct().filter { !it.endsWith("/") }.sortedByDescending { counts[it]?.tokens ?: 0 }
            scopes.forEachIndexed { i, sc ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                UsageRow(vm, sc, counts[sc], onEdit = { editScope = sc }) {
                    Text(com.ravi.omnichat.data.Providers.byId(com.ravi.omnichat.data.Scopes.providerOf(sc)).name,
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        Text("Counted on this phone. Tokens come from the provider when it reports them, otherwise they're estimated. When a limit is hit, OmniChat switches to your next fallback key or stops until the reset.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }

    editScope?.let { sc ->
        val cur = vm.usage.limit(sc)
        var req by remember(sc) { mutableStateOf(if (cur.requests > 0) cur.requests.toString() else "") }
        var tok by remember(sc) { mutableStateOf(if (cur.tokens > 0) cur.tokens.toString() else "") }
        AlertDialog(
            onDismissRequest = { editScope = null },
            title = { Text("Daily limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(com.ravi.omnichat.data.Scopes.label(sc), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(req, { req = it.filter(Char::isDigit).take(7) }, label = { Text("Requests per day") }, singleLine = true,
                        placeholder = { Text("No limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(tok, { tok = it.filter(Char::isDigit).take(10) }, label = { Text("Tokens per day") }, singleLine = true,
                        placeholder = { Text("No limit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp),
                        supportingText = { Text("e.g. 250 requests for Gemini 2.5 Flash free tier, 50 for OpenRouter free models") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setLimit(sc, com.ravi.omnichat.data.DailyLimit(req.toIntOrNull() ?: 0, tok.toLongOrNull() ?: 0)); editScope = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (cur.isSet) TextButton(onClick = { vm.setLimit(sc, com.ravi.omnichat.data.DailyLimit()); editScope = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editScope = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun UsageRow(vm: ChatViewModel, scope: String, c: com.ravi.omnichat.data.Counter?, onEdit: () -> Unit, extra: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val used = c ?: com.ravi.omnichat.data.Counter()
    val lim = vm.usage.limit(scope)
    val frac = vm.usage.fraction(scope)
    val fmt = com.ravi.omnichat.data.UsageStore.Companion::fmt
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(com.ravi.omnichat.data.Scopes.label(scope), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (lim.requests > 0) "${used.requests}/${lim.requests} requests" else "${used.requests} requests") + "  ·  " +
                        (if (lim.tokens > 0) "${fmt(used.tokens)}/${fmt(lim.tokens)} tokens" else "${fmt(used.tokens)} tokens") +
                        "  (${fmt(used.input)} in, ${fmt(used.output)} out)",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            if (frac != null && frac >= 1f) Surface(shape = Pill, color = cs.errorContainer) {
                Text("Limit hit", style = MaterialTheme.typography.labelSmall, color = cs.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            TextButton(onClick = onEdit) { Text(if (lim.isSet) "Edit limit" else "Set limit") }
        }
        if (frac != null) {
            val anim by androidx.compose.animation.core.animateFloatAsState(frac.coerceIn(0f, 1f), androidx.compose.animation.core.tween(600), label = "u")
            LinearProgressIndicator(
                progress = { anim },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = when { frac >= 1f -> cs.error; frac >= 0.8f -> androidx.compose.ui.graphics.Color(0xFFD08A2E); else -> cs.primary },
                trackColor = cs.surfaceContainerHighest
            )
        }
        // last 7 days, tokens
        val hist by androidx.compose.runtime.produceState(initialValue = emptyList<Pair<java.time.LocalDate, com.ravi.omnichat.data.Counter>>(), scope, vm.usageTick) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.usage.history(scope, 7) }
        }
        val max = hist.maxOf { it.second.tokens }.coerceAtLeast(1)
        if (hist.any { it.second.requests > 0 }) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                hist.forEachIndexed { i, (d, cc) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((22f * cc.tokens / max).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp))
                            .background(if (i == hist.lastIndex) cs.primary else cs.primary.copy(alpha = 0.35f)))
                        Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.ENGLISH),
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.padding(top = 4.dp)) { extra() }
    }
}

@Composable
private fun RateLine(r: com.ravi.omnichat.data.RateInfo) {
    val t = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)
    val now = System.currentTimeMillis()
    fun reset(ms: Long?) = ms?.let { if (it > now) " · resets " + t.format(java.util.Date(it)) else " · reset done" } ?: ""
    val ago = ((now - r.at) / 60000).let { if (it < 1) "just now" else if (it < 60) "${it}m ago" else "${it / 60}h ago" }
    val lines = buildList {
        if (r.reqLeft != null || r.reqLimit != null) add("Provider limit: ${r.reqLeft ?: "?"}/${r.reqLimit ?: "?"} requests left${reset(r.reqReset)}")
        if (r.tokLeft != null || r.tokLimit != null) add("Tokens: ${r.tokLeft?.let { com.ravi.omnichat.data.UsageStore.fmt(it) } ?: "?"}/${r.tokLimit?.let { com.ravi.omnichat.data.UsageStore.fmt(it) } ?: "?"} left${reset(r.tokReset)}")
    }
    lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    Text("Checked $ago, from the provider's last reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
