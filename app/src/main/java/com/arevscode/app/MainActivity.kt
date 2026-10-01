package com.arevscode.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF070A0F)
private val Panel = Color(0xFF0D121A)
private val Line = Color(0xFF202B38)
private val Cyan = Color(0xFF35D9FF)
private val Purple = Color(0xFF8B63FF)
private val Green = Color(0xFF50D890)
private val Red = Color(0xFFFF647C)
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
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMediaPermissions()
        setContent {
            MaterialTheme(colorScheme = AvescodeColors) {
                AvescodeIDE()
            }
        }
    }

    private fun requestMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
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
        currentDirectory()?.listFiles()?.map {
            WorkspaceItem(
                it.name.orEmpty().ifBlank { "Unnamed" },
                it.uri,
                it.isDirectory
            )
        }?.sortedWith(
            compareBy<WorkspaceItem> { !it.directory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

    fun enterDirectory(uri: Uri) {
        currentDirUri = uri
        currentFileUri = null
    }

    fun parent() {
        val root = rootUri ?: return
        val current = currentDirUri ?: return
        if (root == current) return

        val rootDoc = DocumentFile.fromTreeUri(context, root) ?: return

        fun findParent(parent: DocumentFile): Uri? {
            for (child in parent.listFiles()) {
                if (child.uri == current) return parent.uri

                if (child.isDirectory) {
                    val found = findParent(child)
                    if (found != null) return found
                }
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

    fun createFile(name: String): Uri? =
        currentDirectory()?.createFile(
            "text/plain",
            name.trim().ifBlank { "untitled.txt" }
        )?.uri

    fun createFolder(name: String): Boolean =
        currentDirectory()?.createDirectory(name.trim().ifBlank { "src" }) != null

    fun delete(uri: Uri): Boolean =
        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.delete() == true
        }.getOrDefault(false)

    fun fileName(uri: Uri?): String =
        uri?.let { DocumentFile.fromSingleUri(context, it)?.name } ?: "Untitled"
}

private class TerminalEngine(context: Context) {
    private var dir = File(context.filesDir, "terminal-workspace").apply { mkdirs() }

    fun pwd(): String = dir.absolutePath

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

                if (!canonical.isDirectory) {
                    "cd: no such directory: $target"
                } else {
                    dir = canonical
                    pwd()
                }
            }.getOrElse {
                "cd: shell error"
            }
        }

        return runCatching {
            val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(45, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                "Process timeout after 45 seconds"
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exit = process.exitValue()

                if (output.isBlank()) {
                    "(exit $exit)"
                } else {
                    output + "\n(exit $exit)"
                }
            }
        }.getOrElse {
            "shell: command failed"
        }
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
        mutableStateListOf(
            "Avescode Terminal",
            "Android /system/bin/sh",
            terminal.pwd()
        )
    }

    val picker = rememberLauncherForActivityResult(
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
        } else {
            workspace.currentFileUri = item.uri
            fileName = item.name
            editorText = workspace.read(item.uri)
            dirty = false
            tool = Tool.EDITOR
        }
    }

    fun save() {
        val current = workspace.currentFileUri

        if (current != null) {
            dirty = !workspace.write(current, editorText)
        } else {
            val created = workspace.createFile(fileName) ?: return
            workspace.currentFileUri = created
            dirty = !workspace.write(created, editorText)
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopBar(
                workspaceName = workspace.rootName,
                tool = tool,
                onOpen = { picker.launch(null) },
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tool) {
                Tool.EXPLORER -> Explorer(
                    workspace = workspace,
                    onOpenWorkspace = { picker.launch(null) },
                    onOpen = ::openItem,
                    onNewFile = { newFileDialog = true },
                    onNewFolder = { newFolderDialog = true },
                    onUp = { workspace.parent() },
                    onDelete = { workspace.delete(it) }
                )

                Tool.EDITOR -> Editor(
                    fileName = fileName,
                    text = editorText,
                    dirty = dirty,
                    onChange = {
                        editorText = it
                        dirty = true
                    },
                    onSave = {
                        save()
                        fileName = workspace.fileName(workspace.currentFileUri)
                    },
                    onPreview = { preview = true }
                )

                Tool.TERMINAL -> Terminal(
                    terminalLines = terminalLines,
                    terminal = terminal,
                    onLines = {
                        terminalLines.clear()
                        terminalLines.addAll(it)
                    }
                )
            }
        }
    }

    if (newFileDialog) {
        NameDialog(
            title = "New file",
            placeholder = "main.kt",
            confirm = "Create",
            onConfirm = {
                workspace.createFile(it)?.let { uri ->
                    workspace.currentFileUri = uri
                    fileName = workspace.fileName(uri)
                    editorText = ""
                    dirty = false
                    tool = Tool.EDITOR
                }
                newFileDialog = false
            },
            onDismiss = { newFileDialog = false }
        )
    }

    if (newFolderDialog) {
        NameDialog(
            title = "New folder",
            placeholder = "src",
            confirm = "Create",
            onConfirm = {
                workspace.createFolder(it)
                newFolderDialog = false
            },
            onDismiss = { newFolderDialog = false }
        )
    }

    if (palette) {
        CommandPalette(
            onExplorer = {
                tool = Tool.EXPLORER
                palette = false
            },
            onEditor = {
                tool = Tool.EDITOR
                palette = false
            },
            onTerminal = {
                tool = Tool.TERMINAL
                palette = false
            },
            onNewFile = {
                palette = false
                newFileDialog = true
            },
            onOpen = {
                palette = false
                picker.launch(null)
            },
            onDismiss = { palette = false }
        )
    }

    if (preview) {
        WebPreview(editorText) {
            preview = false
        }
    }
}

