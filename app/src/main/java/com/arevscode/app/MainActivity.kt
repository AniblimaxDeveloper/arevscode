package com.arevscode.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArevscodeApp() }
    }
}

enum class Screen(val label: String, val glyph: String) {
    Home("Home", "⌂"),
    Explorer("Explorer", "▣"),
    Editor("Editor", "✎"),
    Terminal("Terminal", "›_"),
    GitHub("GitHub", "◉"),
    Copilot("Copilot", "✦"),
    Settings("Settings", "⚙")
}

data class WorkspaceFile(val name: String, val uri: Uri, val directory: Boolean)

data class ChatMessage(val fromUser: Boolean, val text: String)

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("arevscode", Context.MODE_PRIVATE)
    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_key", value).apply()
    var model: String
        get() = prefs.getString("gemini_model", "gemini-3.8-flash") ?: "gemini-3.8-flash"
        set(value) = prefs.edit().putString("gemini_model", value).apply()
}

class ProjectStore(private val context: Context) {
    private val resolver get() = context.contentResolver
    var rootUri by mutableStateOf<Uri?>(null)
    var rootName by mutableStateOf("No workspace")
    var currentUri by mutableStateOf<Uri?>(null)

    fun setRoot(uri: Uri) {
        rootUri = uri
        currentUri = uri
        rootName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Workspace"
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {}
    }

    fun listCurrent(): List<WorkspaceFile> {
        val uri = currentUri ?: rootUri ?: return emptyList()
        val doc = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: return emptyList()

        return doc.listFiles()
            .map { file ->
                WorkspaceFile(
                    name = file.name.orEmpty().ifBlank { "Unnamed" },
                    uri = file.uri,
                    directory = file.isDirectory
                )
            }
            .sortedWith(
                compareBy<WorkspaceFile> { !it.directory }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun enter(uri: Uri) {
        currentUri = uri
    }

    fun up() {
        val root = rootUri ?: return
        if (currentUri == root) return
        currentUri = findParent(root, currentUri) ?: root
    }

    private fun findParent(root: Uri, child: Uri?): Uri? {
        if (child == null) return root
        val rootDoc = DocumentFile.fromTreeUri(context, root) ?: return root
        fun walk(parent: DocumentFile): Uri? {
            for (f in parent.listFiles()) {
                if (f.uri == child) return parent.uri
                if (f.isDirectory) walk(f)?.let { return it }
            }
            return null
        }
        return walk(rootDoc)
    }

    fun readText(uri: Uri): String = runCatching {
        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }.getOrDefault("")

    fun writeText(uri: Uri, text: String): Boolean = runCatching {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
        true
    }.getOrDefault(false)
}

class ShellEngine(private val context: Context) {
    private var workingDir = File(context.filesDir, "workspace").apply { mkdirs() }

    fun pwd(): String = workingDir.absolutePath

    fun run(raw: String): String {
        val command = raw.trim()
        if (command.isEmpty()) return ""
        if (command == "pwd") return workingDir.absolutePath
        if (command.startsWith("cd ")) {
            val target = command.removePrefix("cd ").trim().ifEmpty { "." }
            val next = if (target.startsWith("/")) File(target) else File(workingDir, target)
            if (next.isDirectory) {
                workingDir = next.canonicalFile
                return workingDir.absolutePath
            }
            return "cd: no such directory: $target"
        }
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(45, TimeUnit.SECONDS)
            if (out.isBlank()) "(exit ${process.exitValue()})" else out.trimEnd()
        } catch (e: Exception) {
            "shell error: ${e.message ?: "unknown error"}"
        }
    }
}

class GeminiCopilot(private val prefs: Prefs) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun ask(prompt: String, code: String): String {
        val key = prefs.geminiKey.trim()
        if (key.isEmpty()) return "Masukkan Gemini API key di Settings terlebih dahulu."
        val model = prefs.model.ifBlank { "gemini-3.8-flash" }
        val instruction = "You are arevscode Copilot, an expert mobile software engineering assistant. Be precise. Diagnose errors, propose safe edits, explain why, and return copy-pasteable code when useful. User request:\n$prompt\n\nCurrent editor context:\n$code"
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", instruction)))
            ))
        }.toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", key)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use "Gemini HTTP ${response.code}: ${raw.take(500)}"
                val json = JSONObject(raw)
                json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text")?.takeIf { it.isNotBlank() }
                    ?: "Copilot tidak mengembalikan teks."
            }
        }.getOrElse { "Copilot error: ${it.message ?: "unknown error"}" }
    }
}

