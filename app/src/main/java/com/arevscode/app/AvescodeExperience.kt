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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val AvCyan = Color(0xFF35E7FF)
private val AvPurple = Color(0xFF8B5CFF)
private val AvPink = Color(0xFFFF4DD3)
private val AvGreen = Color(0xFF59E7AA)
private val AvCard = Color(0xE9131822)
private val AvCard2 = Color(0xE90B1018)

private var splashConsumed = false

@Composable
fun AvescodeShellV2WithSplash(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {
    var showSplash by rememberSaveable {
        mutableStateOf(!splashConsumed)
    }

    Box(Modifier.fillMaxSize()) {
        AvescodeShellV2(
            screen = screen,
            onNavigate = onNavigate,
            content = content
        )

        if (showSplash) {
            AvescodeSplashOverlay {
                splashConsumed = true
                showSplash = false
            }
        }
    }
}

@Composable
private fun AvescodeSplashOverlay(
    onFinished: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                1000,
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
                1800,
                easing = LinearEasing
            )
        ),
        label = "sweep"
    )

    LaunchedEffect(Unit) {
        delay(2200L)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF020409))
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(270.dp)
                .graphicsLayer {
                    translationX = sweep * 36f
                    alpha = 0.7f
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            AvCyan.copy(alpha = 0.14f),
                            AvPurple.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AvCyan,
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
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF070A11)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_avescode),
                        contentDescription = "Avescode",
                        modifier = Modifier.size(76.dp)
                    )
                }
            }

            Text(
                "AVESCODE",
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 3.sp
            )

            Text(
                "Developer Studio",
                color = Color(0xFFAEB9CA),
                fontSize = 12.sp
            )

            Text(
                "AvOS 16.0",
                color = AvCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                Modifier
                    .width(150.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF171E2A))
            ) {
                Box(
                    Modifier
                        .width(65.dp)
                        .height(3.dp)
                        .graphicsLayer {
                            translationX = sweep * 42f
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    AvCyan,
                                    AvPurple,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun AvescodeShellV2(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        if (maxWidth >= 700.dp) {
            Row(Modifier.fillMaxSize()) {
                AvRail(screen, onNavigate)

                Column(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    AvRibbon(screen)

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
                AvRibbon(screen)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    content()
                }

                AvBottomBar(screen, onNavigate)
            }
        }
    }
}

@Composable
private fun AvRibbon(screen: Screen) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "AvOS 16.0",
                color = AvCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                when (screen) {
                    Screen.Editor -> "Developer Studio"
                    Screen.Copilot -> "AI Command Center"
                    Screen.Settings -> "System Center"
                    else -> screen.label
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            AvStatus("CPU", AvGreen)
            AvStatus("GPU", AvPurple)
            AvStatus("AI", AvPink)
        }
    }
}

@Composable
private fun AvStatus(
    label: String,
    color: Color
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF101722))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )

        Spacer(Modifier.width(4.dp))

        Text(label, fontSize = 8.sp)
    }
}

