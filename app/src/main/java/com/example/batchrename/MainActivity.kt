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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
private val MutedDark = Color(0xFFB8AFD0)
private val OldChipDark = Color(0xFF3E3658)
private val NewChipDark = Color(0xFF5A4A94)
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
    val warnText: Color,
    val isDark: Boolean
)

private fun lightAppColors() = AppColors(BgLight, CardBgLight, MutedLight, OldChipLight, NewChipLight, WarnChipLight, WarnTextLight, false)
private fun darkAppColors() = AppColors(BgDark, CardBgDark, MutedDark, OldChipDark, NewChipDark, WarnChipDark, WarnTextDark, true)

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
    val placeDirectlyInArtistFolder: Boolean,
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
    data class Preview(
        val title: String, val totalFiles: Int, val duplicates: Int, val newFolders: Int, val proceed: () -> Unit
    ) : ConfirmStep()
}

// פרט בודד בתוך רשומת היסטוריה - שומר גם טקסט לתצוגה וגם URIs לשחזור אפשרי בעתיד.
data class HistoryDetail(
    val kind: String, // "rename" או "move"
    val displayFrom: String,
    val displayTo: String,
    val folderUri: String? = null, // rename: התיקייה שבה בוצע השינוי
    val oldName: String? = null,
    val newName: String? = null,
    val destDirUri: String? = null, // move: תיקיית היעד אליה הועבר הקובץ
    val originalParentUri: String? = null, // move: התיקייה המקורית ממנה הועבר
    val originalName: String? = null,
    val fileName: String? = null
)

data class HistoryEntry(
    val type: String,
    val count: Int,
    val failedCount: Int,
    val timestamp: Long,
    val details: List<HistoryDetail> = emptyList()
)

// ==================== הגדרות + היסטוריה (SharedPreferences) ====================
private const val PREFS_NAME = "batch_rename_prefs"
private const val KEY_DEFAULT_ROOT = "default_root_uri"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_HISTORY = "history_entries"
private const val KEY_IGNORE_LIST = "ignore_list"
private const val KEY_AUTO_DELETE = "auto_delete"
private const val KEY_SAVED_ROOTS = "saved_roots"
private const val MAX_HISTORY_ENTRIES = 25

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

    fun getIgnoreList(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_IGNORE_LIST, emptySet()) ?: emptySet()
    }
    fun setIgnoreList(context: Context, list: Set<String>) {
        prefs(context).edit().putStringSet(KEY_IGNORE_LIST, list).apply()
    }

    fun getAutoDeleteEmpty(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DELETE, false)
    fun setAutoDeleteEmpty(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DELETE, value).apply()
    }

    fun getSavedRoots(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SAVED_ROOTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }
    fun addSavedRoot(context: Context, uri: String) {
        val current = getSavedRoots(context).toMutableList()
        if (!current.contains(uri)) {
            current.add(uri)
            val arr = JSONArray(current)
            prefs(context).edit().putString(KEY_SAVED_ROOTS, arr.toString()).apply()
        }
    }

    fun getHistory(context: Context): List<HistoryEntry> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }
        val entries = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val detailsArr = o.optJSONArray("details")
                val details = mutableListOf<HistoryDetail>()
                if (detailsArr != null) {
                    for (j in 0 until detailsArr.length()) {
                        try {
                            val d = detailsArr.getJSONObject(j)
                            details.add(
                                HistoryDetail(
                                    kind = d.getString("kind"),
                                    displayFrom = d.getString("from"),
                                    displayTo = d.getString("to"),
                                    folderUri = d.optString("folderUri", null),
                                    oldName = d.optString("oldName", null),
                                    newName = d.optString("newName", null),
                                    destDirUri = d.optString("destDirUri", null),
                                    originalParentUri = d.optString("originalParentUri", null),
                                    originalName = d.optString("originalName", null),
                                    fileName = d.optString("fileName", null)
                                )
                            )
                        } catch (e: Exception) { }
                    }
                }
                entries.add(HistoryEntry(o.getString("type"), o.getInt("count"), o.getInt("failed"), o.getLong("time"), details))
            } catch (e: Exception) { }
        }
        return entries.reversed()
    }

    fun addHistoryEntry(context: Context, entry: HistoryEntry) {
        val chronological = getHistory(context).reversed().toMutableList()
        chronological.add(entry)
        val trimmed = if (chronological.size > MAX_HISTORY_ENTRIES) chronological.takeLast(MAX_HISTORY_ENTRIES) else chronological
        val arr = JSONArray()
        trimmed.forEach { e ->
            val o = JSONObject()
            o.put("type", e.type)
            o.put("count", e.count)
            o.put("failed", e.failedCount)
            o.put("time", e.timestamp)
            val detailsArr = JSONArray()
            e.details.forEach { d ->
                val dObj = JSONObject()
                dObj.put("kind", d.kind)
                dObj.put("from", d.displayFrom)
                dObj.put("to", d.displayTo)
                d.folderUri?.let { dObj.put("folderUri", it) }
                d.oldName?.let { dObj.put("oldName", it) }
                d.newName?.let { dObj.put("newName", it) }
                d.destDirUri?.let { dObj.put("destDirUri", it) }
                d.originalParentUri?.let { dObj.put("originalParentUri", it) }
                d.originalName?.let { dObj.put("originalName", it) }
                d.fileName?.let { dObj.put("fileName", it) }
                detailsArr.put(dObj)
            }
            o.put("details", detailsArr)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }
}

// ==================== Activity ====================
// עוקב אחרי מצב קדמה/רקע של האפליקציה - כדי להציג התראה רק כשהמשתמש לא נמצא עליה כרגע.
object AppForegroundState {
    var isInForeground: Boolean = true
}

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

    override fun onResume() {
        super.onResume()
        AppForegroundState.isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppForegroundState.isInForeground = false
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
        val channel = NotificationChannel(NOTIF_CHANNEL_ID, "עדכוני פעולות", NotificationManager.IMPORTANCE_LOW)
        channel.description = "התראות כשפעולת שינוי שמות או מיון מסתיימת"
        mgr?.createNotificationChannel(channel)
    }
}