@Composable
fun ArevscodeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    val project = remember { ProjectStore(context) }
    val shell = remember { ShellEngine(context) }
    val copilot = remember { GeminiCopilot(prefs) }
    var screen by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var openFileName by rememberSaveable { mutableStateOf("Welcome.md") }
    var openFileUriString by rememberSaveable { mutableStateOf("") }
    var editorText by rememberSaveable { mutableStateOf(WELCOME_CODE) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var terminalOutput by remember { mutableStateOf(listOf("arevscode terminal ready", "Type 'help' or any shell command.")) }
    var terminalInput by remember { mutableStateOf("") }
    var terminalBusy by remember { mutableStateOf(false) }
    var chats by remember { mutableStateOf(listOf(ChatMessage(false, "Hai 👋 Saya Copilot arevscode. Saya dapat membantu memperbaiki error, menjelaskan kode, atau membuat fitur baru."))) }
    var copilotInput by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var settingsKey by remember { mutableStateOf(prefs.geminiKey) }
    var settingsModel by remember { mutableStateOf(prefs.model) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            project.setRoot(uri)
            screen = Screen.Explorer.name
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF8B6CFF),
        secondary = Color(0xFF42D7FF),
        background = Color(0xFF080A0F),
        surface = Color(0xFF10131A),
        surfaceVariant = Color(0xFF171B24),
        onSurfaceVariant = Color(0xFFA5ABBA)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ResponsiveShell(
                screen = Screen.valueOf(screen),
                onNavigate = { screen = it.name },
                title = project.rootName,
                content = {
                    when (Screen.valueOf(screen)) {
                        Screen.Home -> HomeScreen(onOpen = { screen = it.name }, workspace = project.rootName)
                        Screen.Explorer -> ExplorerScreen(project, onOpenFile = { item ->
                            if (item.directory) project.enter(item.uri)
                            else {
                                openFileName = item.name
                                openFileUriString = item.uri.toString()
                                editorText = project.readText(item.uri)
                                dirty = false
                                screen = Screen.Editor.name
                            }
                        }, onChoose = { folderPicker.launch(null) })
                        Screen.Editor -> EditorScreen(openFileName, editorText, dirty, onChange = { editorText = it; dirty = true }, onSave = {
                            if (openFileUriString.isNotBlank()) {
                                val ok = project.writeText(Uri.parse(openFileUriString), editorText)
                                dirty = !ok
                                Toast.makeText(context, if (ok) "Saved" else "Save failed", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(context, "Open a file first", Toast.LENGTH_SHORT).show()
                        })
                        Screen.Terminal -> TerminalScreen(terminalOutput, terminalInput, terminalBusy, onInput = { terminalInput = it }, onRun = {
                            val cmd = terminalInput.trim()
                            if (cmd.isNotEmpty()) {
                                terminalBusy = true
                                terminalOutput = terminalOutput + "arev@arevscode:${shell.pwd().substringAfterLast("/")} $cmd"
                                terminalInput = ""
                                scope.launch(Dispatchers.IO) {
                                    val result = shell.run(cmd)
                                    terminalOutput = terminalOutput + result
                                    terminalBusy = false
                                }
                            }
                        }, onClear = { terminalOutput = emptyList() })
                        Screen.GitHub -> GitHubScreen(projectName = project.rootName, onOpenTerminal = { screen = Screen.Terminal.name })
                        Screen.Copilot -> CopilotScreen(chats, copilotInput, aiBusy, onInput = { copilotInput = it }, onAsk = {
                            val prompt = copilotInput.trim()
                            if (prompt.isNotEmpty()) {
                                chats = chats + ChatMessage(true, prompt)
                                copilotInput = ""
                                aiBusy = true
                                scope.launch(Dispatchers.IO) {
                                    val answer = copilot.ask(prompt, editorText)
                                    chats = chats + ChatMessage(false, answer)
                                    aiBusy = false
                                }
                            }
                        }, onQuick = { action -> copilotInput = action })
                        Screen.Settings -> SettingsScreen(settingsKey, settingsModel, onKey = { settingsKey = it }, onModel = { settingsModel = it }, onSave = {
                            prefs.geminiKey = settingsKey.trim()
                            prefs.model = settingsModel.trim().ifBlank { "gemini-3.8-flash" }
                            Toast.makeText(context, "Settings tersimpan", Toast.LENGTH_SHORT).show()
                        }, onSystemSettings = { })
                    }
                }
            )
        }
    }
}

