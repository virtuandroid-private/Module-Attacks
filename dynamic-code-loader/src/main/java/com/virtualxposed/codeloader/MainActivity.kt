package com.virtualxposed.codeloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.virtualxposed.codeloader.ui.theme.MaliciousModuleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        var isLoaded = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isLoaded = isLoaded
        this.cacheDir.deleteRecursively()
        this.cacheDir.mkdirs()

        setContent {
            MaliciousModuleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StartScreen(isLoaded, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun StartScreen(isLoaded: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Dynamic code loading app",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "This app demonstrates dynamic code loading using APK files. " +
                        "It can be loaded as a module, or by clicking the run buttons below. " +
                        "It also supports running an encrypted APK file. \n\n" +
                        "Module version ${BuildConfig.VERSION_NAME}\n\n" +
                        "Loaded status: $isLoaded",
                modifier = Modifier.padding(10.dp)
            )
        }

        ExecuteButton(
            "Run dynamic code from an APK"
        ) {
            val file = ExecuteHelper.getAssetApkFile(context, false) ?: return@ExecuteButton
            ExecuteHelper.executeAndroidLibrary(context, file)
        }

        ExecuteButton(
            "Run dynamic code from an encrypted APK"
        ) {
            val file = ExecuteHelper.getAssetApkFile(context, true) ?: return@ExecuteButton
            ExecuteHelper.executeAndroidLibrary(context, file)
        }

        ExecuteButton(
            "Run dynamic code from an SO file"
        ) {
            val file = ExecuteHelper.getAssetNativeFile(context) ?: return@ExecuteButton
            ExecuteHelper.executeAndroidNativeLibrary(context, file)
        }
    }
}

@Composable
fun ExecuteButton(
    text: String,
    action: suspend () -> Unit,
) {
    var isExecuting by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isExecuting = true
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    action.invoke()
                }.onFailure { t ->
                    t.printStackTrace()
                }
                isExecuting = false
            }
        },
        enabled = !isExecuting
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Cheat to make the button stay consistent in width
            Text(
                text = text,
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


@Preview(showBackground = true)
@Composable
fun StartScreenPreview() {
    MaliciousModuleTheme {
        StartScreen(false)
    }
}