package com.ravi.omnichat.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.foundation.verticalScroll
import com.ravi.omnichat.data.Connectors
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.offset
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravi.omnichat.ChatViewModel
import com.ravi.omnichat.data.Attachment
import com.ravi.omnichat.data.ChatMessage
import kotlinx.coroutines.launch
import com.ravi.omnichat.ui.theme.Pill
import com.ravi.omnichat.ui.theme.SoftGradient
import com.ravi.omnichat.data.ThinkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, openApps: () -> Unit = {}, openSettings: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showModels by remember { mutableStateOf(false) }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.toast = null }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                Modifier.width(310.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ) {
                ClaudeSidebar(
                    vm = vm,
                    close = { scope.launch { drawerState.close() } },
                    openSettings = { scope.launch { drawerState.close() }; openSettings() },
                    openApps = { scope.launch { drawerState.close() }; openApps() }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.bounce()) { Icon(Icons.Filled.Menu, "Menu") }
                    },
                    title = {
                        Row(
                            Modifier.hero("hero-model").clip(RoundedCornerShape(12.dp))
                                .clickable { if (vm.hasKey) showModels = true else openSettings() }
                                .bounce(0.95f)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (vm.model == ChatViewModel.AUTO_MODEL) "Auto" else vm.model.substringAfterLast('/').ifBlank { "Select model" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        if (vm.incognito) {
                            FilledTonalIconButton(onClick = { vm.toggleIncognito() }) { Icon(Icons.Outlined.VisibilityOff, "Leave incognito") }
                        }
                        IconButton(onClick = { vm.newChat() }, modifier = Modifier.bounce()) { Icon(Icons.Outlined.Edit, "New chat") }
                    }
                )
            },
            bottomBar = { InputBar(vm) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                AnimatedVisibility(vm.incognito) {
                    Surface(
                        shape = Pill,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                            Spacer(Modifier.width(10.dp))
                            Text("Incognito · this chat won't be saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
                AnimatedVisibility(vm.canContinue && !vm.isStreaming) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AssistChip(onClick = { vm.continueReply() }, label = { Text("Continue") })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (vm.messages.isEmpty()) EmptyState(vm, openSettings) else MessageList(vm)
                }
            }
        }
    }

    if (showModels) ModelPickerSheet(vm) { showModels = false }
}

