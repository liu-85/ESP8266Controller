package com.example.esp8266controller.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

data class AppConfig(
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val controlConfig: ControlConfig = ControlConfig(),
    val customSwitches: List<CustomSwitch> = CustomSwitch.createDefaultSwitches(),
    val gyroEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f
) {
    companion object {
        private const val PREFS_NAME = "esp8266_controller_prefs"

        fun load(context: Context): AppConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppConfig(
                connectionConfig = loadConnectionConfig(prefs),
                controlConfig = loadControlConfig(prefs),
                customSwitches = loadCustomSwitches(prefs),
                gyroEnabled = prefs.getBoolean("gyro_enabled", false),
                gyroSensitivity = prefs.getFloat("gyro_sensitivity", 1.0f)
            )
        }

        private fun loadConnectionConfig(prefs: SharedPreferences): ConnectionConfig {
            return ConnectionConfig(
                connectionType = ConnectionType.valueOf(
                    prefs.getString("connection_type", ConnectionType.WIFI.name) ?: ConnectionType.WIFI.name
                ),
                wifiIp = prefs.getString("wifi_ip", "192.168.1.100") ?: "192.168.1.100",
                wifiPort = prefs.getInt("wifi_port", 2000),
                bluetoothAddress = prefs.getString("bluetooth_address", "") ?: "",
                bluetoothName = prefs.getString("bluetooth_name", "") ?: ""
            )
        }

        private fun loadControlConfig(prefs: SharedPreferences): ControlConfig {
            return ControlConfig(
                controlMode = ControlMode.valueOf(
                    prefs.getString("control_mode", ControlMode.SEPARATE.name) ?: ControlMode.SEPARATE.name
                ),
                ch1Source = ChannelSource.valueOf(
                    prefs.getString("ch1_source", ChannelSource.THROTTLE.name) ?: ChannelSource.THROTTLE.name
                ),
                ch2Source = ChannelSource.valueOf(
                    prefs.getString("ch2_source", ChannelSource.STEERING.name) ?: ChannelSource.STEERING.name
                ),
                throttleTemplate = prefs.getString("throttle_template", "{value}") ?: "{value}",
                steeringTemplate = prefs.getString("steering_template", "CH2:{value}") ?: "CH2:{value}",
                servoLeftCommand = prefs.getString("servo_left", "SERVO:0") ?: "SERVO:0",
                servoCenterCommand = prefs.getString("servo_center", "SERVO:90") ?: "SERVO:90",
                servoRightCommand = prefs.getString("servo_right", "SERVO:180") ?: "SERVO:180",
                minChannelValue = prefs.getInt("min_channel_value", 1000),
                maxChannelValue = prefs.getInt("max_channel_value", 2000),
                centerValue = prefs.getInt("center_value", 1500),
                sendIntervalMs = prefs.getLong("send_interval_ms", 30)
            )
        }

        private fun loadCustomSwitches(prefs: SharedPreferences): List<CustomSwitch> {
            val switchesJson = prefs.getString("custom_switches", null)
            return if (switchesJson != null) {
                try {
                    val jsonArray = JSONArray(switchesJson)
                    (0 until jsonArray.length()).map { i ->
                        val json = jsonArray.getJSONObject(i)
                        CustomSwitch(
                            id = json.getInt("id"),
                            name = json.getString("name"),
                            onCommand = json.getString("onCommand"),
                            offCommand = json.getString("offCommand"),
                            isOn = json.getBoolean("isOn")
                        )
                    }
                } catch (e: Exception) {
                    CustomSwitch.createDefaultSwitches()
                }
            } else {
                CustomSwitch.createDefaultSwitches()
            }
        }

        fun save(context: Context, config: AppConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("connection_type", config.connectionConfig.connectionType.name)
                putString("wifi_ip", config.connectionConfig.wifiIp)
                putInt("wifi_port", config.connectionConfig.wifiPort)
                putString("bluetooth_address", config.connectionConfig.bluetoothAddress)
                putString("bluetooth_name", config.connectionConfig.bluetoothName)

                putString("control_mode", config.controlConfig.controlMode.name)
                putString("ch1_source", config.controlConfig.ch1Source.name)
                putString("ch2_source", config.controlConfig.ch2Source.name)
                putString("throttle_template", config.controlConfig.throttleTemplate)
                putString("steering_template", config.controlConfig.steeringTemplate)
                putString("servo_left", config.controlConfig.servoLeftCommand)
                putString("servo_center", config.controlConfig.servoCenterCommand)
                putString("servo_right", config.controlConfig.servoRightCommand)
                putInt("min_channel_value", config.controlConfig.minChannelValue)
                putInt("max_channel_value", config.controlConfig.maxChannelValue)
                putInt("center_value", config.controlConfig.centerValue)
                putLong("send_interval_ms", config.controlConfig.sendIntervalMs)

                val switchesJson = JSONArray()
                config.customSwitches.forEach { switch ->
                    val json = org.json.JSONObject()
                    json.put("id", switch.id)
                    json.put("name", switch.name)
                    json.put("onCommand", switch.onCommand)
                    json.put("offCommand", switch.offCommand)
                    json.put("isOn", switch.isOn)
                    switchesJson.put(json)
                }
                putString("custom_switches", switchesJson.toString())

                putBoolean("gyro_enabled", config.gyroEnabled)
                putFloat("gyro_sensitivity", config.gyroSensitivity)
            }.apply()
        }
    }
}