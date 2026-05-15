package com.example.esp8266controller.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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

import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest

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

    private var connectionManager: ConnectionManager? = null
    private var appConfig: AppConfig? = null
    private var dataProcessor: JoystickDataProcessor? = null

    private val controlJob = SupervisorJob()
    private val controlScope = CoroutineScope(Dispatchers.IO + controlJob)
    private var sendDataJob: Job? = null
    private var heartbeatJob: Job? = null
    private var autoStopJob: Job? = null
    private var lastOperationTime: Long = 0

    private var targetLeftY: Int = 1500
    private var targetLeftX: Int = 1500
    private var targetRightY: Int = 1500
    private var targetRightX: Int = 1500

    private lateinit var vibrator: android.os.Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        lastOperationTime = System.currentTimeMillis()

        loadConfig()
        initViews()
        setupListeners()
        setupJoystickProcessor()
        setupGyroController()
        setupHeartbeat()
        setupTimeoutCheck()
        startControlLoop() // Start a continuous loop for smooth control
        applyTheme()
    }

    private fun startControlLoop() {
        sendDataJob?.cancel()
        sendDataJob = controlScope.launch {
            while (isActive) {
                val config = appConfig?.controlConfig ?: ControlConfig()
                val interval = config.sendIntervalMs
                
                if (connectionManager?.connectionState is ConnectionState.Connected) {
                    val processor = dataProcessor ?: return@launch
                    
                    // Always format command to update smoothed values
                    val command = processor.formatCommand(
                        targetLeftY, targetLeftX,
                        targetRightY, targetRightX,
                        listOf(btn1.isSelected, btn2.isSelected, btn3.isSelected, btn4.isSelected),
                        listOf(switch1.isChecked, switch2.isChecked)
                    )
                    
                    // Only send if not neutral OR if it's currently smoothing towards neutral
                    if (!processor.isNeutral() || (System.currentTimeMillis() - lastOperationTime < 1000)) {
                        connectionManager?.sendData(command)
                        withContext(Dispatchers.Main) {
                            updateConnectionStatusIcon(true)
                        }
                    }
                }
                delay(interval)
            }
        }
    }

    private fun setupHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = controlScope.launch {
            while (isActive) {
                val interval = appConfig?.controlConfig?.heartbeatIntervalMs ?: 1000
                delay(interval)
                // Only send heartbeat if we are NOT actively sending control data
                val processor = dataProcessor
                if (connectionManager?.connectionState is ConnectionState.Connected && 
                    (processor == null || processor.isNeutral())) {
                    connectionManager?.sendData("\n")
                }
            }
        }
    }

    private fun setupTimeoutCheck() {
        controlScope.launch {
            while (isActive) {
                delay(10000) // 每10秒检查一次
                val config = appConfig ?: continue
                val timeout = config.controlConfig.inactivityTimeoutMs
                if (timeout > 0 && (System.currentTimeMillis() - lastOperationTime) > timeout) {
                    if (connectionManager?.connectionState is ConnectionState.Connected) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "长时间未操作，已自动断开连接", Toast.LENGTH_LONG).show()
                        }
                        connectionManager?.disconnect()
                        runOnUiThread {
                            updateConnectionStatusIcon(false)
                        }
                    }
                }
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
    }

    private fun applyTheme() {
        appConfig?.let { config ->
            val isIOS = config.currentTheme == AppTheme.THEME_3
            val (bgColor, accentColor) = when (config.currentTheme) {
                AppTheme.THEME_1 -> resources.getColor(R.color.theme1_bg, null) to resources.getColor(R.color.theme1_accent, null)
                AppTheme.THEME_2 -> resources.getColor(R.color.theme2_bg, null) to resources.getColor(R.color.theme2_accent, null)
                AppTheme.THEME_3 -> Color.parseColor("#E5E5EA") to Color.parseColor("#007AFF") // iOS Gray and Blue
                AppTheme.THEME_4 -> resources.getColor(R.color.theme4_bg, null) to resources.getColor(R.color.theme4_accent, null)
            }
            mainLayout.setBackgroundColor(bgColor)
            
            // For iOS glass effect, we wrap the main content in a translucent white overlay if needed
            // But we can also just style the components.
            
            // Apply accent color to icons and buttons
            mainWifiIcon.setTextColor(if (isIOS) Color.BLACK else accentColor)
            mainBtIcon.setTextColor(if (isIOS) Color.BLACK else accentColor)
            openSettings.setColorFilter(if (isIOS) Color.BLACK else accentColor)
            
            // Apply theme colors to all action buttons
            val buttons = listOf(btn1, btn2, btn3, btn4, gyroToggle)
            
            buttons.forEach { btn ->
                if (isIOS) {
                    btn?.setBackgroundResource(R.drawable.ios_button_bg)
                    btn?.setTextColor(Color.BLACK) 
                    btn?.elevation = 0f
                } else {
                    btn?.setBackgroundResource(R.drawable.button_selector)
                    btn?.setTextColor(accentColor)
                    btn?.elevation = 4f
                }
            }

            // Update Status Bar / Icons for iOS
            if (isIOS) {
                mainWifiIcon.setBackgroundResource(R.drawable.ios_button_bg)
                mainBtIcon.setBackgroundResource(R.drawable.ios_button_bg)
                findViewById<View>(R.id.top_bar).setBackgroundColor(Color.parseColor("#4DFFFFFF")) // Translucent white bar
                status_bar.setTextColor(Color.BLACK)
                findViewById<TextView>(R.id.switch1_label).setTextColor(Color.BLACK)
                findViewById<TextView>(R.id.switch2_label).setTextColor(Color.BLACK)
            } else {
                mainWifiIcon.setBackgroundResource(R.drawable.bg_circle_status)
                mainBtIcon.setBackgroundResource(R.drawable.bg_circle_status)
                findViewById<View>(R.id.top_bar).setBackgroundColor(Color.parseColor("#33000000"))
                
                val isLightTheme = config.currentTheme == AppTheme.THEME_2 || config.currentTheme == AppTheme.THEME_4
                val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
                status_bar.setTextColor(textColor)
                findViewById<TextView>(R.id.switch1_label).setTextColor(textColor)
                findViewById<TextView>(R.id.switch2_label).setTextColor(textColor)
            }

            // Update Joystick colors
            if (isIOS) {
                leftJoystick.setColors(Color.parseColor("#80FFFFFF"), Color.parseColor("#007AFF")) // Translucent white ring, iOS blue knob
                rightJoystick.setColors(Color.parseColor("#80FFFFFF"), Color.parseColor("#007AFF"))
            } else {
                leftJoystick.setColors(Color.parseColor("#33FFFFFF"), Color.parseColor("#FFD700"))
                rightJoystick.setColors(Color.parseColor("#33FFFFFF"), Color.parseColor("#FFD700"))
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
            if (appConfig?.connectionConfig?.connectionType != ConnectionType.WIFI_TCP && 
                appConfig?.connectionConfig?.connectionType != ConnectionType.WIFI_UDP) {
                toggleConnectionType(ConnectionType.WIFI_TCP)
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
        }
    }

    private fun setupJoystickProcessor() {
        leftJoystick.setOnJoystickMoveListener { angle, strength ->
            if (appConfig?.controlConfig?.isGyroEnabled == false) {
                targetLeftY = dataProcessor!!.mapToChannelValue(angle, strength, true)
                targetLeftX = dataProcessor!!.mapToChannelValue(angle, strength, false)
                
                if (strength > 0.95f) triggerVibration()
                updateStatus("左摇杆: Y=$targetLeftY, X=$targetLeftX")
                sendControlData()
            }
        }

        rightJoystick.setOnJoystickMoveListener { angle, strength ->
            targetRightY = dataProcessor!!.mapToChannelValue(angle, strength, true)
            targetRightX = dataProcessor!!.mapToChannelValue(angle, strength, false)
            
            if (strength > 0.95f) triggerVibration()
            updateStatus("右摇杆: Y=$targetRightY, X=$targetRightX")
            sendControlData()
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
        val config = appConfig ?: return
        
        // Handle Bluetooth permissions for Android 12+
        if (config.connectionConfig.connectionType == ConnectionType.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), BT_PERMISSION_REQUEST_CODE)
                return
            }
        }

        lifecycleScope.launch {
            connectionManager?.disconnect()
            val config = appConfig ?: return@launch
            
            connectionManager = when (config.connectionConfig.connectionType) {
                ConnectionType.WIFI_TCP -> WifiConnectionManager(
                    config.connectionConfig.wifiIp,
                    config.connectionConfig.wifiPort
                )
                ConnectionType.WIFI_UDP -> UdpConnectionManager(
                    config.connectionConfig.wifiIp,
                    config.connectionConfig.wifiPort
                )
                ConnectionType.BLUETOOTH -> BluetoothConnectionManager(
                    this@MainActivity,
                    config.connectionConfig.bluetoothAddress
                )
            }
            
            val typeStr = when(config.connectionConfig.connectionType) {
                ConnectionType.WIFI_TCP -> "WIFI (TCP)"
                ConnectionType.WIFI_UDP -> "WIFI (UDP)"
                ConnectionType.BLUETOOTH -> "蓝牙"
            }
            
            updateStatus("正在连接 $typeStr...")
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
            val isWifi = currentType == ConnectionType.WIFI_TCP || currentType == ConnectionType.WIFI_UDP
            val isBt = currentType == ConnectionType.BLUETOOTH

            // Update WiFi icon
            mainWifiIcon.alpha = if (isWifi && connected) 1.0f else 0.3f
            mainWifiIcon.scaleX = if (isWifi) 1.1f else 1.0f
            mainWifiIcon.scaleY = if (isWifi) 1.1f else 1.0f
            
            // Update BT icon
            mainBtIcon.alpha = if (isBt && connected) 1.0f else 0.3f
            mainBtIcon.scaleX = if (isBt) 1.1f else 1.0f
            mainBtIcon.scaleY = if (isBt) 1.1f else 1.0f
            
            // Background indication
            val isIOS = config.currentTheme == AppTheme.THEME_3
            val accentColor = when (config.currentTheme) {
                AppTheme.THEME_1 -> resources.getColor(R.color.theme1_accent, null)
                AppTheme.THEME_2 -> resources.getColor(R.color.theme2_accent, null)
                AppTheme.THEME_3 -> Color.parseColor("#007AFF")
                AppTheme.THEME_4 -> resources.getColor(R.color.theme4_accent, null)
            }
            
            if (connected) {
                if (isWifi) {
                    mainWifiIcon.setTextColor(if (isIOS) Color.BLACK else accentColor)
                    mainWifiIcon.text = if (currentType == ConnectionType.WIFI_UDP) "📶U" else "📶"
                } else {
                    mainBtIcon.setTextColor(if (isIOS) Color.BLACK else accentColor)
                }
            } else {
                mainWifiIcon.setTextColor(if (isIOS) Color.BLACK else resources.getColor(R.color.white, null))
                mainBtIcon.setTextColor(if (isIOS) Color.BLACK else resources.getColor(R.color.white, null))
                mainWifiIcon.text = "📶"
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
                targetLeftY = channelData.throttle
                targetLeftX = channelData.steering
                
                // Update UI visually
                val pitchStrength = Math.abs(pitch) / 45f
                val rollStrength = Math.abs(roll) / 45f
                val strength = Math.max(pitchStrength, rollStrength).coerceIn(0f, 1f)
                val angle = Math.toDegrees(Math.atan2(roll.toDouble(), pitch.toDouble())) + 90
                
                runOnUiThread {
                    leftJoystick.setKnobPosition(angle, strength)
                }

                updateStatus("陀螺仪控制: Y=$targetLeftY, X=$targetLeftX")
                sendControlData()
            }
        }
    }

    private fun sendControlData() {
        lastOperationTime = System.currentTimeMillis()
        // No longer sends immediately, startControlLoop handles it
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
                reconnect()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BT_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                reconnect()
            } else {
                updateStatus("连接失败: 缺少蓝牙权限")
                Toast.makeText(this, "需要蓝牙权限才能连接设备", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val SETTINGS_REQUEST_CODE = 100
        private const val BT_PERMISSION_REQUEST_CODE = 101
    }
}
