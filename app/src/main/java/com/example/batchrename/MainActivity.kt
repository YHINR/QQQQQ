package com.example.batchrename

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

data class RenameItem(
    val documentFile: DocumentFile,
    val oldName: String,
    val newName: String,
    val checked: MutableState<Boolean>
)

private val AppPrimary = Color(0xFF4F46E5)
private val AppSurfaceVariant = Color(0xFFF3F2FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = lightColorScheme(
                primary = AppPrimary,
                secondary = AppPrimary,
                surfaceVariant = AppSurfaceVariant
            )
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BatchRenameScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(listOf<RenameItem>()) }
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    var statusText by remember { mutableStateOf("בחר תיקייה כדי להתחיל") }
    var isRunning by remember { mutableStateOf(false) }

    val checkedCount = items.count { it.checked.value }

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
            items = scanFolder(context, uri)
            statusText = if (items.isEmpty())
                "לא נמצאו קבצים בתבנית המתאימה"
            else
                "נמצאו ${items.size} קבצים התואמים לתבנית"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Batch Rename", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                isRunning = true
                                var success = 0
                                var failed = 0
                                items.filter { it.checked.value }.forEach { item ->
                                    try {
                                        if (item.documentFile.renameTo(item.newName)) success++ else failed++
                                    } catch (e: Exception) {
                                        failed++
                                    }
                                }
                                statusText = "✅ הושלם: $success הצליחו, $failed נכשלו"
                                folderUri?.let { items = scanFolder(context, it) }
                                isRunning = false
                            },
                            enabled = checkedCount > 0 && !isRunning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("אשר ורץ ($checkedCount)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("בחר תיקייה", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = items.isNotEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            TextButton(onClick = {
                                items.forEach { it.checked.value = true }
                            }) {
                                Icon(Icons.Filled.CheckBox, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("בחר הכל")
                            }
                            TextButton(onClick = {
                                items.forEach { it.checked.value = false }
                            }) {
                                Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("בטל הכל")
                            }
                        }
                        Text(
                            "$checkedCount / ${items.size} נבחרו",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(items) { item ->
                    ElevatedCard(
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.checked.value,
                                onCheckedChange = { item.checked.value = it },
                                colors = CheckboxDefaults.colors(checkedColor = AppPrimary)
                            )
                            Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                                Text(
                                    item.oldName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = AppPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        item.newName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