@Composable
private fun EmptyState(vm: ChatViewModel, openSettings: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "hero")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    @Composable
    fun Stagger(i: Int, content: @Composable () -> Unit) = AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(420, delayMillis = 80 * i)) +
            slideInVertically(spring(dampingRatio = 0.7f, stiffness = 260f)) { it / 3 }
    ) { content() }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greet = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello, night owl" }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Stagger(0) {
                Icon(
                    Icons.Filled.AutoAwesome, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp).graphicsLayer { rotationZ = spin }
                )
            }
            Spacer(Modifier.height(16.dp))
            Stagger(1) {
                Text(
                    if (vm.incognito) "Incognito chat" else greet,
                    style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(6.dp))
            Stagger(2) {
                Text(
                    if (vm.incognito) "This chat won't be saved or used for memory." else "What can I help with?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Stagger(3) { HeroActions(vm, openSettings) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroActions(vm: ChatViewModel, openSettings: () -> Unit) {
    if (!vm.hasKey) {
        Surface(
            onClick = openSettings,
            modifier = Modifier.fillMaxWidth().bounce(0.97f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add your API key", style = MaterialTheme.typography.titleSmall)
                    Text("OpenAI, Gemini, Claude, OpenRouter, Groq or custom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        data class Idea(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val prompt: String, val web: Boolean = false)
        val ideas = listOf(
            Idea(Icons.Outlined.Lightbulb, "Explain", "quantum computing simply", "Explain quantum computing simply"),
            Idea(Icons.Outlined.Code, "Write code", "a Kotlin string reverser", "Write a Kotlin function to reverse a string"),
            Idea(Icons.Outlined.Public, "Latest news", "in tech today", "What's the latest tech news today?", web = true),
            Idea(Icons.Outlined.Edit, "Caption", "a funny Instagram post", "Write a funny Instagram caption")
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ideas.forEachIndexed { n, idea ->
                val seen = remember { MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(seen, enter = fadeIn(tween(380, 300 + n * 90)) + slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 240f)) { it / 3 }) {
                    Surface(
                        onClick = {
                            if (idea.web) vm.webEnabled = true
                            vm.input = idea.prompt; vm.send()
                        },
                        modifier = Modifier.bounce(0.95f),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(idea.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(idea.title, style = MaterialTheme.typography.labelLarge)
                                Text(idea.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageList(vm: ChatViewModel) {
    val listState = rememberLazyListState()
    val last = vm.messages.lastOrNull()
    // New message: smooth scroll once. While streaming: follow the text without animation,
    // and only if the user hasn't scrolled up to read (prevents jumping / blinking).
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index >= info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 300
        }
    }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) { if (!listState.isScrollInProgress) follow = atBottom }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) { follow = true; listState.animateScrollToItem(vm.messages.lastIndex) }
    }
    // Follow the growing reply by scrolling the remaining distance (no re-anchoring = no flash).
    LaunchedEffect(last?.content?.length, last?.thinking?.length) {
        if (vm.isStreaming && vm.messages.isNotEmpty() && follow) listState.scrollBy(100_000f)
    }
    val initial = remember(vm.currentId) { vm.messages.map { it.id }.toSet() }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(vm.messages, key = { it.id }, contentType = { it.role }) { m ->
            val vis = remember(m.id) { MutableTransitionState(m.id in initial) }.apply { targetState = true }
            AnimatedVisibility(
                visibleState = vis,
                enter = if (m.role == "user")
                    fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.55f, stiffness = 380f)) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.5f, stiffness = 420f), initialScale = 0.82f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
                else fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f), initialScale = 0.9f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f))
            ) {
            if (m.role == "user") UserBubble(m)
            else AssistantMessage(
                m,
                isLast = m.id == last?.id,
                streaming = vm.isStreaming && m.id == last?.id,
                status = vm.status,
                onRegenerate = { vm.regenerate() }
            )
            }
        }
    }
    val showJump by remember { derivedStateOf { !atBottom && vm.messages.size > 1 } }
    AnimatedVisibility(
        showJump,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
        exit = scaleOut(tween(150)) + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            onClick = { follow = true; scope.launch { listState.animateScrollToItem(vm.messages.lastIndex, 100_000) } },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp,
            modifier = Modifier.size(40.dp).bounce(0.85f)
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.ArrowDownward, "Scroll to bottom", Modifier.size(20.dp)) } }
    }
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (m.attachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp).horizontalScroll(rememberScrollState())) {
                m.attachments.forEach { AttachmentPreview(it) }
            }
        }
        if (m.content.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                SelectionContainer {
                    Text(
                        m.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(a: Attachment) {
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, a) {
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val b64 = if (a.isVideo) a.frames.firstOrNull() else if (a.isImage) a.base64 else null
            if (b64 != null) runCatching {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull() else null
        }
        value = decoded
    }
    if (bmp != null) {
        Box(contentAlignment = Alignment.Center) {
            Image(bmp!!, a.name, Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
            if (a.isVideo) Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
        }
    } else {
        Surface(shape = Pill, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (a.isVideo) Icons.Outlined.Videocam else if (a.mime == "application/zip") Icons.Outlined.FolderZip else if (a.isPdf) Icons.Outlined.PictureAsPdf else if (a.isImage) Icons.Outlined.Image else Icons.Outlined.Description, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessage(m: ChatMessage, isLast: Boolean, streaming: Boolean, status: String?, onRegenerate: () -> Unit) {
    val clip = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.weight(1f)) {
            if (m.thinking.isNotBlank()) ThinkingBlock(m.thinking, streaming && m.content.isEmpty(), m.thinkSecs)
            when {
                m.isError -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(m.content, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                m.content.isEmpty() && streaming -> if (m.thinking.isBlank()) TypingIndicator(status)
                // Same tree while streaming and after: switching containers made the reply flash.
                else -> MarkdownText(m.content)
            }
            if (m.sources.isNotEmpty()) {
                var open by remember(m.id) { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { open = !open },
                    shape = Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.bounce(0.93f)
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Public, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${m.sources.size} sources", style = MaterialTheme.typography.labelMedium)
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(top = 4.dp)) {
                        m.sources.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runCatching { uri.openUri(s.url) } }.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        runCatching { java.net.URI(s.url).host.removePrefix("www.") }.getOrNull() ?: s.url,
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(m.appNote.isNotBlank(), enter = fadeIn(tween(300)) + expandVertically(spring(dampingRatio = 0.7f, stiffness = 300f))) {
                Surface(shape = Pill, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(top = 6.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Apps, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text("Used " + m.appNote, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            AnimatedVisibility(m.memoryNote.isNotBlank(), enter = fadeIn(tween(300)) + expandVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) + scaleIn(spring(0.5f, 400f), initialScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f))) {
            Column {
                Spacer(Modifier.height(6.dp))
                Surface(shape = Pill, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bookmark, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text(m.memoryNote, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            }
            AnimatedVisibility(!streaming && m.content.isNotEmpty(), enter = fadeIn(tween(350, 80)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 350f)) { -it / 3 }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-10).dp)) {
                    IconButton(onClick = { clip.setText(AnnotatedString(m.content)) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var selecting by remember(m.id) { mutableStateOf(false) }
                    IconButton(onClick = { selecting = true }) {
                        Icon(Icons.Outlined.TextFields, "Select text", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selecting) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { selecting = false },
                        confirmButton = { TextButton(onClick = { selecting = false }) { Text("Done") } },
                        title = { Text("Select text") },
                        text = {
                            SelectionContainer {
                                Text(m.content, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
                            }
                        }
                    )
                    if (isLast) IconButton(onClick = onRegenerate) {
                        Icon(Icons.Outlined.Refresh, "Regenerate", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (m.model.isNotBlank()) Text(m.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ThinkingBlock(text: String, active: Boolean, secs: Int) {
    var open by remember { mutableStateOf(false) }
    // Live seconds counter while the model is thinking, "Thought for Ns" afterwards (Claude style).
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (active) {
            elapsed = 0
            while (true) { kotlinx.coroutines.delay(1000); elapsed++ }
        }
    }
    val label = when {
        active -> if (elapsed > 0) "Thinking · ${elapsed}s" else "Thinking"
        secs > 0 -> "Thought for ${secs}s"
        else -> "Thought process"
    }
    val t = rememberInfiniteTransition(label = "think")
    val shimmer by t.animateFloat(-200f, 700f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "sh")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val chevron by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, spring(0.6f, 400f), label = "chev")
    Column(Modifier.padding(bottom = 10.dp).animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AutoAwesome, null, Modifier.size(16.dp).graphicsLayer { if (active) rotationZ = spin },
                tint = if (active) MaterialTheme.colorScheme.primary else base
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.let { st ->
                    if (active) st.copy(brush = Brush.linearGradient(listOf(base, MaterialTheme.colorScheme.onSurface, base),
                        start = androidx.compose.ui.geometry.Offset(shimmer, 0f), end = androidx.compose.ui.geometry.Offset(shimmer + 200f, 0f)))
                    else st.copy(color = base)
                }
            )
            Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(18.dp).graphicsLayer { rotationZ = chevron }, tint = base)
        }
        val shown = if (text.length > 8000) "…" + text.takeLast(8000) else text
        if (open || active) {
            Row(Modifier.padding(start = 7.dp, top = 6.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (open) shown.trim() else shown.trim().lines().filter { it.isNotBlank() }.takeLast(3).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = base,
                    maxLines = if (open) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator(status: String?) {
    val t = rememberInfiniteTransition(label = "typing")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val shimmer by t.animateFloat(-300f, 900f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "shimmer")
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val hi = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(
            Icons.Filled.AutoAwesome, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = spin; scaleX = pulse; scaleY = pulse }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            status ?: "Thinking…",
            style = MaterialTheme.typography.bodyMedium.copy(
                brush = Brush.linearGradient(
                    listOf(base, hi, base),
                    start = androidx.compose.ui.geometry.Offset(shimmer, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmer + 250f, 0f)
                )
            )
        )
    }
}

@Composable
private fun InputBar(vm: ChatViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    var attachMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 4.dp)
        ) {
            // Actions the assistant asked for that send or change something wait here for the user's tap.
            vm.pendingActions.forEach { pa ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .semantics { contentDescription = "Action waiting for approval: ${pa.description}" }
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
                        Text("OmniChat wants to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(pa.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.denyAction(pa.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Don't allow") }
                            Button(onClick = { vm.approveAction(pa.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") }
                        }
                    }
                }
            }
            val focusSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val focused by focusSrc.collectIsFocusedAsState()
            val borderC by androidx.compose.animation.animateColorAsState(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant, tween(250), label = "border")
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(if (focused) 1.5.dp else 1.dp, borderC),
                shadowElevation = 0.dp,
                modifier = Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f))
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    AnimatedVisibility(vm.pending.isNotEmpty(), enter = fadeIn() + expandVertically(spring(0.8f, 400f)), exit = fadeOut() + shrinkVertically()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.pending.forEach { a ->
                                InputChip(
                                    selected = false,
                                    onClick = { vm.removePending(a) },
                                    label = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                                    leadingIcon = { Icon(if (a.isVideo) Icons.Outlined.Videocam else if (a.mime == "application/zip") Icons.Outlined.FolderZip else if (a.isImage) Icons.Outlined.Image else if (a.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description, null, Modifier.size(18.dp)) },
                                    trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) },
                                    shape = Pill
                                )
                            }
                        }
                    }
                    // "@" suggests apps, "/" suggests skills (like Claude/ChatGPT).
                    val token = remember(vm.input) { Regex("(^|\\s)([@/])([\\w-]*)$").find(vm.input) }
                    val trigger = token?.groupValues?.get(2)
                    val q = token?.groupValues?.get(3)?.lowercase() ?: ""
                    val appMatches = if (trigger == "@") Connectors.all.filter { it.id.startsWith(q) || it.name.lowercase().contains(q) } else emptyList()
                    val skillMatches = if (trigger == "/") vm.skills.filter { it.slug.contains(q) || it.name.lowercase().contains(q) } else emptyList()
                    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
                    fun pick(word: String) {
                        val t = token ?: return
                        vm.input = vm.input.substring(0, t.range.first) + t.groupValues[1] + word + " "
                    }
                    AnimatedVisibility(
                        appMatches.isNotEmpty() || skillMatches.isNotEmpty() || (trigger == "/" && vm.skills.isEmpty()),
                        enter = fadeIn(tween(150)) + expandVertically(spring(0.8f, 500f)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
                    ) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(4.dp)) {
                            Text(if (trigger == "@") "Apps" else "Skills", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
                            appMatches.forEach { c ->
                                SuggestRow(connectorIcon(c.id), c.name, "@" + c.id + (if (vm.connectors[c.id] == true) "" else " · will turn on")) {
                                    if (c.permissions.isNotEmpty()) permLauncher.launch(c.permissions.toTypedArray())
                                    vm.setConnector(c.id, true); pick("@" + c.id)
                                }
                            }
                            skillMatches.forEach { sk -> SuggestRow(Icons.Outlined.AutoFixHigh, sk.name, "/" + sk.slug + if (sk.description.isNotBlank()) " · " + sk.description else "") { pick("/" + sk.slug) } }
                            if (trigger == "/" && vm.skills.isEmpty())
                                Text("No skills yet. Add one from Apps & Skills in the side panel.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        }
                    }
                    TextField(
                        value = vm.input,
                        onValueChange = { vm.input = it; vm.onTyping() },
                        placeholder = { Text(if (vm.incognito) "Message incognito" else "Ask anything", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        interactionSource = focusSrc,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedIconButton(
                                onClick = { attachMenu = true },
                                modifier = Modifier.size(36.dp).bounce(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) { Icon(Icons.Filled.Add, "Attach", Modifier.size(20.dp)) }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, shape = RoundedCornerShape(24.dp)) {
                                DropdownMenuItem(
                                    text = { Text("Photos") },
                                    leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                    onClick = {
                                        attachMenu = false
                                        mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Videos") },
                                    leadingIcon = { Icon(Icons.Outlined.VideoLibrary, null) },
                                    onClick = {
                                        attachMenu = false
                                        mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Files") },
                                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                                    onClick = {
                                        attachMenu = false
                                        picker.launch(arrayOf("application/pdf", "text/*", "application/json", "application/zip", "application/x-zip-compressed", "*/*"))
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = vm.webEnabled,
                            onClick = { vm.webEnabled = !vm.webEnabled },
                            label = { Text("Web") },
                            leadingIcon = { Icon(Icons.Outlined.Public, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(50)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box {
                            var thinkMenu by remember { mutableStateOf(false) }
                            FilterChip(
                                selected = vm.thinkMode != ThinkMode.AUTO && vm.thinkMode != ThinkMode.OFF,
                                onClick = { thinkMenu = true },
                                label = { Text(if (vm.thinkMode == ThinkMode.AUTO) "Think" else vm.thinkMode.label) },
                                leadingIcon = { Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(50)
                            )
                            DropdownMenu(expanded = thinkMenu, onDismissRequest = { thinkMenu = false }, shape = RoundedCornerShape(24.dp)) {
                                Text("Thinking mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                ThinkMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(mode.label)
                                                Text(
                                                    when (mode) {
                                                        ThinkMode.AUTO -> "Model decides"
                                                        ThinkMode.OFF -> "Fastest replies"
                                                        ThinkMode.LOW -> "Quick reasoning"
                                                        ThinkMode.MEDIUM -> "Balanced"
                                                        ThinkMode.HIGH -> "Deep reasoning, slower"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = { if (vm.thinkMode == mode) Icon(Icons.Filled.Check, null) },
                                        onClick = { vm.selectThink(mode); thinkMenu = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedContent(
                            targetState = vm.isStreaming,
                            transitionSpec = { (scaleIn(spring(0.6f, 400f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                            label = "send"
                        ) { streaming ->
                            if (streaming) {
                                FilledIconButton(
                                    onClick = { vm.stop() },
                                    modifier = Modifier.size(38.dp).bounce(),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
                                ) { Icon(Icons.Filled.Stop, "Stop", Modifier.size(18.dp)) }
                            } else {
                                val ready = vm.input.isNotBlank() || vm.pending.isNotEmpty()
                                val sc by androidx.compose.animation.core.animateFloatAsState(if (ready) 1f else 0.88f, spring(0.5f, 500f), label = "s")
                                FilledIconButton(
                                    onClick = { vm.send() },
                                    enabled = ready,
                                    modifier = Modifier.size(38.dp).scale(sc).bounce(0.85f),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.surface,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) { Icon(Icons.Filled.ArrowUpward, "Send", Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
            Text(
                "AI can make mistakes. Check important info.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(vm: ChatViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    val filtered = vm.models.filter { query.isBlank() || it.id.contains(query, true) || it.displayName.contains(query, true) }
    // Always load the provider's model list when the picker opens.
    LaunchedEffect(Unit) { if (vm.models.isEmpty() && !vm.modelsLoading) vm.refreshModels() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose a model", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${vm.provider.name} · ${vm.models.size} available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.refreshModels() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search models") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            when {
                vm.modelsLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.modelsError != null -> Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(vm.modelsError ?: "", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                }
                vm.models.isEmpty() -> Text(
                    if (vm.hasKey) "Tap refresh to load models" else "Add an API key for ${vm.provider.name} in Settings to see its models",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 460.dp)) {
                item(key = "__auto") {
                    val selected = vm.model == ChatViewModel.AUTO_MODEL
                    ListItem(
                        headlineContent = { Text("Auto") },
                        supportingContent = { Text("Best available model, auto-switches if one fails") },
                        leadingContent = { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier.bounce(0.96f).clip(Pill).clickable { vm.selectModel(ChatViewModel.AUTO_MODEL); onDismiss() }
                    )
                }
                items(filtered, key = { it.id }) { m ->
                    val selected = m.id == vm.model
                    ListItem(
                        headlineContent = { Text(m.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (m.displayName != m.id) ({ Text(m.id, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        ),
                        modifier = Modifier.bounce(0.96f).clip(Pill).clickable { vm.selectModel(m.id); onDismiss() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                OutlinedTextField(
                    value = manual, onValueChange = { manual = it },
                    label = { Text("Or type a model ID") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = Pill
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { if (manual.isNotBlank()) { vm.selectModel(manual.trim()); onDismiss() } }) { Text("Use") }
            }
        }
    }
}


@Composable
private fun SuggestRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun connectorIcon(id: String): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    "calendar" -> Icons.Outlined.CalendarMonth
    "contacts" -> Icons.Outlined.Contacts
    "phone" -> Icons.Outlined.Call
    "alarms" -> Icons.Outlined.Alarm
    "news" -> Icons.Outlined.Newspaper
    "finance" -> Icons.Outlined.CurrencyRupee
    "wiki" -> Icons.Outlined.MenuBook
    "location" -> Icons.Outlined.LocationOn
    "maps" -> Icons.Outlined.Map
    "youtube" -> Icons.Outlined.PlayCircle
    "weather" -> Icons.Outlined.WbSunny
    "github" -> Icons.Outlined.Code
    else -> Icons.Outlined.PhoneAndroid
}


/** Claude-style sidebar: search, new chat, recents grouped by day, profile row at the bottom. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClaudeSidebar(vm: ChatViewModel, close: () -> Unit, openSettings: () -> Unit, openApps: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("OmniChat", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif), modifier = Modifier.weight(1f))
        }
        // Search
        Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search chats", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", Modifier.size(18.dp).clip(CircleShape).clickable { query = "" }, tint = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        SidebarRow(Icons.Filled.Add, "New chat", accent = true) { vm.newChat(); close() }
        SidebarRow(Icons.Outlined.VisibilityOff, if (vm.incognito) "Leave incognito" else "Incognito chat", selected = vm.incognito) { vm.toggleIncognito(); close() }
        SidebarRow(Icons.Outlined.Apps, "Apps & Skills") { openApps() }

        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val startToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (vm.chatsLoading && vm.conversations.isEmpty()) {
            Text("Loading chats…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        }
        val list = vm.conversations.filter { query.isBlank() || it.title.contains(query, true) || it.messages.any { m -> m.content.contains(query, true) } }
        val groups = list.groupBy { c ->
            when {
                c.updated >= startToday -> "Today"
                c.updated >= startToday - day -> "Yesterday"
                c.updated >= now - 7 * day -> "Previous 7 days"
                c.updated >= now - 30 * day -> "Previous 30 days"
                else -> "Older"
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            if (list.isEmpty()) item {
                Text(if (query.isBlank()) "Your chats will show up here" else "No chats match \"$query\"",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            listOf("Today", "Yesterday", "Previous 7 days", "Previous 30 days", "Older").forEach { g ->
                val items = groups[g] ?: return@forEach
                item(key = "h-$g") {
                    Text(g, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
                }
                items(items, key = { it.id }) { c ->
                    var menu by remember { mutableStateOf(false) }
                    val sel = c.id == vm.currentId
                    Box(Modifier.animateItem()) {
                        Row(
                            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) cs.surfaceContainerHighest else Color.Transparent)
                                .combinedClickable(onClick = { vm.openChat(c.id); close() }, onLongClick = { menu = true })
                                .padding(start = 12.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (sel) IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.MoreHoriz, "More", Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = cs.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = cs.error) },
                                onClick = { menu = false; vm.deleteChat(c.id) }
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
        // Profile row
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = openSettings).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val name = vm.memories.firstNotNullOfOrNull { Regex("(?i)(?:name is|called|naam)\\s+([A-Za-z]+)").find(it.text)?.groupValues?.get(1) } ?: "You"
            Surface(shape = CircleShape, color = cs.primary, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = cs.onPrimary, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(if (vm.model == ChatViewModel.AUTO_MODEL) "${vm.provider.name} · Auto" else vm.provider.name,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Outlined.Settings, "Settings", tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SidebarRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) cs.surfaceContainerHighest else Color.Transparent)
            .clickable(onClick = onClick).bounce(0.97f).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (accent) Surface(shape = CircleShape, color = cs.primary, modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(16.dp), tint = cs.onPrimary) }
        } else Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
            color = if (accent) cs.primary else cs.onSurface)
    }
}
