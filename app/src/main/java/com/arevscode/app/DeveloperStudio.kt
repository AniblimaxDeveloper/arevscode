package com.arevscode.app

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

data class StudioSearchHit(
    val uri: Uri,
    val path: String,
    val line: Int,
    val snippet: String
)

data class AgentOperation(
    val action: String,
    val path: String,
    val target: String = "",
    val content: String = ""
)

data class AgentPlan(
    val summary: String,
    val operations: List<AgentOperation>,
    val raw: String
)

class WorkspaceTools(
    private val context: Context
) {
    private val resolver get() = context.contentResolver

    private fun root(project: ProjectStore): DocumentFile? {
        val rootUri = project.rootUri ?: return null
        return DocumentFile.fromTreeUri(context, rootUri)
    }

    private fun parts(path: String): List<String> =
        path.replace("\\", "/")
            .split("/")
            .filter { it.isNotBlank() && it != "." && it != ".." }

    fun find(
        project: ProjectStore,
        relative: String
    ): DocumentFile? {
        var current = root(project) ?: return null

        for (piece in parts(relative)) {
            current = current.findFile(piece) ?: return null
        }

        return current
    }

    fun read(
        project: ProjectStore,
        relative: String
    ): String {
        val file = find(project, relative) ?: return ""
        return runCatching {
            resolver.openInputStream(file.uri)
                ?.bufferedReader()
                ?.use { it.readText() } ?: ""
        }.getOrDefault("")
    }

    fun create(
        project: ProjectStore,
        relative: String,
        content: String
    ): Boolean {
        val items = parts(relative)
        if (items.isEmpty()) return false

        var current = root(project) ?: return false

        for (folder in items.dropLast(1)) {
            current =
                current.findFile(folder)
                    ?: current.createDirectory(folder)
                    ?: return false
        }

        val file =
            current.findFile(items.last())
                ?: current.createFile(
                    "text/plain",
                    items.last()
                )
                ?: return false

        return runCatching {
            resolver.openOutputStream(file.uri, "wt")
                ?.bufferedWriter()
                ?.use { it.write(content) }
            true
        }.getOrDefault(false)
    }

    fun write(
        project: ProjectStore,
        relative: String,
        content: String
    ): Boolean {
        val file = find(project, relative) ?: return false

        return runCatching {
            resolver.openOutputStream(file.uri, "wt")
                ?.bufferedWriter()
                ?.use { it.write(content) }
            true
        }.getOrDefault(false)
    }

    fun delete(
        project: ProjectStore,
        relative: String
    ): Boolean =
        find(project, relative)?.delete() == true

    fun rename(
        project: ProjectStore,
        relative: String,
        target: String
    ): Boolean {
        val source = find(project, relative) ?: return false
        val targetParts = parts(target)
        if (targetParts.isEmpty()) return false

        val sourceParts = parts(relative)

        // Safe v0.6 behavior: rename within the same parent.
        if (sourceParts.dropLast(1) != targetParts.dropLast(1)) {
            return false
        }

        return source.renameTo(targetParts.last())
    }

    fun search(
        project: ProjectStore,
        query: String
    ): List<StudioSearchHit> {
        val rootDoc = root(project) ?: return emptyList()
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()

        val hits = mutableListOf<StudioSearchHit>()
        var fileCount = 0

        fun walk(
            dir: DocumentFile,
            prefix: String
        ) {
            if (fileCount >= 400 || hits.size >= 200) return

            for (file in dir.listFiles()) {
                if (fileCount >= 400 || hits.size >= 200) return

                val name = file.name.orEmpty()
                val path =
                    if (prefix.isBlank()) name
                    else "$prefix/$name"

                if (file.isDirectory) {
                    walk(file, path)
                    continue
                }

                fileCount++

                if (file.length() > 750_000) continue

                val text = runCatching {
                    resolver.openInputStream(file.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull() ?: continue

                val lower = text.lowercase()
                var start = 0

                while (true) {
                    val index = lower.indexOf(needle, start)
                    if (index < 0) break

                    val line =
                        text.substring(0, index)
                            .count { it == '\n' } + 1

                    val lineStart =
                        text.lastIndexOf('\n', index - 1)
                            .let { if (it < 0) 0 else it + 1 }

                    val lineEnd =
                        text.indexOf('\n', index)
                            .let {
                                if (it < 0) text.length else it
                            }

                    hits += StudioSearchHit(
                        uri = file.uri,
                        path = path,
                        line = line,
                        snippet =
                            text.substring(
                                lineStart,
                                lineEnd
                            ).trim().take(220)
                    )

                    start = index + needle.length
                }
            }
        }

        walk(rootDoc, "")
        return hits
    }
}

class GeminiAgent(
    private val gemini: GeminiTurbo
) {
    fun plan(
        instruction: String,
        context: String
    ): AgentPlan {
        val request = """
            You are Avescode Developer Studio Agent.

            User instruction:
            $instruction

            Workspace context:
            $context

            Return ONLY JSON:
            {
              "summary": "brief plan",
              "operations": [
                {
                  "action": "create|write|delete|rename",
                  "path": "relative/path",
                  "target": "relative/target or empty",
                  "content": "complete content or empty"
                }
              ]
            }

            Safety:
            - Relative paths only.
            - Never use ../ or absolute paths.
            - Keep edits minimal.
            - Do not delete or rename unrelated files.
            - Complete content is required for create/write.
        """.trimIndent()

        val raw = gemini.ask(
            request,
            context.take(100_000)
        )

        return parse(raw)
    }

    private fun parse(raw: String): AgentPlan {
        val first = raw.indexOf('{')
        val last = raw.lastIndexOf('}')

        if (first < 0 || last <= first) {
            return AgentPlan(
                summary = raw,
                operations = emptyList(),
                raw = raw
            )
        }

        return runCatching {
            val json =
                JSONObject(
                    raw.substring(
                        first,
                        last + 1
                    )
                )

            val array =
                json.optJSONArray("operations")
                    ?: JSONArray()

            val ops = mutableListOf<AgentOperation>()

            for (i in 0 until array.length()) {
                val obj =
                    array.optJSONObject(i)
                        ?: continue

                ops += AgentOperation(
                    action =
                        obj.optString(
                            "action"
                        ).lowercase(),
                    path =
                        safePath(
                            obj.optString("path")
                        ),
                    target =
                        safePath(
                            obj.optString("target")
                        ),
                    content =
                        obj.optString("content")
                )
            }

            AgentPlan(
                summary =
                    json.optString(
                        "summary",
                        "AI generated a workspace change plan."
                    ),
                operations = ops,
                raw = raw
            )
        }.getOrElse {
            AgentPlan(
                summary = "AI output could not be parsed safely.",
                operations = emptyList(),
                raw = raw
            )
        }
    }

    private fun safePath(raw: String): String =
        raw.replace("\\", "/")
            .trim()
            .removePrefix("./")
            .removePrefix("/")
            .let {
                if (it.split("/").any { part ->
                        part == ".."
                    }
                ) "" else it
            }
}

class LocalhostPreviewServer(
    private val context: Context
) {
    private var server: ServerSocket? = null
    private var running = false
    private var port = 4173

    fun start(
        project: ProjectStore
    ): String {
        stop()

        val rootUri = project.rootUri ?: return ""

        var opened: ServerSocket? = null

        for (candidate in 4173..4190) {
            try {
                opened = ServerSocket(
                    candidate,
                    20,
                    InetAddress.getByName(
                        "127.0.0.1"
                    )
                )
                port = candidate
                break
            } catch (_: Exception) {
            }
        }

        val socket = opened ?: return ""

        server = socket
        running = true

        thread(
            start = true,
            isDaemon = true,
            name = "avescode-localhost"
        ) {
            while (running) {
                try {
                    val client =
                        socket.accept()

                    thread(
                        start = true,
                        isDaemon = true
                    ) {
                        serve(
                            client,
                            rootUri
                        )
                    }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }

        return url()
    }

    fun stop() {
        running = false
        runCatching {
            server?.close()
        }
        server = null
    }

    fun url(): String =
        "http://127.0.0.1:$port/"

    private fun serve(
        client: Socket,
        rootUri: Uri
    ) {
        client.use { socket ->
            val input =
                socket.getInputStream()
                    .bufferedReader()

            val request =
                input.readLine()
                    ?: return

            while (true) {
                val line =
                    input.readLine()
                        ?: break

                if (line.isEmpty()) break
            }

            val pieces =
                request.split(" ")

            if (pieces.size < 2) return

            if (pieces[0] != "GET" &&
                pieces[0] != "HEAD"
            ) {
                writeText(
                    socket,
                    405,
                    "Method Not Allowed"
                )
                return
            }

            var path =
                pieces[1]
                    .substringBefore("?")
                    .removePrefix("/")
                    .replace("\\", "/")

            if (path.isBlank()) {
                path = "index.html"
            }

            if (
                path.split("/").any {
                    it == ".."
                }
            ) {
                writeText(
                    socket,
                    400,
                    "Bad Request"
                )
                return
            }

            val root =
                DocumentFile.fromTreeUri(
                    context,
                    rootUri
                ) ?: return

            var current = root

            for (piece in path.split("/")) {
                if (piece.isBlank()) continue

                current =
                    current.findFile(piece)
                        ?: run {
                            writeText(
                                socket,
                                404,
                                "Not Found"
                            )
                            return
                        }
            }

            if (current.isDirectory) {
                writeText(
                    socket,
                    404,
                    "Not Found"
                )
                return
            }

            val bytes =
                context.contentResolver
                    .openInputStream(
                        current.uri
                    )
                    ?.use { it.readBytes() }
                    ?: ByteArray(0)

            val mime =
                when (
                    current.name
                        ?.substringAfterLast(
                            ".",
                            ""
                        )
                        ?.lowercase()
                ) {
                    "html", "htm" ->
                        "text/html; charset=utf-8"
                    "css" ->
                        "text/css; charset=utf-8"
                    "js", "mjs" ->
                        "text/javascript; charset=utf-8"
                    "json" ->
                        "application/json; charset=utf-8"
                    "svg" ->
                        "image/svg+xml"
                    "png" ->
                        "image/png"
                    "jpg", "jpeg" ->
                        "image/jpeg"
                    "gif" ->
                        "image/gif"
                    "webp" ->
                        "image/webp"
                    else ->
                        "application/octet-stream"
                }

            val header =
                "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n"

            socket.getOutputStream().apply {
                write(header.toByteArray())
                if (pieces[0] != "HEAD") {
                    write(bytes)
                }
                flush()
            }
        }
    }

    private fun writeText(
        socket: Socket,
        code: Int,
        message: String
    ) {
        val reason =
            when (code) {
                400 -> "Bad Request"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                else -> "Error"
            }

        val body =
            message.toByteArray()

        val header =
            "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"

        socket.getOutputStream().apply {
            write(header.toByteArray())
            write(body)
            flush()
        }
    }
}

data class StudioTab(
    val uri: String,
    val name: String,
    val text: String,
    val dirty: Boolean
)

@Composable
fun DeveloperStudio(
    project: ProjectStore,
    agent: GeminiAgent,
    onNavigate: (Screen) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tools = remember {
        WorkspaceTools(context)
    }

    val preview = remember {
        LocalhostPreviewServer(context)
    }

    var tabs by remember {
        mutableStateOf(
            emptyList<StudioTab>()
        )
    }

    var activeUri by rememberSaveable {
        mutableStateOf("")
    }

    var search by rememberSaveable {
        mutableStateOf("")
    }

    var results by remember {
        mutableStateOf(
            emptyList<StudioSearchHit>()
        )
    }

    var showSearch by rememberSaveable {
        mutableStateOf(false)
    }

    var showCommand by rememberSaveable {
        mutableStateOf(false)
    }

    var showAgent by rememberSaveable {
        mutableStateOf(false)
    }

    var showPreview by rememberSaveable {
        mutableStateOf(false)
    }

    var agentInput by rememberSaveable {
        mutableStateOf("")
    }

    var agentPlan by remember {
        mutableStateOf<AgentPlan?>(null)
    }

    var agentBusy by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            preview.stop()
        }
    }

    fun activeTab(): StudioTab? =
        tabs.firstOrNull {
            it.uri == activeUri
        }

    fun open(uri: Uri, name: String) {
        val key = uri.toString()

        if (tabs.none { it.uri == key }) {
            tabs =
                tabs + StudioTab(
                    uri = key,
                    name = name,
                    text = project.readText(uri),
                    dirty = false
                )
        }

        activeUri = key
    }

    fun update(text: String) {
        tabs =
            tabs.map {
                if (it.uri == activeUri) {
                    it.copy(
                        text = text,
                        dirty = true
                    )
                } else {
                    it
                }
            }
    }

    fun save() {
        val tab = activeTab()
            ?: return

        val ok =
            project.writeText(
                Uri.parse(tab.uri),
                tab.text
            )

        tabs =
            tabs.map {
                if (it.uri == tab.uri) {
                    it.copy(
                        dirty = !ok
                    )
                } else {
                    it
                }
            }

        onToast(
            if (ok) {
                "Saved ${tab.name}"
            } else {
                "Save failed"
            }
        )
    }

    fun agentRun() {
        if (
            agentBusy ||
            agentInput.isBlank()
        ) return

        agentBusy = true

        val contextText =
            buildString {
                append(
                    "Project: "
                )
                append(
                    project.rootName
                )
                append(
                    "\nOpen files:\n"
                )

                tabs.take(8).forEach {
                    append(
                        "\n--- "
                    )
                    append(
                        it.name
                    )
                    append(
                        " ---\n"
                    )
                    append(
                        it.text.take(16_000)
                    )
                    append("\n")
                }
            }

        scope.launch(Dispatchers.IO) {
            val plan =
                agent.plan(
                    agentInput,
                    contextText
                )

            agentPlan = plan
            agentBusy = false
        }
    }

    fun applyPlan(
        plan: AgentPlan
    ) {
        scope.launch(Dispatchers.IO) {
            var ok = 0
            var failed = 0

            plan.operations.forEach {
                val success =
                    when (it.action) {
                        "create" ->
                            tools.create(
                                project,
                                it.path,
                                it.content
                            )

                        "write" ->
                            tools.write(
                                project,
                                it.path,
                                it.content
                            )

                        "delete" ->
                            tools.delete(
                                project,
                                it.path
                            )

                        "rename" ->
                            tools.rename(
                                project,
                                it.path,
                                it.target
                            )

                        else -> false
                    }

                if (success) ok++
                else failed++
            }

            agentPlan = null
            agentInput = ""

            onToast(
                "AI Apply: $ok berhasil, $failed gagal"
            )
        }
    }

    Page {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    "DEVELOPER STUDIO",
                    color =
                        MaterialTheme
                            .colorScheme
                            .secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Code workspace",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            SmallFloatingActionButton(
                onClick = {
                    showCommand = true
                }
            ) {
                Text("⌘")
            }

            Spacer(
                Modifier.width(7.dp)
            )

            SmallFloatingActionButton(
                onClick = {
                    showAgent = true
                }
            ) {
                Text("✦")
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    showSearch = !showSearch
                }
            ) {
                Text("Search")
            }

            OutlinedButton(
                onClick = {
                    save()
                }
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    showCommand = true
                }
            ) {
                Text("Command")
            }

            OutlinedButton(
                onClick = {
                    showPreview = !showPreview
                }
            ) {
                Text("Preview")
            }

            OutlinedButton(
                onClick = {
                    onNavigate(Screen.Terminal)
                }
            ) {
                Text("Terminal")
            }
        }

        if (showSearch) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xE6111621)
                    )
            ) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = {
                                search = it
                            },
                            modifier =
                                Modifier.weight(1f),
                            singleLine = true,
                            label = {
                                Text(
                                    "Search project"
                                )
                            }
                        )

                        Spacer(
                            Modifier.width(7.dp)
                        )

                        Button(
                            onClick = {
                                val q =
                                    search.trim()

                                scope.launch(
                                    Dispatchers.IO
                                ) {
                                    results =
                                        tools.search(
                                            project,
                                            q
                                        )
                                }
                            }
                        ) {
                            Text("Find")
                        }
                    }

                    LazyColumn(
                        Modifier.height(150.dp)
                    ) {
                        items(results) { hit ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        open(
                                            hit.uri,
                                            hit.path.substringAfterLast("/")
                                        )
                                    }
                                    .padding(7.dp)
                            ) {
                                Text(
                                    "${hit.path}:${hit.line}",
                                    fontSize = 11.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    hit.snippet,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                    fontFamily =
                                        FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (tabs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState()
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    Card(
                        Modifier.clickable {
                            activeUri = tab.uri
                        },
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (tab.uri ==
                                        activeUri
                                    ) {
                                        Color(0xFF262C3B)
                                    } else {
                                        Color(0xFF11151E)
                                    }
                            )
                    ) {
                        Row(
                            Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                if (tab.dirty) "● " else "",
                                color =
                                    Color(0xFFFFC857),
                                fontSize = 8.sp
                            )

                            Text(
                                tab.name,
                                fontSize = 10.sp
                            )

                            TextButton(
                                onClick = {
                                    tabs =
                                        tabs.filterNot {
                                            it.uri ==
                                                tab.uri
                                        }

                                    if (
                                        activeUri ==
                                            tab.uri
                                    ) {
                                        activeUri =
                                            tabs.lastOrNull()
                                                ?.uri
                                                .orEmpty()
                                    }
                                }
                            ) {
                                Text("×")
                            }
                        }
                    }
                }
            }
        }

        if (showPreview) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFF10141D)
                    )
            ) {
                LocalhostPreviewPanel(
                    project = project,
                    server = preview
                )
            }
        } else {
            val tab = activeTab()

            if (tab == null) {
                AvescodeSurface(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Color(0xEC0D1017)
                            )
                            .padding(22.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(9.dp)
                    ) {
                        Text(
                            "Studio siap digunakan",
                            fontSize = 19.sp,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        Text(
                            "Buka Explorer untuk membuka file sebagai tab. Search, Command Palette, Preview, Terminal dan AI Agent tersedia dari Studio.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Button(
                            onClick = {
                                onNavigate(
                                    Screen.Explorer
                                )
                            }
                        ) {
                            Text("Open Explorer")
                        }
                    }
                }
            } else {
                StudioEditor(
                    tab = tab,
                    onChange = ::update
                )
            }
        }

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xE60B0F17)
                )
        ) {
            Column(
                Modifier.padding(8.dp)
            ) {
                Text(
                    "OUTPUT / PROBLEMS",
                    color =
                        MaterialTheme
                            .colorScheme
                            .secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (agentBusy) {
                        "AI agent is planning..."
                    } else {
                        "Ready • Workspace operations require explicit Apply."
                    },
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }

    if (showCommand) {
        CommandPalette(
            onDismiss = {
                showCommand = false
            },
            onCommand = { command ->
                showCommand = false

                when (command) {
                    "Search project" ->
                        showSearch = true
                    "Open Explorer" ->
                        onNavigate(Screen.Explorer)
                    "Open Terminal" ->
                        onNavigate(Screen.Terminal)
                    "Open Copilot" ->
                        onNavigate(Screen.Copilot)
                    "Preview localhost" ->
                        showPreview = true
                    "Save" ->
                        save()
                }
            }
        )
    }

    if (showAgent) {
        AgentDialog(
            value = agentInput,
            busy = agentBusy,
            plan = agentPlan,
            onValue = {
                agentInput = it
                if (!agentBusy) {
                    agentPlan = null
                }
            },
            onRun = ::agentRun,
            onApply = ::applyPlan,
            onReject = {
                agentPlan = null
            },
            onDismiss = {
                showAgent = false
                agentPlan = null
            }
        )
    }
}

@Composable
private fun StudioEditor(
    tab: StudioTab,
    onChange: (String) -> Unit
) {
    val value =
        remember(tab.uri) {
            mutableStateOf(
                TextFieldValue(
                    tab.text
                )
            )
        }

    androidx.compose.runtime.LaunchedEffect(
        tab.text
    ) {
        if (tab.text != value.value.text) {
            value.value =
                TextFieldValue(
                    tab.text,
                    TextRange(
                        tab.text.length
                    )
                )
        }
    }

    val language =
        when (
            tab.name.substringAfterLast(
                ".",
                ""
            ).lowercase()
        ) {
            "kt", "kts" -> "Kotlin"
            "java" -> "Java"
            "py" -> "Python"
            "js" -> "JavaScript"
            "ts" -> "TypeScript"
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            "json" -> "JSON"
            "xml" -> "XML"
            "c" -> "C"
            "cc", "cpp", "h", "hpp" -> "C++"
            "rs" -> "Rust"
            "go" -> "Go"
            "sh", "bash" -> "Shell"
            "yaml", "yml" -> "YAML"
            else -> "Text"
        }

    val lines =
        maxOf(
            1,
            value.value.text.count {
                it == '\n'
            } + 1
        )

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            SmallPill(language)

            Spacer(
                Modifier.weight(1f)
            )

            Text(
                "$lines lines • ${value.value.text.length} chars",
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                fontSize = 9.sp
            )
        }

        AvescodeSurface(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .background(
                            Color(0xFF0A0D12)
                        )
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 8.dp,
                            vertical = 8.dp
                        ),
                    horizontalAlignment =
                        Alignment.End
                ) {
                    repeat(lines) {
                        Text(
                            (it + 1).toString(),
                            color = Color(0xFF596273),
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                BasicTextField(
                    value = value.value,
                    onValueChange = {
                        value.value = it
                        onChange(
                            it.text
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                rememberScrollState()
                            )
                            .horizontalScroll(
                                rememberScrollState()
                            )
                            .padding(9.dp),
                    textStyle =
                        TextStyle(
                            color =
                                Color(0xFFE0E4ED),
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
            horizontalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {
            SmallPill("CTRL+A Select")
            SmallPill("CTRL+S Save")
            SmallPill("CTRL+F Find")
            SmallPill("CTRL+Z Undo")
            SmallPill("TAB Indent")
            SmallPill("ESC Clear")
        }
    }
}

@Composable
private fun CommandPalette(
    onDismiss: () -> Unit,
    onCommand: (String) -> Unit
) {
    val commands =
        listOf(
            "Search project",
            "Open Explorer",
            "Open Terminal",
            "Open Copilot",
            "Preview localhost",
            "Save"
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Command Palette")
        },
        text = {
            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                items(commands) { command ->
                    OutlinedButton(
                        onClick = {
                            onCommand(
                                command
                            )
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(command)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AgentDialog(
    value: String,
    busy: Boolean,
    plan: AgentPlan?,
    onValue: (String) -> Unit,
    onRun: () -> Unit,
    onApply: (AgentPlan) -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Avescode AI Agent")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValue,
                    modifier =
                        Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = {
                        Text("Perintah")
                    },
                    placeholder = {
                        Text(
                            "Contoh: buat halaman login modern di web/login.html"
                        )
                    }
                )

                when {
                    busy ->
                        SmallPill(
                            "Thinking…"
                        )

                    plan != null -> {
                        Text(
                            plan.summary,
                            fontWeight =
                                FontWeight.Bold
                        )

                        if (
                            plan.operations.isEmpty()
                        ) {
                            Text(
                                plan.raw.take(
                                    1500
                                ),
                                fontSize = 10.sp,
                                fontFamily =
                                    FontFamily.Monospace
                            )
                        } else {
                            plan.operations.forEach {
                                Card(
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                Color(
                                                    0xFF151A23
                                                )
                                        )
                                ) {
                                    Column(
                                        Modifier.padding(
                                            8.dp
                                        )
                                    ) {
                                        Text(
                                            "${it.action.uppercase()}  ${it.path}",
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .secondary,
                                            fontSize = 10.sp,
                                            fontWeight =
                                                FontWeight.Bold
                                        )

                                        if (
                                            it.target
                                                .isNotBlank()
                                        ) {
                                            Text(
                                                "→ ${it.target}",
                                                fontSize = 9.sp
                                            )
                                        }

                                        if (
                                            it.content
                                                .isNotBlank()
                                        ) {
                                            Text(
                                                it.content
                                                    .take(420),
                                                fontFamily =
                                                    FontFamily.Monospace,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                if (
                    plan != null &&
                    plan.operations.isNotEmpty()
                ) {
                    Button(
                        onClick = {
                            onApply(plan)
                        }
                    ) {
                        Text("Apply")
                    }

                    OutlinedButton(
                        onClick = onReject
                    ) {
                        Text("Reject")
                    }
                } else {
                    Button(
                        enabled =
                            value.isNotBlank() &&
                                !busy,
                        onClick = onRun
                    ) {
                        Text(
                            if (busy) {
                                "Planning…"
                            } else {
                                "Plan changes"
                            }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LocalhostPreviewPanel(
    project: ProjectStore,
    server: LocalhostPreviewServer
) {
    val context = LocalContext.current

    var url by remember {
        mutableStateOf("")
    }

    var running by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            server.stop()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                "LOCALHOST",
                Modifier.weight(1f),
                color =
                    MaterialTheme
                        .colorScheme
                        .secondary,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (running) {
                OutlinedButton(
                    onClick = {
                        server.stop()
                        running = false
                    }
                ) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = {
                        val started =
                            server.start(project)

                        if (started.isBlank()) {
                            return@Button
                        }

                        url = started
                        running = true
                    }
                ) {
                    Text("Run")
                }
            }
        }

        Text(
            if (url.isBlank()) {
                "Pilih workspace web yang memiliki index.html."
            } else {
                url
            },
            fontSize = 9.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        if (url.isNotBlank()) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        ),
                factory = {
                    WebView(
                        context
                    ).apply {
                        webViewClient =
                            WebViewClient()
                        settings.javaScriptEnabled =
                            true
                        settings.domStorageEnabled =
                            true
                        settings.loadsImagesAutomatically =
                            true
                        loadUrl(url)
                    }
                },
                update = {
                    if (it.url != url) {
                        it.loadUrl(url)
                    }
                }
            )
        }
    }
}
