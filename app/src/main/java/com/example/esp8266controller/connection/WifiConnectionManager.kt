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
    
    private var reconnectJob: Job? = null
    private var receiveJob: Job? = null
    private var lastResponseTime: Long = 0
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (_connectionState is ConnectionState.Connected) return@withContext Result.success(Unit)
            
            _connectionState = ConnectionState.Connecting
            val newSocket = Socket(ipAddress, port)
            newSocket.soTimeout = 1000 // 1秒超时，用于读取
            newSocket.tcpNoDelay = true
            socket = newSocket
            outputStream = OutputStreamWriter(newSocket.getOutputStream(), Charset.forName("UTF-8"))
            _connectionState = ConnectionState.Connected("$ipAddress:$port")
            lastResponseTime = System.currentTimeMillis()
            
            // 取消之前的重连任务
            reconnectJob?.cancel()
            
            // 启动接收任务以支持失控保护
            startReceiveJob()
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState = ConnectionState.Error(e.message ?: "连接失败")
            startReconnect() // 尝试重连
            Result.failure(e)
        }
    }

    private fun startReceiveJob() {
        receiveJob?.cancel()
        receiveJob = managerScope.launch {
            val reader = socket?.getInputStream()?.bufferedReader()
            try {
                while (isActive && _connectionState is ConnectionState.Connected) {
                    val line = withContext(Dispatchers.IO) {
                        reader?.readLine()
                    }
                    if (line != null) {
                        lastResponseTime = System.currentTimeMillis()
                    } else {
                        // Connection closed by server
                        break
                    }
                }
            } catch (e: Exception) {
                // Read error or timeout
            } finally {
                if (_connectionState is ConnectionState.Connected) {
                    _connectionState = ConnectionState.Error("连接已断开")
                    startReconnect()
                }
            }
        }
    }

    fun isFailsafeTriggered(): Boolean {
        if (_connectionState !is ConnectionState.Connected) return true
        return (System.currentTimeMillis() - lastResponseTime) > 300
    }

    private fun startReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = managerScope.launch {
            var delayTime = 1000L
            while (_connectionState !is ConnectionState.Connected) {
                delay(delayTime)
                connect()
                // 指数退避，最高 10 秒
                delayTime = (delayTime * 2).coerceAtMost(10000L)
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            reconnectJob?.cancel()
            receiveJob?.cancel()
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
            startReconnect() // 发送失败尝试重连
            Result.failure(e)
        }
    }

    override fun getConnectionInfo(): String {
        return when (_connectionState) {
            is ConnectionState.Connected -> "Wi-Fi: $ipAddress:$port"
            is ConnectionState.Connecting -> "正在连接..."
            is ConnectionState.Disconnected -> "未连接"
            is ConnectionState.Error -> "连接错误"
        }
    }
}
