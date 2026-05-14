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

    private var leftY: Int = 1500
    private var leftX: Int = 1500
    private var rightY: Int = 1500
    private var rightX: Int = 1500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadConfig()
        initViews()
        setupListeners()
        setupJoystickProcessor()
        setupGyroController()
        setupPeriodicSend()
        applyTheme()
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
            
            // Toggle icons based on connection type
            if (config.connectionConfig.connectionType == ConnectionType.WIFI) {
                mainWifiIcon.visibility = View.VISIBLE
                mainBtIcon.visibility = View.GONE
            } else {
                mainWifiIcon.visibility = View.GONE
                mainBtIcon.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        openSettings.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
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

        // Button clicks also trigger immediate send if timer is off
        val onButtonClick = { sendControlData() }
        btn1.setOnClickListener { onButtonClick() }
        btn2.setOnClickListener { onButtonClick() }
        btn3.setOnClickListener { onButtonClick() }
        btn4.setOnClickListener { onButtonClick() }
        switch1.setOnCheckedChangeListener { _, _ -> onButtonClick() }
        switch2.setOnCheckedChangeListener { _, _ -> onButtonClick() }

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
                updateStatus("左摇杆: Y=$leftY, X=$leftX")
                if (appConfig?.controlConfig?.isTimerEnabled == false) {
                    sendControlData()
                }
            }
        }

        rightJoystick.setOnJoystickMoveListener { angle, strength ->
            rightY = dataProcessor!!.mapToChannelValue(angle, strength, true)
            rightX = dataProcessor!!.mapToChannelValue(angle, strength, false)
            updateStatus("右摇杆: Y=$rightY, X=$rightX")
            if (appConfig?.controlConfig?.isTimerEnabled == false) {
                sendControlData()
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

    private fun sendControlData() {
        val manager = connectionManager ?: return
        if (manager.connectionState !is ConnectionState.Connected) return

        controlScope.launch {
            val command = dataProcessor?.formatCommand(
                leftY, leftX,
                rightY, rightX,
                listOf(btn1.isPressed, btn2.isPressed, btn3.isPressed, btn4.isPressed),
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

    private fun updateConnectionStatusIcon(connected: Boolean) {
        val wifiIcon = mainWifiIcon
        val btIcon = mainBtIcon
        if (connected) {
            wifiIcon.setBackgroundResource(R.drawable.bg_circle_status)
            btIcon.setBackgroundResource(R.drawable.bg_circle_status)
        } else {
            // Default grey or something
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
