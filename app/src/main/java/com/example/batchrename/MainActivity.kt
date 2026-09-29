package com.example.batchrename

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

// ==================== פלטת צבעים (בהיר/כהה) ====================
private val Primary = Color(0xFF6C5CE7)
private val PrimaryDark = Color(0xFF5646C7)
private val Accent = Color(0xFF00CEC9)

private val BgLight = Color(0xFFF3F0FF)
private val CardBgLight = Color(0xFFFFFFFF)
private val MutedLight = Color(0xFF8A8A9E)
private val OldChipLight = Color(0xFFF1F1F7)
private val NewChipLight = Color(0xFFEDE9FE)
private val WarnChipLight = Color(0xFFFFF3E0)
private val WarnTextLight = Color(0xFFE67E22)

private val BgDark = Color(0xFF17131F)
private val CardBgDark = Color(0xFF241E33)
private val MutedDark = Color(0xFFA79FBD)
private val OldChipDark = Color(0xFF332B4A)
private val NewChipDark = Color(0xFF3A2E5C)
private val WarnChipDark = Color(0xFF4A3420)
private val WarnTextDark = Color(0xFFFFB74D)

private val HeaderGradient = Brush.horizontalGradient(listOf(Primary, PrimaryDark))
private val TileGradient = Brush.linearGradient(listOf(Primary, PrimaryDark))
private val SuccessColor = Color(0xFF2ECC71)
private val ErrorColor = Color(0xFFE74C3C)

data class AppColors(
    val bg: Color,
    val cardBg: Color,
    val mutedText: Color,
    val oldChip: Color,
    val newChip: Color,
    val warnChip: Color,
    val warnText: Color
)

private fun lightAppColors() = AppColors(BgLight, CardBgLight, MutedLight, OldChipLight, NewChipLight, WarnChipLight, WarnTextLight)
private fun darkAppColors() = AppColors(BgDark, CardBgDark, MutedDark, OldChipDark, NewChipDark, WarnChipDark, WarnTextDark)

val LocalAppColors = staticCompositionLocalOf { lightAppColors() }

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "wma", "opus")
private const val SINGLES_WORD = "סינגלים"
private const val APP_NAME = "החלף בקליק"
private const val NOTIF_CHANNEL_ID = "batch_rename_channel"
private const val BIG_BATCH_THRESHOLD = 20

enum class AppMode { RENAME, SORT, SETTINGS, HISTORY }

data class RenameItem(
    val documentFile: DocumentFile,
    val oldName: String,
    val newName: String,
    val checked: MutableState<Boolean>
)

data class SortItem(
    val documentFile: DocumentFile,
    val fileName: String,
    val artist: String,
    val letter: String,
    val destDisplay: String,
    val willCreateFolder: Boolean,
    val checked: MutableState<Boolean>
)

data class FailureDetail(val fileName: String, val reason: String)

data class MoveRecord(
    val destinationDir: DocumentFile,
    val fileName: String,
    val originalParent: DocumentFile?,
    val originalName: String
)

data class RenameRunResult(val successCount: Int, val failures: List<FailureDetail>, val renameRecords: List<Pair<String, String>>)
data class SortRunResult(val successCount: Int, val failures: List<FailureDetail>, val moveRecords: List<MoveRecord>)

sealed class UndoAction {
    data class Rename(val folderUri: Uri, val newName: String, val oldName: String) : UndoAction()
    data class Move(val record: MoveRecord) : UndoAction()
}

data class UndoBatch(
    val id: Long,
    val summary: String,
    val actions: List<UndoAction>,
    val onRefresh: suspend () -> Unit
)

sealed class ConfirmStep {
    data class BigBatch(val count: Int, val proceed: () -> Unit) : ConfirmStep()
    data class LowSpace(val neededMb: Long, val availMb: Long, val proceed: () -> Unit) : ConfirmStep()
    data class Duplicates(val count: Int, val onSkip: () -> Unit, val onOverwrite: () -> Unit) : ConfirmStep()
}

data class HistoryEntry(val type: String, val count: Int, val failedCount: Int, val timestamp: Long)

// ==================== הגדרות + היסטוריה (SharedPreferences) ====================
private const val PREFS_NAME = "batch_rename_prefs"
private const val KEY_DEFAULT_ROOT = "default_root_uri"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_HISTORY = "history_entries"

object AppPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDefaultRoot(context: Context): String? = prefs(context).getString(KEY_DEFAULT_ROOT, null)
    fun setDefaultRoot(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_DEFAULT_ROOT, uri).apply()
    }

    fun getThemeMode(context: Context): String = prefs(context).getString(KEY_THEME_MODE, "system") ?: "system"
    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getHistory(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getString("type"), o.getInt("count"), o.getInt("failed"), o.getLong("time"))
            }.reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryEntry(context: Context, entry: HistoryEntry) {
        val chronological = getHistory(context).reversed().toMutableList()
        chronological.add(entry)
        val trimmed = if (chronological.size > 100) chronological.takeLast(100) else chronological
        val arr = JSONArray()
        trimmed.forEach { e ->
            val o = JSONObject()
            o.put("type", e.type)
            o.put("count", e.count)
            o.put("failed", e.failedCount)
            o.put("time", e.timestamp)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }
}

// ==================== מטמון סריקות (בזיכרון בלבד) ====================
private object ScanCache {
    data class Entry(val signature: String, val items: List<SortItem>)
    val store = mutableMapOf<String, Entry>()
    fun invalidate(key: String) { store.remove(key) }
}

// ==================== Activity ====================
class MainActivity : ComponentActivity() {
    val initialModeExtra = mutableStateOf<String?>(null)
    var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        initialModeExtra.value = intent?.getStringExtra("mode")
        ensureNotificationChannel(this)

