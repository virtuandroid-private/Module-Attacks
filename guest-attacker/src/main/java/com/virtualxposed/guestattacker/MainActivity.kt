package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.virtualxposed.guestattacker.ui.theme.AttacksTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.virtualxposed.guestattacker.StorageBypass.HOST_APP
import com.virtualxposed.guestattacker.StorageBypass.victimFile
import com.virtualxposed.guestattacker.Utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("guestattacker")
        }
    }

    @SuppressLint("SdCardPath")
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
                            CoroutineScope(Dispatchers.IO).launch {
                                Interference.killVictim(this@MainActivity)
                            }
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
                                return@DemoAction
                            }

                            val result =
                                Native.ptraceOpen(hostApp.pid, victimFile.absolutePath, 1000)
                            toast(
                                this,
                                "Private file results: $result",
                            )
                        },
                        // TODO Find context abuse
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
    SharedProcess("Shared process vulnerabilities")
}

data class DemoAction(
    val title: String,
    val description: String,
    val category: DemoCategory,
    val onExecute: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(actions: List<DemoAction>) {
    // Group actions by category name
    val groupedActions = remember(actions) {
        actions.groupBy { it.category }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Guest Attacks Demo", style = MaterialTheme.typography.headlineMedium
                    )
                })
        }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groupedActions.forEach { (categoryName, categoryActions) ->
                // Sticky header for each category section
                stickyHeader(key = categoryName) {
                    CategoryHeader(title = categoryName.categoryName)
                }

                items(
                    items = categoryActions, key = { "${it.category}_${it.title}" }) { action ->
                    DemoRow(action = action)
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    title: String, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background
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
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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


            Button(onClick = action.onExecute) {
                Text(text = "Execute")
            }
        }
    }
}