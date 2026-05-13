package com.example.esp8266controller.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.esp8266controller.model.ConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.util.*

class BluetoothConnectionManager(
    private val context: Context,
    private val deviceAddress: String
) : ConnectionManager {

    override val connectionType: ConnectionType = ConnectionType.BLUETOOTH

    private var _connectionState: ConnectionState = ConnectionState.Disconnected
    override val connectionState: ConnectionState
        get() = _connectionState

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStreamWriter? = null
    private var deviceName: String = ""

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState = ConnectionState.Connecting

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                _connectionState = ConnectionState.Error("蓝牙未启用")
                return@withContext Result.failure(Exception("蓝牙未启用"))
            }

            val device: BluetoothDevice? = bluetoothAdapter.bondedDevices.find { it.address == deviceAddress }
            if (device == null) {
                _connectionState = ConnectionState.Error("未找到设备")
                return@withContext Result.failure(Exception("未找到设备"))
            }

            deviceName = device.name ?: deviceAddress

            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            outputStream = OutputStreamWriter(bluetoothSocket?.outputStream, Charsets.UTF_8)

            _connectionState = ConnectionState.Connected(deviceName)
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
                bluetoothSocket?.close()
            } catch (e: Exception) {
                // Ignore close errors
            } finally {
                outputStream = null
                bluetoothSocket = null
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
            is ConnectionState.Connected -> "蓝牙: $deviceName"
            is ConnectionState.Connecting -> "正在连接..."
            is ConnectionState.Disconnected -> "未连接"
            is ConnectionState.Error -> "错误: ${(_connectionState as ConnectionState.Error).message}"
        }
    }
}