        setContent {
            val context = LocalContext.current
            val themeModeState = remember { mutableStateOf(AppPrefs.getThemeMode(context)) }
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeModeState.value) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            val appColors = if (useDark) darkAppColors() else lightAppColors()
            val colorScheme = if (useDark) {
                darkColorScheme(primary = Primary, secondary = Accent, background = appColors.bg, surface = appColors.cardBg)
            } else {
                lightColorScheme(primary = Primary, secondary = Accent, background = appColors.bg, surface = appColors.cardBg)
            }

            MaterialTheme(colorScheme = colorScheme) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalAppColors provides appColors
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = appColors.bg) {
                        AppRoot(initialModeExtra, themeModeState)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialModeExtra.value = intent.getStringExtra("mode")
    }
}

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(NOTIF_CHANNEL_ID, "עדכוני פעולות", NotificationManager.IMPORTANCE_DEFAULT)
        mgr?.createNotificationChannel(channel)
    }
}

fun postCompletionNotification(context: Context, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    try {
        val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    } catch (e: SecurityException) {
        // הרשאה נדחתה - מתעלמים בשקט
    }
}

@Composable
fun AppRoot(initialModeExtra: MutableState<String?>, themeModeState: MutableState<String>) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1300)
        showSplash = false
    }

    if (showSplash) {
        AnimatedSplash()
    } else {
        BatchRenameScreen(initialModeExtra, themeModeState)
    }
}

@Composable
fun AnimatedSplash() {
    val scale = remember { Animatable(0.75f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(150); textAlpha.animateTo(1f, animationSpec = tween(500)) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Primary), // זהה בדיוק ל-splash_background הנייטיבי
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                APP_NAME,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
            )
        }
    }
}

