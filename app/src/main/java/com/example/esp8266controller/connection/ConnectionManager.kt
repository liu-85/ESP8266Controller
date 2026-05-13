package com.example.esp8266controller.connection

import com.example.esp8266controller.model.ConnectionType

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val info: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

interface ConnectionManager {
    val connectionType: ConnectionType
    val connectionState: ConnectionState

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun sendData(data: String): Result<Unit>
    fun getConnectionInfo(): String
}