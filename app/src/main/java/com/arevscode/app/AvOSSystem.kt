package com.arevscode.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import java.util.Locale

class AvOSPreferences(context: Context) {
    private val prefs =
        context.getSharedPreferences("avos_system", Context.MODE_PRIVATE)

    var performance: Boolean
        get() = prefs.getBoolean("performance", true)
        set(value) = prefs.edit().putBoolean("performance", value).apply()

    var aiBoost: Boolean
        get() = prefs.getBoolean("ai_boost", true)
        set(value) = prefs.edit().putBoolean("ai_boost", value).apply()

    var rgbMotion: Boolean
        get() = prefs.getBoolean("rgb_motion", true)
        set(value) = prefs.edit().putBoolean("rgb_motion", value).apply()

    var lowPower: Boolean
        get() = prefs.getBoolean("low_power", false)
        set(value) = prefs.edit().putBoolean("low_power", value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean("auto_save", true)
        set(value) = prefs.edit().putBoolean("auto_save", value).apply()

    var safeMode: Boolean
        get() = prefs.getBoolean("safe_mode", false)
        set(value) = prefs.edit().putBoolean("safe_mode", value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var smartCache: Boolean
        get() = prefs.getBoolean("smart_cache", true)
        set(value) = prefs.edit().putBoolean("smart_cache", value).apply()

    var backgroundTasks: Boolean
        get() = prefs.getBoolean("background_tasks", true)
        set(value) = prefs.edit().putBoolean("background_tasks", value).apply()

    var networkAssist: Boolean
        get() = prefs.getBoolean("network_assist", true)
        set(value) = prefs.edit().putBoolean("network_assist", value).apply()

    var compactMode: Boolean
        get() = prefs.getBoolean("compact_mode", false)
        set(value) = prefs.edit().putBoolean("compact_mode", value).apply()

    var immersive: Boolean
        get() = prefs.getBoolean("immersive", true)
        set(value) = prefs.edit().putBoolean("immersive", value).apply()
}

data class AvOSDeviceSnapshot(
    val device: String,
    val android: String,
    val api: Int,
    val soc: String,
    val cpuCores: Int,
    val battery: String,
    val network: String,
    val storage: String,
    val ram: String
)

object AvOSSystem {
    fun snapshot(context: Context): AvOSDeviceSnapshot {
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val batteryPercent =
            batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: -1

        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager

        val networkName =
            connectivity?.activeNetwork?.let { active ->
                connectivity.getNetworkCapabilities(active)?.let { caps ->
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                            "Wi-Fi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                            "Mobile data"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                            "Ethernet"
                        else ->
                            "Connected"
                    }
                }
            } ?: "Offline"

        val stat = StatFs(context.filesDir.absolutePath)
        val freeGb =
            stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        val totalGb =
            stat.totalBytes / 1024.0 / 1024.0 / 1024.0

        val runtime = Runtime.getRuntime()
        val maxMb =
            runtime.maxMemory() / 1024L / 1024L
        val usedMb =
            (runtime.totalMemory() - runtime.freeMemory()) /
                1024L / 1024L

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

        return AvOSDeviceSnapshot(
            device =
                "${Build.MANUFACTURER} ${Build.MODEL}",
            android =
                Build.VERSION.RELEASE ?: "?",
            api =
                Build.VERSION.SDK_INT,
            soc =
                soc,
            cpuCores =
                Runtime.getRuntime().availableProcessors(),
            battery =
                if (batteryPercent >= 0) {
                    "$batteryPercent%"
                } else {
                    "Unavailable"
                },
            network =
                networkName,
            storage =
                String.format(
                    Locale.US,
                    "%.1f / %.1f GB free",
                    freeGb,
                    totalGb
                ),
            ram =
                "$usedMb MB used • $maxMb MB limit"
        )
    }

    fun openAndroidSettings(context: Context) =
        safeStart(
            context,
            Intent(Settings.ACTION_SETTINGS)
        )

    fun openWifiSettings(context: Context) =
        safeStart(
            context,
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )

    fun openDisplaySettings(context: Context) =
        safeStart(
            context,
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        )

    fun openBatterySettings(context: Context) =
        safeStart(
            context,
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        )

    fun openDeveloperSettings(context: Context) =
        safeStart(
            context,
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )

    fun openAppInfo(context: Context) =
        safeStart(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data =
                    Uri.parse(
                        "package:${context.packageName}"
                    )
            }
        )

    fun shareDiagnostics(
        context: Context,
        snapshot: AvOSDeviceSnapshot
    ) {
        val report = buildString {
            appendLine("Avescode System Diagnostics")
            appendLine("AvOS 16.0")
            appendLine("App: ${context.packageName}")
            appendLine("Device: ${snapshot.device}")
            appendLine(
                "Android: ${snapshot.android} / API ${snapshot.api}"
            )
            appendLine("SoC: ${snapshot.soc}")
            appendLine(
                "CPU cores: ${snapshot.cpuCores}"
            )
            appendLine(
                "Battery: ${snapshot.battery}"
            )
            appendLine(
                "Network: ${snapshot.network}"
            )
            appendLine(
                "Storage: ${snapshot.storage}"
            )
            appendLine(
                "RAM: ${snapshot.ram}"
            )
        }

        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    report
                )
            }

        safeStart(
            context,
            Intent.createChooser(
                send,
                "Share diagnostics"
            )
        )
    }

    fun clearAppCache(context: Context) {
        runCatching {
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        }
    }

    private fun safeStart(
        context: Context,
        intent: Intent
    ) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
        runCatching {
            context.startActivity(intent)
        }
    }
}