@Composable
fun BatchRenameScreen(initialModeExtra: MutableState<String?>, themeModeState: MutableState<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val haptics = LocalHapticFeedback.current

    var mode by remember { mutableStateOf<AppMode?>(null) }

    // ---- מצב שינוי שמות ----
    var renameItems by remember { mutableStateOf(listOf<RenameItem>()) }
    var renameFolderUri by remember { mutableStateOf<Uri?>(null) }
    var hasRenameFolder by remember { mutableStateOf(false) }
    var isScanningRename by remember { mutableStateOf(false) }
    var renameStatusText by remember { mutableStateOf("") }
    var renameStatusOk by remember { mutableStateOf(true) }
    var renameQuery by remember { mutableStateOf("") }
    var renameProgress by remember { mutableStateOf(0 to 0) }
    var renameFailures by remember { mutableStateOf(listOf<FailureDetail>()) }

    // ---- מצב מיון סינגלים ----
    var sortRootUri by remember { mutableStateOf<Uri?>(null) }
    var sortFolderUri by remember { mutableStateOf<Uri?>(null) }
    var hasSortRoot by remember { mutableStateOf(false) }
    var hasSortFolder by remember { mutableStateOf(false) }
    var sortItems by remember { mutableStateOf(listOf<SortItem>()) }
    var isScanningSort by remember { mutableStateOf(false) }
    var sortStatusText by remember { mutableStateOf("") }
    var sortStatusOk by remember { mutableStateOf(true) }
    var sortQuery by remember { mutableStateOf("") }
    var sortProgress by remember { mutableStateOf(0 to 0) }
    var sortFailures by remember { mutableStateOf(listOf<FailureDetail>()) }

    // ---- אישור/דיאלוגים ----
    var confirmStep by remember { mutableStateOf<ConfirmStep?>(null) }

    // ---- ביטול פעולה (Undo) ----
    var undoBatch by remember { mutableStateOf<UndoBatch?>(null) }

    // ---- הרשאת התראות ----
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // כפתור/קיצור מהמסך הראשי - קפיצה ישירה למצב מבוקש
    LaunchedEffect(initialModeExtra.value) {
        when (initialModeExtra.value) {
            "RENAME" -> mode = AppMode.RENAME
            "SORT" -> mode = AppMode.SORT
        }
    }

    // ביטול אוטומטי של Undo אחרי 6 שניות
    LaunchedEffect(undoBatch?.id) {
        if (undoBatch != null) {
            delay(6000)
            undoBatch = null
        }
    }

    // כפתור חזור פיזי: אם בתוך מצב פעולה - חזור לבית. אם בבית - יציאה רגילה.
    BackHandler(enabled = mode != null) {
        mode = null
    }

    val renameFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            renameFolderUri = uri
            hasRenameFolder = true
            renameStatusText = ""
            renameFailures = emptyList()
            scope.launch {
                isScanningRename = true
                val result = withContext(Dispatchers.IO) { scanRenameFolder(context, uri) }
                renameItems = result
                isScanningRename = false
                if (result.isEmpty()) { renameStatusText = "לא נמצאו קבצים בתבנית המתאימה"; renameStatusOk = false }
            }
        }
    }

    val sortRootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sortRootUri = uri
            hasSortRoot = true
        }
    }

    val sortFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sortFolderUri = uri
            hasSortFolder = true
        }
    }

    val settingsRootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            AppPrefs.setDefaultRoot(context, uri.toString())
        }
    }

    // אם יש תיקיית אב קבועה בהגדרות - נטען אותה אוטומטית כשנכנסים למצב מיון
    LaunchedEffect(mode) {
        if (mode == AppMode.SORT && sortRootUri == null) {
            val saved = AppPrefs.getDefaultRoot(context)
            if (saved != null) {
                try {
                    val uri = Uri.parse(saved)
                    sortRootUri = uri
                    hasSortRoot = true
                } catch (e: Exception) { /* מתעלמים */ }
            }
        }
    }

    // מריץ סריקה אוטומטית ברגע ששתי התיקיות (אב + מיון) נבחרו
    LaunchedEffect(sortRootUri, sortFolderUri) {
        val root = sortRootUri
        val folder = sortFolderUri
        if (root != null && folder != null) {
            sortStatusText = ""
            sortFailures = emptyList()
            isScanningSort = true
            sortProgress = 0 to 0
            val result = withContext(Dispatchers.IO) {
                scanSortFolder(context, folder, root) { done, total -> sortProgress = done to total }
            }
            sortItems = result
            isScanningSort = false
            if (result.isEmpty()) { sortStatusText = "לא נמצאו שירים עם התגית \"$SINGLES_WORD\""; sortStatusOk = false }
        }
    }

    fun refreshRename(uri: Uri) {
        scope.launch {
            renameItems = withContext(Dispatchers.IO) { scanRenameFolder(context, uri) }
        }
    }

    fun refreshSort(folder: Uri, root: Uri, forceRefresh: Boolean) {
        scope.launch {
            isScanningSort = true
            sortProgress = 0 to 0
            sortItems = withContext(Dispatchers.IO) {
                scanSortFolder(context, folder, root, forceRefresh) { done, total -> sortProgress = done to total }
            }
            isScanningSort = false
        }
    }

    // ---- ביצוע שינוי שמות בפועל ----
    fun runRename(items: List<RenameItem>) {
        scope.launch {
            var wl: PowerManager.WakeLock? = null
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatchRename:rename")
                wl?.acquire(5 * 60 * 1000L)

                renameProgress = 0 to items.size
                val result = withContext(Dispatchers.IO) {
                    performRename(items) { done, total -> renameProgress = done to total }
                }
                renameFailures = result.failures
                renameStatusOk = result.failures.isEmpty()
                renameStatusText = "הושלם: ${result.successCount} הצליחו" + if (result.failures.isNotEmpty()) ", ${result.failures.size} נכשלו" else ""

                if (result.successCount > 0) {
                    val uri = renameFolderUri
                    if (uri != null) {
                        undoBatch = UndoBatch(
                            id = System.currentTimeMillis(),
                            summary = "שונו שמות ל-${result.successCount} קבצים",
                            actions = result.renameRecords.map { (newName, oldName) -> UndoAction.Rename(uri, newName, oldName) },
                            onRefresh = { refreshRename(uri) }
                        )
                    }
                }

                AppPrefs.addHistoryEntry(context, HistoryEntry("rename", result.successCount, result.failures.size, System.currentTimeMillis()))
                postCompletionNotification(context, APP_NAME, "שינוי שמות הושלם: ${result.successCount} קבצים")

                renameFolderUri?.let { refreshRename(it) }
            } finally {
                wl?.release()
            }
        }
    }

    fun startRenameConfirmFlow(items: List<RenameItem>) {
        if (items.size > BIG_BATCH_THRESHOLD) {
            confirmStep = ConfirmStep.BigBatch(items.size) {
                confirmStep = null
                runRename(items)
            }
        } else {
            runRename(items)
        }
    }

    // ---- ביצוע מיון בפועל ----
    fun runSort(items: List<SortItem>, root: Uri, folder: Uri) {
        scope.launch {
            var wl: PowerManager.WakeLock? = null
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatchRename:sort")
                wl?.acquire(10 * 60 * 1000L)

                sortProgress = 0 to items.size
                val result = withContext(Dispatchers.IO) {
                    performSort(context, root, items) { done, total -> sortProgress = done to total }
                }
                sortFailures = result.failures
                sortStatusOk = result.failures.isEmpty()
                sortStatusText = "הושלם: ${result.successCount} הועברו" + if (result.failures.isNotEmpty()) ", ${result.failures.size} נכשלו" else ""

                if (result.successCount > 0) {
                    undoBatch = UndoBatch(
                        id = System.currentTimeMillis(),
                        summary = "הועברו ${result.successCount} שירים",
                        actions = result.moveRecords.map { UndoAction.Move(it) },
                        onRefresh = { refreshSort(folder, root, true) }
                    )
                }

                AppPrefs.addHistoryEntry(context, HistoryEntry("sort", result.successCount, result.failures.size, System.currentTimeMillis()))
                postCompletionNotification(context, APP_NAME, "מיון סינגלים הושלם: ${result.successCount} שירים")

                ScanCache.invalidate("$folder|$root")
                refreshSort(folder, root, true)
            } finally {
                wl?.release()
            }
        }
    }

    fun continueAfterSpaceCheck(items: List<SortItem>, root: Uri, folder: Uri) {
        scope.launch {
            val dupUris = withContext(Dispatchers.IO) { findDuplicateTargets(context, root, items) }
            if (dupUris.isNotEmpty()) {
                confirmStep = ConfirmStep.Duplicates(
                    count = dupUris.size,
                    onSkip = {
                        confirmStep = null
                        runSort(items.filter { it.documentFile.uri !in dupUris }, root, folder)
                    },
                    onOverwrite = {
                        confirmStep = null
                        runSort(items, root, folder)
                    }
                )
            } else {
                runSort(items, root, folder)
            }
        }
    }

    fun continueAfterBigBatch(items: List<SortItem>, root: Uri, folder: Uri) {
        scope.launch {
            val totalBytes = withContext(Dispatchers.IO) { items.sumOf { runCatching { it.documentFile.length() }.getOrDefault(0L) } }
            val stat = try { StatFs(Environment.getExternalStorageDirectory().path) } catch (e: Exception) { null }
            val availableBytes = stat?.availableBytes ?: Long.MAX_VALUE
            if (totalBytes > availableBytes) {
                confirmStep = ConfirmStep.LowSpace(
                    neededMb = totalBytes / (1024 * 1024),
                    availMb = availableBytes / (1024 * 1024),
                    proceed = {
                        confirmStep = null
                        continueAfterSpaceCheck(items, root, folder)
                    }
                )
            } else {
                continueAfterSpaceCheck(items, root, folder)
            }
        }
    }

    fun startSortConfirmFlow(items: List<SortItem>, root: Uri, folder: Uri) {
        if (items.size > BIG_BATCH_THRESHOLD) {
            confirmStep = ConfirmStep.BigBatch(items.size) {
                confirmStep = null
                continueAfterBigBatch(items, root, folder)
            }
        } else {
            continueAfterBigBatch(items, root, folder)
        }
    }

    val renameCheckedCount = renameItems.count { it.checked.value }
    val sortCheckedCount = sortItems.count { it.checked.value }

    val filteredRename = remember(renameItems, renameQuery) {
        if (renameQuery.isBlank()) renameItems
        else renameItems.filter { it.oldName.contains(renameQuery, true) || it.newName.contains(renameQuery, true) }
    }
    val renameFilteredChecked = filteredRename.count { it.checked.value }
    val renameAllFilteredSelected = filteredRename.isNotEmpty() && renameFilteredChecked == filteredRename.size

    val filteredSort = remember(sortItems, sortQuery) {
        if (sortQuery.isBlank()) sortItems
        else sortItems.filter { it.fileName.contains(sortQuery, true) || it.artist.contains(sortQuery, true) }
    }
    val sortFilteredChecked = filteredSort.count { it.checked.value }
    val sortAllFilteredSelected = filteredSort.isNotEmpty() && sortFilteredChecked == filteredSort.size

    val groupedSort = remember(filteredSort) {
        val groups = filteredSort.groupBy { it.artist }
        groups.keys.sortedWith(
            compareByDescending<String> { artist -> groups[artist]!!.any { it.willCreateFolder } }.thenBy { it }
        ).map { artist -> artist to groups[artist]!!.sortedWith(compareBy({ !it.willCreateFolder }, { it.fileName })) }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- כותרת מודרנית: פינות תחתונות מעוגלות + צל ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(HeaderGradient)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (mode != null) {
                        IconButton(onClick = { mode = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "חזרה", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(
                            when (mode) {
                                AppMode.RENAME -> "שינוי שמות קבצים"
                                AppMode.SORT -> "מיון סינגלים"
                                AppMode.SETTINGS -> "הגדרות"
                                AppMode.HISTORY -> "היסטוריית פעולות"
                                null -> APP_NAME
                            },
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when (mode) {
                            AppMode.RENAME -> if (renameItems.isNotEmpty()) "${renameItems.size} קבצים" else null
                            AppMode.SORT -> if (sortItems.isNotEmpty()) "${sortItems.size} שירים" else null
                            else -> null
                        }
                        if (subtitle != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                                Text(
                                    subtitle,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mode == null) {
                        HeaderIconButton(Icons.Filled.History, "היסטוריה") { mode = AppMode.HISTORY }
                        HeaderIconButton(Icons.Filled.Settings, "הגדרות") { mode = AppMode.SETTINGS }
                    }
                    if (mode == AppMode.RENAME && hasRenameFolder) {
                        HeaderIconButton(Icons.Filled.FolderOpen, "החלף תיקייה") { renameFolderPicker.launch(null) }
                    }
                    if (mode == AppMode.SORT) {
                        if (hasSortRoot) HeaderIconButton(Icons.Filled.AccountTree, "החלף תיקיית אב") { sortRootPicker.launch(null) }
                        if (hasSortFolder) HeaderIconButton(Icons.Filled.FolderOpen, "החלף תיקיית מיון") { sortFolderPicker.launch(null) }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            when (mode) {

                // ---------- מסך בית ----------
                null -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.newChip),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("ברוך הבא", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text("בחר פעולה כדי להתחיל", fontSize = 12.sp, color = colors.mutedText)
                            }
                        }

                        Spacer(modifier = Modifier.height(26.dp))
                        Text("פעולות זמינות", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionTile(
                                icon = Icons.Filled.DriveFileRenameOutline,
                                title = "שינוי שמות",
                                subtitle = "החלפת סדר בשמות קבצים",
                                modifier = Modifier.weight(1f).aspectRatio(0.92f)
                            ) { mode = AppMode.RENAME }

                            ActionTile(
                                icon = Icons.Filled.LibraryMusic,
                                title = "מיון סינגלים",
                                subtitle = "מיון שירים לפי תגיות",
                                modifier = Modifier.weight(1f).aspectRatio(0.92f)
                            ) { mode = AppMode.SORT }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ---------- מצב שינוי שמות ----------
                AppMode.RENAME -> {
                    if (!hasRenameFolder) {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            FolderPickerCard(Icons.Filled.FolderOpen, "בחר תיקייה", "בחר את התיקייה שבה נמצאים הקבצים לשינוי שם") { renameFolderPicker.launch(null) }
                        }
                    } else if (isScanningRename) {
                        ScanningAnimation("סורק קבצים בתיקייה...")
                    } else {
                        if (renameStatusText.isNotEmpty()) StatusBanner(renameStatusText, renameStatusOk)
                        if (renameFailures.isNotEmpty()) FailuresPanel(renameFailures)

                        if (renameItems.isNotEmpty()) {
                            SearchField(renameQuery, "חפש קובץ...") { renameQuery = it }
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectAllRow(renameFilteredChecked, filteredRename.size, renameAllFilteredSelected) {
                                val newValue = !renameAllFilteredSelected
                                filteredRename.forEach { it.checked.value = newValue }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            filteredRename.isEmpty() && renameItems.isNotEmpty() -> EmptyState(Icons.Filled.SearchOff, "אין תוצאות לחיפוש")
                            renameItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו קבצים תואמים")
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                items(filteredRename) { item -> FileRenameCard(item, haptics) }
                            }
                        }
                    }
                }

                // ---------- מצב מיון סינגלים ----------
                AppMode.SORT -> {
                    if (!hasSortRoot || !hasSortFolder) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!hasSortRoot) FolderPickerCard(Icons.Filled.AccountTree, "בחר תיקיית אב", "התיקייה עם האותיות א׳ ב׳ ג׳ שבתוכן תיקיות האמנים") { sortRootPicker.launch(null) }
                            if (!hasSortRoot && !hasSortFolder) Spacer(modifier = Modifier.height(12.dp))
                            if (!hasSortFolder) FolderPickerCard(Icons.Filled.FolderOpen, "בחר תיקיית מיון", "התיקייה עם השירים שיש לסרוק ולמיין") { sortFolderPicker.launch(null) }
                        }
                    } else if (isScanningSort) {
                        ScanningAnimation("סורק שירים ובודק תגיות... (${sortProgress.first}/${sortProgress.second})")
                    } else {
                        if (sortStatusText.isNotEmpty()) StatusBanner(sortStatusText, sortStatusOk)
                        if (sortFailures.isNotEmpty()) FailuresPanel(sortFailures)

                        if (sortItems.isNotEmpty()) {
                            SearchField(sortQuery, "חפש שיר או אמן...") { sortQuery = it }
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectAllRow(sortFilteredChecked, filteredSort.size, sortAllFilteredSelected) {
                                val newValue = !sortAllFilteredSelected
                                filteredSort.forEach { it.checked.value = newValue }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            filteredSort.isEmpty() && sortItems.isNotEmpty() -> EmptyState(Icons.Filled.SearchOff, "אין תוצאות לחיפוש")
                            sortItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו שירים עם התגית \"$SINGLES_WORD\"")
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                groupedSort.forEach { (artist, songs) ->
                                    item { GroupHeader(artist, songs.size) }
                                    items(songs) { item -> SortCard(item, haptics) }
                                }
                            }
                        }
                    }
                }

                // ---------- מסך הגדרות ----------
                AppMode.SETTINGS -> {
                    SettingsScreen(
                        context = context,
                        themeModeState = themeModeState,
                        onPickDefaultRoot = { settingsRootPicker.launch(null) }
                    )
                }

                // ---------- מסך היסטוריה ----------
                AppMode.HISTORY -> {
                    HistoryScreen(context = context)
                }
            }
        }

        // ---------- שורת ביטול (Undo) ----------
        AnimatedVisibility(visible = undoBatch != null) {
            val batch = undoBatch
            if (batch != null) {
                Surface(color = colors.cardBg, shadowElevation = 6.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(batch.summary, fontSize = 13.sp, color = colors.mutedText, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { performUndo(context, batch) }
                                batch.onRefresh()
                                undoBatch = null
                            }
                        }) {
                            Icon(Icons.Filled.Undo, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("בטל", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ---------- כפתור תחתון ----------
        val showConfirm = when (mode) {
            AppMode.RENAME -> hasRenameFolder && renameItems.isNotEmpty()
            AppMode.SORT -> hasSortRoot && hasSortFolder && sortItems.isNotEmpty()
            else -> false
        }
        AnimatedVisibility(visible = showConfirm) {
            Surface(shadowElevation = 12.dp, color = colors.cardBg) {
                Box(modifier = Modifier.padding(10.dp)) {
                    if (mode == AppMode.RENAME) {
                        ConfirmButton("אשר ורץ ($renameCheckedCount)", renameCheckedCount > 0) {
                            startRenameConfirmFlow(renameItems.filter { it.checked.value })
                        }
                    } else if (mode == AppMode.SORT) {
                        ConfirmButton("אשר והעבר ($sortCheckedCount)", sortCheckedCount > 0) {
                            val root = sortRootUri ?: return@ConfirmButton
                            val folder = sortFolderUri ?: return@ConfirmButton
                            startSortConfirmFlow(sortItems.filter { it.checked.value }, root, folder)
                        }
                    }
                }
            }
        }
    }

    // ---------- דיאלוגי אישור (מנת גודל / שטח פנוי / כפילויות) ----------
    val step = confirmStep
    if (step != null) {
        when (step) {
            is ConfirmStep.BigBatch -> AlertDialog(
                onDismissRequest = { confirmStep = null },
                title = { Text("פעולה על כמות גדולה") },
                text = { Text("בחרת ${step.count} קבצים. זו פעולה בלתי הפיכה (אך ניתנת לביטול לזמן קצר לאחר הביצוע). להמשיך?") },
                confirmButton = { TextButton(onClick = step.proceed) { Text("המשך") } },
                dismissButton = { TextButton(onClick = { confirmStep = null }) { Text("ביטול") } }
            )
            is ConfirmStep.LowSpace -> AlertDialog(
                onDismissRequest = { confirmStep = null },
                title = { Text("שטח אחסון נמוך") },
                text = { Text("נדרשים כ-${step.neededMb}MB, פנויים במכשיר כ-${step.availMb}MB (הערכה). להמשיך בכל זאת?") },
                confirmButton = { TextButton(onClick = step.proceed) { Text("המשך") } },
                dismissButton = { TextButton(onClick = { confirmStep = null }) { Text("ביטול") } }
            )
            is ConfirmStep.Duplicates -> AlertDialog(
                onDismissRequest = { confirmStep = null },
                title = { Text("נמצאו קבצים כפולים") },
                text = { Text("${step.count} קבצים כבר קיימים ביעד. מה לעשות?") },
                confirmButton = { TextButton(onClick = step.onOverwrite) { Text("דרוס קיימים") } },
                dismissButton = { TextButton(onClick = step.onSkip) { Text("דלג עליהם") } }
            )
        }
    }
}

// ==================== רכיבי UI כלליים ====================

@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = colors.cardBg, shadowElevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(TileGradient), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = colors.mutedText, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FolderPickerCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = colors.cardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.newChip), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 12.sp, color = colors.mutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedText)
        }
    }
}

