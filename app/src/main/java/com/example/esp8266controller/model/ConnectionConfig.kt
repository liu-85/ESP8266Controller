package com.example.esp8266controller.model

data class ConnectionConfig(
    val connectionType: ConnectionType = ConnectionType.WIFI_TCP,
    val wifiIp: String = "192.168.1.100",
    val wifiPort: Int = 2000,
    val bluetoothAddress: String = "",
    val bluetoothName: String = ""
)

enum class ConnectionType {
    WIFI_TCP,
    WIFI_UDP,
    BLUETOOTH
}