// הופך את אייקון האפליקציה לביטמאפ עגול בצבע המותג, לשימוש כ-largeIcon בהתראה
private fun brandedNotificationIcon(context: Context): android.graphics.Bitmap? {
    return try {
        val size = 128
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Primary.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        drawable?.setBounds(size / 5, size / 5, size - size / 5, size - size / 5)
        drawable?.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun postCompletionNotification(context: Context, title: String, text: String) {
    if (AppForegroundState.isInForeground) return

    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    try {
        val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(Primary.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        brandedNotificationIcon(context)?.let { builder.setLargeIcon(it) }

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
    } catch (e: SecurityException) { }
}

@Composable
fun AppRoot(initialModeExtra: MutableState<String?>, themeModeState: MutableState<String>) {
    BatchRenameScreen(initialModeExtra, themeModeState)
}

@Composable
fun BatchRenameScreen(initialModeExtra: MutableState<String?>, themeModeState: MutableState<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val haptics = LocalHapticFeedback.current

    // שימוש ב-rememberSaveable מאפשר לשמור על המצב בעת סיבוב מסך
    var mode by rememberSaveable { mutableStateOf<AppMode?>(null) }

    // ---- מצב שינוי שמות ----
    var renameItems by remember { mutableStateOf(listOf<RenameItem>()) }
    var renameFolderUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    val renameFolderUri = renameFolderUriStr?.let { try { Uri.parse(it) } catch(e:Exception){null} }
    val hasRenameFolder = renameFolderUri != null
    var isScanningRename by remember { mutableStateOf(false) }
    var renameStatusText by remember { mutableStateOf("") }
    var renameStatusOk by remember { mutableStateOf(true) }
    var renameQuery by rememberSaveable { mutableStateOf("") }
    var underscoreToSpace by rememberSaveable { mutableStateOf(false) }
    var renameProgress by remember { mutableStateOf(0 to 0) }
    var renameFailures by remember { mutableStateOf(listOf<FailureDetail>()) }

    // ---- מצב מיון סינגלים ----
    var sortRootUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    val sortRootUri = sortRootUriStr?.let { try { Uri.parse(it) } catch(e:Exception){null} }
    val hasSortRoot = sortRootUri != null
    var sortFolderUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    val sortFolderUri = sortFolderUriStr?.let { try { Uri.parse(it) } catch(e:Exception){null} }
    val hasSortFolder = sortFolderUri != null
    var sortItems by remember { mutableStateOf(listOf<SortItem>()) }
    var isScanningSort by remember { mutableStateOf(false) }
    var sortStatusText by remember { mutableStateOf("") }
    var sortStatusOk by remember { mutableStateOf(true) }
    var sortQuery by rememberSaveable { mutableStateOf("") }
    var sortProgress by remember { mutableStateOf(0 to 0) }
    var sortFailures by remember { mutableStateOf(listOf<FailureDetail>()) }

    // ---- אישור/דיאלוגים ----
    var confirmStep by remember { mutableStateOf<ConfirmStep?>(null) }

    // ---- מנהל קבצים פנימי ----
    var fileManagerTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // ---- חיפוש בסרגל + תפריט החלפת תיקייה ----
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var folderMenuExpanded by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(initialModeExtra.value) {
        when (initialModeExtra.value) {
            "RENAME" -> mode = AppMode.RENAME
            "SORT" -> mode = AppMode.SORT
        }
        if (initialModeExtra.value != null) initialModeExtra.value = null
    }

    LaunchedEffect(undoBatch?.id) {
        if (undoBatch != null) {
            delay(6000)
            undoBatch = null
        }
    }

    BackHandler(enabled = mode != null) {
        mode = null
    }

    // אם יש תיקיית אב קבועה בהגדרות - נטען אותה אוטומטית כשנכנסים למצב מיון
    LaunchedEffect(mode) {
        if (mode == AppMode.SORT && sortRootUriStr == null) {
            val saved = AppPrefs.getDefaultRoot(context)
            if (saved != null) {
                sortRootUriStr = saved
            }
        }
    }

    fun refreshRename(uri: Uri) {
        scope.launch {
            isScanningRename = true
            renameItems = withContext(Dispatchers.IO) { scanRenameFolder(context, uri, underscoreToSpace) }
            isScanningRename = false
        }
    }

    fun refreshSort(folder: Uri, root: Uri, forceRefresh: Boolean) {
        if (isScanningSort) return
        scope.launch {
            isScanningSort = true
            sortProgress = 0 to 0
            sortItems = withContext(Dispatchers.IO) {
                scanSortFolder(context, folder, root, forceRefresh) { done, total -> sortProgress = done to total }
            }
            isScanningSort = false
        }
    }

    LaunchedEffect(sortRootUriStr, sortFolderUriStr) {
        if (sortRootUri != null && sortFolderUri != null && !isScanningSort) {
            sortStatusText = ""
            sortFailures = emptyList()
            isScanningSort = true
            sortProgress = 0 to 0
            val result = withContext(Dispatchers.IO) {
                scanSortFolder(context, sortFolderUri, sortRootUri) { done, total -> sortProgress = done to total }
            }
            sortItems = result
            isScanningSort = false
            if (result.isEmpty()) { sortStatusText = "לא נמצאו שירים עם התגית \"$SINGLES_WORD\""; sortStatusOk = false }
        }
    }

    LaunchedEffect(mode) {
        if (mode == AppMode.RENAME && hasRenameFolder) {
            renameFolderUri?.let { refreshRename(it) }
        } else if (mode == AppMode.SORT && hasSortRoot && hasSortFolder) {
            if (sortRootUri != null && sortFolderUri != null) refreshSort(sortFolderUri, sortRootUri, true)
        }
    }

    LaunchedEffect(underscoreToSpace) {
        if (hasRenameFolder) {
            renameFolderUri?.let { refreshRename(it) }
        }
    }

    fun runRename(items: List<RenameItem>) {
        scope.launch {
            var wl: PowerManager.WakeLock? = null
            val notifManager = NotificationManagerCompat.from(context)
            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("משנה שמות קבצים...")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatchRename:rename")
                wl?.acquire(5 * 60 * 1000L)

                renameProgress = 0 to items.size
                val result = withContext(Dispatchers.IO) {
                    performRename(items) { done, total ->
                        renameProgress = done to total
                        if (done % 5 == 0 || done == total) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                builder.setProgress(total, done, false).setContentText("$done / $total")
                                notifManager.notify(888, builder.build())
                            }
                        }
                    }
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

                AppPrefs.addHistoryEntry(
                    context,
                    HistoryEntry(
                        "rename", result.successCount, result.failures.size, System.currentTimeMillis(),
                        details = renameFolderUri?.let { uri ->
                            result.renameRecords.map { (newName, oldName) ->
                                HistoryDetail("rename", oldName, newName, folderUri = uri.toString(), oldName = oldName, newName = newName)
                            }
                        } ?: emptyList()
                    )
                )
                postCompletionNotification(context, APP_NAME, "שינוי שמות הושלם: ${result.successCount} קבצים")

                renameFolderUri?.let { refreshRename(it) }
            } finally {
                wl?.release()
                notifManager.cancel(888)
            }
        }
    }

    fun startRenameConfirmFlow(items: List<RenameItem>) {
        val totalFiles = items.size
        confirmStep = ConfirmStep.Preview(
            title = "סיכום שינוי שמות",
            totalFiles = totalFiles,
            duplicates = 0,
            newFolders = 0,
            proceed = {
                confirmStep = null
                if (totalFiles > BIG_BATCH_THRESHOLD) {
                    confirmStep = ConfirmStep.BigBatch(totalFiles) {
                        confirmStep = null
                        runRename(items)
                    }
                } else {
                    runRename(items)
                }
            }
        )
    }

    fun runSort(items: List<SortItem>, root: Uri, folder: Uri) {
        scope.launch {
            var wl: PowerManager.WakeLock? = null
            val notifManager = NotificationManagerCompat.from(context)
            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ממיין סינגלים...")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatchRename:sort")
                wl?.acquire(10 * 60 * 1000L)

                sortProgress = 0 to items.size
                val result = withContext(Dispatchers.IO) {
                    performSort(context, root, items, AppPrefs.getAutoDeleteEmpty(context)) { done, total ->
                        sortProgress = done to total
                        if (done % 5 == 0 || done == total) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                builder.setProgress(total, done, false).setContentText("$done / $total")
                                notifManager.notify(888, builder.build())
                            }
                        }
                    }
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

                AppPrefs.addHistoryEntry(
                    context,
                    HistoryEntry(
                        "sort", result.successCount, result.failures.size, System.currentTimeMillis(),
                        details = result.moveRecords.map { r ->
                            HistoryDetail(
                                "move", r.fileName, "${r.destinationDir.name ?: "?"} / ${r.fileName}",
                                destDirUri = r.destinationDir.uri.toString(),
                                originalParentUri = r.originalParent?.uri?.toString(),
                                originalName = r.originalName,
                                fileName = r.fileName
                            )
                        }.filter { it.originalParentUri != null }
                    )
                )
                postCompletionNotification(context, APP_NAME, "מיון סינגלים הושלם: ${result.successCount} שירים")

                refreshSort(folder, root, true)
            } finally {
                wl?.release()
                notifManager.cancel(888)
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
        scope.launch {
            val newFoldersCount = items.distinctBy { it.letter + "/" + it.artist }.count { it.willCreateFolder }
            val dupUris = withContext(Dispatchers.IO) { findDuplicateTargets(context, root, items) }
            
            confirmStep = ConfirmStep.Preview(
                title = "סיכום מיון",
                totalFiles = items.size,
                duplicates = dupUris.size,
                newFolders = newFoldersCount,
                proceed = {
                    confirmStep = null
                    if (items.size > BIG_BATCH_THRESHOLD) {
                        confirmStep = ConfirmStep.BigBatch(items.size) {
                            confirmStep = null
                            continueAfterBigBatch(items, root, folder)
                        }
                    } else {
                        continueAfterBigBatch(items, root, folder)
                    }
                }
            )
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
        val hasListShowing = (mode == AppMode.RENAME && renameItems.isNotEmpty()) || (mode == AppMode.SORT && sortItems.isNotEmpty())
        val headerVPad = if (hasListShowing && !searchExpanded) 10.dp else 16.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(HeaderGradient)
                .padding(horizontal = 18.dp, vertical = headerVPad)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            if (subtitle != null && !hasListShowing) {
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
                        if ((mode == AppMode.RENAME && renameItems.isNotEmpty()) || (mode == AppMode.SORT && sortItems.isNotEmpty())) {
                            HeaderIconButton(Icons.Filled.Search, "חיפוש") { searchExpanded = !searchExpanded }
                        }
                        if (mode == AppMode.RENAME && hasRenameFolder) {
                            HeaderIconButton(Icons.Filled.FolderOpen, "החלף תיקייה") { fileManagerTarget = "RENAME" }
                        }
                        if (mode == AppMode.SORT && (hasSortRoot || hasSortFolder)) {
                            Box {
                                HeaderIconButton(Icons.Filled.FolderOpen, "החלף תיקייה") { folderMenuExpanded = true }
                                DropdownMenu(expanded = folderMenuExpanded, onDismissRequest = { folderMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("שנה תיקיית אב") },
                                        leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                                        onClick = { folderMenuExpanded = false; fileManagerTarget = "SORT_ROOT" }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("שנה תיקיית מיון") },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                        onClick = { folderMenuExpanded = false; fileManagerTarget = "SORT_FOLDER" }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = searchExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        val (queryValue, onQueryChange, placeholder) = when (mode) {
                            AppMode.RENAME -> Triple(renameQuery, { s: String -> renameQuery = s }, "חפש קובץ...")
                            AppMode.SORT -> Triple(sortQuery, { s: String -> sortQuery = s }, "חפש שיר או אמן...")
                            else -> Triple("", { _: String -> }, "")
                        }
                        HeaderSearchField(queryValue, placeholder, onQueryChange)
                    }
                }
            }
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val forward = initialState == null && targetState != null
                val backward = targetState == null && initialState != null
                when {
                    forward -> (slideInHorizontally(tween(260)) { w -> w / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> -w / 6 } + fadeOut(tween(180)))
                    backward -> (slideInHorizontally(tween(260)) { w -> -w / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> w / 6 } + fadeOut(tween(180)))
                    else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                }
            },
            label = "modeTransition",
            modifier = Modifier.weight(1f)
        ) { targetMode ->
            val currentMode = targetMode
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
            when (currentMode) {
                null -> {
                    val recentHistory = remember(currentMode) { AppPrefs.getHistory(context).take(2) }
                    val homeDateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale("he")) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = colors.newChip.copy(alpha = if (colors.isDark) 0.35f else 0.55f),
                            modifier = Modifier.size(300.dp).align(Alignment.BottomCenter).offset(y = 90.dp, x = 60.dp)
                        )

                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(52.dp).clip(CircleShape).background(TileGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_launcher_foreground),
                                        contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("ברוך הבא", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("בחר פעולה כדי להתחיל", fontSize = 12.sp, color = colors.mutedText)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            StorageStatsCard()

                            Spacer(modifier = Modifier.height(28.dp))
                            Text("פעולות זמינות", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionTile(
                                    icon = Icons.Filled.DriveFileRenameOutline,
                                    title = "שינוי שמות",
                                    subtitle = "החלפת סדר בשמות קבצים",
                                    gradient = TileGradient,
                                    modifier = Modifier.weight(1f).aspectRatio(0.95f)
                                ) { mode = AppMode.RENAME }

                                ActionTile(
                                    icon = Icons.Filled.LibraryMusic,
                                    title = "מיון סינגלים",
                                    subtitle = "מיון שירים לפי תגיות",
                                    gradient = Brush.linearGradient(listOf(Accent, Primary)),
                                    modifier = Modifier.weight(1f).aspectRatio(0.95f)
                                ) { mode = AppMode.SORT }
                            }

                            Spacer(modifier = Modifier.height(28.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("פעילות אחרונה", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
                                if (recentHistory.isNotEmpty()) {
                                    TextButton(onClick = { mode = AppMode.HISTORY }) {
                                        Text("הצג הכל", fontSize = 12.sp, color = Primary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (recentHistory.isEmpty()) {
                                Surface(shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = colors.warnText, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "פעולות שתבצע יופיעו כאן, ואפשר יהיה לבטל אותן מאוחר יותר",
                                            fontSize = 12.sp, color = colors.mutedText, modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    recentHistory.forEach { entry ->
                                        Surface(
                                            onClick = { mode = AppMode.HISTORY },
                                            shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 1.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.newChip),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        if (entry.type == "rename") Icons.Filled.DriveFileRenameOutline else Icons.Filled.LibraryMusic,
                                                        contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        if (entry.type == "rename") "שינוי שמות - ${entry.count} קבצים" else "מיון סינגלים - ${entry.count} שירים",
                                                        fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(homeDateFormat.format(Date(entry.timestamp)), fontSize = 11.sp, color = colors.mutedText)
                                                }
                                                if (entry.failedCount > 0) {
                                                    Surface(shape = RoundedCornerShape(50), color = colors.warnChip) {
                                                        Text(
                                                            "${entry.failedCount} נכשלו",
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.warnText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                AppMode.RENAME -> {
                    if (!hasRenameFolder) {
                        EmptyFolderPrompt(
                            icon = Icons.Filled.FolderOpen,
                            title = "בחר תיקייה",
                            description = "בחר את התיקייה שבה נמצאים הקבצים לשינוי שם",
                            tip = "אפשר לבחור כל תיקייה שמכילה קבצים עם מקף (\" - \") בשם שלהם",
                            onClick = { fileManagerTarget = "RENAME" }
                        )
                    } else {
                        RenameOptionsRow(
                            underscoreToSpace = underscoreToSpace,
                            onToggle = { underscoreToSpace = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (isScanningRename) {
                            SkeletonScanningList("סורק קבצים בתיקייה...")
                        } else {
                            if (renameStatusText.isNotEmpty()) StatusBanner(renameStatusText, renameStatusOk)
                            if (renameFailures.isNotEmpty()) FailuresPanel(renameFailures)

                            if (renameItems.isNotEmpty()) {
                                SelectAllRow(renameFilteredChecked, filteredRename.size, renameAllFilteredSelected) {
                                    val newValue = !renameAllFilteredSelected
                                    filteredRename.forEach { it.checked.value = newValue }
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            when {
                                filteredRename.isEmpty() && renameItems.isNotEmpty() -> EmptyState(Icons.Filled.SearchOff, "אין תוצאות לחיפוש")
                                renameItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו קבצים תואמים (לאחר סינון חריגים)")
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
                }

                AppMode.SORT -> {
                    if (!hasSortRoot || !hasSortFolder) {
                        if (!hasSortRoot) {
                            EmptyFolderPrompt(
                                icon = Icons.Filled.AccountTree,
                                title = "בחר תיקיית אב",
                                description = "התיקייה עם האותיות א׳ ב׳ ג׳ שבתוכן תיקיות האמנים",
                                buttonLabel = "בחר תיקיית אב",
                                tip = "תיקיית האב היא זו שבה כבר יש תיקיות לפי אותיות (א, ב, ג...) ובתוכן תיקיות האמנים",
                                badge = "שלב 1 מתוך 2",
                                onClick = { fileManagerTarget = "SORT_ROOT" }
                            )
                        } else {
                            EmptyFolderPrompt(
                                icon = Icons.Filled.LibraryMusic,
                                title = "בחר תיקיית מיון",
                                description = "התיקייה עם השירים שיש לסרוק ולמיין",
                                buttonLabel = "בחר תיקיית מיון",
                                tip = "אפשר לבחור כל תיקייה עם שירים שיש בתגיות שלהם את המילה \"$SINGLES_WORD\"",
                                badge = "שלב 2 מתוך 2",
                                onClick = { fileManagerTarget = "SORT_FOLDER" }
                            )
                        }
                    } else if (isScanningSort) {
                        SkeletonScanningList("סורק שירים ובודק תגיות... (${sortProgress.first}/${sortProgress.second})")
                    } else {
                        if (sortStatusText.isNotEmpty()) StatusBanner(sortStatusText, sortStatusOk)
                        if (sortFailures.isNotEmpty()) FailuresPanel(sortFailures)

                        if (sortItems.isNotEmpty()) {
                            SelectAllRow(sortFilteredChecked, filteredSort.size, sortAllFilteredSelected) {
                                val newValue = !sortAllFilteredSelected
                                filteredSort.forEach { it.checked.value = newValue }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            filteredSort.isEmpty() && sortItems.isNotEmpty() -> EmptyState(Icons.Filled.SearchOff, "אין תוצאות לחיפוש")
                            sortItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו שירים עם התגית \"$SINGLES_WORD\" (לאחר סינון חריגים)")
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                groupedSort.forEach { (artist, songs) ->
                                    if (songs.size > 1) {
                                        item { GroupHeader(artist, songs.size) }
                                    }
                                    items(songs) { item -> SortCard(item, haptics) }
                                }
                            }
                        }
                    }
                }

                AppMode.SETTINGS -> {
                    SettingsScreen(
                        context = context,
                        themeModeState = themeModeState,
                        onPickDefaultRoot = { fileManagerTarget = "DEFAULT_ROOT" }
                    )
                }

                AppMode.HISTORY -> {
                    HistoryScreen(context = context)
                }
            }
            }
        }

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

    if (fileManagerTarget != null) {
        InAppFileManagerDialog(
            context = context,
            onDismiss = { fileManagerTarget = null },
            onSelect = { selectedUri ->
                when (fileManagerTarget) {
                    "RENAME" -> renameFolderUriStr = selectedUri.toString()
                    "SORT_ROOT" -> sortRootUriStr = selectedUri.toString()
                    "SORT_FOLDER" -> sortFolderUriStr = selectedUri.toString()
                    "DEFAULT_ROOT" -> AppPrefs.setDefaultRoot(context, selectedUri.toString())
                }
                fileManagerTarget = null
            }
        )
    }

    val step = confirmStep
    if (step != null) {
        when (step) {
            is ConfirmStep.Preview -> PreviewDashboardDialog(step) { confirmStep = null }
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
fun StorageStatsCard() {
    val colors = LocalAppColors.current
    var total by remember { mutableStateOf(0L) }
    var free by remember { mutableStateOf(0L) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                total = stat.totalBytes
                free = stat.availableBytes
            } catch (e: Exception) {}
        }
    }
    
    if (total > 0L) {
        val used = total - free
        val usedPercent = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        Surface(shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("אחסון במכשיר", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
                    Text("${free / (1024*1024*1024)}GB פנויים", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(colors.newChip)) {
                    Box(modifier = Modifier.fillMaxWidth(usedPercent).fillMaxHeight().clip(RoundedCornerShape(50)).background(TileGradient))
                }
            }
        }
    }
}

@Composable
fun PreviewDashboardDialog(step: ConfirmStep.Preview, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(step.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewDetailRow(Icons.Filled.FileCopy, "סה\"כ קבצים לפעולה", "${step.totalFiles}")
                if (step.newFolders > 0) {
                    PreviewDetailRow(Icons.Filled.CreateNewFolder, "תיקיות חדשות שייווצרו", "${step.newFolders}", Primary)
                }
                if (step.duplicates > 0) {
                    PreviewDetailRow(Icons.Filled.WarningAmber, "קבצים קיימים (כפילויות)", "${step.duplicates}", ErrorColor)
                }
            }
        },
        confirmButton = { TextButton(onClick = step.proceed) { Text("אשר ורץ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול", color = colors.mutedText) } }
    )
}

@Composable
fun PreviewDetailRow(icon: ImageVector, label: String, value: String, color: Color = LocalAppColors.current.mutedText) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = LocalAppColors.current.mutedText, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun InAppFileManagerDialog(context: Context, onDismiss: () -> Unit, onSelect: (Uri) -> Unit) {
    var savedRoots by remember { mutableStateOf(AppPrefs.getSavedRoots(context)) }
    var pathStack by remember { mutableStateOf(listOf<DocumentFile>()) }
    val colors = LocalAppColors.current

    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            AppPrefs.addSavedRoot(context, uri.toString())
            savedRoots = AppPrefs.getSavedRoots(context)
            DocumentFile.fromTreeUri(context, uri)?.let { pathStack = listOf(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("מנהל אחסון", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedRoots) { rootStr ->
                        val rootUri = Uri.parse(rootStr)
                        val name = try { DocumentFile.fromTreeUri(context, rootUri)?.name ?: "אחסון" } catch (e: Exception) { "אחסון" }
                        Surface(
                            onClick = { DocumentFile.fromTreeUri(context, rootUri)?.let { pathStack = listOf(it) } },
                            shape = RoundedCornerShape(12.dp),
                            color = if (pathStack.firstOrNull()?.uri == rootUri) Primary else colors.newChip
                        ) {
                            Text(name, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp,
                                color = if (pathStack.firstOrNull()?.uri == rootUri) Color.White else Primary)
                        }
                    }
                    item {
                        Surface(onClick = { rootPicker.launch(null) }, shape = RoundedCornerShape(12.dp), color = colors.cardBg, border = BorderStroke(1.dp, Primary)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("הוסף מקור", fontSize = 12.sp, color = Primary)
                            }
                        }
                    }
                }
            }
        },
        text = {
            val currentDir = pathStack.lastOrNull()
            if (savedRoots.isEmpty() && currentDir == null) {
                Text("לא נבחרו מקורות אחסון. לחץ על 'הוסף מקור' כדי לבחור כונן פנימי, כרטיס זיכרון או USB.", color = colors.mutedText, fontSize = 13.sp)
            } else if (currentDir != null) {
                val folders = remember(currentDir) { currentDir.listFiles().filter { it.isDirectory }.sortedBy { it.name } }
                Column {
                    if (pathStack.size > 1) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { pathStack = pathStack.dropLast(1) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SubdirectoryArrowRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp).graphicsLayer(scaleX = -1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("חזור אחורה...", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Divider(color = colors.mutedText.copy(alpha=0.2f))
                    }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                        items(folders) { folder ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { pathStack = pathStack + folder }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(folder.name ?: "תיקייה", fontSize = 14.sp, color = colors.mutedText)
                            }
                            Divider(color = colors.mutedText.copy(alpha=0.1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            val currentDir = pathStack.lastOrNull()
            TextButton(
                onClick = { currentDir?.let { onSelect(it.uri) } },
                enabled = currentDir != null
            ) { Text("בחר תיקייה זו") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול", color = colors.mutedText) } }
    )
}


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
fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    gradient: Brush = TileGradient,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = colors.cardBg, shadowElevation = 4.dp, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(gradient), contentAlignment = Alignment.Center) {
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
fun EmptyFolderPrompt(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String = "בחר תיקייה",
    tip: String? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.newChip.copy(alpha = if (colors.isDark) 0.5f else 0.7f),
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 70.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 30.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (badge != null) {
                Surface(shape = RoundedCornerShape(50), color = colors.newChip) {
                    Text(
                        badge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Primary
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = Primary, spotColor = Primary)
                    .clip(CircleShape)
                    .background(TileGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description, fontSize = 13.sp, color = colors.mutedText, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Primary, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (tip != null) {
                Spacer(modifier = Modifier.height(22.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.padding(horizontal = 28.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = colors.warnText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tip, fontSize = 11.sp, color = colors.mutedText)
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        innerTextField()
                    }
                }
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "נקה", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun RenameOptionsRow(underscoreToSpace: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Surface(shape = RoundedCornerShape(14.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SpaceBar, contentDescription = null, tint = if (underscoreToSpace) Primary else colors.mutedText, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "המר קו תחתון ( _ ) לרווח",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (underscoreToSpace) Primary else colors.mutedText,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = underscoreToSpace,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
            )
        }
    }
}

@Composable
fun SelectAllRow(checkedCount: Int, total: Int, allSelected: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggle, shape = RoundedCornerShape(50), color = if (allSelected) colors.oldChip else colors.newChip,
            border = if (colors.isDark) BorderStroke(1.dp, colors.mutedText.copy(alpha = 0.35f)) else null
        ) {
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
fun ScanningStatusRow(text: String) {
    val colors = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "scanIcon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)), label = "rotation"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Icon(Icons.Filled.Autorenew, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = colors.mutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SkeletonCard() {
    val colors = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(750, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Surface(shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(colors.mutedText.copy(alpha = alpha * 0.3f)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.68f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(colors.mutedText.copy(alpha = alpha * 0.25f)))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.48f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(colors.mutedText.copy(alpha = alpha * 0.22f)))
            }
        }
    }
}

@Composable
fun SkeletonScanningList(text: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScanningStatusRow(text)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(7) { SkeletonCard() }
        }
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
            Checkbox(checked = item.checked.value, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = colors.mutedText, checkmarkColor = Color.White))
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
            Checkbox(checked = item.checked.value, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = colors.mutedText, checkmarkColor = Color.White))
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow("קובץ", item.fileName, false)
                Spacer(modifier = Modifier.height(3.dp))
                NameRow("יעד", item.destDisplay, true, maxLines = Int.MAX_VALUE, fontSize = 12.sp)
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
fun NameRow(label: String, name: String, isNew: Boolean, maxLines: Int = 2, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
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
            name, fontSize = fontSize, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
            color = if (isNew) Primary else colors.mutedText, maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Visible else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== מסך הגדרות ====================
@Composable
fun SettingsScreen(context: Context, themeModeState: MutableState<String>, onPickDefaultRoot: () -> Unit) {
    val colors = LocalAppColors.current
    val defaultRoot = AppPrefs.getDefaultRoot(context)
    val defaultRootName = remember(defaultRoot) {
        defaultRoot?.let { raw ->
            try { DocumentFile.fromTreeUri(context, Uri.parse(raw))?.name } catch (e: Exception) { null }
        }
    }

    var ignoreInput by remember { mutableStateOf("") }
    var ignoreList by remember { mutableStateOf(AppPrefs.getIgnoreList(context).toList()) }
    var autoDelete by remember { mutableStateOf(AppPrefs.getAutoDeleteEmpty(context)) }

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
                    Text(
                        if (defaultRoot == null) "לא נבחרה תיקייה" else (defaultRootName ?: "תיקייה נבחרה"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("תיטען אוטומטית במצב מיון סינגלים", fontSize = 11.sp, color = colors.mutedText)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("ניהול תיקיות", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ניקוי תיקיות ריקות", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("מחיקה אוטומטית של תיקיות מקור שהתרוקנו לאחר המיון", fontSize = 11.sp, color = colors.mutedText)
                }
                Switch(
                    checked = autoDelete,
                    onCheckedChange = { autoDelete = it; AppPrefs.setAutoDeleteEmpty(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("רשימת התעלמות (חריגים)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = colors.cardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("הכנס מילות מפתח או סיומות (כמו mp4.) שהאפליקציה תתעלם מהם בסריקה:", fontSize = 12.sp, color = colors.mutedText)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = ignoreInput,
                        onValueChange = { ignoreInput = it },
                        modifier = Modifier.weight(1f).background(colors.bg, RoundedCornerShape(8.dp)).padding(10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = if(colors.isDark) Color.White else Color.Black)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (ignoreInput.isNotBlank()) {
                            val newList = ignoreList + ignoreInput.trim()
                            ignoreList = newList
                            AppPrefs.setIgnoreList(context, newList.toSet())
                            ignoreInput = ""
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("הוסף") }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ignoreList) { item ->
                        Surface(shape = RoundedCornerShape(50), color = colors.newChip) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(item, fontSize = 12.sp, color = Primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Filled.Close, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp).clickable {
                                    val newList = ignoreList - item
                                    ignoreList = newList
                                    AppPrefs.setIgnoreList(context, newList.toSet())
                                })
                            }
                        }
                    }
                }
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
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    
    val fullHistory = remember(refreshKey) { AppPrefs.getHistory(context) }
    val history = remember(fullHistory, historyQuery) {
        if (historyQuery.isBlank()) fullHistory
        else fullHistory.filter { entry ->
            entry.details.any { it.displayFrom.contains(historyQuery, true) || it.displayTo.contains(historyQuery, true) }
        }
    }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he")) }

    var expandedId by remember { mutableStateOf<Long?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<Pair<HistoryEntry, Set<Int>>?>(null) }
    var restoreResultText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shape = RoundedCornerShape(12.dp), color = colors.cardBg, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            BasicTextField(
                value = historyQuery,
                onValueChange = { historyQuery = it },
                modifier = Modifier.padding(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = if(colors.isDark) Color.White else Color.Black),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (historyQuery.isEmpty()) Text("חיפוש בהיסטוריה...", color = colors.mutedText, fontSize = 14.sp)
                            inner()
                        }
                    }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("פעולות אחרונות (עד $MAX_HISTORY_ENTRIES)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.mutedText)
            if (fullHistory.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("נקה היסטוריה", fontSize = 12.sp, color = ErrorColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (restoreResultText != null) {
            StatusBanner(restoreResultText!!, true)
        }

        if (history.isEmpty()) {
            EmptyState(Icons.Filled.History, "אין פעולות או תוצאות חיפוש")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { entry ->
                    HistoryRow(
                        entry = entry,
                        dateFormat = dateFormat,
                        expanded = expandedId == entry.timestamp,
                        onToggleExpand = { expandedId = if (expandedId == entry.timestamp) null else entry.timestamp },
                        onRestoreClick = { selectedIndices -> restoreTarget = entry to selectedIndices }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("ניקוי היסטוריה") },
            text = { Text("למחוק את כל היסטוריית הפעולות? לא ניתן לבטל פעולה זו.") },
            confirmButton = {
                TextButton(onClick = { AppPrefs.clearHistory(context); showClearConfirm = false; refreshKey++ }) { Text("מחק", color = ErrorColor) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("ביטול") } }
        )
    }

    val targetPair = restoreTarget
    if (targetPair != null) {
        val count = targetPair.second.size
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("שחזור פעולה") },
            text = { Text("לשחזר $count קבצים נבחרים? הם יוחזרו למיקומם/לשמם הקודם.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    scope.launch {
                        val (ok, failed) = withContext(Dispatchers.IO) { restoreHistoryEntry(context, targetPair.first, targetPair.second) }
                        restoreResultText = "שוחזרו $ok קבצים" + if (failed > 0) ", $failed נכשלו" else ""
                    }
                }) { Text("שחזר") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("ביטול") } }
        )
    }
}

@Composable
fun HistoryRow(
    entry: HistoryEntry,
    dateFormat: SimpleDateFormat,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRestoreClick: (Set<Int>) -> Unit
) {
    val colors = LocalAppColors.current
    var selectedIndices by remember(expanded) { mutableStateOf((entry.details.indices).toSet()) }

    Surface(
        shape = RoundedCornerShape(14.dp), color = colors.cardBg, shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = entry.details.isNotEmpty(), onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                if (entry.details.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(18.dp).padding(start = 4.dp)
                    )
                }
            }

            if (expanded && entry.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("בחר קבצים לשחזור:", fontSize = 12.sp, color = colors.mutedText)
                    TextButton(onClick = {
                        selectedIndices = if (selectedIndices.size == entry.details.size) emptySet() else (entry.details.indices).toSet()
                    }) { Text(if (selectedIndices.size == entry.details.size) "בטל הכל" else "בחר הכל", fontSize = 11.sp) }
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    entry.details.forEachIndexed { index, d ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            val newSet = selectedIndices.toMutableSet()
                            if (newSet.contains(index)) newSet.remove(index) else newSet.add(index)
                            selectedIndices = newSet
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedIndices.contains(index), onCheckedChange = null, modifier = Modifier.scale(0.8f))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("• ${d.displayFrom} ← ${d.displayTo}", fontSize = 11.sp, color = colors.mutedText, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onRestoreClick(selectedIndices) }, enabled = selectedIndices.isNotEmpty()) {
                    Icon(Icons.Filled.Undo, contentDescription = null, tint = if(selectedIndices.isNotEmpty()) Primary else colors.mutedText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("שחזר נבחרים", color = if(selectedIndices.isNotEmpty()) Primary else colors.mutedText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==================== לוגיקת שינוי שמות ====================

fun scanRenameFolder(context: Context, uri: Uri, convertUnderscoreToSpace: Boolean = false): List<RenameItem> {
    val tree = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
    val result = mutableListOf<RenameItem>()
    val ignoreList = AppPrefs.getIgnoreList(context)

    tree.listFiles().forEach { file ->
        if (file.isFile) {
            val name = file.name ?: return@forEach
            
            if (ignoreList.any { ignoreItem -> name.contains(ignoreItem, ignoreCase = true) }) return@forEach

            val dotIndex = name.lastIndexOf('.')
            val hasExtension = dotIndex > 0
            val baseName = if (hasExtension) name.substring(0, dotIndex) else name
            val extension = if (hasExtension) name.substring(dotIndex) else ""

            var newBaseName = baseName
            val parts = baseName.split(" - ")
            if (parts.size == 2) {
                newBaseName = "${parts[1]} - ${parts[0]}"
            }

            if (convertUnderscoreToSpace && newBaseName.contains('_')) {
                newBaseName = newBaseName.replace('_', ' ')
            }

            val newName = "$newBaseName$extension"
            if (newName != name) {
                result.add(RenameItem(file, name, newName, mutableStateOf(true)))
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

private fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

private fun DocumentFile.findChildByName(name: String): DocumentFile? {
    val target = normalizeName(name)
    return listFiles().firstOrNull { child ->
        val childName = child.name ?: return@firstOrNull false
        normalizeName(childName).equals(target, ignoreCase = true)
    }
}

private data class ArtistPlacement(val artistDir: DocumentFile?, val placeDirectly: Boolean, val willCreateFolder: Boolean)
private data class PlacementCacheEntry(val signature: String, val placement: ArtistPlacement)
private val placementCache = java.util.concurrent.ConcurrentHashMap<String, PlacementCacheEntry>()

private fun resolveArtistPlacement(context: Context, rootTree: DocumentFile, letter: String, artist: String): ArtistPlacement {
    val letterDir = rootTree.findChildByName(letter)
    val artistDir = letterDir?.findChildByName(artist)

    if (artistDir == null) {
        return ArtistPlacement(null, placeDirectly = false, willCreateFolder = true)
    }

    val cacheKey = artistDir.uri.toString()
    val children = artistDir.listFiles()
    val namesSignature = children.sortedBy { it.name ?: "" }
        .joinToString("|") { "${it.name}:${if (it.isDirectory) "d" else "f"}" }
        .hashCode()
    val signature = "${children.size}:$namesSignature"

    placementCache[cacheKey]?.let { cached ->
        if (cached.signature == signature) return cached.placement
    }

    val singlesDir = children.firstOrNull { c ->
        val n = c.name ?: return@firstOrNull false
        c.isDirectory && normalizeName(n).equals(SINGLES_WORD, ignoreCase = true)
    }

    val result = if (singlesDir != null) {
        ArtistPlacement(artistDir, placeDirectly = false, willCreateFolder = false)
    } else {
        val hasLooseSingles = children.any { f ->
            f.isFile && isAudioFile(f.name) && extractArtistFromTags(context, f.uri) != null
        }
        if (hasLooseSingles) {
            ArtistPlacement(artistDir, placeDirectly = true, willCreateFolder = false)
        } else {
            ArtistPlacement(artistDir, placeDirectly = false, willCreateFolder = true)
        }
    }
    placementCache[cacheKey] = PlacementCacheEntry(signature, result)
    return result
}

suspend fun scanSortFolder(
    context: Context,
    folderUri: Uri,
    rootUri: Uri,
    forceRefresh: Boolean = false,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): List<SortItem> {
    val folderTree = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val rootTree = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
    val ignoreList = AppPrefs.getIgnoreList(context)

    val audioFiles = mutableListOf<DocumentFile>()
    collectAudioFiles(folderTree, audioFiles)

    val filteredAudioFiles = audioFiles.filter { file ->
        val name = file.name ?: return@filter false
        !ignoreList.any { ignoreItem -> name.contains(ignoreItem, ignoreCase = true) }
    }

    val total = filteredAudioFiles.size
    val processed = AtomicInteger(0)
    val semaphore = Semaphore(6)

    val results = coroutineScope {
        filteredAudioFiles.map { file ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val name = file.name
                    val item = if (name != null) {
                        val artist = extractArtistFromTags(context, file.uri)
                        val letter = artist?.firstOrNull()?.toString()
                        if (artist != null && letter != null) {
                            val placement = resolveArtistPlacement(context, rootTree, letter, artist)
                            val destDisplay = if (placement.placeDirectly) "$letter / $artist / $name" else "$letter / $artist / $SINGLES_WORD / $name"
                            SortItem(file, name, artist, letter, destDisplay, placement.willCreateFolder, placement.placeDirectly, mutableStateOf(true))
                        } else null
                    } else null
                    onProgress(processed.incrementAndGet(), total)
                    item
                }
            }
        }.awaitAll()
    }

    return results.filterNotNull()
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

    val seenTargetKeys = mutableMapOf<String, MutableList<Uri>>()
    items.forEach { item ->
        val key = "${item.letter}/${item.artist}/${item.placeDirectlyInArtistFolder}/${item.fileName}"
        seenTargetKeys.getOrPut(key) { mutableListOf() }.add(item.documentFile.uri)
    }
    seenTargetKeys.values.filter { it.size > 1 }.forEach { uris -> dup.addAll(uris) }

    items.forEach { item ->
        if (!item.willCreateFolder) {
            val letterDir = rootTree.findChildByName(item.letter)
            val artistDir = letterDir?.findChildByName(item.artist)
            val targetDir = if (item.placeDirectlyInArtistFolder) artistDir else artistDir?.findChildByName(SINGLES_WORD)
            val existing = targetDir?.findChildByName(item.fileName)
            if (existing != null) dup.add(item.documentFile.uri)
        }
    }
    return dup
}

fun performSort(context: Context, rootUri: Uri, items: List<SortItem>, autoDeleteEmpty: Boolean, onProgress: (Int, Int) -> Unit): SortRunResult {
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
            val targetDir = if (item.placeDirectlyInArtistFolder) {
                artistDir
            } else {
                artistDir?.findChildByName(SINGLES_WORD) ?: artistDir?.createDirectory(SINGLES_WORD)
            }

            if (targetDir == null) {
                failures.add(FailureDetail(item.fileName, "לא ניתן ליצור את תיקיית היעד"))
                return@forEachIndexed
            }

            val mime = guessAudioMime(item.fileName)
            val existingAtTarget = targetDir.findChildByName(item.fileName)
            val newFile = existingAtTarget ?: targetDir.createFile(mime, item.fileName)

            if (newFile == null) {
                failures.add(FailureDetail(item.fileName, "יצירת קובץ היעד נכשלה"))
                return@forEachIndexed
            }

            val input = context.contentResolver.openInputStream(item.documentFile.uri)
            if (input == null) {
                failures.add(FailureDetail(item.fileName, "לא ניתן לקרוא את קובץ המקור"))
                return@forEachIndexed
            }
            val output = context.contentResolver.openOutputStream(newFile.uri)
            if (output == null) {
                input.close()
                failures.add(FailureDetail(item.fileName, "לא ניתן לכתוב לקובץ היעד"))
                return@forEachIndexed
            }
            val bytesCopied = input.use { inp -> output.use { out -> inp.copyTo(out) } }

            val destLength = newFile.length()
            if (destLength != bytesCopied) {
                newFile.delete()
                val msg = if (existingAtTarget != null) {
                    "אימות תקינות נכשל - ההעתקה נקטעה (קובץ קיים ביעד נדרס ונפגע)"
                } else {
                    "אימות תקינות נכשל - הגדלים אינם תואמים"
                }
                failures.add(FailureDetail(item.fileName, msg))
                return@forEachIndexed
            }

            val originalParent = item.documentFile.parentFile
            val originalName = item.fileName

            item.documentFile.delete()
            
            if (autoDeleteEmpty && originalParent != null && originalParent.listFiles().isEmpty()) {
                originalParent.delete()
            }

            success++
            records.add(MoveRecord(targetDir, item.fileName, originalParent, originalName))
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

        val input = context.contentResolver.openInputStream(movedFile.uri) ?: return false
        val output = context.contentResolver.openOutputStream(restored.uri)
        if (output == null) {
            input.close()
            return false
        }
        val bytesCopied = input.use { inp -> output.use { out -> inp.copyTo(out) } }

        val restoredLength = restored.length()
        if (restoredLength != bytesCopied) {
            return false
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

fun restoreHistoryEntry(context: Context, entry: HistoryEntry, selectedIndices: Set<Int>): Pair<Int, Int> {
    var success = 0
    var failed = 0
    val toRestore = entry.details.filterIndexed { index, _ -> selectedIndices.contains(index) }
    
    toRestore.forEach { d ->
        val ok = try {
            when (d.kind) {
                "rename" -> {
                    val folderUri = d.folderUri?.let { Uri.parse(it) }
                    val newName = d.newName
                    val oldName = d.oldName
                    if (folderUri != null && newName != null && oldName != null) {
                        undoRename(context, folderUri, newName, oldName)
                    } else false
                }
                "move" -> {
                    val destDirUri = d.destDirUri?.let { Uri.parse(it) }
                    val originalParentUri = d.originalParentUri?.let { Uri.parse(it) }
                    val fileName = d.fileName
                    val originalName = d.originalName
                    if (destDirUri != null && originalParentUri != null && fileName != null && originalName != null) {
                        val destDir = DocumentFile.fromTreeUri(context, destDirUri)
                        val originalParent = DocumentFile.fromTreeUri(context, originalParentUri)
                        if (destDir != null && originalParent != null) {
                            undoMove(context, MoveRecord(destDir, fileName, originalParent, originalName))
                        } else false
                    } else false
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
        if (ok) success++ else failed++
    }
    return success to failed
}