@Composable
private fun TopBar(
    workspaceName: String,
    tool: Tool,
    onOpen: () -> Unit,
    onPalette: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Panel, Color(0xFF101826))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Avescode",
                color = TextMain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                tool.label + " • " + workspaceName,
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        TextButton(onClick = onPalette) {
            Text("⌘", color = Cyan, fontSize = 18.sp)
        }

        TextButton(onClick = onOpen) {
            Text("Open", color = TextMain)
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
    onUp: () -> Unit,
    onDelete: (Uri) -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(onClick = onOpenWorkspace) {
                Text("Open")
            }

            OutlinedButton(onClick = onNewFile) {
                Text("+ File")
            }

            OutlinedButton(onClick = onNewFolder) {
                Text("+ Folder")
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Explorer",
                color = TextDim,
                fontSize = 11.sp
            )

            TextButton(onClick = onUp) {
                Text("↑ Up")
            }
        }

        if (workspace.rootUri == null) {
            EmptyState(
                title = "No workspace",
                message = "Open a folder to start coding.",
                action = onOpenWorkspace
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(
                    workspace.items(),
                    key = { it.uri.toString() }
                ) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (item.directory) "▸" else "•",
                            color = if (item.directory) Cyan else Green,
                            fontSize = 17.sp
                        )

                        Spacer(Modifier.width(10.dp))

                        Text(
                            item.name,
                            color = TextMain,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )

                        TextButton(
                            onClick = {
                                onDelete(item.uri)
                            }
                        ) {
                            Text("×", color = Red)
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
    text: String,
    dirty: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                fileName + if (dirty) " •" else "",
                color = if (dirty) Cyan else TextMain,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onSave) {
                Text("Save")
            }

            TextButton(onClick = onPreview) {
                Text("Preview")
            }
        }

        BasicTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            textStyle = TextStyle(
                color = TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            cursorBrush = SolidColor(Cyan)
        )
    }
}

@Composable
private fun Terminal(
    terminalLines: List<String>,
    terminal: TerminalEngine,
    onLines: (List<String>) -> Unit
) {
    var command by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            items(terminalLines) { line ->
                Text(
                    line,
                    color = if (line.startsWith("Avescode")) Cyan else TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                color = Green,
                fontFamily = FontFamily.Monospace
            )

            BasicTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = TextMain,
                    fontFamily = FontFamily.Monospace
                )
            )

            TextButton(
                onClick = {
                    val result = terminal.run(command)

                    if (result == "__CLEAR__") {
                        onLines(emptyList())
                    } else {
                        val next = terminalLines.toMutableList()
                        next.add("$ " + command)

                        if (result.isNotBlank()) {
                            next.addAll(result.lines())
                        }

                        onLines(next)
                    }

                    command = ""
                }
            ) {
                Text("Run", color = Cyan)
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    action: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            message,
            color = TextDim,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))

        Button(onClick = action) {
            Text("Open folder")
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    placeholder: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = {
                    Text(placeholder)
                },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(value)
                }
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Text(
                    "Command Palette",
                    color = TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                PaletteButton("Explorer", onExplorer)
                PaletteButton("Editor", onEditor)
                PaletteButton("Terminal", onTerminal)
                PaletteButton("New File", onNewFile)
                PaletteButton("Open Folder", onOpen)

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun PaletteButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun IdeBottomBar(
    selected: Tool,
    onSelect: (Tool) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF090D13))
            .navigationBarsPadding()
            .border(1.dp, Line)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ToolBottomButton(
            selected == Tool.EXPLORER,
            modifier = Modifier.weight(1f),
            "⌁",
            "Explorer"
        ) {
            onSelect(Tool.EXPLORER)
        }

        ToolBottomButton(
            selected == Tool.EDITOR,
            modifier = Modifier.weight(1f),
            "</>",
            "Editor"
        ) {
            onSelect(Tool.EDITOR)
        }

        ToolBottomButton(
            selected == Tool.TERMINAL,
            modifier = Modifier.weight(1f),
            ">_",
            "Terminal"
        ) {
            onSelect(Tool.TERMINAL)
        }
    }
}

@Composable
private fun ToolBottomButton(
    selected: Boolean,
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
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
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )

        Text(
            label,
            color = if (selected) Cyan else TextDim,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun WebPreview(
    html: String,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Close", color = TextMain)
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(
                            "https://avescode.local/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private val WELCOME = """
# Avescode 0.9.1

VS Code + Termux style Android IDE.

Explorer:
- Open a workspace folder.
- Create files and folders.
- Open, edit, save and delete files.

Editor:
- Monospace code editor.
- Save and HTML preview.

Terminal:
- Android /system/bin/sh.
- pwd, cd and normal shell commands.
- 45 second command timeout.

Avescode stays focused on coding and terminal workflows.
""".trimIndent()