@Composable
fun ResponsiveShell(screen: Screen, onNavigate: (Screen) -> Unit, title: String, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = Color(0xFF0B0D13),
                    modifier = Modifier.width(76.dp)
                ) {
                    Spacer(Modifier.height(16.dp))
                    LogoMark()
                    Spacer(Modifier.height(22.dp))
                    listOf(Screen.Home, Screen.Explorer, Screen.Editor, Screen.Terminal, Screen.GitHub, Screen.Copilot, Screen.Settings).forEach {
                        NavigationRailItem(selected = screen == it, onClick = { onNavigate(it) }, icon = { Text(it.glyph, fontSize = 18.sp) }, label = null, alwaysShowLabel = false)
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    TopBar(title)
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(title)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                NavigationBar(containerColor = Color(0xFF0B0D13)) {
                    listOf(Screen.Home, Screen.Explorer, Screen.Editor, Screen.Terminal, Screen.Copilot).forEach {
                        NavigationBarItem(selected = screen == it, onClick = { onNavigate(it) }, icon = { Text(it.glyph, fontSize = 16.sp) }, label = { Text(it.label, fontSize = 10.sp) })
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0B0D13)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark(compact = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("arevscode", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text("●", color = Color(0xFF3DDC84), fontSize = 10.sp)
        Spacer(Modifier.width(7.dp))
        Text("Ready", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
fun LogoMark(compact: Boolean = false) {
    Box(
        Modifier.size(if (compact) 34.dp else 42.dp).clip(RoundedCornerShape(12.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFF41D6FF)))
        ),
        contentAlignment = Alignment.Center
    ) { Text("A", fontWeight = FontWeight.Black, color = Color.White, fontSize = if (compact) 18.sp else 22.sp) }
}

@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun SectionTitle(kicker: String, title: String, sub: String? = null) {
    Column {
        Text(kicker.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, workspace: String) {
    Page {
        SectionTitle("Mobile IDE", "Build anything from your phone", "Editor + terminal + GitHub + Copilot dalam satu workspace")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Workspace", workspace, Modifier.weight(1f))
            StatCard("Engine", "arev engine", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard("▣", "Explorer", "File manager", Modifier.weight(1f)) { onOpen(Screen.Explorer) }
            ActionCard("›_", "Terminal", "Run commands", Modifier.weight(1f)) { onOpen(Screen.Terminal) }
            ActionCard("✦", "Copilot", "Fix & generate", Modifier.weight(1f)) { onOpen(Screen.Copilot) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111621)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Developer cockpit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Open a project folder, edit files, run commands, inspect Git state, and ask Copilot about the current code.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Android")
                    SmallPill("Kotlin")
                    SmallPill("Git")
                    SmallPill("AI")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("arevscode foundation • v0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF10131A)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

@Composable
fun ActionCard(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF151923)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(icon, fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary); Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
    }
}

@Composable
fun SmallPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1B2030)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(text, fontSize = 10.sp, color = Color(0xFFC9CFFF)) }
}

@Composable
fun ExplorerScreen(project: ProjectStore, onOpenFile: (WorkspaceFile) -> Unit, onChoose: () -> Unit) {
    val items = project.listCurrent()
    Page {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { SectionTitle("Project", project.rootName, "SAF workspace") }
            Button(onClick = onChoose) { Text("Open") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { project.up() }) { Text("← Up") }
            SmallPill(project.currentUri?.toString()?.takeLast(32) ?: "No folder")
        }
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1117)), shape = RoundedCornerShape(18.dp)) {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                if (items.isEmpty()) item { EmptyState("Belum ada file", "Pilih folder project atau buat workspace dari GitHub pada tahap berikutnya.") }
                items(items, key = { it.uri.toString() }) { item ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenFile(item) }.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.directory) "▰" else "□", color = if (item.directory) Color(0xFFFFC857) else MaterialTheme.colorScheme.secondary, fontSize = 17.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(item.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text(if (item.directory) ">" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EditorScreen(fileName: String, text: String, dirty: Boolean, onChange: (String) -> Unit, onSave: () -> Unit) {
    val scroll = rememberScrollState()
    val lines = maxOf(1, text.count { it == '\n' } + 1)
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fileName.ifBlank { "No file" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (dirty) "Unsaved changes" else "Saved", color = if (dirty) Color(0xFFFFC857) else Color(0xFF61D99A), fontSize = 10.sp)
            }
            Button(onClick = onSave) { Text("Save") }
        }
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0D12)), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Column(Modifier.padding(top = 12.dp).background(Color(0xFF0D1016)).padding(horizontal = 11.dp), horizontalAlignment = Alignment.End) {
                    repeat(lines) { Text((it + 1).toString(), color = Color(0xFF566071), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp) }
                }
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
                    textStyle = TextStyle(color = Color(0xFFD7DBE5), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8B6CFF))
                )
            }
        }
    }
}

