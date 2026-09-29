package com.arevscode.app

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val AvCyan = Color(0xFF39E8FF)
private val AvBlue = Color(0xFF4A7BFF)
private val AvPurple = Color(0xFF8D62FF)
private val AvPink = Color(0xFFFF4FD7)
private val AvGreen = Color(0xFF59E8A8)
private val AvOrange = Color(0xFFFFB04B)
private val AvRed = Color(0xFFFF5C72)
private val AvSurface = Color(0xEA111823)
private val AvSurface2 = Color(0xEA0B1018)

private var splashConsumed088 = false

@Composable
fun AvescodeShellV2WithSplash(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {
    var showSplash by rememberSaveable {
        mutableStateOf(!splashConsumed088)
    }

    var showCenter by rememberSaveable { mutableStateOf(false) }

    val avosContext = LocalContext.current
    val avosPrefs = remember { AvOSPreferences(avosContext) }

    var performance by rememberSaveable { mutableStateOf(avosPrefs.performance) }
    var aiBoost by rememberSaveable { mutableStateOf(avosPrefs.aiBoost) }
    var rgbMotion by rememberSaveable { mutableStateOf(avosPrefs.rgbMotion) }
    var lowPower by rememberSaveable { mutableStateOf(avosPrefs.lowPower) }
    var autoSave by rememberSaveable { mutableStateOf(avosPrefs.autoSave) }
    var safeMode by rememberSaveable { mutableStateOf(avosPrefs.safeMode) }
    var haptics by rememberSaveable { mutableStateOf(avosPrefs.haptics) }
    var smartCache by rememberSaveable { mutableStateOf(avosPrefs.smartCache) }
    var backgroundTasks by rememberSaveable { mutableStateOf(avosPrefs.backgroundTasks) }
    var networkAssist by rememberSaveable { mutableStateOf(avosPrefs.networkAssist) }
    var compactMode by rememberSaveable { mutableStateOf(avosPrefs.compactMode) }
    var immersive by rememberSaveable { mutableStateOf(avosPrefs.immersive) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        AvAnimatedBackground(
            modifier = Modifier.fillMaxSize(),
            enabled = rgbMotion && !safeMode && !lowPower
        )

        val wide = maxWidth >= 700.dp

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AvRail088(
                    screen = screen,
                    onNavigate = onNavigate
                )

                Column(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    AvTopBar088(
                        screen = screen,
                        safeMode = safeMode,
                        onCenter = { showCenter = true }
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        content()
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                AvTopBar088(
                    screen = screen,
                    safeMode = safeMode,
                    onCenter = { showCenter = true }
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                }

                AvBottomBar088(
                    screen = screen,
                    onNavigate = onNavigate
                )
            }
        }

        if (showCenter) {
            AvSystemCenter088(
                context = avosContext,
                performance = performance,
                aiBoost = aiBoost,
                rgbMotion = rgbMotion,
                lowPower = lowPower,
                autoSave = autoSave,
                safeMode = safeMode,
                haptics = haptics,
                smartCache = smartCache,
                backgroundTasks = backgroundTasks,
                networkAssist = networkAssist,
                compactMode = compactMode,
                immersive = immersive,
                onPerformance = { performance = it; avosPrefs.performance = it },
                onAiBoost = { aiBoost = it; avosPrefs.aiBoost = it },
                onRgbMotion = { rgbMotion = it; avosPrefs.rgbMotion = it },
                onLowPower = { lowPower = it; avosPrefs.lowPower = it },
                onAutoSave = { autoSave = it; avosPrefs.autoSave = it },
                onSafeMode = { safeMode = it; avosPrefs.safeMode = it },
                onHaptics = { haptics = it; avosPrefs.haptics = it },
                onSmartCache = { smartCache = it; avosPrefs.smartCache = it },
                onBackgroundTasks = { backgroundTasks = it; avosPrefs.backgroundTasks = it },
                onNetworkAssist = { networkAssist = it; avosPrefs.networkAssist = it },
                onCompactMode = { compactMode = it; avosPrefs.compactMode = it },
                onImmersive = { immersive = it; avosPrefs.immersive = it },
                onDismiss = { showCenter = false }
            )
        }

        if (showSplash) {
            AvSplash088 {
                splashConsumed088 = true
                showSplash = false
            }
        }
    }
}

@Composable
private fun AvAnimatedBackground(
    modifier: Modifier,
    enabled: Boolean
) {
    val transition =
        rememberInfiniteTransition(label = "system-rgb")

    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                4200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    Box(
        modifier.graphicsLayer {
            alpha = if (enabled) 1f else 0.20f
        }
    ) {
        Box(
            Modifier
                .size(390.dp)
                .graphicsLayer {
                    translationX = phase * 100f
                    translationY = phase * -45f
                    alpha = 0.20f
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            AvCyan,
                            AvBlue.copy(alpha = 0.42f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(370.dp)
                .graphicsLayer {
                    translationX = phase * -95f
                    translationY = phase * 50f
                    alpha = 0.17f
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            AvPurple,
                            AvPink.copy(alpha = 0.34f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun AvTopBar088(
    screen: Screen,
    safeMode: Boolean,
    onCenter: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xBD070A10))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f)
        ) {
            Text(
                "AvOS 16.0",
                color = AvCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                when (screen) {
                    Screen.Home -> "Avescode"
                    Screen.Explorer -> "Workspace Explorer"
                    Screen.Editor -> "Developer Studio"
                    Screen.Terminal -> "Terminal Runtime"
                    Screen.GitHub -> "GitHub Control"
                    Screen.Copilot -> "AI Command Center"
                    Screen.Settings -> "System Settings"
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        if (safeMode) {
            AvMiniChip("SAFE", AvOrange, onCenter)
            Spacer(Modifier.width(4.dp))
        }

        AvMiniChip("CPU", AvGreen, onCenter)
        Spacer(Modifier.width(4.dp))
        AvMiniChip("GPU", AvPurple, onCenter)
        Spacer(Modifier.width(4.dp))
        AvMiniChip("AI", AvPink, onCenter)
        Spacer(Modifier.width(4.dp))
        AvMiniChip("⚙", AvCyan, onCenter)
    }
}

@Composable
private fun AvMiniChip(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF101722))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text,
            color = Color(0xFFE7EEF8),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvRail088(
    screen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val items = listOf(
        Screen.Home,
        Screen.Explorer,
        Screen.Editor,
        Screen.Terminal,
        Screen.GitHub,
        Screen.Copilot,
        Screen.Settings
    )

    NavigationRail(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xF4070A10)
    ) {
        Spacer(Modifier.height(9.dp))

        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AvCyan,
                            AvPurple,
                            AvPink
                        )
                    )
                )
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF080B12)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "AV",
                    color = AvCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        items.forEach { item ->
            NavigationRailItem(
                selected = screen == item,
                onClick = { onNavigate(item) },
                icon = { Text(item.glyph, fontSize = 17.sp) },
                label = {
                    Text(
                        when (item) {
                            Screen.Editor -> "Studio"
                            Screen.Settings -> "System"
                            else -> item.label
                        },
                        fontSize = 8.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun AvBottomBar088(
    screen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val items = listOf(
        Screen.Home,
        Screen.Explorer,
        Screen.Editor,
        Screen.Terminal,
        Screen.Copilot,
        Screen.Settings
    )

    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color(0xF4090D13)
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = screen == item,
                onClick = { onNavigate(item) },
                icon = { Text(item.glyph, fontSize = 17.sp) },
                label = {
                    Text(
                        when (item) {
                            Screen.Editor -> "Studio"
                            Screen.Settings -> "System"
                            else -> item.label
                        },
                        fontSize = 9.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun AvSplash088(onFinished: () -> Unit) {
    val transition =
        rememberInfiniteTransition(label = "splash-088")

    val pulse by transition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                950,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                1700,
                easing = LinearEasing
            )
        ),
        label = "sweep"
    )

    LaunchedEffect(Unit) {
        delay(2300L)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF020409)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(310.dp)
                .graphicsLayer {
                    translationX = sweep * 42f
                    alpha = 0.72f
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            AvCyan.copy(alpha = 0.16f),
                            AvPurple.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(118.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AvCyan,
                                AvBlue,
                                AvPurple,
                                AvPink
                            )
                        )
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF070A11)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            R.drawable.ic_avescode
                        ),
                        contentDescription = "Avescode",
                        modifier = Modifier.size(79.dp)
                    )
                }
            }

            Text(
                "AVESCODE",
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                letterSpacing = 3.sp
            )

            Text(
                "Developer Studio",
                color = Color(0xFFB8C2D1),
                fontSize = 12.sp
            )

            Text(
                "AvOS 16.0",
                color = AvCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "BOOT • RUNTIME • AI • GPU",
                color = Color(0xFF788599),
                fontSize = 8.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun AvSystemCenter088(
    context: android.content.Context,
    performance: Boolean,
    aiBoost: Boolean,
    rgbMotion: Boolean,
    lowPower: Boolean,
    autoSave: Boolean,
    safeMode: Boolean,
    haptics: Boolean,
    smartCache: Boolean,
    backgroundTasks: Boolean,
    networkAssist: Boolean,
    compactMode: Boolean,
    immersive: Boolean,
    onPerformance: (Boolean) -> Unit,
    onAiBoost: (Boolean) -> Unit,
    onRgbMotion: (Boolean) -> Unit,
    onLowPower: (Boolean) -> Unit,
    onAutoSave: (Boolean) -> Unit,
    onSafeMode: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onSmartCache: (Boolean) -> Unit,
    onBackgroundTasks: (Boolean) -> Unit,
    onNetworkAssist: (Boolean) -> Unit,
    onCompactMode: (Boolean) -> Unit,
    onImmersive: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var snapshot by remember {
        mutableStateOf(
            AvOSSystem.snapshot(context)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("DONE")
            }
        },
        title = {
            Column {
                Text(
                    "AvOS Control Center",
                    fontWeight =
                        FontWeight.Black
                )

                Text(
                    "Avescode 0.9.0 • Full System",
                    color = AvCyan,
                    fontSize = 10.sp
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                AvSystemSection(
                    "LIVE DEVICE",
                    listOf(
                        "Device" to snapshot.device,
                        "Android" to
                            "${snapshot.android} / API ${snapshot.api}",
                        "SoC" to snapshot.soc,
                        "CPU cores" to
                            snapshot.cpuCores.toString(),
                        "Battery" to snapshot.battery,
                        "Network" to snapshot.network,
                        "Storage" to snapshot.storage,
                        "RAM" to snapshot.ram
                    )
                )

                AvSystemSection(
                    "AVOS PROFILE",
                    listOf(
                        "OS" to "AvOS 16.0",
                        "CPU" to
                            "AvCPU 16.0 Virtual",
                        "GPU" to
                            "AvGPU Ultra AI Max",
                        "AI" to
                            "Aves Neural Runtime"
                    )
                )

                Text(
                    "RUNTIME CONTROLS",
                    color = AvCyan,
                    fontSize = 9.sp,
                    fontWeight =
                        FontWeight.Black
                )

                AvToggle(
                    "Performance mode",
                    "Prefer responsive UI",
                    performance,
                    onPerformance
                )

                AvToggle(
                    "AI Boost",
                    "Prioritize Copilot workflow",
                    aiBoost,
                    onAiBoost
                )

                AvToggle(
                    "RGB Motion",
                    "Animated system shell",
                    rgbMotion,
                    onRgbMotion
                )

                AvToggle(
                    "Low Power",
                    "Reduce decorative processing",
                    lowPower,
                    onLowPower
                )

                AvToggle(
                    "Auto Save",
                    "Prefer safe editor recovery",
                    autoSave,
                    onAutoSave
                )

                AvToggle(
                    "Safe Mode",
                    "Disable decorative effects",
                    safeMode,
                    onSafeMode
                )

                AvToggle(
                    "Haptics",
                    "Allow supported tactile feedback",
                    haptics,
                    onHaptics
                )

                AvToggle(
                    "Smart Cache",
                    "Prefer cached UI state",
                    smartCache,
                    onSmartCache
                )

                AvToggle(
                    "Background Tasks",
                    "Keep app-side background work available",
                    backgroundTasks,
                    onBackgroundTasks
                )

                AvToggle(
                    "Network Assist",
                    "Prefer responsive network UX",
                    networkAssist,
                    onNetworkAssist
                )

                AvToggle(
                    "Compact Mode",
                    "Denser controls for small screens",
                    compactMode,
                    onCompactMode
                )

                AvToggle(
                    "Immersive UI",
                    "Prefer edge-to-edge visual shell",
                    immersive,
                    onImmersive
                )

                Text(
                    "FILES & MEDIA",
                    color = AvCyan,
                    fontSize = 9.sp,
                    fontWeight =
                        FontWeight.Black
                )

                AvSystemSection(
                    "ACCESS",
                    listOf(
                        "Media" to
                            AvOSSystem.mediaAccessSummary(
                                context
                            ),
                        "Workspace" to
                            "Storage Access Framework"
                    )
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.requestMediaAccess(
                                context
                            )
                            snapshot =
                                AvOSSystem.snapshot(
                                    context
                                )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Grant media")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openFiles(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Files")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openMediaPicker(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Media")
                    }
                }

                Text(
                    "SYSTEM ACTIONS",
                    color = AvCyan,
                    fontSize = 9.sp,
                    fontWeight =
                        FontWeight.Black
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openAndroidSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Android")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openWifiSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Wi-Fi")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openDisplaySettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Display")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openBatterySettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Battery")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openBatteryOptimizationSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Battery mode")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openStorageSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Storage")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openNotificationSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Notifications")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openDefaultApps(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Default apps")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openDeveloperSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Developer")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openLanguageSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Language")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openDateTimeSettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Date & time")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openAccessibilitySettings(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Accessibility")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AvOSSystem.openAppInfo(
                                context
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Permissions")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.shareDiagnostics(
                                context,
                                snapshot
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Diagnostics")
                    }

                    OutlinedButton(
                        onClick = {
                            AvOSSystem.clearAppCache(
                                context
                            )
                            snapshot =
                                AvOSSystem.snapshot(
                                    context
                                )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("Clear cache")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = {
                            snapshot =
                                AvOSSystem.snapshot(
                                    context
                                )
                        }
                    ) {
                        Text("Refresh system state")
                    }
                }

                Text(
                    "AvCPU/AvGPU adalah profil software Avescode; APK tetap berjalan pada hardware Android asli.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        },
        containerColor =
            Color(0xF5161C26)
    )
}

@Composable
private fun AvSystemSection(
    title: String,
    rows: List<Pair<String, String>>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D131D))
            .border(
                1.dp,
                Color(0xFF202B3A),
                RoundedCornerShape(14.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            title,
            color = AvCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )

        rows.forEach { (key, value) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    key,
                    Modifier.weight(0.35f),
                    color = Color(0xFF8D9BB0),
                    fontSize = 9.sp
                )

                Text(
                    value,
                    Modifier.weight(0.65f),
                    color = Color(0xFFE3EAF5),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AvToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f)
        ) {
            Text(
                title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onChecked
        )
    }
}
