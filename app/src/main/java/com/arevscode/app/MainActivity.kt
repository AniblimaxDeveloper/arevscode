package com.arevscode.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF070A0F)
private val Panel = Color(0xFF0D121A)
private val Panel2 = Color(0xFF121A25)
private val Line = Color(0xFF202B38)
private val Cyan = Color(0xFF35D9FF)
private val Purple = Color(0xFF8B63FF)
private val Green = Color(0xFF50D890)
private val TextMain = Color(0xFFEAF1FA)
private val TextDim = Color(0xFF8794A6)

private val AvescodeColors = darkColorScheme(
    primary = Cyan,
    secondary = Purple,
    background = Bg,
    surface = Panel,
    onBackground = TextMain,
    onSurface = TextMain
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AvescodeColors) {
                AvescodeIDE()
            }
        }
    }
}

private enum class Tool(val label: String) {
    EXPLORER("Explorer"),
    EDITOR("Editor"),
    TERMINAL("Terminal")
}

private data class WorkspaceItem(
    val name: String,
    val uri: Uri,
    val directory: Boolean
)

private class WorkspaceStore(private val context: Context) {
    private val resolver get() = context.contentResolver

    var rootUri by mutableStateOf<Uri?>(null)
    var currentDirUri by mutableStateOf<Uri?>(null)
    var currentFileUri by mutableStateOf<Uri?>(null)
    var rootName by mutableStateOf("No workspace")

    fun setRoot(uri: Uri) {
        rootUri = uri
        currentDirUri = uri
        currentFileUri = null
        rootName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Workspace"
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun currentDirectory(): DocumentFile? {
        val uri = currentDirUri ?: return null
        return if (uri == rootUri) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }

    fun items(): List<WorkspaceItem> =
        currentDirectory()
            ?.listFiles()
            ?.map {
                WorkspaceItem(
                    it.name.orEmpty().ifBlank { "Unnamed" },
                    it.uri,
                    it.isDirectory
                )
            }
            ?.sortedWith(compareBy<WorkspaceItem> { !it.directory }.thenBy { it.name.lowercase() })
            ?: emptyList()

    fun enterDirectory(uri: Uri) {
        currentDirUri = uri
        currentFileUri = null
    }

    fun parent() {
        val root = rootUri ?: return
        val current = currentDirUri ?: return
        if (current == root) return
        val rootDoc = DocumentFile.fromTreeUri(context, root) ?: return

        fun findParent(parent: DocumentFile): Uri? {
            for (child in parent.listFiles()) {
                if (child.uri == current) return parent.uri
                if (child.isDirectory) findParent(child)?.let { return it }
            }
            return null
        }

        currentDirUri = findParent(rootDoc) ?: root
        currentFileUri = null
    }

    fun read(uri: Uri): String =
        runCatching {
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }.getOrDefault("")

    fun write(uri: Uri, text: String): Boolean =
        runCatching {
            resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            true
        }.getOrDefault(false)

    fun createFile(name: String): Uri? {
        val parent = currentDirectory() ?: return null
        return parent.createFile("text/plain", name.trim().ifBlank { "untitled.txt" })?.uri
    }

    fun createFolder(name: String): Boolean {
        val parent = currentDirectory() ?: return false
        return parent.createDirectory(name.trim().ifBlank { "src" }) != null
    }

    fun delete(uri: Uri): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }.getOrDefault(false)

    fun fileName(uri: Uri?): String =
        if (uri == null) {
            "Untitled"
        } else {
            DocumentFile.fromSingleUri(context, uri)?.name ?: "Untitled"
        }
}

private class TerminalEngine(context: Context) {
    private var dir = File(context.filesDir, "terminal-workspace").apply { mkdirs() }

    fun pwd() = dir.absolutePath

    fun run(command: String): String {
        val cmd = command.trim()
        if (cmd.isBlank()) return ""
        if (cmd == "clear") return "__CLEAR__"
        if (cmd == "pwd") return pwd()

        if (cmd == "cd ..") {
            dir = dir.parentFile ?: dir
            return pwd()
        }

        if (cmd.startsWith("cd ")) {
            val target = cmd.removePrefix("cd ").trim()
            val next = if (target.startsWith("/")) File(target) else File(dir, target)
            return runCatching {
                val canonical = next.canonicalFile
                if (!canonical.isDirectory) "cd: no such directory: $target"
                else {
                    dir = canonical
                    pwd()
                }
            }.getOrElse { "cd: ${it.message ?: "error"}" }
        }

        return runCatching {
            val p = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(45, TimeUnit.SECONDS)
            val exit = p.exitValue()
            if (out.isBlank()) "(exit $exit)" else "$out\n(exit $exit)"
        }.getOrElse { "shell: ${it.message ?: "unknown error"}" }
    }
}