@Composable
fun TerminalScreen(output: List<String>, input: String, busy: Boolean, onInput: (String) -> Unit, onRun: () -> Unit, onClear: () -> Unit) {
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("Shell", "Terminal", "App-local Android shell") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(output) { line -> Text(line, color = if (line.contains("error", true)) Color(0xFFFF7C82) else Color(0xFFB9F8D5), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                }
                Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF242A36), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$", color = Color(0xFF8B6CFF), fontFamily = FontFamily.Monospace)
                    BasicTextField(value = input, onValueChange = onInput, modifier = Modifier.weight(1f).padding(8.dp), textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onRun() }))
                    Button(enabled = !busy, onClick = onRun, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text(if (busy) "…" else "Run") }
                }
            }
        }
    }
}

@Composable
fun GitHubScreen(projectName: String, onOpenTerminal: () -> Unit) {
    Page {
        SectionTitle("Source control", "GitHub", "Repos, branch, commits, pull requests — UI foundation")
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111621)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current workspace", fontWeight = FontWeight.Bold)
                Text(projectName, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                Text("GitHub account OAuth, clone/push, branch switcher and PR actions are the next integration layer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenTerminal) { Text("Open terminal") }
                    OutlinedButton(onClick = {}) { Text("Connect GitHub") }
                }
            }
        }
        InfoRow("Repository", "Not connected")
        InfoRow("Branch", "main")
        InfoRow("Changes", "0 staged • 0 unstaged")
        InfoRow("Remote", "Not configured")
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F1218)).padding(14.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CopilotScreen(chats: List<ChatMessage>, input: String, busy: Boolean, onInput: (String) -> Unit, onAsk: () -> Unit, onQuick: (String) -> Unit) {
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle("AI pair programmer", "Copilot", "Fix • explain • refactor • generate") }
            SmallPill(if (busy) "Thinking…" else "Ready")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("Fix error") { onQuick("Analisis error pada kode aktif. Jelaskan akar masalah lalu berikan patch yang aman.") }
            QuickButton("Explain") { onQuick("Jelaskan kode aktif secara bertahap dan tunjukkan bagian yang berisiko.") }
            QuickButton("Optimize") { onQuick("Optimalkan kode aktif untuk performa dan maintainability tanpa mengubah perilaku yang diinginkan.") }
            QuickButton("Add feature") { onQuick("Tambahkan fitur yang masuk akal pada kode aktif dan berikan perubahan lengkap.") }
        }
        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1016)), shape = RoundedCornerShape(18.dp)) {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chats) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                        Box(Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(16.dp)).background(if (msg.fromUser) Color(0xFF25213A) else Color(0xFF151A23)).padding(12.dp)) {
                            Text(msg.text, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF2A3040), RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = input, onValueChange = onInput, modifier = Modifier.weight(1f).padding(8.dp), textStyle = TextStyle(color = Color.White, fontSize = 12.sp), minLines = 1, maxLines = 4)
            Button(enabled = !busy, onClick = onAsk, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) { Text(if (busy) "…" else "Send") }
        }
    }
}

@Composable
fun QuickButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text(text, fontSize = 10.sp) }
}

@Composable
fun SettingsScreen(key: String, model: String, onKey: (String) -> Unit, onModel: (String) -> Unit, onSave: () -> Unit, onSystemSettings: () -> Unit) {
    Page {
        SectionTitle("Configuration", "Settings", "Provider, model, app permissions")
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111621)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gemini Copilot", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = key, onValueChange = onKey, modifier = Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(value = model, onValueChange = onModel, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                Text("Default: gemini-3.8-flash", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save provider settings") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111621)), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Android integration", fontWeight = FontWeight.Bold)
                Text("Manage app permissions, storage access, battery behavior, and notifications from the Android system.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(onClick = onSystemSettings) { Text("Open app system settings") }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⌁", fontSize = 32.sp, color = MaterialTheme.colorScheme.secondary)
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

private const val WELCOME_CODE = """# Welcome to arevscode\n\n// Your mobile developer cockpit\n// 1. Open a project in Explorer\n// 2. Edit code in Editor\n// 3. Run commands in Terminal\n// 4. Ask Copilot to diagnose or generate code\n\nfun main() {\n    println(\"Build something great.\")\n}\n"""
