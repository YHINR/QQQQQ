package com.example.batchrename

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

data class RenameItem(
    val documentFile: DocumentFile,
    val oldName: String,
    val newName: String,
    val checked: MutableState<Boolean>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
    var statusText by remember { mutableStateOf("בחר תיקייה כדי להתחיל") }

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
                "נמצאו ${items.size} קבצים"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { folderPicker.launch(null) }) {
            Text("בחר תיקייה")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(statusText)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.checked.value,
                        onCheckedChange = { item.checked.value = it }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(item.oldName)
                        Text("→ ${item.newName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Divider()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
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
                statusText = "הושלם: $success הצליחו, $failed נכשלו"
                folderUri?.let { items = scanFolder(context, it) }
            },
            enabled = items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("אשר ורץ")
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
