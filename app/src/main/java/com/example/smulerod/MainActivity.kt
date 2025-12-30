package com.example.smulerod

import android.Manifest
import android.app.DownloadManager
import android.content.ContentUris
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File

data class SmuleMedia(val title: String, val url: String)
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
            VideoPlayerDialog(file = file, onDismiss = { playingFile = null })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    fun HomeContent(initialUrl: String, snackbarHostState: SnackbarHostState) {
        var url by remember { mutableStateOf(initialUrl) }
        var isLoading by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        val clipboardManager = LocalClipboardManager.current

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
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.getText()?.let { url = it.text }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        keyboardController?.hide()
                        isLoading = true
                        scope.launch {
                            val media = fetchMediaInfo(url)
                            isLoading = false
                            if (media != null) {
                                startDownload(media)
                                snackbarHostState.showSnackbar("Downloading: ${media.title}")
                            } else {
                                snackbarHostState.showSnackbar("Failed to extract media")
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
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
    fun VideoPlayerDialog(file: DownloadedFile, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(file.uri))
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(Unit) {
            onDispose { exoPlayer.release() }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    private suspend fun fetchMediaInfo(smuleUrl: String): SmuleMedia? = withContext(Dispatchers.IO) {
        try {
            var cleanUrl = smuleUrl.trim()
            // Handle cases where user pastes "smule.com/..." or "www.smule.com/..."
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            
            // Remove query parameters but keep the path
            val uri = Uri.parse(cleanUrl)
            val path = uri.path ?: return@withContext null
            val baseHost = if (uri.host?.contains("smule.com") == true) uri.host else "www.smule.com"
            val finalBaseUrl = "https://$baseHost$path"
            
            val twitterUrl = "${finalBaseUrl.trimEnd('/')}/twitter"
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()
            
            val request = Request.Builder()
                .url(twitterUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            
            val title = doc.select("meta[property=og:title]").attr("content").ifBlank { "Smule_Recording" }
            val redirUrl = doc.select("meta[name=twitter:player:stream]").attr("content")
            
            if (redirUrl.isBlank()) {
                // Fallback: try to find the stream URL in the main page if /twitter fails
                val mainRequest = Request.Builder().url(finalBaseUrl).header("User-Agent", "Mozilla/5.0").build()
                val mainResponse = client.newCall(mainRequest).execute()
                val mainHtml = mainResponse.body?.string() ?: ""
                val mainDoc = Jsoup.parse(mainHtml)
                val fallbackUrl = mainDoc.select("meta[name=twitter:player:stream]").attr("content")
                if (fallbackUrl.isBlank()) return@withContext null
                
                val redirResponse = client.newCall(Request.Builder().url(fallbackUrl).head().build()).execute()
                return@withContext SmuleMedia(title, redirResponse.request.url.toString())
            }

            val redirResponse = client.newCall(Request.Builder().url(redirUrl).head().build()).execute()
            return@withContext SmuleMedia(title, redirResponse.request.url.toString())
        } catch (e: Exception) { 
            e.printStackTrace()
            null 
        }
    }

    private fun startDownload(media: SmuleMedia) {
        val uri = Uri.parse(media.url)
        val ext = if (media.url.contains(".mp4")) "mp4" else "m4a"
        val fileName = "${media.title.replace(Regex("[^a-zA-Z0-9-_ ]"), "_")}.$ext"
        
        val request = DownloadManager.Request(uri)
            .setTitle(media.title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
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
}


