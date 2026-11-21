package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs

enum class ThermalHealth(val label: String, val isWarning: Boolean) {
    COOL("Optimal", false),
    WARM("Warm", false),
    HOT("Heating Up", true),
    CRITICAL("Overheating", true)
}

data class SystemTelemetry(
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 4096L,
    val availableRamMb: Long = 2048L,
    val ramUsageFraction: Float = 0.5f,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 32f,
    val thermalHealth: ThermalHealth = ThermalHealth.COOL,
    val thermalStatusLabel: String = "Nominal",
    val totalStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val usedStorageFraction: Float = 0f,
    val isLowStorage: Boolean = false
) {
    val totalStorageGb: Float get() = totalStorageBytes / (1024f * 1024f * 1024f)
    val availableStorageGb: Float get() = availableStorageBytes / (1024f * 1024f * 1024f)
    val availableStorageMb: Long get() = availableStorageBytes / (1024L * 1024L)
}

/**
 * Reads live device RAM, CPU cores, Battery temperature, Android PowerManager Thermal Status,
 * and external storage metrics via StatFs with minimal allocation overhead.
 */
object SystemTelemetryMonitor {

    fun snapshot(context: Context, lowStorageThresholdMb: Int = 1024): SystemTelemetry {
        // 1. RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val availRamMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)
        val ramFraction = (usedRamMb.toFloat() / totalRamMb.toFloat()).coerceIn(0f, 1f)

        // 2. Battery & Temperature
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val rawTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
        val tempC = rawTempTenths / 10f

        // 3. Thermal Status (API 29+ PowerManager + battery temp heuristic)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val (thermalHealth, thermalLabel) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> {
                    if (tempC >= 41.5f) ThermalHealth.HOT to "Warm Battery (${"%.1f".format(tempC)}°C)"
                    else ThermalHealth.COOL to "Nominal"
                }
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalHealth.WARM to "Light Thermal Load"
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalHealth.HOT to "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalHealth.CRITICAL to "Severe Heat Warning"
                else -> ThermalHealth.COOL to "Nominal"
            }
        } else {
            when {
                tempC >= 44f -> ThermalHealth.CRITICAL to "High Battery Temp"
                tempC >= 40.5f -> ThermalHealth.HOT to "Warming Up"
                tempC >= 37.5f -> ThermalHealth.WARM to "Moderate Temp"
                else -> ThermalHealth.COOL to "Nominal"
            }
        }

        // 4. Storage (StatFs)
        val (totalBytes, availBytes) = runCatching {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val avail = stat.availableBlocksLong * stat.blockSizeLong
            total to avail
        }.getOrDefault(0L to 0L)

        val usedFraction = if (totalBytes > 0L) {
            ((totalBytes - availBytes).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val availMb = availBytes / (1024L * 1024L)

        return SystemTelemetry(
            usedRamMb = usedRamMb,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            ramUsageFraction = ramFraction,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempC,
            thermalHealth = thermalHealth,
            thermalStatusLabel = thermalLabel,
            totalStorageBytes = totalBytes,
            availableStorageBytes = availBytes,
            usedStorageFraction = usedFraction,
            isLowStorage = availMb < lowStorageThresholdMb
        )
    }
}
