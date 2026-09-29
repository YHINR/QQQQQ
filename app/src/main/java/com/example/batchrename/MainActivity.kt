package com.example.batchrename

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- פלטת צבעים ----------
private val Primary = Color(0xFF6C5CE7)
private val PrimaryDark = Color(0xFF5646C7)
private val Accent = Color(0xFF00CEC9)
private val BgLight = Color(0xFFF7F7FC)
private val CardBg = Color(0xFFFFFFFF)
private val MutedText = Color(0xFF8A8A9E)
private val OldChipBg = Color(0xFFF1F1F7)
private val NewChipBg = Color(0xFFEDE9FE)
private val WarnChipBg = Color(0xFFFFF3E0)
private val WarnText = Color(0xFFE67E22)
private val HeaderGradient = Brush.horizontalGradient(listOf(Primary, PrimaryDark))

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "wma", "opus")
private const val SINGLES_WORD = "סינגלים"

enum class AppMode { RENAME, SORT }

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = lightColorScheme(
                primary = Primary,
                secondary = Accent,
                background = BgLight,
                surface = CardBg
            )
            MaterialTheme(colorScheme = colorScheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = BgLight) {
                        BatchRenameScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun BatchRenameScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf<AppMode?>(null) }

    // ---- מצב שינוי שמות ----
    var renameItems by remember { mutableStateOf(listOf<RenameItem>()) }
    var renameFolderUri by remember { mutableStateOf<Uri?>(null) }
    var hasRenameFolder by remember { mutableStateOf(false) }
    var isScanningRename by remember { mutableStateOf(false) }
    var renameStatusText by remember { mutableStateOf("") }

    // ---- מצב מיון סינגלים ----
    var sortRootUri by remember { mutableStateOf<Uri?>(null) }
    var sortFolderUri by remember { mutableStateOf<Uri?>(null) }
    var hasSortRoot by remember { mutableStateOf(false) }
    var hasSortFolder by remember { mutableStateOf(false) }
    var sortItems by remember { mutableStateOf(listOf<SortItem>()) }
    var isScanningSort by remember { mutableStateOf(false) }
    var sortStatusText by remember { mutableStateOf("") }

    // כפתור חזור פיזי: אם בתוך מצב פעולה - חזור לבית. אם בבית - התנהגות רגילה (יציאה).
    BackHandler(enabled = mode != null) {
        mode = null
    }

    val renameFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            renameFolderUri = uri
            hasRenameFolder = true
            renameStatusText = ""
            scope.launch {
                isScanningRename = true
                val result = withContext(Dispatchers.IO) { scanRenameFolder(context, uri) }
                renameItems = result
                isScanningRename = false
                if (result.isEmpty()) renameStatusText = "לא נמצאו קבצים בתבנית המתאימה"
            }
        }
    }

    val sortRootPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sortFolderUri = uri
            hasSortFolder = true
        }
    }

    // מריץ סריקה אוטומטית ברגע ששתי התיקיות (אב + מיון) נבחרו
    LaunchedEffect(sortRootUri, sortFolderUri) {
        val root = sortRootUri
        val folder = sortFolderUri
        if (root != null && folder != null) {
            sortStatusText = ""
            isScanningSort = true
            val result = withContext(Dispatchers.IO) { scanSortFolder(context, folder, root) }
            sortItems = result
            isScanningSort = false
            if (result.isEmpty()) sortStatusText = "לא נמצאו שירים עם התגית \"$SINGLES_WORD\""
        }
    }

    val renameCheckedCount = renameItems.count { it.checked.value }
    val renameAllSelected = renameItems.isNotEmpty() && renameCheckedCount == renameItems.size

    val sortCheckedCount = sortItems.count { it.checked.value }
    val sortAllSelected = sortItems.isNotEmpty() && sortCheckedCount == sortItems.size

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- כותרת ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderGradient)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (mode != null) {
                        IconButton(onClick = { mode = null }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "חזרה", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            when (mode) {
                                AppMode.RENAME -> "שינוי שמות קבצים"
                                AppMode.SORT -> "מיון סינגלים"
                                null -> "כלי קבצים"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when (mode) {
                            AppMode.RENAME -> if (renameItems.isNotEmpty()) "נמצאו ${renameItems.size} קבצים" else null
                            AppMode.SORT -> if (sortItems.isNotEmpty()) "נמצאו ${sortItems.size} שירים" else null
                            null -> null
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // אייקוני "החלף תיקייה" - רק אחרי שכבר נבחרה תיקייה
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mode == AppMode.RENAME && hasRenameFolder) {
                        IconButton(onClick = { renameFolderPicker.launch(null) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "החלף תיקייה", tint = Color.White)
                        }
                    }
                    if (mode == AppMode.SORT) {
                        if (hasSortRoot) {
                            IconButton(onClick = { sortRootPicker.launch(null) }) {
                                Icon(Icons.Filled.AccountTree, contentDescription = "החלף תיקיית אב", tint = Color.White)
                            }
                        }
                        if (hasSortFolder) {
                            IconButton(onClick = { sortFolderPicker.launch(null) }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = "החלף תיקיית מיון", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            when (mode) {

                // ---------- מסך בית: בחירת פעולה (ממורכז) ----------
                null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "מה תרצה לעשות?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedText,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        ModeCard(
                            icon = Icons.Filled.DriveFileRenameOutline,
                            title = "שינוי שמות קבצים",
                            description = "הפוך את סדר השם בקבצים עם התבנית \"XX - AA\"",
                            modifier = Modifier.fillMaxWidth()
                        ) { mode = AppMode.RENAME }

                        Spacer(modifier = Modifier.height(14.dp))

                        ModeCard(
                            icon = Icons.Filled.LibraryMusic,
                            title = "מיון סינגלים",
                            description = "סרוק תגיות שירים והעבר אוטומטית לתיקיית האמן המתאימה",
                            modifier = Modifier.fillMaxWidth()
                        ) { mode = AppMode.SORT }
                    }
                }

                // ---------- מצב שינוי שמות ----------
                AppMode.RENAME -> {
                    if (!hasRenameFolder) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FolderPickerCard(
                                icon = Icons.Filled.FolderOpen,
                                title = "בחר תיקייה",
                                description = "בחר את התיקייה שבה נמצאים הקבצים לשינוי שם",
                                onClick = { renameFolderPicker.launch(null) }
                            )
                        }
                    } else {
                        if (renameStatusText.isNotEmpty()) {
                            Text(renameStatusText, fontSize = 13.sp, color = MutedText, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        AnimatedVisibility(visible = renameItems.isNotEmpty()) {
                            SelectAllRow(
                                checkedCount = renameCheckedCount,
                                total = renameItems.size,
                                allSelected = renameAllSelected,
                                onToggle = {
                                    val newValue = !renameAllSelected
                                    renameItems.forEach { it.checked.value = newValue }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isScanningRename -> ScanningAnimation("סורק קבצים בתיקייה...")
                            renameItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו קבצים תואמים")
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                items(renameItems) { item -> FileRenameCard(item) }
                            }
                        }
                    }
                }

                // ---------- מצב מיון סינגלים ----------
                AppMode.SORT -> {
                    if (!hasSortRoot || !hasSortFolder) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!hasSortRoot) {
                                FolderPickerCard(
                                    icon = Icons.Filled.AccountTree,
                                    title = "בחר תיקיית אב",
                                    description = "התיקייה עם האותיות א׳ ב׳ ג׳ שבתוכן תיקיות האמנים",
                                    onClick = { sortRootPicker.launch(null) }
                                )
                            }
                            if (!hasSortRoot && !hasSortFolder) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (!hasSortFolder) {
                                FolderPickerCard(
                                    icon = Icons.Filled.FolderOpen,
                                    title = "בחר תיקיית מיון",
                                    description = "התיקייה עם השירים שיש לסרוק ולמיין",
                                    onClick = { sortFolderPicker.launch(null) }
                                )
                            }
                        }
                    } else {
                        if (sortStatusText.isNotEmpty()) {
                            Text(sortStatusText, fontSize = 13.sp, color = MutedText, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        AnimatedVisibility(visible = sortItems.isNotEmpty()) {
                            SelectAllRow(
                                checkedCount = sortCheckedCount,
                                total = sortItems.size,
                                allSelected = sortAllSelected,
                                onToggle = {
                                    val newValue = !sortAllSelected
                                    sortItems.forEach { it.checked.value = newValue }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isScanningSort -> ScanningAnimation("סורק שירים ובודק תגיות...")
                            sortItems.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "לא נמצאו שירים עם התגית \"$SINGLES_WORD\"")
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 18.dp)
                            ) {
                                items(sortItems) { item -> SortCard(item) }
                            }
                        }
                    }
                }
            }
        }

        // ---------- כפתור תחתון ----------
        val showConfirm = when (mode) {
            AppMode.RENAME -> hasRenameFolder && renameItems.isNotEmpty()
            AppMode.SORT -> hasSortRoot && hasSortFolder && sortItems.isNotEmpty()
            null -> false
        }
        AnimatedVisibility(visible = showConfirm) {
            Surface(shadowElevation = 12.dp, color = CardBg) {
                Box(modifier = Modifier.padding(10.dp)) {
                    if (mode == AppMode.RENAME) {
                        ConfirmButton(
                            label = "אשר ורץ ($renameCheckedCount)",
                            enabled = renameCheckedCount > 0,
                            onClick = {
                                var success = 0
                                var failed = 0
                                renameItems.filter { it.checked.value }.forEach { item ->
                                    try {
                                        if (item.documentFile.renameTo(item.newName)) success++ else failed++
                                    } catch (e: Exception) {
                                        failed++
                                    }
                                }
                                renameStatusText = "✅ הושלם: $success הצליחו" + if (failed > 0) ", $failed נכשלו" else ""
                                renameFolderUri?.let { uri ->
                                    scope.launch {
                                        renameItems = withContext(Dispatchers.IO) { scanRenameFolder(context, uri) }
                                    }
                                }
                            }
                        )
                    } else if (mode == AppMode.SORT) {
                        ConfirmButton(
                            label = "אשר והעבר ($sortCheckedCount)",
                            enabled = sortCheckedCount > 0,
                            onClick = {
                                val root = sortRootUri ?: return@ConfirmButton
                                val folder = sortFolderUri ?: return@ConfirmButton
                                val toMove = sortItems.filter { it.checked.value }
                                scope.launch {
                                    isScanningSort = true
                                    val (success, failed) = withContext(Dispatchers.IO) {
                                        performSort(context, root, toMove)
                                    }
                                    sortStatusText = "✅ הושלם: $success הועברו" + if (failed > 0) ", $failed נכשלו" else ""
                                    sortItems = withContext(Dispatchers.IO) { scanSortFolder(context, folder, root) }
                                    isScanningSort = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeCard(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = CardBg,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(NewChipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FolderPickerCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NewChipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MutedText)
        }
    }
}

@Composable
fun SelectAllRow(checkedCount: Int, total: Int, allSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(50),
            color = if (allSelected) OldChipBg else NewChipBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (allSelected) Icons.Filled.RemoveDone else Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = if (allSelected) MutedText else Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (allSelected) "בטל הכל" else "בחר הכל",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (allSelected) MutedText else Primary
                )
            }
        }

        Surface(shape = RoundedCornerShape(50), color = Primary) {
            Text(
                "$checkedCount / $total",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) Primary else MutedText.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MutedText, modifier = Modifier.size(52.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, color = MutedText, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
fun ScanningAnimation(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "rotation"
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(44.dp).graphicsLayer { rotationZ = rotation }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FileRenameCard(item: RenameItem) {
    Surface(
        onClick = { item.checked.value = !item.checked.value },
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked.value,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow(label = "לפני", name = item.oldName, isNew = false)
                Spacer(modifier = Modifier.height(3.dp))
                NameRow(label = "אחרי", name = item.newName, isNew = true)
            }
        }
    }
}

@Composable
fun SortCard(item: SortItem) {
    Surface(
        onClick = { item.checked.value = !item.checked.value },
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked.value,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow(label = "קובץ", name = item.fileName, isNew = false)
                Spacer(modifier = Modifier.height(3.dp))
                NameRow(label = "יעד", name = item.destDisplay, isNew = true)
                if (item.willCreateFolder) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = WarnChipBg) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CreateNewFolder,
                                contentDescription = null,
                                tint = WarnText,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("תיקייה חדשה תיווצר", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarnText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NameRow(label: String, name: String, isNew: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isNew) NewChipBg else OldChipBg,
            modifier = Modifier.padding(top = 1.dp)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNew) Primary else MutedText
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
            color = if (isNew) Primary else MutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== לוגיקת שינוי שמות ====================

fun scanRenameFolder(context: android.content.Context, uri: Uri): List<RenameItem> {
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

// ==================== לוגיקת מיון סינגלים ====================

private fun isAudioFile(name: String?): Boolean {
    if (name == null) return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

private fun collectAudioFiles(dir: DocumentFile, acc: MutableList<DocumentFile>) {
    dir.listFiles().forEach { f ->
        if (f.isDirectory) {
            collectAudioFiles(f, acc)
        } else if (f.isFile && isAudioFile(f.name)) {
            acc.add(f)
        }
    }
}

private fun extractArtistFromTags(context: android.content.Context, uri: Uri): String? {
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
        tag?.replace(SINGLES_WORD, "")
            ?.trim()
            ?.trim(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
        }
    }
}

private fun folderChainExists(rootTree: DocumentFile, letter: String, artist: String): Boolean {
    val letterDir = rootTree.findFile(letter) ?: return false
    val artistDir = letterDir.findFile(artist) ?: return false
    val singlesDir = artistDir.findFile(SINGLES_WORD) ?: return false
    return singlesDir.isDirectory
}

fun scanSortFolder(context: android.content.Context, folderUri: Uri, rootUri: Uri): List<SortItem> {
    val folderTree = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val rootTree = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

    val audioFiles = mutableListOf<DocumentFile>()
    collectAudioFiles(folderTree, audioFiles)

    val result = mutableListOf<SortItem>()
    audioFiles.forEach { file ->
        val name = file.name ?: return@forEach
        val artist = extractArtistFromTags(context, file.uri) ?: return@forEach
        val letter = artist.trim().firstOrNull()?.toString() ?: return@forEach
        val exists = folderChainExists(rootTree, letter, artist)
        val destDisplay = "$letter / $artist / $SINGLES_WORD / $name"
        result.add(SortItem(file, name, artist, letter, destDisplay, !exists, mutableStateOf(true)))
    }
    return result
}

private fun guessAudioMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wma" -> "audio/x-ms-wma"
        "opus" -> "audio/opus"
        else -> "audio/*"
    }
}

fun performSort(context: android.content.Context, rootUri: Uri, items: List<SortItem>): Pair<Int, Int> {
    val rootTree = DocumentFile.fromTreeUri(context, rootUri) ?: return 0 to items.size
    var success = 0
    var failed = 0

    items.forEach { item ->
        try {
            val letterDir = rootTree.findFile(item.letter) ?: rootTree.createDirectory(item.letter)
            val artistDir = letterDir?.findFile(item.artist) ?: letterDir?.createDirectory(item.artist)
            val singlesDir = artistDir?.findFile(SINGLES_WORD) ?: artistDir?.createDirectory(SINGLES_WORD)

            if (singlesDir == null) {
                failed++
                return@forEach
            }

            val mime = guessAudioMime(item.fileName)
            val newFile = singlesDir.findFile(item.fileName) ?: singlesDir.createFile(mime, item.fileName)

            if (newFile == null) {
                failed++
                return@forEach
            }

            context.contentResolver.openInputStream(item.documentFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            item.documentFile.delete()
            success++
        } catch (e: Exception) {
            failed++
        }
    }

    return success to failed
}
