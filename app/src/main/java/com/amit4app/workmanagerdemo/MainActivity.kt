package com.amit4app.workmanagerdemo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import kotlinx.coroutines.flow.flowOf

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*

private class Prefs(ctx: android.content.Context) {
    private val sp = ctx.getSharedPreferences("upload_prefs", android.content.Context.MODE_PRIVATE)
    fun saveLastUri(s: String) = sp.edit().putString("last_uri", s).apply()
    fun getLastUri(): String? = sp.getString("last_uri", null)
    fun clearLastUri() = sp.edit().remove("last_uri").apply()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ notifications (for foreground worker notification)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        setContent {
            MaterialTheme { UploadScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen() {
    val ctx = LocalContext.current
    val wm = remember { WorkManager.getInstance(ctx) }
    val prefs = remember { Prefs(ctx) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var progress by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf(WorkInfo.State.ENQUEUED) }
    var uniqueName by remember { mutableStateOf<String?>(null) }

    // ---- Restore last URI on cold start
    LaunchedEffect(Unit) {
        prefs.getLastUri()?.let { saved ->
            val uri = Uri.parse(saved)
            val stillGranted = ctx.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (stillGranted) {
                pickedUri = uri
                uniqueName = "upload_$saved"
            } else {
                prefs.clearLastUri()
            }
        }
    }

    // SAF picker
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pickedUri = it
            val s = it.toString()
            prefs.saveLastUri(s)
            uniqueName = "upload_$s"
        }
    }

    // ---- Sticky header + pretty content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WorkManager Upload Demo") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pick File
                Button(
                    onClick = { pickVideo.launch(arrayOf("video/*")) },
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (pickedUri == null) "Pick Video" else "Pick Another Video")
                }

                // File info + status
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pickedUri?.let { it.toString().substringAfterLast('/') }
                                ?: "No file selected",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusChip(state)
                            Text("$progress%", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Progress — circular + linear
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = progress / 100f,
                        strokeWidth = 8.dp,
                        modifier = Modifier.size(110.dp)
                    )
                    Text(
                        "$progress%",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(20.dp))
                )

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        enabled = pickedUri != null,
                        onClick = {
                            val uriStr = pickedUri!!.toString()
                            val input = Data.Builder()
                                .putString(FileUploadWorker.KEY_FILE_URI, uriStr)
                                .putString(FileUploadWorker.KEY_UPLOAD_ID, uriStr)
                                .build()

                            val req = OneTimeWorkRequestBuilder<FileUploadWorker>()
                                .setInputData(input)
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                        .build()
                                )
                                .build()

                            uniqueName = "upload_$uriStr"
                            wm.enqueueUniqueWork(uniqueName!!, ExistingWorkPolicy.KEEP, req)
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start / Resume")
                    }

                    OutlinedButton(
                        enabled = uniqueName != null,
                        onClick = { uniqueName?.let { wm.cancelUniqueWork(it) } }
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }

                // Tiny tip
                AssistChip(
                    onClick = {},
                    label = { Text("Kill app / reboot → relaunch to see resume") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }

    // ---- Observe by unique name (reconnect after relaunch)
    val workInfos: List<WorkInfo>? by remember(uniqueName) {
        uniqueName?.let { name ->
            wm.getWorkInfosForUniqueWorkFlow(name)
        } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(workInfos) {
        val info = workInfos?.firstOrNull()
        if (info != null) {
            progress = info.progress.getInt(FileUploadWorker.PROGRESS, progress)
            state = info.state
            info.outputData.getString("error")?.let {
                android.util.Log.e("WM", "Worker error: $it")
            }
        } else {
            if (pickedUri != null) {
                progress = 0
                state = WorkInfo.State.ENQUEUED
            }
        }
    }
}

// status chip
@Composable
private fun StatusChip(state: WorkInfo.State) {
    val (label, container, content) = when (state) {
        WorkInfo.State.ENQUEUED -> Triple("Enqueued",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer)
        WorkInfo.State.RUNNING -> Triple("Running",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer)
        WorkInfo.State.SUCCEEDED -> Triple("Completed",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer)
        WorkInfo.State.FAILED -> Triple("Failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer)
        WorkInfo.State.CANCELLED -> Triple("Cancelled",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant)
        WorkInfo.State.BLOCKED -> Triple("Blocked",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
