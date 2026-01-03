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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var initialUrl = ""
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            initialUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }

        setContent {
            var isDarkMode by remember { mutableStateOf(false) } // Default to Light Mode
            
            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    background = Color(0xFF1C1B1F),
                    onBackground = Color(0xFFE6E1E5),
                    surface = Color(0xFF1C1B1F),
                    onSurface = Color(0xFFE6E1E5)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    background = Color(0xFFFFFBFE),
                    onBackground = Color(0xFF1C1B1F),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color(0xFF1C1B1F)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(initialUrl, isDarkMode) { isDarkMode = it }
                }
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun MainApp(initialUrl: String, isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
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
                    HomeContent(initialUrl, snackbarHostState)
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
    fun HomeContent(initialUrl: String, snackbarHostState: SnackbarHostState) {
        var url by remember { mutableStateOf(initialUrl) }
        var isExtracting by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("") }
        var downloadProgress by remember { mutableStateOf(0f) }
        var downloadedBytes by remember { mutableStateOf(0L) }
        var totalBytes by remember { mutableStateOf(0L) }
        var downloadComplete by remember { mutableStateOf(false) }
        var mediaType by remember { mutableStateOf("") } // "Video" or "Audio"
        val scope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current
        
        // Animated progress for smooth UI
        val animatedProgress by animateFloatAsState(targetValue = downloadProgress, label = "progress")

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; downloadComplete = false },
                label = { Text("Paste Smule Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isExtracting && !isDownloading,
                trailingIcon = {
                    IconButton(
                        onClick = { clipboardManager.getText()?.let { url = it.text; downloadComplete = false } },
                        enabled = !isExtracting && !isDownloading
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Download Progress Card - Shows during extraction and download
            AnimatedVisibility(visible = isExtracting || isDownloading || downloadComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            downloadComplete -> MaterialTheme.colorScheme.primaryContainer
                            isDownloading -> MaterialTheme.colorScheme.secondaryContainer
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
                                downloadComplete -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                isDownloading -> Icon(
                                    if (mediaType == "Video") Icons.Default.VideoFile else Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
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
                                        downloadComplete -> "Download Complete!"
                                        isDownloading -> "Downloading $mediaType..."
                                        else -> statusText
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isDownloading && totalBytes > 0) {
                                    Text(
                                        text = "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (downloadComplete) {
                                    Text(
                                        text = "Check the Files tab to view",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isDownloading && totalBytes > 0) {
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Progress bar during download
                        if (isDownloading) {
                            Spacer(Modifier.height(12.dp))
                            if (totalBytes > 0) {
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
                            if (totalBytes == 0L) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Downloading... (size unknown)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (url.isNotBlank() && !isExtracting && !isDownloading) {
                        keyboardController?.hide()
                        isExtracting = true
                        isDownloading = false
                        downloadComplete = false
                        downloadProgress = 0f
                        downloadedBytes = 0L
                        totalBytes = 0L
                        statusText = "Connecting to Smule..."
                        scope.launch {
                            statusText = "Extracting media info..."
                            val media = fetchMediaInfo(url)
                            if (media != null) {
                                isExtracting = false
                                isDownloading = true
                                mediaType = if (media.isVideo) "Video" else "Audio"
                                statusText = "Starting download..."
                                
                                // Download with progress tracking
                                val success = downloadWithProgress(
                                    context = context,
                                    media = media,
                                    onProgress = { downloaded, total ->
                                        downloadedBytes = downloaded
                                        totalBytes = total
                                        if (total > 0) {
                                            downloadProgress = downloaded.toFloat() / total.toFloat()
                                        }
                                    }
                                )
                                
                                isDownloading = false
                                if (success) {
                                    downloadComplete = true
                                    url = "" // Clear the URL after successful download
                                } else {
                                    snackbarHostState.showSnackbar("Download failed. Please try again.")
                                }
                            } else {
                                isExtracting = false
                                snackbarHostState.showSnackbar("Failed to extract media. Please check the link.")
                            }
                        }
                    }
                },
                enabled = !isExtracting && !isDownloading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isExtracting || isDownloading) {
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
    
    /**
     * Fetch page HTML using WebView to bypass Cloudflare fingerprinting
     */
    private suspend fun fetchPageWithWebView(url: String): String = withContext(Dispatchers.Main) {
        var html = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        
        val webView = WebView(this@MainActivity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = USER_AGENT
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                        html = result?.trim('"')?.replace("\\u003C", "<")?.replace("\\n", "\n")?.replace("\\\"", "\"")?.replace("\\/", "/") ?: ""
                        android.util.Log.d("SmuleRodDebug", "WebView HTML length: ${html.length}")
                        latch.countDown()
                    }
                }
            }
        }
        
        android.util.Log.d("SmuleRodDebug", "Loading URL in WebView: $url")
        webView.loadUrl(url)
        
        // Wait up to 30 seconds for page to load
        withContext(Dispatchers.IO) {
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        }
        
        webView.destroy()
        html
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
            
            // Test DNS resolution
            try {
                val addresses = java.net.InetAddress.getAllByName("www.smule.com")
                android.util.Log.d("SmuleRodDebug", "DNS Resolution for www.smule.com: ${addresses.joinToString { it.hostAddress ?: "" }}")
            } catch (e: Exception) {
                android.util.Log.e("SmuleRodDebug", "DNS Resolution failed: ${e.message}")
            }

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
            
            val baseHost = if (uri.host?.contains("smule.com") == true) uri.host else "www.smule.com"
            val finalBaseUrl = "https://$baseHost$path"
            
            android.util.Log.d("SmuleRodDebug", "Final Base URL: $finalBaseUrl")
            
            // Use WebView to fetch the page (bypasses Cloudflare TLS/HTTP2 fingerprinting)
            android.util.Log.d("SmuleRodDebug", "Fetching page via WebView")
            val mainHtml = fetchPageWithWebView(finalBaseUrl)
            
            if (mainHtml.isEmpty()) {
                android.util.Log.e("SmuleRodDebug", "Failed to fetch page via WebView")
                return@withContext null
            }
            
            android.util.Log.d("SmuleRodDebug", "WebView fetch complete, HTML length: ${mainHtml.length}")
            
            val mainDoc = Jsoup.parse(mainHtml)
            val title = mainDoc.select("meta[property=og:title]").attr("content").ifBlank { "Smule_Recording" }
            android.util.Log.d("SmuleRodDebug", "Title: $title")
            
            // Detect performance type from the JSON
            val typeMatch = Regex("\"type\":\"([^\"]+)\"").find(mainHtml)
            val performanceType = typeMatch?.groupValues?.get(1) ?: "unknown"
            // "video" and "visualizer" both have video content
            val isVideoPerformance = performanceType == "video" || performanceType == "visualizer"
            android.util.Log.d("SmuleRodDebug", "Performance type: $performanceType, isVideo: $isVideoPerformance")
            
            // Extract all potential media URLs upfront
            val mp4UrlMatch = Regex("\"video_media_mp4_url\":\"([^\"]+)\"").find(mainHtml)
            val videoUrlMatch = Regex("\"video_media_url\":\"([^\"]+)\"").find(mainHtml)
            val visualizerUrlMatch = Regex("\"visualizer_media_url\":\"([^\"]+)\"").find(mainHtml)
            val mediaUrlMatch = Regex("\"media_url\":\"([^\"]+)\"").find(mainHtml)
            
            val encryptedMp4 = mp4UrlMatch?.groupValues?.get(1) ?: ""
            val encryptedVideo = videoUrlMatch?.groupValues?.get(1) ?: ""
            val encryptedVisualizer = visualizerUrlMatch?.groupValues?.get(1) ?: ""
            val encryptedMedia = mediaUrlMatch?.groupValues?.get(1) ?: ""
            
            android.util.Log.d("SmuleRodDebug", "Found URLs - MP4: ${encryptedMp4.take(30)}, Video: ${encryptedVideo.take(30)}, Visualizer: ${encryptedVisualizer.take(30)}, Media: ${encryptedMedia.take(30)}")
            
            // Priority order: video_media_mp4_url > visualizer_media_url > video_media_url > media_url (video) > media_url (audio)
            
            // Try video_media_mp4_url first (best quality for regular videos)
            if (encryptedMp4.isNotBlank()) {
                val resolvedUrl = resolveSmuleUrl(encryptedMp4, finalBaseUrl)
                if (resolvedUrl != null) {
                    android.util.Log.d("SmuleRodDebug", "Returning MP4 video: $resolvedUrl")
                    return@withContext SmuleMedia(title, resolvedUrl, isVideo = true)
                }
            }
            
            // Try visualizer_media_url (for visualizer/text animation videos)
            if (encryptedVisualizer.isNotBlank()) {
                val resolvedUrl = resolveSmuleUrl(encryptedVisualizer, finalBaseUrl)
                if (resolvedUrl != null) {
                    android.util.Log.d("SmuleRodDebug", "Returning visualizer video: $resolvedUrl")
                    return@withContext SmuleMedia(title, resolvedUrl, isVideo = true)
                }
            }
            
            // Try video_media_url
            if (encryptedVideo.isNotBlank()) {
                val resolvedUrl = resolveSmuleUrl(encryptedVideo, finalBaseUrl)
                if (resolvedUrl != null) {
                    android.util.Log.d("SmuleRodDebug", "Returning video_media: $resolvedUrl")
                    return@withContext SmuleMedia(title, resolvedUrl, isVideo = true)
                }
            }
            
            // Fallback to media_url - mark as video if performance type is video/visualizer
            if (encryptedMedia.isNotBlank()) {
                val resolvedUrl = resolveSmuleUrl(encryptedMedia, finalBaseUrl)
                if (resolvedUrl != null) {
                    if (isVideoPerformance) {
                        android.util.Log.w("SmuleRodDebug", "Falling back to media_url for video performance: $resolvedUrl")
                        return@withContext SmuleMedia(title, resolvedUrl, isVideo = true)
                    } else {
                        android.util.Log.d("SmuleRodDebug", "Returning audio: $resolvedUrl")
                        return@withContext SmuleMedia(title, resolvedUrl, isVideo = false)
                    }
                }
            }
            
            android.util.Log.e("SmuleRodDebug", "No media URL could be resolved")
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
            val fileName = "${media.title.replace(Regex("[^a-zA-Z0-9-_ ]"), "_")}.$ext"
            
            android.util.Log.d("SmuleRodDebug", "Downloading: $fileName from ${media.url}")
            
            // Use downloadClient which follows redirects for CDN URLs
            val request = Request.Builder()
                .url(media.url)
                .header("User-Agent", USER_AGENT)
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
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
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
        
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%Smule%'"
        
        context.contentResolver.query(collection, projection, selection, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)
                // Filter for files we likely downloaded
                if (name.contains("Smule", ignoreCase = true)) {
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
        
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230805.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"
    }
}


