package com.example.esp8266controller.connection

import com.example.esp8266controller.model.ConnectionType
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpConnectionManager(
    private val ipAddress: String,
    private val port: Int
) : ConnectionManager {

    override val connectionType: ConnectionType = ConnectionType.WIFI_UDP

    private var _connectionState: ConnectionState = ConnectionState.Disconnected
    override val connectionState: ConnectionState
        get() = _connectionState

    private var socket: DatagramSocket? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            socket = DatagramSocket()
            _connectionState = ConnectionState.Connected("$ipAddress:$port (UDP)")
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState = ConnectionState.Error(e.message ?: "UDP初始化失败")
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            socket?.close()
            socket = null
            _connectionState = ConnectionState.Disconnected
        }
    }

    override suspend fun sendData(data: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val s = socket ?: return@withContext Result.failure(Exception("UDP未初始化"))
            val bytes = data.toByteArray()
            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            s.send(packet)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getConnectionInfo(): String {
        return when (_connectionState) {
            is ConnectionState.Connected -> "Wi-Fi (UDP): $ipAddress:$port"
            is ConnectionState.Connecting -> "正在初始化..."
            is ConnectionState.Disconnected -> "未连接"
            is ConnectionState.Error -> "初始化错误"
        }
    }
}
