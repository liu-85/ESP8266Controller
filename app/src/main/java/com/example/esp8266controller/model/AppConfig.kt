package com.example.esp8266controller.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

enum class AppTheme {
    THEME_1, THEME_2, THEME_3, THEME_4
}

data class AppConfig(
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val controlConfig: ControlConfig = ControlConfig(),
    val currentTheme: AppTheme = AppTheme.THEME_1
) {
    companion object {
        private const val PREFS_NAME = "esp8266_controller_prefs"

        fun load(context: Context): AppConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppConfig(
                connectionConfig = loadConnectionConfig(prefs),
                controlConfig = loadControlConfig(prefs),
                currentTheme = AppTheme.valueOf(prefs.getString("app_theme", AppTheme.THEME_1.name) ?: AppTheme.THEME_1.name)
            )
        }

        private fun loadConnectionConfig(prefs: SharedPreferences): ConnectionConfig {
            return ConnectionConfig(
                connectionType = ConnectionType.valueOf(
                    prefs.getString("connection_type", ConnectionType.WIFI.name) ?: ConnectionType.WIFI.name
                ),
                wifiIp = prefs.getString("wifi_ip", "192.168.2.169") ?: "192.168.2.169",
                wifiPort = prefs.getInt("wifi_port", 2000),
                bluetoothAddress = prefs.getString("bluetooth_address", "") ?: "",
                bluetoothName = prefs.getString("bluetooth_name", "") ?: ""
            )
        }

        private fun loadControlConfig(prefs: SharedPreferences): ControlConfig {
            val sourcesJson = prefs.getString("channel_sources_v2", null)
            val sources = if (sourcesJson != null) {
                try {
                    val outerArr = JSONArray(sourcesJson)
                    (0 until 8).map { i ->
                        val innerArr = outerArr.getJSONArray(i)
                        (0 until innerArr.length()).map { j ->
                            ControlSource.valueOf(innerArr.getString(j))
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            return ControlConfig(
                channelSources = sources ?: listOf(
                    listOf(ControlSource.LEFT_JOYSTICK_Y),
                    listOf(ControlSource.RIGHT_JOYSTICK_X),
                    listOf(ControlSource.NONE),
                    listOf(ControlSource.NONE),
                    listOf(ControlSource.NONE),
                    listOf(ControlSource.NONE),
                    listOf(ControlSource.NONE),
                    listOf(ControlSource.NONE)
                ),
                minChannelValue = prefs.getInt("min_channel_value", 1000),
                maxChannelValue = prefs.getInt("max_channel_value", 2000),
                centerValue = prefs.getInt("center_value", 1500),
                servoCenterOffset = prefs.getInt("servo_center_offset", 0),
                throttleCurve = ThrottleCurve.valueOf(prefs.getString("throttle_curve", ThrottleCurve.LINEAR.name) ?: ThrottleCurve.LINEAR.name),
                sendIntervalMs = prefs.getLong("send_interval_ms", 40),
                heartbeatIntervalMs = prefs.getLong("heartbeat_interval_ms", 1000),
                inactivityTimeoutMs = prefs.getLong("inactivity_timeout_ms", 1800000),
                isTimerEnabled = prefs.getBoolean("is_timer_enabled", false),
                isGyroEnabled = prefs.getBoolean("is_gyro_enabled", false),
                gyroSensitivity = prefs.getFloat("gyro_sensitivity", 1.0f),
                smoothingFactor = prefs.getFloat("smoothing_factor", 0.5f),
                enableVibration = prefs.getBoolean("enable_vibration", true)
            )
        }

        fun save(context: Context, config: AppConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("connection_type", config.connectionConfig.connectionType.name)
                putString("wifi_ip", config.connectionConfig.wifiIp)
                putInt("wifi_port", config.connectionConfig.wifiPort)
                putString("bluetooth_address", config.connectionConfig.bluetoothAddress)
                putString("bluetooth_name", config.connectionConfig.bluetoothName)

                val outerArr = JSONArray()
                config.controlConfig.channelSources.forEach { sources ->
                    val innerArr = JSONArray()
                    sources.forEach { innerArr.put(it.name) }
                    outerArr.put(innerArr)
                }
                putString("channel_sources_v2", outerArr.toString())

                putInt("min_channel_value", config.controlConfig.minChannelValue)
                putInt("max_channel_value", config.controlConfig.maxChannelValue)
                putInt("center_value", config.controlConfig.centerValue)
                putInt("servo_center_offset", config.controlConfig.servoCenterOffset)
                putString("throttle_curve", config.controlConfig.throttleCurve.name)
                putLong("send_interval_ms", config.controlConfig.sendIntervalMs)
                putLong("heartbeat_interval_ms", config.controlConfig.heartbeatIntervalMs)
                putLong("inactivity_timeout_ms", config.controlConfig.inactivityTimeoutMs)
                putBoolean("is_timer_enabled", config.controlConfig.isTimerEnabled)
                putBoolean("is_gyro_enabled", config.controlConfig.isGyroEnabled)
                putFloat("gyro_sensitivity", config.controlConfig.gyroSensitivity)
                putFloat("smoothing_factor", config.controlConfig.smoothingFactor)
                putBoolean("enable_vibration", config.controlConfig.enableVibration)

                putString("app_theme", config.currentTheme.name)
            }.apply()
        }
    }
}
