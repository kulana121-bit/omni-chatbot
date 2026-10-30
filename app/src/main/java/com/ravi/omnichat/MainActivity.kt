package com.ravi.omnichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import com.ravi.omnichat.ui.LocalNavAnimScope
import com.ravi.omnichat.ui.LocalSharedScope
import com.ravi.omnichat.ui.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.ravi.omnichat.ui.ChatScreen
import com.ravi.omnichat.ui.SettingsScreen
import com.ravi.omnichat.ui.theme.OmniTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Explicit icon + label for the Recents screen (some launchers skip adaptive icons there).
        runCatching {
            @Suppress("DEPRECATION")
            setTaskDescription(
                if (android.os.Build.VERSION.SDK_INT >= 28)
                    android.app.ActivityManager.TaskDescription(getString(R.string.app_name), R.mipmap.ic_launcher, 0xFF8FA4E8.toInt())
                else android.app.ActivityManager.TaskDescription(getString(R.string.app_name),
                    android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher), 0xFF8FA4E8.toInt())
            )
        }
        setContent {
            OmniTheme(dynamic = vm.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by rememberSaveable { mutableIntStateOf(0) } // 0 chat, 1 settings, 2 apps & skills
                    CrashReportDialog()
                    BackHandler(enabled = screen != 0) { screen = 0 }
                    run {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                if (targetState != 0)
                                    (slideInHorizontally(Motion.offset) { it / 4 } + fadeIn(tween(260, 60)) + scaleIn(Motion.float, 0.96f)) togetherWith
                                        (slideOutHorizontally(Motion.offset) { -it / 6 } + fadeOut(tween(180)) + scaleOut(Motion.float, 0.96f))
                                else
                                    (slideInHorizontally(Motion.offset) { -it / 6 } + fadeIn(tween(260, 60)) + scaleIn(Motion.float, 0.96f)) togetherWith
                                        (slideOutHorizontally(Motion.offset) { it / 4 } + fadeOut(tween(180)) + scaleOut(Motion.float, 0.96f))
                            },
                            label = "nav"
                        ) { sc ->
                            CompositionLocalProvider(LocalNavAnimScope provides this) {
                                when (sc) {
                                    1 -> SettingsScreen(vm) { screen = 0 }
                                    2 -> com.ravi.omnichat.ui.AppsSkillsScreen(vm) { screen = 0 }
                                    else -> ChatScreen(vm, openApps = { screen = 2 }) { screen = 1 }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CrashReportDialog() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val file = java.io.File(ctx.filesDir, OmniApp.CRASH_FILE)
    var dismissed by androidx.compose.runtime.remember { mutableStateOf(false) }
    val report by androidx.compose.runtime.produceState<String?>(initialValue = null) {
        if (file.exists()) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
        }
    }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val r = report
    if (r == null || dismissed) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { file.delete(); dismissed = true },
        title = { androidx.compose.material3.Text("App crashed last time") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("Sorry! Copy this report and share it so it can be fixed.")
                androidx.compose.material3.Text(
                    r.take(3000),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).heightIn(max = 260.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(r)); file.delete(); dismissed = true }) {
                androidx.compose.material3.Text("Copy report")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { file.delete(); dismissed = true }) { androidx.compose.material3.Text("Close") }
        }
    )

}