@Composable
fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "נקה", modifier = Modifier.size(16.dp)) }
            }
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedContainerColor = colors.cardBg, focusedContainerColor = colors.cardBg),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    )
}

@Composable
fun SelectAllRow(checkedCount: Int, total: Int, allSelected: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(onClick = onToggle, shape = RoundedCornerShape(50), color = if (allSelected) colors.oldChip else colors.newChip) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (allSelected) Icons.Filled.RemoveDone else Icons.Filled.DoneAll,
                    contentDescription = null, tint = if (allSelected) colors.mutedText else Primary, modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (allSelected) "בטל הכל" else "בחר הכל",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (allSelected) colors.mutedText else Primary
                )
            }
        }
        Surface(shape = RoundedCornerShape(50), color = Primary) {
            Text("$checkedCount / $total", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun ConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = if (enabled) Primary else colors.mutedText.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(52.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, color = colors.mutedText, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
fun ScanningAnimation(text: String) {
    val colors = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)), label = "rotation"
    )
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Autorenew, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp).graphicsLayer { rotationZ = rotation })
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, color = colors.mutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
fun AnimatedStatusIcon(ok: Boolean) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    Icon(
        if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
        contentDescription = null,
        tint = if (ok) SuccessColor else ErrorColor,
        modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
    )
}

