package com.example.esp8266controller.connection

import com.example.esp8266controller.model.ConnectionType
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.Charset

class WifiConnectionManager(
    private val ipAddress: String,
    private val port: Int
) : ConnectionManager {

    override val connectionType: ConnectionType = ConnectionType.WIFI

    private var _connectionState: ConnectionState = ConnectionState.Disconnected
    override val connectionState: ConnectionState
        get() = _connectionState

    private var socket: Socket? = null
    private var outputStream: OutputStreamWriter? = null
    private var scope: CoroutineScope? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState = ConnectionState.Connecting
            socket = Socket(ipAddress, port)
            socket?.soTimeout = 5000
            outputStream = OutputStreamWriter(socket?.getOutputStream(), Charset.forName("UTF-8"))
            _connectionState = ConnectionState.Connected("$ipAddress:$port")
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState = ConnectionState.Error(e.message ?: "连接失败")
            disconnect()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            } finally {
                outputStream = null
                socket = null
                _connectionState = ConnectionState.Disconnected
            }
        }
    }

    override suspend fun sendData(data: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (_connectionState !is ConnectionState.Connected) {
                return@withContext Result.failure(Exception("未连接"))
            }
            outputStream?.write(data)
            outputStream?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState = ConnectionState.Error(e.message ?: "发送失败")
            disconnect()
            Result.failure(e)
        }
    }

    override fun getConnectionInfo(): String {
        return when (_connectionState) {
            is ConnectionState.Connected -> "Wi-Fi: $ipAddress:$port"
            is ConnectionState.Connecting -> "正在连接..."
            is ConnectionState.Disconnected -> "未连接"
            is ConnectionState.Error -> "错误: ${(_connectionState as ConnectionState.Error).message}"
        }
    }
}