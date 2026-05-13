package com.example.esp8266controller.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.esp8266controller.R
import com.example.esp8266controller.connection.*
import com.example.esp8266controller.joystick.*
import com.example.esp8266controller.model.*
import com.example.esp8266controller.sensor.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var leftJoystick: JoystickView
    private lateinit var rightJoystick: JoystickView
    private lateinit var gyroController: GyroController

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvModeStatus: TextView
    private lateinit var switchGyroMode: SwitchCompat
    private lateinit var btnCalibrate: Button

    private lateinit var btnServoLeft: Button
    private lateinit var btnServoCenter: Button
    private lateinit var btnServoRight: Button

    private lateinit var btnSwitch1: Button
    private lateinit var btnSwitch2: Button
    private lateinit var btnSwitch3: Button
    private lateinit var btnSwitch4: Button
    private lateinit var btnSettings: Button

    // V7 Style UI Elements
    private lateinit var tvLeftValLarge: TextView
    private lateinit var tvLeftValRaw: TextView
    private lateinit var tvRightValLarge: TextView
    private lateinit var tvRightValRaw: TextView
    private lateinit var tvConnectionStatusV7: TextView
    private lateinit var tvDeviceInfoV7: TextView
    private lateinit var tvConnectTypeV7: TextView
    private lateinit var btnSettingsTop: ImageButton
    private lateinit var btnChannelsTop: ImageButton
    private lateinit var btnServoLeftV7: ImageButton
    private lateinit var btnServoCenterV7: ImageButton
    private lateinit var btnServoRightV7: ImageButton

    private var connectionManager: ConnectionManager? = null
    private var appConfig: AppConfig? = null
    private var dataProcessor: JoystickDataProcessor? = null

    private val controlJob = SupervisorJob()
    private val controlScope = CoroutineScope(Dispatchers.IO + controlJob)
    private var sendDataJob: Job? = null

    private var lastThrottleValue: Int = 1500
    private var lastSteeringValue: Int = 1500
    private var isGyroEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadConfig()
        initViews()
        setupListeners()
        setupJoystickProcessor()
        setupGyroController()
        setupPeriodicSend()
    }

    private fun loadConfig() {
        appConfig = AppConfig.load(this)
        dataProcessor = JoystickDataProcessor(appConfig!!.controlConfig)
    }

    private fun initViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvConnectionStatus = findViewById<TextView>(R.id.tv_connection_status)
        tvModeStatus = findViewById<TextView>(R.id.tv_mode_status)
        switchGyroMode = findViewById<SwitchCompat>(R.id.switch_gyro_mode)
        btnCalibrate = findViewById<Button>(R.id.btn_calibrate)

        leftJoystick = findViewById<JoystickView>(R.id.left_joystick)
        rightJoystick = findViewById<JoystickView>(R.id.right_joystick)

        btnServoLeft = findViewById<Button>(R.id.btn_servo_left)
        btnServoCenter = findViewById<Button>(R.id.btn_servo_center)
        btnServoRight = findViewById<Button>(R.id.btn_servo_right)

        btnSwitch1 = findViewById<Button>(R.id.btn_switch_1)
        btnSwitch2 = findViewById<Button>(R.id.btn_switch_2)
        btnSwitch3 = findViewById<Button>(R.id.btn_switch_3)
        btnSwitch4 = findViewById<Button>(R.id.btn_switch_4)
        btnSettings = findViewById<Button>(R.id.btn_settings)

        // V7 UI Bindings
        tvLeftValLarge = findViewById(R.id.tv_left_val_large)
        tvLeftValRaw = findViewById(R.id.tv_left_val_raw)
        tvRightValLarge = findViewById(R.id.tv_right_val_large)
        tvRightValRaw = findViewById(R.id.tv_right_val_raw)
        tvConnectionStatusV7 = findViewById(R.id.tv_connection_status_v7)
        tvDeviceInfoV7 = findViewById(R.id.tv_device_info_v7)
        tvConnectTypeV7 = findViewById(R.id.tv_connect_type_v7)
        btnSettingsTop = findViewById(R.id.btn_settings_top)
        btnChannelsTop = findViewById(R.id.btn_channels_top)
        btnServoLeftV7 = findViewById(R.id.btn_servo_left_v7)
        btnServoCenterV7 = findViewById(R.id.btn_servo_center_v7)
        btnServoRightV7 = findViewById(R.id.btn_servo_right_v7)

        updateCustomSwitches()
        updateV7Status()
    }

    private fun updateV7Status() {
        tvDeviceInfoV7.text = "装置：${appConfig?.connectionConfig?.wifiIp ?: "未知"}"
        tvConnectTypeV7.text = "连结方式：${appConfig?.connectionConfig?.connectionType?.name ?: "WIFI"}"
        
        val statusText = when (connectionManager?.connectionState) {
            is ConnectionState.Connected -> "已连线"
            is ConnectionState.Connecting -> "连线中..."
            else -> "未连线"
        }
        tvConnectionStatusV7.text = statusText
    }

    private fun updateCustomSwitches() {
        appConfig?.customSwitches?.let { switches ->
            switches.forEachIndexed { index, switch ->
                val button = when (index) {
                    0 -> btnSwitch1
                    1 -> btnSwitch2
                    2 -> btnSwitch3
                    3 -> btnSwitch4
                    else -> null
                }
                button?.text = switch.name
                button?.isSelected = switch.isOn
                updateButtonStyle(button, switch.isOn)
            }
        }
    }

    private fun updateButtonStyle(button: Button?, isOn: Boolean) {
        button?.let {
            if (isOn) {
                it.setTextColor(resources.getColor(R.color.white, null))
            } else {
                it.setTextColor(resources.getColor(R.color.textDark, null))
            }
        }
    }

    private fun setupListeners() {
        btnSettings.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
        }

        btnSettingsTop.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
        }

        btnChannelsTop.setOnClickListener {
            // TODO: Implement channel mapping UI or just open settings for now
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
        }

        switchGyroMode.setOnCheckedChangeListener { _, isChecked ->
            isGyroEnabled = isChecked
            tvModeStatus.text = if (isChecked) getString(R.string.gyro_mode) else getString(R.string.joystick_mode)
            btnCalibrate.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE

            if (isChecked) {
                leftJoystick.visibility = android.view.View.INVISIBLE
                rightJoystick.visibility = android.view.View.INVISIBLE
                gyroController.start()
            } else {
                leftJoystick.visibility = android.view.View.VISIBLE
                rightJoystick.visibility = android.view.View.VISIBLE
                gyroController.stop()
                leftJoystick.reset()
                rightJoystick.reset()
            }
        }

        btnCalibrate.setOnClickListener {
            gyroController.calibrate()
            Toast.makeText(this, "陀螺仪已校准", Toast.LENGTH_SHORT).show()
        }

        setupServoButtons()
        setupCustomSwitchButtons()
    }

    private fun setupServoButtons() {
        val onServoLeft = { sendServoCommand(appConfig!!.controlConfig.servoLeftCommand) }
        val onServoCenter = { sendServoCommand(appConfig!!.controlConfig.servoCenterCommand) }
        val onServoRight = { sendServoCommand(appConfig!!.controlConfig.servoRightCommand) }

        btnServoLeft.setOnClickListener { onServoLeft() }
        btnServoCenter.setOnClickListener { onServoCenter() }
        btnServoRight.setOnClickListener { onServoRight() }

        btnServoLeftV7.setOnClickListener { onServoLeft() }
        btnServoCenterV7.setOnClickListener { onServoCenter() }
        btnServoRightV7.setOnClickListener { onServoRight() }
    }

    private fun setupCustomSwitchButtons() {
        btnSwitch1.setOnClickListener { toggleCustomSwitch(0) }
        btnSwitch2.setOnClickListener { toggleCustomSwitch(1) }
        btnSwitch3.setOnClickListener { toggleCustomSwitch(2) }
        btnSwitch4.setOnClickListener { toggleCustomSwitch(3) }
    }

    private fun toggleCustomSwitch(index: Int) {
        appConfig?.customSwitches?.getOrNull(index)?.let { switch ->
            switch.isOn = !switch.isOn
            val command = if (switch.isOn) switch.onCommand else switch.offCommand
            sendCustomCommand(command)

            val button = when (index) {
                0 -> btnSwitch1
                1 -> btnSwitch2
                2 -> btnSwitch3
                3 -> btnSwitch4
                else -> null
            }
            button?.isSelected = switch.isOn
            updateButtonStyle(button, switch.isOn)

            AppConfig.save(this, appConfig!!)
        }
    }

    private fun setupJoystickProcessor() {
        leftJoystick.setOnJoystickMoveListener { angle, strength ->
            if (!isGyroEnabled) {
                processDataWithJoystick(angle, strength, rightJoystick.angle, rightJoystick.strength)
            }
        }

        rightJoystick.setOnJoystickMoveListener { angle, strength ->
            if (!isGyroEnabled) {
                processDataWithJoystick(leftJoystick.angle, leftJoystick.strength, angle, strength)
            }
        }
    }

    private fun processDataWithJoystick(
        throttleAngle: Double,
        throttleStrength: Float,
        steeringAngle: Double,
        steeringStrength: Float
    ) {
        val channelData = dataProcessor?.processJoystickData(
            throttleAngle, throttleStrength,
            steeringAngle, steeringStrength
        )
        channelData?.let {
            lastThrottleValue = it.throttle
            lastSteeringValue = it.steering
        }
    }

    private fun setupGyroController() {
        gyroController = GyroController(this, appConfig!!.controlConfig)
        gyroController.setSensitivity(appConfig!!.gyroSensitivity)

        gyroController.setOnDataUpdateListener { pitch, roll ->
            if (isGyroEnabled) {
                val channelData = gyroController.processGyroData(pitch, roll)
                lastThrottleValue = channelData.throttle
                lastSteeringValue = channelData.steering
            }
        }
    }

    private fun setupPeriodicSend() {
        sendDataJob = controlScope.launch {
            while (isActive) {
                sendControlData()
                delay(appConfig!!.controlConfig.sendIntervalMs)
            }
        }
    }

    private suspend fun sendControlData() {
        connectionManager?.let { manager ->
            if (manager.connectionState is ConnectionState.Connected) {
                // 如果摇杆没有被触摸，确保发送中性值 1500
                val throttle = if (leftJoystick.strength < 0.05f) 1500 else lastThrottleValue
                val steering = if (rightJoystick.strength < 0.05f) 1500 else lastSteeringValue
                
                val command = dataProcessor?.formatCommand(throttle, steering)
                command?.let { payload ->
                    // 更新 UI 数值显示 (回到主线程)
                    withContext(Dispatchers.Main) {
                        tvLeftValRaw.text = throttle.toString()
                        tvRightValRaw.text = steering.toString()
                        tvLeftValLarge.text = (throttle - 1500).toString()
                        tvRightValLarge.text = (steering - 1500).toString()
                        updateV7Status()
                    }

                    val sendResult = manager.sendData(payload)
                    if (sendResult.isFailure) {
                        val error = sendResult.exceptionOrNull()
                        lifecycleScope.launch {
                            Toast.makeText(
                                this@MainActivity,
                                "发送失败: ${error?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun sendServoCommand(command: String) {
        lifecycleScope.launch {
            val manager = connectionManager ?: return@launch
            val sendResult = manager.sendData("$command\n")
            if (sendResult.isFailure) {
                val error = sendResult.exceptionOrNull()
                Toast.makeText(this@MainActivity, "发送失败: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendCustomCommand(command: String) {
        lifecycleScope.launch {
            val manager = connectionManager ?: return@launch
            val sendResult = manager.sendData("$command\n")
            if (sendResult.isFailure) {
                val error = sendResult.exceptionOrNull()
                Toast.makeText(this@MainActivity, "发送失败: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun connectToWifi(ip: String, port: Int) {
        lifecycleScope.launch {
            connectionManager?.disconnect()
            connectionManager = WifiConnectionManager(ip, port)

            tvConnectionStatus.text = getString(R.string.connecting)
            val connectResult = connectionManager!!.connect()
            if (connectResult.isSuccess) {
                tvConnectionStatus.text = connectionManager!!.getConnectionInfo()
                // 连接成功后立即发送一次停止指令 (1500, 1500)，防止电机因旧状态乱转
                connectionManager?.sendData("SS2:1500,1500\n")
            } else {
                tvConnectionStatus.text = getString(R.string.disconnected)
                val error = connectResult.exceptionOrNull()
                Toast.makeText(this@MainActivity, "连接失败: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun connectToBluetooth(deviceAddress: String) {
        lifecycleScope.launch {
            connectionManager?.disconnect()
            connectionManager = BluetoothConnectionManager(this@MainActivity, deviceAddress)

            tvConnectionStatus.text = getString(R.string.connecting)
            val connectResult = connectionManager!!.connect()
            if (connectResult.isSuccess) {
                tvConnectionStatus.text = connectionManager!!.getConnectionInfo()
                // 连接成功后立即发送一次停止指令 (1500, 1500)
                connectionManager?.sendData("SS2:1500,1500\n")
            } else {
                tvConnectionStatus.text = getString(R.string.disconnected)
                val error = connectResult.exceptionOrNull()
                Toast.makeText(this@MainActivity, "连接失败: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun disconnect() {
        lifecycleScope.launch {
            connectionManager?.disconnect()
            tvConnectionStatus.text = getString(R.string.disconnected)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            loadConfig()
            updateCustomSwitches()
            gyroController.setSensitivity(appConfig!!.gyroSensitivity)
            dataProcessor = JoystickDataProcessor(appConfig!!.controlConfig)

            // Attempt to connect/reconnect with new settings
            disconnect()
            when (appConfig!!.connectionConfig.connectionType) {
                ConnectionType.WIFI -> connectToWifi(
                    appConfig!!.connectionConfig.wifiIp,
                    appConfig!!.connectionConfig.wifiPort
                )
                ConnectionType.BLUETOOTH -> connectToBluetooth(
                    appConfig!!.connectionConfig.bluetoothAddress
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendDataJob?.cancel()
        controlJob.cancel()
        runBlocking {
            connectionManager?.disconnect()
        }
        gyroController.stop()
    }

    companion object {
        private const val SETTINGS_REQUEST_CODE = 100
    }
}