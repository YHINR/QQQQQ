package com.example.batchrename

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile

data class RenameItem(
    val documentFile: DocumentFile,
    val oldName: String,
    val newName: String,
    val checked: MutableState<Boolean>
)

// ---------- פלטת צבעים מודרנית ----------
private val Primary = Color(0xFF6C5CE7)
private val PrimaryDark = Color(0xFF5646C7)
private val Accent = Color(0xFF00CEC9)
private val BgLight = Color(0xFFF7F7FC)
private val CardBg = Color(0xFFFFFFFF)
private val MutedText = Color(0xFF8A8A9E)
private val OldChipBg = Color(0xFFF1F1F7)
private val NewChipBg = Color(0xFFEDE9FE)

private val HeaderGradient = Brush.horizontalGradient(listOf(Primary, PrimaryDark))

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
                Surface(modifier = Modifier.fillMaxSize(), color = BgLight) {
                    BatchRenameScreen()
                }
            }
        }
    }
}

@Composable
fun BatchRenameScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(listOf<RenameItem>()) }
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    var statusText by remember { mutableStateOf("") }
    var hasFolder by remember { mutableStateOf(false) }

    val checkedCount = items.count { it.checked.value }
    val allSelected = items.isNotEmpty() && checkedCount == items.size

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folderUri = uri
            hasFolder = true
            items = scanFolder(context, uri)
            statusText = if (items.isEmpty()) "לא נמצאו קבצים בתבנית המתאימה" else ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- כותרת עם גרדיאנט ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderGradient)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "החלף בקליק",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (items.isNotEmpty()) "נמצאו ${items.size} קבצים להחלפה"
                    else "שינוי שמות קבצים בכמות גדולה",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // ---------- כפתור בחירת תיקייה ----------
            Surface(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NewChipBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (hasFolder) "החלף תיקייה" else "בחר תיקייה",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "לחץ כדי לסרוק קבצים",
                            fontSize = 12.sp,
                            color = MutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MutedText)
                }
            }

            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(statusText, fontSize = 13.sp, color = MutedText)
            }

            // ---------- שורת בחר הכל + מונה ----------
            AnimatedVisibility(visible = items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            val newValue = !allSelected
                            items.forEach { it.checked.value = newValue }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (allSelected) OldChipBg else NewChipBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Primary
                    ) {
                        Text(
                            "$checkedCount / ${items.size}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ---------- רשימת קבצים ----------
            if (hasFolder && items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("לא נמצאו קבצים תואמים", color = MutedText, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(items) { item ->
                        FileRenameCard(item)
                    }
                }
            }
        }

        // ---------- כפתור אשר ורץ ----------
        AnimatedVisibility(visible = items.isNotEmpty()) {
            Surface(shadowElevation = 12.dp, color = CardBg) {
                Box(modifier = Modifier.padding(14.dp)) {
                    Surface(
                        onClick = {
                            var success = 0
                            var failed = 0
                            items.filter { it.checked.value }.forEach { item ->
                                try {
                                    if (item.documentFile.renameTo(item.newName)) success++ else failed++
                                } catch (e: Exception) {
                                    failed++
                                }
                            }
                            statusText = "✅ הושלם: $success הצליחו" + if (failed > 0) ", $failed נכשלו" else ""
                            folderUri?.let { items = scanFolder(context, it) }
                        },
                        enabled = checkedCount > 0,
                        shape = RoundedCornerShape(16.dp),
                        color = if (checkedCount > 0) Primary else MutedText.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "אשר ורץ ($checkedCount)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileRenameCard(item: RenameItem) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked.value,
                onCheckedChange = { item.checked.value = it },
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                NameRow(label = "לפני", name = item.oldName, isNew = false)
                Spacer(modifier = Modifier.height(6.dp))
                NameRow(label = "אחרי", name = item.newName, isNew = true)
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

fun scanFolder(context: android.content.Context, uri: Uri): List<RenameItem> {
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
                        result.add(
                            RenameItem(file, name, newName, mutableStateOf(true))
                        )
                    }
                }
            }
        }
    }
    return result
}