@Composable
private fun AvRail(
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
            .width(82.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF070A10)
    ) {
        Spacer(Modifier.height(10.dp))

        Text(
            "AV",
            color = AvCyan,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(10.dp))

        items.forEach { item ->
            NavigationRailItem(
                selected = screen == item,
                onClick = { onNavigate(item) },
                icon = {
                    Text(
                        item.glyph,
                        fontSize = 17.sp
                    )
                },
                label = {
                    Text(
                        if (item == Screen.Settings) "System"
                        else item.label,
                        fontSize = 8.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun AvBottomBar(
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
                icon = {
                    Text(item.glyph, fontSize = 17.sp)
                },
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
fun AvescodeHomeV2(
    workspace: String,
    onOpen: (Screen) -> Unit
) {
    Page {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "DEVELOPER OS",
                    color = AvCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Build. Run. Ship.",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    "Avescode Developer Studio • mobile-first development",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Box(
                Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AvCyan,
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF070A10)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_avescode),
                        contentDescription = "Avescode",
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AvCard
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "AvOS 16.0 Runtime",
                    color = AvCyan,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    AvMetric(
                        "CPU",
                        "AvCPU 16.0",
                        Modifier.weight(1f)
                    )
                    AvMetric(
                        "GPU",
                        "AvGPU AI Max",
                        Modifier.weight(1f)
                    )
                    AvMetric(
                        "AI",
                        "Neural",
                        Modifier.weight(1f)
                    )
                }

                Text(
                    "Workspace • ${
                        workspace.ifBlank { "No workspace" }
                    }",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        Text(
            "QUICK LAUNCH",
            color = AvCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AvQuick("Studio", "⌘", "Edit code", AvPurple) {
                onOpen(Screen.Editor)
            }

            AvQuick("Terminal", ">_", "Run commands", AvGreen) {
                onOpen(Screen.Terminal)
            }

            AvQuick("AI", "✦", "Coding agent", AvPink) {
                onOpen(Screen.Copilot)
            }

            AvQuick("GitHub", "◎", "Source control", AvCyan) {
                onOpen(Screen.GitHub)
            }

            AvQuick("System", "◈", "AvOS Center", AvCyan) {
                onOpen(Screen.Settings)
            }
        }

        Text(
            "WORKSPACE STATUS",
            color = AvCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        AvFeature(
            "Explorer",
            "Workspace, files, folders and quick actions.",
            "01"
        )

        AvFeature(
            "Studio",
            "Editor, tabs, search, command palette and preview.",
            "02"
        )

        AvFeature(
            "AI Agent",
            "Natural-language coding with explicit Apply / Reject.",
            "03"
        )

        AvFeature(
            "Runtime",
            "Terminal plus localhost web preview.",
            "04"
        )
    }
}

@Composable
private fun AvMetric(
    label: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101722))
            .padding(9.dp)
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp
        )

        Text(
            value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvQuick(
    title: String,
    icon: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xE9131822)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                icon,
                color = accent,
                fontSize = 21.sp
            )

            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AvFeature(
    title: String,
    text: String,
    index: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE9121720))
            .border(
                1.dp,
                Color(0xFF1F2937),
                RoundedCornerShape(16.dp)
            )
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index,
            color = AvCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun AvSystemCenter(
    onOpenGitHub: () -> Unit,
    onOpenAndroidSettings: () -> Unit
) {
    var performance by rememberSaveable { mutableStateOf(true) }
    var aiBoost by rememberSaveable { mutableStateOf(true) }
    var rgbMotion by rememberSaveable { mutableStateOf(true) }
    var reducedMotion by rememberSaveable { mutableStateOf(false) }

    val soc =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                Build.SOC_MANUFACTURER,
                Build.SOC_MODEL
            )
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Device-managed" }
        } else {
            "Device-managed"
        }

    Page {
        Text(
            "SYSTEM CENTER",
            color = AvCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "AvOS 16.0",
            fontSize = 29.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            "Avescode system profile, controls and runtime information.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AvCard
            ),
            shape = RoundedCornerShape(21.dp)
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvRow("OS", "AvOS 16.0", AvCyan)
                AvRow("CPU profile", "AvCPU 16.0 Compute", AvGreen)
                AvRow("GPU profile", "AvGPU Ultra AI Max", AvPurple)
                AvRow("AI engine", "Aves Neural Runtime", AvPink)
                AvRow(
                    "Target",
                    "iOS 27-class UX / Core Ultra AI-PC UX",
                    AvCyan
                )
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AvCard2
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "ACTUAL DEVICE",
                    color = AvCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                AvRow("Device", Build.MODEL, Color.White)
                AvRow(
                    "Android API",
                    Build.VERSION.SDK_INT.toString(),
                    Color.White
                )
                AvRow("SoC", soc, Color.White)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AvCard2
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    "PERFORMANCE",
                    color = AvCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                AvToggle(
                    "Performance mode",
                    "Prefer responsive UI",
                    performance
                ) { performance = it }

                AvToggle(
                    "AI Boost",
                    "Prioritize AI workflow",
                    aiBoost
                ) { aiBoost = it }

                AvToggle(
                    "RGB Motion",
                    "Animated visual shell",
                    rgbMotion
                ) { rgbMotion = it }

                AvToggle(
                    "Reduced motion",
                    "Disable decorative movement",
                    reducedMotion
                ) { reducedMotion = it }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AvCard2
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "SYSTEM ACTIONS",
                    color = AvCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenGitHub,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GitHub")
                    }

                    OutlinedButton(
                        onClick = onOpenAndroidSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Android")
                    }
                }

                Text(
                    "AvOS/AvCPU/AvGPU adalah profil software Avescode; tidak mengubah CPU/GPU fisik perangkat.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun AvRow(
    label: String,
    value: String,
    accent: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(0.36f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )

        Text(
            value,
            Modifier.weight(0.64f),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}
