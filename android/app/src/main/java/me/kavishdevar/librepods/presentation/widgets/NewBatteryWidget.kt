package me.kavishdevar.librepods.presentation.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.provider.Settings
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import me.kavishdevar.librepods.R
import kotlin.jvm.java

enum class DeviceType {
    PHONE,
    BLUETOOTH
}

data class DeviceBattery(
    val name: String,
    val percent: Int,
    val type: DeviceType,
//    val icon: Int // drawable res id
)

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun getConnectedDevicesBatteryInfo(context: Context): List<DeviceBattery> {
    val batteryManager = context.getSystemService(BatteryManager::class.java)
    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    val deviceName = Settings.Global.getString(
        context.contentResolver,
        Settings.Global.DEVICE_NAME
    ) ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    val phoneBatteryInfo = DeviceBattery(
        name = deviceName,
        percent = batteryLevel,
        type = DeviceType.PHONE
    )

    val adapter =
        context.getSystemService(BluetoothManager::class.java).adapter
            ?: return listOf(phoneBatteryInfo)

    val connectedDevices = adapter.bondedDevices.filter { device ->
        try {
            val m = BluetoothDevice::class.java.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.e("BatteryWidget", "Could not check connection: ${e.message}")
            false
        }
    }

    // should check if device is an AirPod and should be skipped
    val bluetoothBatteryInfo = connectedDevices.map { device ->
        val level = try {
            val m = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            m.invoke(device) as Int
        } catch (e: Exception) {
            Log.e(
                "BatteryWidget",
                "Could not get battery level for ${device.name}: ${e.message}"
            )
            -1
        }

        DeviceBattery(
            name = device.alias ?: device.name,
            percent = level,
            type = DeviceType.BLUETOOTH
        )
    }

    return listOf(phoneBatteryInfo) + bluetoothBatteryInfo
}

val fontFamily = if (Build.MANUFACTURER.equals("Google", ignoreCase = true)) {
    FontFamily("google-sans")
} else {
    FontFamily.SansSerif
}

val WidgetPadding get() = 10.dp
val SpacerPadding get() = 2.dp
class NewBatteryWidget : GlanceAppWidget() {


    override val sizeMode = SizeMode.Exact

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val connectedDevices = getConnectedDevicesBatteryInfo(context)
        provideContent {
            GlanceTheme {
                DeviceList(connectedDevices)
            }
        }
    }
}

@Composable
fun DeviceList(devices: List<DeviceBattery>) {

    val deviceCount = devices.size

    val totalSpacerHeight =
        SpacerPadding * (deviceCount - 1).coerceAtLeast(0)

    val availableWidth = (LocalSize.current.width - WidgetPadding * 2)
    val rowHeight =
        (LocalSize.current.height
            - WidgetPadding * 2
            - totalSpacerHeight) / deviceCount
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(WidgetPadding)
            .cornerRadius(24.dp)
    ) {
        devices.forEachIndexed { index, deviceBattery ->
            DeviceRow(deviceBattery, availableWidth, rowHeight)
            if (index != devices.lastIndex) {
                Spacer(GlanceModifier.height(SpacerPadding))
            }
        }
    }
}

@Composable
fun isSystemInDarkThemeGlance(): Boolean {
    val context = LocalContext.current
    val uiMode = context.resources.configuration.uiMode
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
@SuppressLint("RestrictedApi")
fun DeviceRow(device: DeviceBattery, availableWidth: Dp, rowHeight: Dp) {
    val (fillColor, textColor, backgroundColor) = when (device.type to isSystemInDarkThemeGlance()) {
        DeviceType.PHONE to true -> Triple(
            GlanceTheme.colors.primaryContainer,
            GlanceTheme.colors.primary,
            ColorProvider(android.R.color.system_accent1_900)
        )
        DeviceType.PHONE to false -> Triple(
            ColorProvider(android.R.color.system_accent1_300),
            GlanceTheme.colors.onPrimaryContainer,
            GlanceTheme.colors.primaryContainer
        )
        DeviceType.BLUETOOTH to true -> Triple(
            GlanceTheme.colors.tertiaryContainer,
            GlanceTheme.colors.tertiary,
            ColorProvider(android.R.color.system_accent3_900)
        )
        DeviceType.BLUETOOTH to false -> Triple(
            ColorProvider(android.R.color.system_accent3_300),
            GlanceTheme.colors.onTertiaryContainer,
            GlanceTheme.colors.tertiaryContainer
        )
        else -> error("Unhandled device type / theme combination")
    }

    Box( // Color is NOT onPrimary or onTertiary seems to be a composite or something
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(backgroundColor)
            .cornerRadius(14.dp)
    ) {
        if (device.percent in 0..100) {
            val fillWidth = (availableWidth * (device.percent / 100f))
            Image(
                provider = ImageProvider(R.drawable.battery_fill),
                contentDescription = null,
                colorFilter = ColorFilter.tint(fillColor),
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(fillWidth)
            )
        }

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { // text color is primary for the Phone battery and tertiary for everything else
            Text(
                text = device.name,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = textColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (device.percent >= 0) "${device.percent} %" else "—",
                style = TextStyle(color = textColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold)
            )
        }
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = NewBatteryWidget()
}