@Composable
fun StatusBanner(text: String, ok: Boolean) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedStatusIcon(ok)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = colors.mutedText)
    }
}

@Composable
fun FailuresPanel(failures: List<FailureDetail>) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    Surface(shape = RoundedCornerShape(12.dp), color = colors.warnChip, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${failures.size} קבצים נכשלו - הצג פירוט", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.warnText)
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null, tint = colors.warnText, modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                failures.forEach { f ->
                    Text("• ${f.fileName}: ${f.reason}", fontSize = 11.sp, color = colors.warnText, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun GroupHeader(artist: String, count: Int) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("$artist ($count שירים)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
    }
}

@Composable
fun FileRenameCard(item: RenameItem, haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val colors = LocalAppColors.current
    Surface(
        onClick = { item.checked.value = !item.checked.value; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.checked.value, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Primary))
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow("לפני", item.oldName, false)
                Spacer(modifier = Modifier.height(3.dp))
                NameRow("אחרי", item.newName, true)
            }
        }
    }
}

@Composable
fun SortCard(item: SortItem, haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val colors = LocalAppColors.current
    Surface(
        onClick = { item.checked.value = !item.checked.value; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.checked.value, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Primary))
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow("קובץ", item.fileName, false)
                Spacer(modifier = Modifier.height(3.dp))
                NameRow("יעד", item.destDisplay, true)
                if (item.willCreateFolder) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.warnChip) {
                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = colors.warnText, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("תיקייה חדשה תיווצר", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.warnText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NameRow(label: String, name: String, isNew: Boolean) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(6.dp), color = if (isNew) colors.newChip else colors.oldChip, modifier = Modifier.padding(top = 1.dp)) {
            Text(
                label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isNew) Primary else colors.mutedText
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            name, fontSize = 14.sp, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
            color = if (isNew) Primary else colors.mutedText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
    }
}

