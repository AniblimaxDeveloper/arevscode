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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
        DocumentFile.fromSingleUri(context, uri ?: return Uri.EMPTY)?.name ?: "Untitled"
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
            NavigationBar(
                containerColor = Color(0xFF090D13),
                modifier = Modifier.navigationBarsPadding()
            ) {
                ToolNavItem(tool, Tool.EXPLORER) { tool = Tool.EXPLORER }
                ToolNavItem(tool, Tool.EDITOR) { tool = Tool.EDITOR }
                ToolNavItem(tool, Tool.TERMINAL) { tool = Tool.TERMINAL }
            }
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
private fun ToolNavItem(
    selected: Tool,
    tool: Tool,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected == tool,
        onClick = onClick,
        icon = {
            Text(
                when (tool) {
                    Tool.EXPLORER -> "⌁"
                    Tool.EDITOR -> "</>"
                    Tool.TERMINAL -> ">_"
                }
            )
        },
        label = { Text(tool.label) }
    )
}

@Composable
private fun TopBar(
    workspace: String,
    tool: Tool,
    onOpen: () -> Unit,
    onPalette: () -> Unit
) {
    Column(
        Modifier
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF07141D),
                        Color(0xFF141024),
                        Color(0xFF090D14)
                    )
                )
            )
            .border(1.dp, Line)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("</>", color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Avescode", fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("$workspace • ${tool.label}", fontSize = 9.sp, color = TextDim)
            }
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onPalette) { Text("⌘") }
        }
    }
}

@Composable
private fun Explorer(
    workspace: WorkspaceStore,
    onOpenWorkspace: () -> Unit,
    onOpen: (WorkspaceItem) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onUp: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = onOpenWorkspace) { Text("Open Workspace") }
            OutlinedButton(onClick = onNewFile, enabled = workspace.rootUri != null) { Text("+ File") }
            OutlinedButton(onClick = onNewFolder, enabled = workspace.rootUri != null) { Text("+ Folder") }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("WORKSPACE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(workspace.rootName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("SAF folder access • no broad storage permission", color = TextDim, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (workspace.rootUri == null) {
            EmptyPanel(
                "Open a project",
                "Avescode is now focused on the VS Code + Termux workflow.",
                onOpenWorkspace
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FILES", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                TextButton(onClick = onUp) { Text("..") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(workspace.items(), key = { it.uri.toString() }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Panel2, RoundedCornerShape(12.dp))
                            .border(1.dp, Line, RoundedCornerShape(12.dp))
                            .clickable { onOpen(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (item.directory) "📁" else "▣")
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(if (item.directory) "folder" else languageOf(item.name), color = TextDim, fontSize = 9.sp)
                        }
                        TextButton(onClick = { workspace.delete(item.uri) }) {
                            Text("×", color = Color(0xFFFF6A80), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Editor(
    fileName: String,
    code: String,
    dirty: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(fileName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(if (dirty) "● modified" else "saved", color = if (dirty) Color(0xFFFFC857) else Green, fontSize = 9.sp)
            TextButton(onClick = onSave) { Text("Save") }
            if (fileName.endsWith(".html", true)) {
                TextButton(onClick = onPreview) { Text("Run") }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0A0F16)).horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("CTRL", "SHIFT", "ALT", "ESC", "TAB", "CTRL+S", "CTRL+F", "CTRL+Z", "CTRL+Y").forEach {
                Surface(color = Color(0xFF141D28), shape = RoundedCornerShape(7.dp)) {
                    Text(it, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = TextDim, fontSize = 8.sp)
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF080B10)).padding(8.dp)) {
            BasicTextField(
                value = code,
                onValueChange = onChange,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                textStyle = TextStyle(
                    color = TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxSize()) {
                        val count = maxOf(1, code.count { it == '\n' } + 1)
                        Column {
                            repeat(count) {
                                Text("${it + 1}", color = Color(0xFF4C596B), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 19.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.fillMaxSize()) { inner() }
                    }
                }
            )
        }
    }
}

@Composable
private fun Terminal(
    lines: List<String>,
    terminal: TerminalEngine,
    onLines: (List<String>) -> Unit
) {
    var command by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFF030506))) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(">_", color = Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Terminal", fontWeight = FontWeight.Bold)
                Text(terminal.pwd(), color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp)
        ) {
            lines.forEach {
                Text(
                    it,
                    color = if (it.startsWith("Avescode")) Cyan else Color(0xFFD3D9E3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ls", "pwd", "git status", "cd ..", "clear").forEach { cmd ->
                OutlinedButton(onClick = { command = cmd }) {
                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = Green, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true
            )
            TextButton(
                onClick = {
                    val result = terminal.run(command)
                    if (result == "__CLEAR__") {
                        onLines(emptyList())
                    } else {
                        onLines(lines + listOf("$ $command", result))
                    }
                    command = ""
                }
            ) { Text("Run") }
        }
    }
}

@Composable
private fun EmptyPanel(title: String, body: String, action: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("</>", color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TextDim, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = action) { Text("Open Workspace") }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CommandPalette(
    onExplorer: () -> Unit,
    onEditor: () -> Unit,
    onTerminal: () -> Unit,
    onNewFile: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command Palette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "Explorer" to onExplorer,
                    "Editor" to onEditor,
                    "Terminal" to onTerminal,
                    "New File" to onNewFile,
                    "Open Workspace" to onOpen
                ).forEach { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun WebPreview(
    html: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web Preview") },
        text = {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                    }
                },
                update = {
                    it.loadDataWithBaseURL(
                        null,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                modifier = Modifier.fillMaxWidth().height(420.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun languageOf(name: String): String =
    when {
        name.endsWith(".kt", true) -> "Kotlin"
        name.endsWith(".java", true) -> "Java"
        name.endsWith(".js", true) -> "JavaScript"
        name.endsWith(".ts", true) -> "TypeScript"
        name.endsWith(".html", true) -> "HTML"
        name.endsWith(".css", true) -> "CSS"
        name.endsWith(".json", true) -> "JSON"
        name.endsWith(".xml", true) -> "XML"
        name.endsWith(".py", true) -> "Python"
        name.endsWith(".sh", true) -> "Shell"
        name.endsWith(".md", true) -> "Markdown"
        else -> "Text"
    }

private const val WELCOME = """
# Avescode

Focused mobile coding workspace.

Core:
- Explorer
- Editor
- Terminal
- Command Palette
- HTML Preview
- Workspace file/folder management

Open a project folder to start.
"""