@Composable
private fun AvescodeIDE() {
    val context = LocalContext.current
    val workspace = remember { WorkspaceStore(context) }
    val terminal = remember { TerminalEngine(context) }

    var tool by remember { mutableStateOf(Tool.EXPLORER) }
    var fileName by remember { mutableStateOf("Welcome.md") }
    var editorText by remember { mutableStateOf(WELCOME) }
    var dirty by remember { mutableStateOf(false) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }

    val terminalLines = remember {
        mutableStateListOf("Avescode Terminal", "Android /system/bin/sh", terminal.pwd())
    }

    val workspacePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            workspace.setRoot(uri)
            tool = Tool.EXPLORER
        }
    }

    fun openItem(item: WorkspaceItem) {
        if (item.directory) {
            workspace.enterDirectory(item.uri)
            return
        }
        workspace.currentFileUri = item.uri
        fileName = item.name
        editorText = workspace.read(item.uri)
        dirty = false
        tool = Tool.EDITOR
    }

    fun save() {
        val uri = workspace.currentFileUri
        if (uri != null) {
            dirty = !workspace.write(uri, editorText)
            return
        }
        val created = workspace.createFile(fileName) ?: return
        workspace.currentFileUri = created
        dirty = !workspace.write(created, editorText)
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopBar(
                workspace.rootName,
                tool,
                onOpen = { workspacePicker.launch(null) },
                onPalette = { palette = true }
            )
        },
        bottomBar = {
            IdeBottomBar(
                selected = tool,
                onSelect = { tool = it }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tool) {
                Tool.EXPLORER -> Explorer(
                    workspace,
                    onOpenWorkspace = { workspacePicker.launch(null) },
                    onOpen = ::openItem,
                    onNewFile = { newFileDialog = true },
                    onNewFolder = { newFolderDialog = true },
                    onUp = { workspace.parent() }
                )
                Tool.EDITOR -> Editor(
                    fileName,
                    editorText,
                    dirty,
                    onChange = { editorText = it; dirty = true },
                    onSave = { save(); fileName = workspace.fileName(workspace.currentFileUri) },
                    onPreview = { preview = true }
                )
                Tool.TERMINAL -> Terminal(
                    terminalLines,
                    terminal
                ) {
                    terminalLines.clear()
                    terminalLines.addAll(it)
                }
            }
        }
    }

    if (newFileDialog) {
        NameDialog("New file", "main.kt", "Create", {
            workspace.createFile(it)?.let { uri ->
                workspace.currentFileUri = uri
                fileName = workspace.fileName(uri)
                editorText = ""
                dirty = false
                tool = Tool.EDITOR
            }
            newFileDialog = false
        }) { newFileDialog = false }
    }

    if (newFolderDialog) {
        NameDialog("New folder", "src", "Create", {
            workspace.createFolder(it)
            newFolderDialog = false
        }) { newFolderDialog = false }
    }

    if (palette) {
        CommandPalette(
            onExplorer = { tool = Tool.EXPLORER; palette = false },
            onEditor = { tool = Tool.EDITOR; palette = false },
            onTerminal = { tool = Tool.TERMINAL; palette = false },
            onNewFile = { palette = false; newFileDialog = true },
            onOpen = { palette = false; workspacePicker.launch(null) },
            onDismiss = { palette = false }
        )
    }

    if (preview) {
        WebPreview(editorText) { preview = false }
    }
}

@Composable
private fun IdeBottomBar(
    selected: Tool,
    onSelect: (Tool) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF090D13))
            .navigationBarsPadding()
            .border(1.dp, Line)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolBottomButton(
            selected = selected == Tool.EXPLORER,
            icon = "⌁",
            label = "Explorer",
            onClick = { onSelect(Tool.EXPLORER) }
        )
        ToolBottomButton(
            selected = selected == Tool.EDITOR,
            icon = "</>",
            label = "Editor",
            onClick = { onSelect(Tool.EDITOR) }
        )
        ToolBottomButton(
            selected = selected == Tool.TERMINAL,
            icon = ">_",
            label = "Terminal",
            onClick = { onSelect(Tool.TERMINAL) }
        )
    }
}

@Composable
private fun ToolBottomButton(
    selected: Boolean,
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(
                if (selected) Color(0xFF172333) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            icon,
            color = if (selected) Cyan else TextDim,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp
        )
        Text(
            label,
            color = if (selected) Cyan else TextDim,
            fontSize = 9.sp
        )
    }
}