// ==================== מסך הגדרות ====================
@Composable
fun SettingsScreen(context: Context, themeModeState: MutableState<String>, onPickDefaultRoot: () -> Unit) {
    val colors = LocalAppColors.current
    val defaultRoot = AppPrefs.getDefaultRoot(context)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("תיקיית אב קבועה", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(onClick = onPickDefaultRoot, shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.newChip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AccountTree, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (defaultRoot != null) "תיקייה נבחרה" else "לא נבחרה תיקייה", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("תיטען אוטומטית במצב מיון סינגלים", fontSize = 11.sp, color = colors.mutedText)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("ערכת נושא", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip("מערכת", themeModeState.value == "system") { themeModeState.value = "system"; AppPrefs.setThemeMode(context, "system") }
            ThemeChip("בהיר", themeModeState.value == "light") { themeModeState.value = "light"; AppPrefs.setThemeMode(context, "light") }
            ThemeChip("כהה", themeModeState.value == "dark") { themeModeState.value = "dark"; AppPrefs.setThemeMode(context, "dark") }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Primary else colors.cardBg, shadowElevation = if (selected) 2.dp else 1.dp) {
        Text(
            label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else colors.mutedText
        )
    }
}

// ==================== מסך היסטוריה ====================
@Composable
fun HistoryScreen(context: Context) {
    val colors = LocalAppColors.current
    var refreshKey by remember { mutableStateOf(0) }
    val history = remember(refreshKey) { AppPrefs.getHistory(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he")) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("פעולות אחרונות", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
            if (history.isNotEmpty()) {
                TextButton(onClick = { AppPrefs.clearHistory(context); refreshKey++ }) {
                    Text("נקה היסטוריה", fontSize = 12.sp, color = ErrorColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            EmptyState(Icons.Filled.History, "אין פעולות עדיין")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { entry -> HistoryRow(entry, dateFormat) }
            }
        }
    }
}

@Composable
fun HistoryRow(entry: HistoryEntry, dateFormat: SimpleDateFormat) {
    val colors = LocalAppColors.current
    Surface(shape = RoundedCornerShape(14.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = if (entry.type == "rename") Icons.Filled.DriveFileRenameOutline else Icons.Filled.LibraryMusic
            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.newChip), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (entry.type == "rename") "שינוי שמות - ${entry.count} קבצים" else "מיון סינגלים - ${entry.count} שירים",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                Text(dateFormat.format(Date(entry.timestamp)), fontSize = 11.sp, color = colors.mutedText)
            }
            if (entry.failedCount > 0) {
                Surface(shape = RoundedCornerShape(50), color = colors.warnChip) {
                    Text("${entry.failedCount} נכשלו", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.sp, color = colors.warnText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== לוגיקת שינוי שמות ====================

fun scanRenameFolder(context: Context, uri: Uri): List<RenameItem> {
    val tree = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
    val result = mutableListOf<RenameItem>()

    tree.listFiles().forEach { file ->
        if (file.isFile) {
            val name = file.name ?: return@forEach
            if (name.contains(" - ")) {
                val dotIndex = name.lastIndexOf('.')
                val hasExtension = dotIndex > 0
                val baseName = if (hasExtension) name.substring(0, dotIndex) else name
                val extension = if (hasExtension) name.substring(dotIndex) else ""

                val sepIndex = baseName.indexOf(" - ")
                if (sepIndex >= 0) {
                    val part1 = baseName.substring(0, sepIndex)
                    val part2 = baseName.substring(sepIndex + 3)
                    val newName = "$part2 - $part1$extension"

                    if (newName != name) {
                        result.add(RenameItem(file, name, newName, mutableStateOf(true)))
                    }
                }
            }
        }
    }
    return result
}

fun performRename(items: List<RenameItem>, onProgress: (Int, Int) -> Unit): RenameRunResult {
    var success = 0
    val failures = mutableListOf<FailureDetail>()
    val records = mutableListOf<Pair<String, String>>()
    val total = items.size

    items.forEachIndexed { index, item ->
        onProgress(index + 1, total)
        try {
            if (item.documentFile.renameTo(item.newName)) {
                success++
                records.add(item.newName to item.oldName)
            } else {
                failures.add(FailureDetail(item.oldName, "שינוי השם נכשל"))
            }
        } catch (e: Exception) {
            failures.add(FailureDetail(item.oldName, e.message ?: "שגיאה לא ידועה"))
        }
    }
    return RenameRunResult(success, failures, records)
}

// ==================== לוגיקת מיון סינגלים ====================

private fun isAudioFile(name: String?): Boolean {
    if (name == null) return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

private fun collectAudioFiles(dir: DocumentFile, acc: MutableList<DocumentFile>) {
    dir.listFiles().forEach { f ->
        if (f.isDirectory) collectAudioFiles(f, acc)
        else if (f.isFile && isAudioFile(f.name)) acc.add(f)
    }
}

// כמו collectAudioFiles, אבל לא נכנס לתוך תיקיות שכבר נקראות "סינגלים" -
// אלה כבר ממוינות, אין טעם לסרוק אותן שוב כשסורקים את כל תיקיית האב.
private fun collectAudioFilesSkippingSingles(dir: DocumentFile, acc: MutableList<DocumentFile>) {
    dir.listFiles().forEach { f ->
        if (f.isDirectory) {
            val dirName = f.name?.let { normalizeName(it) } ?: ""
            if (!dirName.equals(SINGLES_WORD, ignoreCase = true)) collectAudioFilesSkippingSingles(f, acc)
        } else if (f.isFile && isAudioFile(f.name)) {
            acc.add(f)
        }
    }
}

private fun extractArtistFromTags(context: Context, uri: Uri): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val candidates = listOf(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        )
        val tag = candidates.firstOrNull { it != null && it.contains(SINGLES_WORD) }
        tag?.replace(SINGLES_WORD, "")?.trim()?.trim(',')?.trim()?.let { normalizeName(it) }?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    } finally {
        try { retriever.release() } catch (e: Exception) { }
    }
}

// מנרמל שם: מסיר רווחים כפולים/מובילים, כדי שהתאמת תיקיות תהיה עקבית
// גם אם בתגית יש רווחים נוספים שלא נראים לעין.
private fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

// מחפש תת-תיקייה לפי שם, בהתעלם מרווחים מיותרים ומרישיות.
private fun DocumentFile.findChildByName(name: String): DocumentFile? {
    val target = normalizeName(name)
    return listFiles().firstOrNull { child ->
        val childName = child.name ?: return@firstOrNull false
        normalizeName(childName).equals(target, ignoreCase = true)
    }
}

private fun folderChainExists(rootTree: DocumentFile, letter: String, artist: String): Boolean {
    val letterDir = rootTree.findChildByName(letter) ?: return false
    val artistDir = letterDir.findChildByName(artist) ?: return false
    val singlesDir = artistDir.findChildByName(SINGLES_WORD) ?: return false
    return singlesDir.isDirectory
}

// סריקה מקבילית (עד 6 קבצים בו-זמנית) עם מטמון תוצאות בזיכרון.
suspend fun scanSortFolder(
    context: Context,
    folderUri: Uri,
    rootUri: Uri,
    forceRefresh: Boolean = false,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): List<SortItem> {
    val folderTree = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val rootTree = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

    val cacheKey = "$folderUri|$rootUri"
    val topLevelFiles = folderTree.listFiles()
    val signature = "${topLevelFiles.size}:${folderTree.lastModified()}"

    if (!forceRefresh) {
        val cached = ScanCache.store[cacheKey]
        if (cached != null && cached.signature == signature) {
            onProgress(cached.items.size, cached.items.size)
            return cached.items
        }
    }

    // 1) קבצים בתיקיית המיון שנבחרה
    val audioFiles = mutableListOf<DocumentFile>()
    collectAudioFiles(folderTree, audioFiles)

    // 2) בנוסף: קבצים "יתומים" בתוך תיקיית האב עם תגית "סינגלים" שעדיין לא ממוינים
    val rootAudioFiles = mutableListOf<DocumentFile>()
    collectAudioFilesSkippingSingles(rootTree, rootAudioFiles)
    val existingUris = audioFiles.map { it.uri }.toSet()
    rootAudioFiles.forEach { f -> if (f.uri !in existingUris) audioFiles.add(f) }

    val total = audioFiles.size
    val processed = AtomicInteger(0)
    val semaphore = Semaphore(6)

    val results = coroutineScope {
        audioFiles.map { file ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val name = file.name
                    val item = if (name != null) {
                        val artist = extractArtistFromTags(context, file.uri)
                        val letter = artist?.firstOrNull()?.toString()
                        if (artist != null && letter != null) {
                            val exists = folderChainExists(rootTree, letter, artist)
                            val destDisplay = "$letter / $artist / $SINGLES_WORD / $name"
                            SortItem(file, name, artist, letter, destDisplay, !exists, mutableStateOf(true))
                        } else null
                    } else null
                    onProgress(processed.incrementAndGet(), total)
                    item
                }
            }
        }.awaitAll()
    }

    val finalList = results.filterNotNull()
    ScanCache.store[cacheKey] = ScanCache.Entry(signature, finalList)
    return finalList
}

private fun guessAudioMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"; "wav" -> "audio/wav"
        "flac" -> "audio/flac"; "ogg" -> "audio/ogg"; "wma" -> "audio/x-ms-wma"; "opus" -> "audio/opus"
        else -> "audio/*"
    }
}

fun findDuplicateTargets(context: Context, rootUri: Uri, items: List<SortItem>): Set<Uri> {
    val rootTree = DocumentFile.fromTreeUri(context, rootUri) ?: return emptySet()
    val dup = mutableSetOf<Uri>()
    items.forEach { item ->
        if (!item.willCreateFolder) {
            val letterDir = rootTree.findChildByName(item.letter)
            val artistDir = letterDir?.findChildByName(item.artist)
            val singlesDir = artistDir?.findChildByName(SINGLES_WORD)
            val existing = singlesDir?.findChildByName(item.fileName)
            if (existing != null) dup.add(item.documentFile.uri)
        }
    }
    return dup
}

fun performSort(context: Context, rootUri: Uri, items: List<SortItem>, onProgress: (Int, Int) -> Unit): SortRunResult {
    val rootTree = DocumentFile.fromTreeUri(context, rootUri)
        ?: return SortRunResult(0, items.map { FailureDetail(it.fileName, "לא ניתן לגשת לתיקיית האב") }, emptyList())

    var success = 0
    val failures = mutableListOf<FailureDetail>()
    val records = mutableListOf<MoveRecord>()
    val total = items.size

    items.forEachIndexed { index, item ->
        onProgress(index + 1, total)
        try {
            val letterDir = rootTree.findChildByName(item.letter) ?: rootTree.createDirectory(item.letter)
            val artistDir = letterDir?.findChildByName(item.artist) ?: letterDir?.createDirectory(item.artist)
            val singlesDir = artistDir?.findChildByName(SINGLES_WORD) ?: artistDir?.createDirectory(SINGLES_WORD)

            if (singlesDir == null) {
                failures.add(FailureDetail(item.fileName, "לא ניתן ליצור את תיקיית היעד"))
                return@forEachIndexed
            }

            val mime = guessAudioMime(item.fileName)
            val newFile = singlesDir.findChildByName(item.fileName) ?: singlesDir.createFile(mime, item.fileName)

            if (newFile == null) {
                failures.add(FailureDetail(item.fileName, "יצירת קובץ היעד נכשלה"))
                return@forEachIndexed
            }

            val sourceLength = item.documentFile.length()

            context.contentResolver.openInputStream(item.documentFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) }
            }

            // בדיקת תקינות: משווים גודל מקור מול גודל יעד לפני מחיקת המקור
            val destLength = newFile.length()
            if (sourceLength > 0 && destLength != sourceLength) {
                failures.add(FailureDetail(item.fileName, "אימות תקינות נכשל - הגדלים אינם תואמים"))
                return@forEachIndexed
            }

            val originalParent = item.documentFile.parentFile
            val originalName = item.fileName

            item.documentFile.delete()
            success++
            records.add(MoveRecord(singlesDir, item.fileName, originalParent, originalName))
        } catch (e: Exception) {
            failures.add(FailureDetail(item.fileName, e.message ?: "שגיאה לא ידועה"))
        }
    }

    return SortRunResult(success, failures, records)
}

// ==================== ביטול פעולה (Undo) ====================

fun undoRename(context: Context, folderUri: Uri, newName: String, oldName: String): Boolean {
    return try {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        val file = folder.findChildByName(newName) ?: return false
        file.renameTo(oldName)
    } catch (e: Exception) {
        false
    }
}

fun undoMove(context: Context, record: MoveRecord): Boolean {
    return try {
        val parent = record.originalParent ?: return false
        val movedFile = record.destinationDir.findChildByName(record.fileName) ?: return false
        val mime = guessAudioMime(record.fileName)
        val restored = parent.findChildByName(record.originalName) ?: parent.createFile(mime, record.originalName) ?: return false
        context.contentResolver.openInputStream(movedFile.uri)?.use { input ->
            context.contentResolver.openOutputStream(restored.uri)?.use { output -> input.copyTo(output) }
        }
        movedFile.delete()
        true
    } catch (e: Exception) {
        false
    }
}

fun performUndo(context: Context, batch: UndoBatch) {
    batch.actions.forEach { action ->
        when (action) {
            is UndoAction.Rename -> undoRename(context, action.folderUri, action.newName, action.oldName)
            is UndoAction.Move -> undoMove(context, action.record)
        }
    }
}
