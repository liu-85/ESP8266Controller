package com.example.esp8266controller.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.esp8266controller.R
import com.example.esp8266controller.connection.*
import com.example.esp8266controller.joystick.*
import com.example.esp8266controller.model.*
import com.example.esp8266controller.sensor.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var mainLayout: View
    private lateinit var leftJoystick: JoystickView
    private lateinit var rightJoystick: JoystickView
    private lateinit var gyroController: GyroController

    private lateinit var mainWifiIcon: TextView
    private lateinit var mainBtIcon: TextView
    private lateinit var status_bar: TextView

    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button
    private lateinit var btn4: Button
    private lateinit var switch1: SwitchCompat
    private lateinit var switch2: SwitchCompat
    private lateinit var openSettings: ImageButton

    private lateinit var gyroToggle: Button
    private lateinit var timerToggle: Button

    private var connectionManager: ConnectionManager? = null
    private var appConfig: AppConfig? = null
    private var dataProcessor: JoystickDataProcessor? = null

    private val controlJob = SupervisorJob()
    private val controlScope = CoroutineScope(Dispatchers.IO + controlJob)
    private var sendDataJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isFailsafeActive: Boolean = false

    private var leftY: Int = 1500
    private var leftX: Int = 1500
    private var rightY: Int = 1500
    private var rightX: Int = 1500

    private lateinit var vibrator: android.os.Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator

        loadConfig()
        initViews()
        setupListeners()
        setupJoystickProcessor()
        setupGyroController()
        setupPeriodicSend()
        setupHeartbeat()
        applyTheme()
    }

    private fun setupHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = controlScope.launch {
            while (isActive) {
                delay(200)
                if (connectionManager?.connectionState is ConnectionState.Connected) {
                    sendControlData(isHeartbeat = true)
                }
            }
        }
    }

    private fun checkFailsafe() {
        val manager = connectionManager
        if (manager is WifiConnectionManager) {
            if (manager.isFailsafeTriggered()) {
                if (!isFailsafeActive) {
                    isFailsafeActive = true
                    runOnUiThread {
                        Toast.makeText(this, "失控保护：设备无响应", Toast.LENGTH_SHORT).show()
                        updateStatus("失控保护：停止发送")
                    }
                }
            } else {
                isFailsafeActive = false
            }
        }
    }

    private fun loadConfig() {
        appConfig = AppConfig.load(this)
        dataProcessor = JoystickDataProcessor(appConfig!!.controlConfig)
    }

    private fun initViews() {
        mainLayout = findViewById(R.id.main_layout)
        leftJoystick = findViewById(R.id.leftJoystick)
        rightJoystick = findViewById(R.id.rightJoystick)

        mainWifiIcon = findViewById(R.id.mainWifiIcon)
        mainBtIcon = findViewById(R.id.mainBtIcon)
        status_bar = findViewById(R.id.status_bar)

        btn1 = findViewById(R.id.btn1)
        btn2 = findViewById(R.id.btn2)
        btn3 = findViewById(R.id.btn3)
        btn4 = findViewById(R.id.btn4)
        switch1 = findViewById(R.id.switch1)
        switch2 = findViewById(R.id.switch2)
        openSettings = findViewById(R.id.openSettings)

        gyroToggle = findViewById(R.id.gyroToggle)
        timerToggle = findViewById(R.id.timerToggle)
    }

    private fun applyTheme() {
        appConfig?.let { config ->
            val (bgColor, accentColor) = when (config.currentTheme) {
                AppTheme.THEME_1 -> resources.getColor(R.color.theme1_bg, null) to resources.getColor(R.color.theme1_accent, null)
                AppTheme.THEME_2 -> resources.getColor(R.color.theme2_bg, null) to resources.getColor(R.color.theme2_accent, null)
                AppTheme.THEME_3 -> resources.getColor(R.color.theme3_bg, null) to resources.getColor(R.color.theme3_accent, null)
                AppTheme.THEME_4 -> resources.getColor(R.color.theme4_bg, null) to resources.getColor(R.color.theme4_accent, null)
            }
            mainLayout.setBackgroundColor(bgColor)
            
            // Apply accent color to icons and buttons
            mainWifiIcon.setTextColor(accentColor)
            mainBtIcon.setTextColor(accentColor)
            openSettings.setColorFilter(accentColor)
            gyroToggle.setTextColor(accentColor)
            timerToggle.setTextColor(accentColor)
            
            // Apply theme colors to all action buttons
            val buttons = listOf(
                btn1, btn2, btn3, btn4, 
                gyroToggle, timerToggle,
                findViewById<Button>(R.id.btn_servo_left),
                findViewById<Button>(R.id.btn_servo_center),
                findViewById<Button>(R.id.btn_servo_right)
            )
            
            buttons.forEach { btn ->
                btn?.setTextColor(accentColor)
                // If the theme is light (Theme 2 or 4), use dark text for contrast if needed
                if (config.currentTheme == AppTheme.THEME_2 || config.currentTheme == AppTheme.THEME_4) {
                    // Maybe adjust text color for light themes
                }
            }
            
            // Update connection icons state
            updateConnectionStatusIcon(connectionManager?.connectionState is ConnectionState.Connected)
        }
    }

    private fun setupListeners() {
        openSettings.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
        }

        mainWifiIcon.setOnClickListener {
            if (appConfig?.connectionConfig?.connectionType != ConnectionType.WIFI) {
                toggleConnectionType(ConnectionType.WIFI)
            } else {
                reconnect()
            }
        }

        mainBtIcon.setOnClickListener {
            if (appConfig?.connectionConfig?.connectionType != ConnectionType.BLUETOOTH) {
                toggleConnectionType(ConnectionType.BLUETOOTH)
            } else {
                reconnect()
            }
        }

        findViewById<Button>(R.id.btn_servo_left).setOnClickListener { sendImmediateCommand("SERVO_LEFT") }
        findViewById<Button>(R.id.btn_servo_right).setOnClickListener { sendImmediateCommand("SERVO_RIGHT") }
        findViewById<Button>(R.id.btn_servo_center).setOnClickListener { sendImmediateCommand("SERVO_CENTER") }
        findViewById<Button>(R.id.btn_emergency_stop).setOnClickListener { sendImmediateCommand("BOTH_STOP") }

        gyroToggle.setOnClickListener {
            appConfig?.let { config ->
                val newState = !config.controlConfig.isGyroEnabled
                val newConfig = config.copy(
                    controlConfig = config.controlConfig.copy(isGyroEnabled = newState)
                )
                appConfig = newConfig
                AppConfig.save(this, newConfig)
                updateToggleButtons()
                
                if (newState) {
                    gyroController.start()
                } else {
                    gyroController.stop()
                    leftJoystick.reset()
                }
            }
        }

        timerToggle.setOnClickListener {
            appConfig?.let { config ->
                val newState = !config.controlConfig.isTimerEnabled
                val newConfig = config.copy(
                    controlConfig = config.controlConfig.copy(isTimerEnabled = newState)
                )
                appConfig = newConfig
                AppConfig.save(this, newConfig)
                updateToggleButtons()
            }
        }

        // Button clicks toggle state and trigger immediate send if timer is off
        val onButtonClick = { view: View -> 
            view.isSelected = !view.isSelected
            sendControlData() 
        }
        btn1.setOnClickListener { onButtonClick(it) }
        btn2.setOnClickListener { onButtonClick(it) }
        btn3.setOnClickListener { onButtonClick(it) }
        btn4.setOnClickListener { onButtonClick(it) }
        
        switch1.setOnCheckedChangeListener { _, _ -> sendControlData() }
        switch2.setOnCheckedChangeListener { _, _ -> sendControlData() }

        updateToggleButtons()
    }

    private fun updateToggleButtons() {
        appConfig?.let { config ->
            gyroToggle.text = "陀螺仪: ${if (config.controlConfig.isGyroEnabled) "ON" else "OFF"}"
            gyroToggle.isSelected = config.controlConfig.isGyroEnabled
            
            timerToggle.text = "定时发送: ${if (config.controlConfig.isTimerEnabled) "ON" else "OFF"}"
            timerToggle.isSelected = config.controlConfig.isTimerEnabled
        }
    }

    private fun setupJoystickProcessor() {
        leftJoystick.setOnJoystickMoveListener { angle, strength ->
            if (appConfig?.controlConfig?.isGyroEnabled == false) {
                leftY = dataProcessor!!.mapToChannelValue(angle, strength, true)
                leftX = dataProcessor!!.mapToChannelValue(angle, strength, false)
                
                if (strength > 0.95f) triggerVibration()
                
                updateStatus("左摇杆: Y=$leftY, X=$leftX")
                if (appConfig?.controlConfig?.isTimerEnabled == false) {
                    sendControlData()
                }
            }
        }

        rightJoystick.setOnJoystickMoveListener { angle, strength ->
            rightY = dataProcessor!!.mapToChannelValue(angle, strength, true)
            rightX = dataProcessor!!.mapToChannelValue(angle, strength, false)
            
            if (strength > 0.95f) triggerVibration()
            
            updateStatus("右摇杆: Y=$rightY, X=$rightX")
            if (appConfig?.controlConfig?.isTimerEnabled == false) {
                sendControlData()
            }
        }
    }

    private fun toggleConnectionType(type: ConnectionType) {
        appConfig?.let { config ->
            val newConfig = config.copy(
                connectionConfig = config.connectionConfig.copy(connectionType = type)
            )
            appConfig = newConfig
            AppConfig.save(this, newConfig)
            updateConnectionStatusIcon(false)
            reconnect()
        }
    }

    private fun reconnect() {
        lifecycleScope.launch {
            connectionManager?.disconnect()
            val config = appConfig ?: return@launch
            
            connectionManager = when (config.connectionConfig.connectionType) {
                ConnectionType.WIFI -> WifiConnectionManager(
                    config.connectionConfig.wifiIp,
                    config.connectionConfig.wifiPort
                )
                ConnectionType.BLUETOOTH -> BluetoothConnectionManager(
                    this@MainActivity,
                    config.connectionConfig.bluetoothAddress
                )
            }
            
            updateStatus("正在连接 ${if (config.connectionConfig.connectionType == ConnectionType.WIFI) "WIFI" else "蓝牙"}...")
            val result = connectionManager?.connect()
            
            runOnUiThread {
                if (result?.isSuccess == true) {
                    updateConnectionStatusIcon(true)
                    updateStatus("连接成功")
                } else {
                    updateConnectionStatusIcon(false)
                    updateStatus("连接失败: ${result?.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun updateConnectionStatusIcon(connected: Boolean) {
        val config = appConfig ?: return
        val currentType = config.connectionConfig.connectionType
        
        runOnUiThread {
            // Update WiFi icon
            mainWifiIcon.alpha = if (currentType == ConnectionType.WIFI && connected) 1.0f else 0.3f
            mainWifiIcon.scaleX = if (currentType == ConnectionType.WIFI) 1.1f else 1.0f
            mainWifiIcon.scaleY = if (currentType == ConnectionType.WIFI) 1.1f else 1.0f
            
            // Update BT icon
            mainBtIcon.alpha = if (currentType == ConnectionType.BLUETOOTH && connected) 1.0f else 0.3f
            mainBtIcon.scaleX = if (currentType == ConnectionType.BLUETOOTH) 1.1f else 1.0f
            mainBtIcon.scaleY = if (currentType == ConnectionType.BLUETOOTH) 1.1f else 1.0f
            
            // Background indication
            val accentColor = when (config.currentTheme) {
                AppTheme.THEME_1 -> resources.getColor(R.color.theme1_accent, null)
                AppTheme.THEME_2 -> resources.getColor(R.color.theme2_accent, null)
                AppTheme.THEME_3 -> resources.getColor(R.color.theme3_accent, null)
                AppTheme.THEME_4 -> resources.getColor(R.color.theme4_accent, null)
            }
            
            if (connected) {
                if (currentType == ConnectionType.WIFI) mainWifiIcon.setTextColor(accentColor)
                else mainBtIcon.setTextColor(accentColor)
            } else {
                mainWifiIcon.setTextColor(resources.getColor(R.color.white, null))
                mainBtIcon.setTextColor(resources.getColor(R.color.white, null))
            }
        }
    }

    private fun updateStatus(text: String) {
        status_bar.text = text
    }

    private fun setupGyroController() {
        gyroController = GyroController(this, appConfig!!.controlConfig)
        gyroController.setSensitivity(appConfig!!.controlConfig.gyroSensitivity)
        gyroController.setOnDataUpdateListener { pitch, roll ->
            if (appConfig?.controlConfig?.isGyroEnabled == true) {
                val channelData = gyroController.processGyroData(pitch, roll)
                leftY = channelData.throttle
                leftX = channelData.steering
                
                // Update UI visually
                val pitchStrength = Math.abs(pitch) / 45f
                val rollStrength = Math.abs(roll) / 45f
                val strength = Math.max(pitchStrength, rollStrength).coerceIn(0f, 1f)
                val angle = Math.toDegrees(Math.atan2(roll.toDouble(), pitch.toDouble())) + 90
                
                runOnUiThread {
                    leftJoystick.setKnobPosition(angle, strength)
                }

                updateStatus("陀螺仪控制: Y=$leftY, X=$leftX")
                if (appConfig?.controlConfig?.isTimerEnabled == false) {
                    sendControlData()
                }
            }
        }
    }

    private fun setupPeriodicSend() {
        sendDataJob = controlScope.launch {
            while (isActive) {
                if (appConfig?.controlConfig?.isTimerEnabled == true) {
                    sendControlData()
                }
                delay(appConfig?.controlConfig?.sendIntervalMs ?: 50)
            }
        }
    }

    private fun sendControlData(isHeartbeat: Boolean = false) {
        val manager = connectionManager ?: return
        if (manager.connectionState !is ConnectionState.Connected) return

        if (!isHeartbeat) {
            checkFailsafe()
            if (isFailsafeActive) return
        }

        controlScope.launch {
            val command = dataProcessor?.formatCommand(
                leftY, leftX,
                rightY, rightX,
                listOf(btn1.isSelected, btn2.isSelected, btn3.isSelected, btn4.isSelected),
                listOf(switch1.isChecked, switch2.isChecked)
            )
            command?.let { payload ->
                manager.sendData(payload)
                withContext(Dispatchers.Main) {
                    updateConnectionStatusIcon(true)
                }
            }
        }
    }

    private fun sendImmediateCommand(cmd: String) {
        val manager = connectionManager ?: return
        if (manager.connectionState !is ConnectionState.Connected) return

        controlScope.launch {
            val payload = dataProcessor?.formatButtonCommand(cmd)
            payload?.let { manager.sendData(it) }
        }
    }

    private fun triggerVibration() {
        if (appConfig?.controlConfig?.enableVibration == true) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            loadConfig()
            applyTheme()
            updateToggleButtons()
            dataProcessor = JoystickDataProcessor(appConfig!!.controlConfig)
            setupGyroController() // Re-initialize with new sensitivity

            // Reconnect if needed
            lifecycleScope.launch {
                connectionManager?.disconnect()
                when (appConfig!!.connectionConfig.connectionType) {
                    ConnectionType.WIFI -> {
                        connectionManager = WifiConnectionManager(
                            appConfig!!.connectionConfig.wifiIp,
                            appConfig!!.connectionConfig.wifiPort
                        )
                    }
                    ConnectionType.BLUETOOTH -> {
                        connectionManager = BluetoothConnectionManager(
                            this@MainActivity,
                            appConfig!!.connectionConfig.bluetoothAddress
                        )
                    }
                }
                connectionManager?.connect()
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
