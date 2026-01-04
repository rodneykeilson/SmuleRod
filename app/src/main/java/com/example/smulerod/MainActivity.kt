package com.example.smulerod

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerControlView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream

data class SmuleMedia(val title: String, val url: String, val isVideo: Boolean)
data class DownloadedFile(val id: Long, val name: String, val uri: Uri, val size: String, val date: Long)

// Download state that survives tab switches
data class DownloadState(
    val isExtracting: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadComplete: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val mediaType: String = "",
    val statusText: String = ""
)

class MainActivity : ComponentActivity() {
    private var urlState = mutableStateOf("")
    private var downloadState = mutableStateOf(DownloadState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        setContent {
            var isDarkMode by remember { mutableStateOf(false) } // Default to Light Mode
            
            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    primary = Color(0xFF7CB8FF),       // Soft blue - high contrast on dark
                    onPrimary = Color(0xFF00315C),
                    primaryContainer = Color(0xFF1A4A73),
                    onPrimaryContainer = Color(0xFFD4E7FF),
                    secondary = Color(0xFF8FD68A),     // Soft green for success
                    onSecondary = Color(0xFF003A00),
                    secondaryContainer = Color(0xFF285224),
                    onSecondaryContainer = Color(0xFFB5F2AF),
                    background = Color(0xFF121619),   // Deep blue-gray
                    onBackground = Color(0xFFE4E4E7),
                    surface = Color(0xFF1A1E22),
                    onSurface = Color(0xFFE4E4E7),
                    surfaceVariant = Color(0xFF252A30),
                    onSurfaceVariant = Color(0xFFC2C6CD)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0061A6),       // Strong blue - WCAG AAA
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFD4E7FF),
                    onPrimaryContainer = Color(0xFF001C38),
                    secondary = Color(0xFF2E6B29),     // Forest green
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFD4F5D0),
                    onSecondaryContainer = Color(0xFF002200),
                    background = Color(0xFFFCFCFF),   // Clean white with slight blue tint
                    onBackground = Color(0xFF1A1C1E),
                    surface = Color(0xFFFCFCFF),
                    onSurface = Color(0xFF1A1C1E),
                    surfaceVariant = Color(0xFFE8ECF2),
                    onSurfaceVariant = Color(0xFF42474E)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(
                        initialUrl = urlState.value,
                        isDarkMode = isDarkMode,
                        onThemeToggle = { isDarkMode = it },
                        downloadState = downloadState.value,
                        onStartDownload = { url -> startDownload(url) },
                        onClearUrl = { urlState.value = "" }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            urlState.value = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else if (intent?.action == Intent.ACTION_VIEW) {
            urlState.value = intent.dataString ?: ""
        }
    }

    private fun startDownload(url: String) {
        if (downloadState.value.isExtracting || downloadState.value.isDownloading) return
        
        lifecycleScope.launch {
            downloadState.value = DownloadState(
                isExtracting = true,
                statusText = "Extracting media info..."
            )
            
            val media = fetchMediaInfo(url)
            if (media != null) {
                downloadState.value = downloadState.value.copy(
                    isExtracting = false,
                    isDownloading = true,
                    mediaType = if (media.isVideo) "Video" else "Audio",
                    statusText = "Downloading..."
                )
                
                val success = downloadWithProgress(
                    context = this@MainActivity,
                    media = media,
                    onProgress = { downloaded, total ->
                        downloadState.value = downloadState.value.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            downloadProgress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                        )
                    }
                )
                
                if (success) {
                    downloadState.value = DownloadState(downloadComplete = true)
                    urlState.value = ""
                } else {
                    downloadState.value = DownloadState()
                }
            } else {
                downloadState.value = DownloadState()
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun MainApp(
        initialUrl: String,
        isDarkMode: Boolean,
        onThemeToggle: (Boolean) -> Unit,
        downloadState: DownloadState,
        onStartDownload: (String) -> Unit,
        onClearUrl: () -> Unit
    ) {
        var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Files
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var isMultiSelectMode by remember { mutableStateOf(false) }
        val selectedFiles = remember { mutableStateListOf<Long>() }
        var files by remember { mutableStateOf(listOf<DownloadedFile>()) }
        val context = LocalContext.current
        
        var playingFile by remember { mutableStateOf<DownloadedFile?>(null) }

        if (isMultiSelectMode) {
            BackHandler {
                isMultiSelectMode = false
                selectedFiles.clear()
            }
        }

        // Load files whenever we switch to Files tab
        LaunchedEffect(currentTab) {
            if (currentTab == 1) {
                files = loadDownloadedFiles(context)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text("SmuleRod", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) 
                    },
                    navigationIcon = {
                        if (isMultiSelectMode) {
                            IconButton(onClick = { 
                                isMultiSelectMode = false
                                selectedFiles.clear()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Multi-select")
                            }
                        }
                    },
                    actions = {
                        if (currentTab == 1) {
                            if (!isMultiSelectMode) {
                                IconButton(onClick = { isMultiSelectMode = true }) {
                                    Icon(Icons.Default.Checklist, contentDescription = "Enter Multi-select")
                                }
                            } else {
                                IconButton(onClick = {
                                    if (selectedFiles.size == files.size) {
                                        selectedFiles.clear()
                                    } else {
                                        selectedFiles.clear()
                                        selectedFiles.addAll(files.map { it.id })
                                    }
                                }) {
                                    Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                                }
                            }
                        }
                        IconButton(onClick = { onThemeToggle(!isDarkMode) }) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0; isMultiSelectMode = false; selectedFiles.clear() },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("Files") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (currentTab == 0) {
                    HomeContent(
                        initialUrl = initialUrl,
                        snackbarHostState = snackbarHostState,
                        downloadState = downloadState,
                        onStartDownload = onStartDownload,
                        onClearUrl = onClearUrl
                    )
                } else {
                    FilesContent(
                        files = files,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedFiles = selectedFiles,
                        snackbarHostState = snackbarHostState,
                        onExitMultiSelect = { isMultiSelectMode = false; selectedFiles.clear() },
                        onRefresh = { 
                            scope.launch { files = loadDownloadedFiles(context) }
                        },
                        onPlay = { playingFile = it }
                    )
                }

                // Snackbar at the top
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }
        }

        playingFile?.let { file ->
            MediaPlayerDialog(file = file, onDismiss = { playingFile = null })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun HomeContent(
        initialUrl: String,
        snackbarHostState: SnackbarHostState,
        downloadState: DownloadState,
        onStartDownload: (String) -> Unit,
        onClearUrl: () -> Unit
    ) {
        var url by remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current
        val clipboardManager = LocalClipboardManager.current
        
        // Animated progress for smooth UI
        val animatedProgress by animateFloatAsState(targetValue = downloadState.downloadProgress, label = "progress")

        // Sync URL from intent
        LaunchedEffect(initialUrl) {
            if (initialUrl.isNotBlank() && url != initialUrl) {
                url = initialUrl
                onStartDownload(initialUrl)
            }
        }
        
        // Clear URL on download complete
        LaunchedEffect(downloadState.downloadComplete) {
            if (downloadState.downloadComplete) {
                url = ""
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Paste Smule Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !downloadState.isExtracting && !downloadState.isDownloading,
                trailingIcon = {
                    IconButton(
                        onClick = { clipboardManager.getText()?.let { url = it.text } },
                        enabled = !downloadState.isExtracting && !downloadState.isDownloading
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Download Progress Card - Shows during extraction and download
            AnimatedVisibility(visible = downloadState.isExtracting || downloadState.isDownloading || downloadState.downloadComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            downloadState.downloadComplete -> MaterialTheme.colorScheme.secondaryContainer
                            downloadState.isDownloading -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                downloadState.downloadComplete -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                downloadState.isDownloading -> Icon(
                                    if (downloadState.mediaType == "Video") Icons.Default.VideoFile else Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                else -> CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        downloadState.downloadComplete -> "Download Complete!"
                                        downloadState.isDownloading -> "Downloading ${downloadState.mediaType}..."
                                        else -> downloadState.statusText
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (downloadState.isDownloading && downloadState.totalBytes > 0) {
                                    Text(
                                        text = "${formatSize(downloadState.downloadedBytes)} / ${formatSize(downloadState.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (downloadState.downloadComplete) {
                                    Text(
                                        text = "Check the Files tab to view",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (downloadState.isDownloading && downloadState.totalBytes > 0) {
                                Text(
                                    text = "${(downloadState.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Progress bar during download
                        if (downloadState.isDownloading) {
                            Spacer(Modifier.height(12.dp))
                            if (downloadState.totalBytes > 0) {
                                @Suppress("DEPRECATION")
                                LinearProgressIndicator(
                                    progress = animatedProgress,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (url.isNotBlank() && !downloadState.isExtracting && !downloadState.isDownloading) {
                        keyboardController?.hide()
                        onStartDownload(url)
                    }
                },
                enabled = !downloadState.isExtracting && !downloadState.isDownloading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (downloadState.isExtracting || downloadState.isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), 
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Media")
                }
            }
        }
    }

    @Composable
    fun FilesContent(
        files: List<DownloadedFile>,
        isMultiSelectMode: Boolean,
        selectedFiles: MutableList<Long>,
        snackbarHostState: SnackbarHostState,
        onExitMultiSelect: () -> Unit,
        onRefresh: () -> Unit,
        onPlay: (DownloadedFile) -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var fileToDelete by remember { mutableStateOf<DownloadedFile?>(null) }
        var showBulkDeleteConfirm by remember { mutableStateOf(false) }
        
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onRefresh()
            }
        }

        LaunchedEffect(Unit) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                onRefresh()
            } else {
                permissionLauncher.launch(permission)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isMultiSelectMode && selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedFiles.size} selected", modifier = Modifier.padding(start = 8.dp))
                    Row {
                        IconButton(onClick = { showBulkDeleteConfirm = true }) { 
                            Icon(Icons.Default.Delete, "Delete Selected") 
                        }
                        IconButton(onClick = { 
                            shareMultipleFiles(context, files.filter { it.id in selectedFiles })
                        }) { Icon(Icons.Default.Share, "Share Selected") }
                    }
                }
            }

            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloads found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(files) { file ->
                        FileItem(
                            file = file,
                            isSelected = file.id in selectedFiles,
                            isMultiSelectMode = isMultiSelectMode,
                            onToggleSelect = {
                                if (file.id in selectedFiles) selectedFiles.remove(file.id)
                                else selectedFiles.add(file.id)
                            },
                            onPlay = { onPlay(file) },
                            onShare = { shareFile(context, file) },
                            onDelete = { fileToDelete = file }
                        )
                    }
                }
            }
        }

        fileToDelete?.let { file ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text("Delete File") },
                text = { Text("Are you sure you want to delete '${file.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            deleteFile(context, file)
                            onRefresh()
                            snackbarHostState.showSnackbar("Deleted ${file.name}")
                        }
                        fileToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                }
            )
        }

        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text("Delete Multiple Files") },
                text = { Text("Are you sure you want to delete ${selectedFiles.size} files?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            deleteMultipleFiles(context, selectedFiles)
                            onRefresh()
                            onExitMultiSelect()
                            snackbarHostState.showSnackbar("Deleted ${selectedFiles.size} files")
                        }
                        showBulkDeleteConfirm = false
                    }) { Text("Delete All", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }

    @Composable
    fun FileItem(
        file: DownloadedFile,
        isSelected: Boolean,
        isMultiSelectMode: Boolean,
        onToggleSelect: () -> Unit,
        onPlay: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        var showMenu by remember { mutableStateOf(false) }
        val context = LocalContext.current

        ListItem(
            modifier = Modifier.clickable { if (isMultiSelectMode) onToggleSelect() else onPlay() },
            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("${file.size} • ${file.name.substringAfterLast(".", "").uppercase()}") },
            leadingContent = {
                if (isMultiSelectMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (file.name.endsWith(".mp4")) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(file.uri)
                                    .videoFrameMillis(1000)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            trailingContent = {
                if (!isMultiSelectMode) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { showMenu = false; onShare() },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { Icon(Icons.Default.Delete, null) }
                            )
                        }
                    }
                }
            }
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    fun MediaPlayerDialog(file: DownloadedFile, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val isVideo = file.name.endsWith(".mp4")
        
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(file.uri))
                prepare()
                playWhenReady = true
            }
        }

        var isPlaying by remember { mutableStateOf(true) }
        var currentPosition by remember { mutableStateOf(0L) }
        var duration by remember { mutableStateOf(0L) }

        DisposableEffect(Unit) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration = exoPlayer.duration
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose { 
                exoPlayer.removeListener(listener)
                exoPlayer.release() 
            }
        }

        // Update progress
        LaunchedEffect(isPlaying) {
            while (isActive && isPlaying) {
                currentPosition = exoPlayer.currentPosition
                delay(500)
            }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isVideo) Color.Black else MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isVideo) {
                        AndroidView(
                            factory = {
                                PlayerView(it).apply {
                                    player = exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Modern Audio Player UI (Spotify-style)
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Album Art
                            Card(
                                modifier = Modifier.size(280.dp),
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(140.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            // Title & Artist
                            Text(
                                text = file.name.substringBeforeLast("."),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = "Smule Recording",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            // Progress Bar
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                    onValueChange = { 
                                        if (duration > 0) {
                                            exoPlayer.seekTo((it * duration).toLong())
                                            currentPosition = exoPlayer.currentPosition
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(currentPosition),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatTime(duration),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Playback Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { exoPlayer.seekTo(currentPosition - 10000) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Replay10, null, modifier = Modifier.size(32.dp))
                                }
                                
                                FilledIconButton(
                                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = Modifier.size(80.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = { exoPlayer.seekTo(currentPosition + 10000) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Forward10, null, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Close", 
                            tint = if (isVideo) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    private class SmuleSpider(private val onFound: (String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun OnVideoFound(json: String) {
            onFound(json)
        }
    }

    /**
     * Fetch page HTML using WebView to bypass Cloudflare fingerprinting
     */
    private suspend fun extractMediaWithWebView(url: String): String? = withContext(Dispatchers.Main) {
        var resultJson: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        
        // Clear cookies to ensure a fresh session
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        
        val spider = SmuleSpider { json ->
            resultJson = json
            latch.countDown()
        }
        
        val webView = WebView(this@MainActivity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            // Use the specific User-Agent from the successful app
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Mobile Safari/537.36UCBrowser/13.3.5.1304"
            
            addJavascriptInterface(spider, "phoenix")
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("SmuleRodDebug", "WebView Console: ${consoleMessage?.message()}")
                    return true
                }
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    android.util.Log.d("SmuleRodDebug", "WebView onPageFinished: $url")
                    
                    val script = """
                        var phxVideoIsFound=false;
                        var phxVideoSpiderTicks=0;
                        var phxVideoSpiderTickCount=50;
                        console.log('Starting Smule Spider...');
                        smuleVideoSpider();
                        function smuleVideoSpider(){
                            try{
                                if(phxVideoIsFound) return;
                                phxVideoSpiderTicks++;
                                var data={};
                                
                                // Try to find metadata in DataStore
                                var recording = null;
                                if(window.DataStore && window.DataStore.Pages) {
                                    var pages = window.DataStore.Pages;
                                    recording = pages.Recording || pages.Listen || pages.RecordingPlayer || pages.Song;
                                }
                                
                                if(recording && recording.performance) {
                                    var json = recording.performance;
                                    data.title = json.title || document.title || 'Recording';
                                    data.cover_url = json.cover_url;
                                    data.handle = (json.owner && json.owner.handle) || json.performed_by || '';
                                } else {
                                    data.title = document.title || 'Recording';
                                }

                                // Find media element
                                var video = document.querySelector('video');
                                var audio = document.querySelector('audio');
                                var mediaUrl = null;
                                
                                if(video && video.src && video.src.indexOf('http') === 0) {
                                    mediaUrl = video.src;
                                } else if(audio && audio.src && audio.src.indexOf('http') === 0) {
                                    mediaUrl = audio.src;
                                }
                                
                                if(mediaUrl) {
                                    console.log('Media found! URL: ' + mediaUrl);
                                    data.url = mediaUrl;
                                    phxVideoIsFound = true;
                                    phoenix.OnVideoFound(JSON.stringify(data));
                                } else if(phxVideoSpiderTicks % 10 === 0) {
                                    console.log('Still searching... (Tick ' + phxVideoSpiderTicks + ')');
                                }
                            } catch(e) {
                                console.log('Spider Error: ' + e);
                            }
                            
                            if(!phxVideoIsFound) {
                                if(phxVideoSpiderTicks < phxVideoSpiderTickCount) {
                                    setTimeout(smuleVideoSpider, 300);
                                } else {
                                    console.log('Spider timed out after 15 seconds');
                                }
                            }
                        }
                    """.trimIndent()
                    view.evaluateJavascript(script, null)
                }
            }
        }
        
        android.util.Log.d("SmuleRodDebug", "Loading URL in WebView: $url")
        webView.loadUrl(url)
        
        withContext(Dispatchers.IO) {
            latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        val finalResult = resultJson
        webView.destroy()
        finalResult
    }

    private fun Request.Builder.addCommonHeaders(referer: String? = null): Request.Builder {
        header("User-Agent", USER_AGENT)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        header("Accept-Language", "en-US,en;q=0.9")
        header("Accept-Encoding", "gzip, deflate, br")
        header("sec-ch-ua", "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
        header("sec-ch-ua-mobile", "?1")
        header("sec-ch-ua-platform", "\"Android\"")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", if (referer != null) "same-origin" else "none")
        header("Sec-Fetch-User", "?1")
        header("Upgrade-Insecure-Requests", "1")
        if (referer != null) {
            header("Referer", referer)
        }
        return this
    }
    
    /**
     * Resolves an encrypted Smule URL via the /redir endpoint.
     * Returns the final CDN URL or null if resolution fails.
     */
    private fun resolveSmuleUrl(encryptedUrl: String, referer: String): String? {
        if (encryptedUrl.isBlank()) return null
        
        val encodedUrl = java.net.URLEncoder.encode(encryptedUrl, "UTF-8")
        val timestamp = System.currentTimeMillis() / 1000
        val redirUrl = "https://www.smule.com/redir?e=1&t=$timestamp.12345&url=$encodedUrl"
        
        android.util.Log.d("SmuleRodDebug", "Resolving redir URL: $redirUrl")
        
        val redirRequest = Request.Builder()
            .url(redirUrl)
            .addCommonHeaders(referer)
            .build()
        
        return try {
            val redirResponse = client.newCall(redirRequest).execute()
            val statusCode = redirResponse.code
            android.util.Log.d("SmuleRodDebug", "Redir response code: $statusCode")
            
            when (statusCode) {
                302, 301, 303, 307, 308 -> {
                    // Get the Location header for the redirect
                    val location = redirResponse.header("Location")
                    android.util.Log.d("SmuleRodDebug", "Redirect Location: $location")
                    
                    if (location != null && location.contains("cdn.smule.com")) {
                        location
                    } else {
                        android.util.Log.w("SmuleRodDebug", "Unexpected redirect location: $location")
                        null
                    }
                }
                418 -> {
                    android.util.Log.e("SmuleRodDebug", "Got 418 (blocked by Cloudflare)")
                    null
                }
                200 -> {
                    // Sometimes Smule returns 200 with the actual content or a different structure
                    val body = redirResponse.body?.string() ?: ""
                    android.util.Log.w("SmuleRodDebug", "Got 200, body length: ${body.length}")
                    null
                }
                else -> {
                    android.util.Log.w("SmuleRodDebug", "Unexpected status code: $statusCode")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmuleRodDebug", "Resolution error: ${e.message}")
            null
        }
    }

    private suspend fun fetchMediaInfo(smuleUrl: String): SmuleMedia? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SmuleRodDebug", "Starting extraction for: $smuleUrl")

            var cleanUrl = smuleUrl.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            
            val uri = Uri.parse(cleanUrl)
            val path = uri.path
            if (path.isNullOrBlank()) {
                android.util.Log.e("SmuleRodDebug", "Failed to parse path from: $cleanUrl")
                return@withContext null
            }
            
            // Extract performance key from URL path
            val performanceKey = path.split("/").lastOrNull { it.isNotBlank() }
            if (performanceKey.isNullOrBlank()) {
                android.util.Log.e("SmuleRodDebug", "Failed to extract performance key from: $path")
                return@withContext null
            }
            
            android.util.Log.d("SmuleRodDebug", "Performance key: $performanceKey")
            
            // Use the original clean URL - it has the full path which works better
            android.util.Log.d("SmuleRodDebug", "Loading WebView: $cleanUrl")
            
            val spiderJson = extractMediaWithWebView(cleanUrl)
            if (!spiderJson.isNullOrBlank()) {
                android.util.Log.d("SmuleRodDebug", "Spider found data: $spiderJson")
                
                // Parse the spider JSON
                val spiderTitle = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(spiderJson)?.groupValues?.get(1) ?: "Recording"
                val spiderUrl = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(spiderJson)?.groupValues?.get(1) ?: ""
                
                if (spiderUrl.isNotBlank()) {
                    val isVideo = spiderUrl.contains("mp4") || spiderUrl.contains("renvideo") || spiderUrl.contains("video")
                    android.util.Log.d("SmuleRodDebug", "Found media: $spiderUrl (isVideo: $isVideo)")
                    return@withContext SmuleMedia(spiderTitle, spiderUrl, isVideo = isVideo)
                }
            }
            
            android.util.Log.e("SmuleRodDebug", "Failed to extract media from WebView")
            return@withContext null
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("SmuleRodDebug", "DNS Error: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Network error: Could not resolve Smule. Check your connection.", Toast.LENGTH_LONG).show()
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("SmuleRodDebug", "Extraction error: ${e.message}", e)
            null
        }
    }


    private suspend fun downloadWithProgress(
        context: Context,
        media: SmuleMedia,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ext = if (media.isVideo) "mp4" else "m4a"
            val sanitizedTitle = media.title.replace(Regex("[^a-zA-Z0-9-_ ]"), "_").take(100)
            val fileName = "SmuleRod_${sanitizedTitle}.$ext"
            
            android.util.Log.d("SmuleRodDebug", "Downloading: $fileName from ${media.url}")
            
            // Get cookies from WebView's CookieManager to pass to the download request
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie(media.url)
            if (cookies != null) {
                android.util.Log.d("SmuleRodDebug", "Using cookies for download: ${cookies.take(20)}...")
            }

            // Use downloadClient which follows redirects for CDN URLs
            val request = Request.Builder()
                .url(media.url)
                .header("User-Agent", USER_AGENT)
                .apply {
                    if (cookies != null) {
                        header("Cookie", cookies)
                    }
                }
                .build()
            
            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("SmuleRodDebug", "Download failed: ${response.code}")
                return@withContext false
            }
            
            val contentType = response.header("Content-Type") ?: ""
            android.util.Log.d("SmuleRodDebug", "Content-Type: $contentType")
            
            // Validate content type matches expected media type
            val isVideoContent = contentType.contains("video") || media.url.contains("renvideo")
            if (media.isVideo && !isVideoContent && !contentType.contains("octet-stream")) {
                android.util.Log.w("SmuleRodDebug", "Expected video but got: $contentType")
            }
            
            val totalBytes = response.body?.contentLength() ?: -1L
            android.util.Log.d("SmuleRodDebug", "Total bytes: $totalBytes")
            
            // Validate we have a reasonable file size (at least 100KB for video)
            if (media.isVideo && totalBytes > 0 && totalBytes < 100_000) {
                android.util.Log.e("SmuleRodDebug", "File too small for video: $totalBytes bytes")
                return@withContext false
            }
            
            // Save to Downloads folder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (media.isVideo) "video/mp4" else "audio/m4a")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SmuleRod")
                }
                
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    android.util.Log.e("SmuleRodDebug", "Failed to create file in MediaStore")
                    return@withContext false
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    response.body?.byteStream()?.use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            withContext(Dispatchers.Main) {
                                onProgress(downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)
                            }
                        }
                    }
                }
            } else {
                // For older Android versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                FileOutputStream(file).use { outputStream ->
                    response.body?.byteStream()?.use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            withContext(Dispatchers.Main) {
                                onProgress(downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)
                            }
                        }
                    }
                }
            }
            
            android.util.Log.d("SmuleRodDebug", "Download complete: $fileName")
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("SmuleRodDebug", "Download error: ${e.message}", e)
            false
        }
    }

    private suspend fun loadDownloadedFiles(context: Context): List<DownloadedFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DownloadedFile>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'SmuleRod_%'"
        
        context.contentResolver.query(collection, projection, selection, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                // Filter for files we downloaded (prefixed with SmuleRod_)
                if (name.startsWith("SmuleRod_", ignoreCase = true)) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    list.add(DownloadedFile(id, name, contentUri, formatSize(size), date))
                }
            }
        }
        list
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun playFile(context: Context, file: DownloadedFile) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, if (file.name.endsWith(".mp4")) "video/*" else "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Play with"))
    }

    private fun shareFile(context: Context, file: DownloadedFile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".mp4")) "video/*" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun shareMultipleFiles(context: Context, files: List<DownloadedFile>) {
        val uris = ArrayList<Uri>().apply { files.forEach { add(it.uri) } }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share files"))
    }

    private suspend fun deleteFile(context: Context, file: DownloadedFile) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(file.uri, null, null)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun deleteMultipleFiles(context: Context, ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            try { context.contentResolver.delete(uri, null, null) } catch (e: Exception) {}
        }
    }

    companion object {
        // Shared cookie store across all requests - domain-aware
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false) // We'll handle redirects manually for redir endpoint
            .followSslRedirects(false)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    for (cookie in cookies) {
                        val domain = cookie.domain.removePrefix(".")
                        val key = domain
                        if (cookieStore[key] == null) {
                            cookieStore[key] = mutableListOf()
                        }
                        // Remove old cookie with same name, then add new one
                        cookieStore[key]!!.removeAll { it.name == cookie.name }
                        cookieStore[key]!!.add(cookie)
                        android.util.Log.d("SmuleRodDebug", "Saved cookie: ${cookie.name}=${cookie.value.take(20)}... for domain $domain")
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = mutableListOf<Cookie>()
                    // Match cookies for this host and parent domains
                    for ((domain, domainCookies) in cookieStore) {
                        if (url.host == domain || url.host.endsWith(".$domain")) {
                            cookies.addAll(domainCookies.filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } })
                        }
                    }
                    return cookies
                }
            })
            .build()
        
        // Separate client for following redirects (CDN downloads)
        private val downloadClient = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Mobile Safari/537.36UCBrowser/13.3.5.1304"
    }
}


