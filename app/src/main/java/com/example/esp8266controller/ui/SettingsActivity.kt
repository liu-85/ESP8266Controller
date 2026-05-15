package com.example.esp8266controller.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.esp8266controller.R
import com.example.esp8266controller.model.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var wifiModeBtn: Button
    private lateinit var btModeBtn: Button
    private lateinit var wifiSettings: View
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var saveConnectionBtn: Button
    private lateinit var connectionStatus: TextView
    private lateinit var closeSettings: Button

    private lateinit var sbGyroSensitivity: SeekBar
    private lateinit var tvGyroSensitivityValue: TextView
    private lateinit var sbSmoothing: SeekBar
    private lateinit var tvSmoothingValue: TextView
    private lateinit var sbCenterDeadzone: SeekBar
    private lateinit var tvCenterDeadzoneVal: TextView
    private lateinit var sbEndDeadzone: SeekBar
    private lateinit var tvEndDeadzoneVal: TextView

    private lateinit var sbHeartbeat: SeekBar
    private lateinit var tvHeartbeatVal: TextView
    private lateinit var sbTimeout: SeekBar
    private lateinit var tvTimeoutVal: TextView

    private lateinit var sbServoOffset: SeekBar
    private lateinit var tvServoOffsetVal: TextView
    private lateinit var rgThrottleCurve: RadioGroup
    private lateinit var rbCurveLinear: RadioButton
    private lateinit var rbCurveExp: RadioButton

    private lateinit var rgWifiProtocol: RadioGroup
    private lateinit var rbProtoTcp: RadioButton
    private lateinit var rbProtoUdp: RadioButton

    private lateinit var theme1Btn: Button
    private lateinit var theme2Btn: Button
    private lateinit var theme3Btn: Button
    private lateinit var theme4Btn: Button

    private lateinit var spinners: List<View> // Changed to View to handle multi-select logic

    private var appConfig: AppConfig? = null
    private var selectedTheme: AppTheme = AppTheme.THEME_1
    private var connectionType: ConnectionType = ConnectionType.WIFI_TCP
    private var channelSources: MutableList<MutableList<ControlSource>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        loadConfig()
        initViews()
        setupListeners()
        populateViews()
    }

    private fun loadConfig() {
        appConfig = AppConfig.load(this)
        selectedTheme = appConfig?.currentTheme ?: AppTheme.THEME_1
        connectionType = appConfig?.connectionConfig?.connectionType ?: ConnectionType.WIFI_TCP
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        wifiModeBtn = findViewById(R.id.wifiModeBtn)
        btModeBtn = findViewById(R.id.btModeBtn)
        wifiSettings = findViewById(R.id.wifiSettings)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)
        saveConnectionBtn = findViewById(R.id.saveConnectionBtn)
        connectionStatus = findViewById(R.id.connectionStatus)
        closeSettings = findViewById(R.id.closeSettings)

        sbGyroSensitivity = findViewById(R.id.sb_gyro_sensitivity)
        tvGyroSensitivityValue = findViewById(R.id.tv_gyro_sensitivity_value)
        sbSmoothing = findViewById(R.id.sb_smoothing)
        tvSmoothingValue = findViewById(R.id.tv_smoothing_value)
        sbCenterDeadzone = findViewById(R.id.sb_center_deadzone)
        tvCenterDeadzoneVal = findViewById(R.id.tv_center_deadzone_val)
        sbEndDeadzone = findViewById(R.id.sb_end_deadzone)
        tvEndDeadzoneVal = findViewById(R.id.tv_end_deadzone_val)
        
        sbHeartbeat = findViewById(R.id.sb_heartbeat)
        tvHeartbeatVal = findViewById(R.id.tv_heartbeat_val)
        sbTimeout = findViewById(R.id.sb_timeout)
        tvTimeoutVal = findViewById(R.id.tv_timeout_val)
        
        sbServoOffset = findViewById(R.id.sb_servo_offset)
        tvServoOffsetVal = findViewById(R.id.tv_servo_offset_val)
        rgThrottleCurve = findViewById(R.id.rg_throttle_curve)
        rbCurveLinear = findViewById(R.id.rb_curve_linear)
        rbCurveExp = findViewById(R.id.rb_curve_exp)

        rgWifiProtocol = findViewById(R.id.rg_wifi_protocol)
        rbProtoTcp = findViewById(R.id.rb_proto_tcp)
        rbProtoUdp = findViewById(R.id.rb_proto_udp)

        theme1Btn = findViewById(R.id.theme1Btn)
        theme2Btn = findViewById(R.id.theme2Btn)
        theme3Btn = findViewById(R.id.theme3Btn)
        theme4Btn = findViewById(R.id.theme4Btn)

        spinners = listOf(
            findViewById(R.id.spinner_ch1),
            findViewById(R.id.spinner_ch2),
            findViewById(R.id.spinner_ch3),
            findViewById(R.id.spinner_ch4),
            findViewById(R.id.spinner_ch5),
            findViewById(R.id.spinner_ch6),
            findViewById(R.id.spinner_ch7),
            findViewById(R.id.spinner_ch8)
        )
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        wifiModeBtn.setOnClickListener {
            connectionType = ConnectionType.WIFI_TCP
            updateConnectionUI()
        }

        btModeBtn.setOnClickListener {
            connectionType = ConnectionType.BLUETOOTH
            updateConnectionUI()
            showBluetoothDevicePicker()
        }

        saveConnectionBtn.setOnClickListener {
            saveConfig()
            connectionStatus.text = "配置已储存"
        }

        val onTextChange = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateConnectionUI() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        ipInput.addTextChangedListener(onTextChange)
        portInput.addTextChangedListener(onTextChange)

        connectionStatus.setOnClickListener {
            connectionStatus.text = "正在尝试连接..."
        }

        theme1Btn.setOnClickListener { selectedTheme = AppTheme.THEME_1; updateThemeUI() }
        theme2Btn.setOnClickListener { selectedTheme = AppTheme.THEME_2; updateThemeUI() }
        theme3Btn.setOnClickListener { selectedTheme = AppTheme.THEME_3; updateThemeUI() }
        theme4Btn.setOnClickListener { selectedTheme = AppTheme.THEME_4; updateThemeUI() }

        sbGyroSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvGyroSensitivityValue.text = String.format("%.1f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbSmoothing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvSmoothingValue.text = String.format("%.1f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbCenterDeadzone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvCenterDeadzoneVal.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbEndDeadzone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvEndDeadzoneVal.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbHeartbeat.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvHeartbeatVal.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTimeoutVal.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbServoOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress - 100
                tvServoOffsetVal.text = value.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        spinners.forEachIndexed { index, view ->
            view.setOnClickListener { showMultiSelectDialog(index) }
        }

        closeSettings.setOnClickListener {
            saveConfig()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun showMultiSelectDialog(channelIndex: Int) {
        val options = ControlSource.values()
        val optionLabels = options.map { it.name }.toTypedArray()
        val currentSources = channelSources.getOrNull(channelIndex) ?: mutableListOf(ControlSource.NONE)
        val checkedItems = options.map { currentSources.contains(it) }.toBooleanArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择 CH${channelIndex + 1} 来源 (多选)")
            .setMultiChoiceItems(optionLabels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                val newSources = mutableListOf<ControlSource>()
                checkedItems.forEachIndexed { i, checked ->
                    if (checked) newSources.add(options[i])
                }
                if (newSources.isEmpty()) newSources.add(ControlSource.NONE)
                channelSources[channelIndex] = newSources
                updateChannelTextView(channelIndex)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBluetoothDevicePicker() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
            // Optional: prompt to enable bluetooth
            return
        }

        // Permission check for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }

        val bondedDevices = bluetoothAdapter.bondedDevices.toList()
        if (bondedDevices.isEmpty()) {
            Toast.makeText(this, "未发现已配对设备，请先在手机系统设置中配对", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = bondedDevices.map { "${it.name ?: "未知设备"}\n${it.address}" }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert)
            .setTitle("选择蓝牙设备")
            .setItems(deviceNames) { _, which ->
                val device = bondedDevices[which]
                appConfig?.let { config ->
                    val newConfig = config.copy(
                        connectionConfig = config.connectionConfig.copy(
                            connectionType = ConnectionType.BLUETOOTH,
                            bluetoothAddress = device.address,
                            bluetoothName = device.name ?: "未知"
                        )
                    )
                    AppConfig.save(this, newConfig)
                    appConfig = newConfig
                    connectionStatus.text = "已选蓝牙: ${device.name ?: device.address}"
                    updateConnectionUI()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateChannelTextView(index: Int) {
        val view = spinners[index]
        if (view is TextView) {
            view.text = channelSources[index].joinToString(", ") { it.name }
        }
    }

    private fun updateConnectionUI() {
        wifiModeBtn.isSelected = connectionType == ConnectionType.WIFI_TCP || connectionType == ConnectionType.WIFI_UDP
        btModeBtn.isSelected = connectionType == ConnectionType.BLUETOOTH
        wifiSettings.visibility = if (connectionType == ConnectionType.WIFI_TCP || connectionType == ConnectionType.WIFI_UDP) View.VISIBLE else View.GONE
        
        // Update connection status text based on input
        val ip = ipInput.text.toString()
        val port = portInput.text.toString()
        if (ip.isNotEmpty() && port.isNotEmpty()) {
            connectionStatus.text = "配置就绪: $ip:$port (点击尝试连接)"
            connectionStatus.isEnabled = true
        } else {
            connectionStatus.text = "请先设置 IP 和 端口"
            connectionStatus.isEnabled = false
        }
    }

    private fun updateThemeUI() {
        val isIOS = selectedTheme == AppTheme.THEME_3
        val bgColor = when (selectedTheme) {
            AppTheme.THEME_1 -> resources.getColor(R.color.theme1_bg, null)
            AppTheme.THEME_2 -> resources.getColor(R.color.theme2_bg, null)
            AppTheme.THEME_3 -> Color.parseColor("#E5E5EA") // iOS Background
            AppTheme.THEME_4 -> resources.getColor(R.color.theme4_bg, null)
        }
        
        findViewById<View>(R.id.settings_root).setBackgroundColor(bgColor)
        
        // Update all text labels to black if light theme
        val isLightTheme = selectedTheme == AppTheme.THEME_2 || selectedTheme == AppTheme.THEME_3 || selectedTheme == AppTheme.THEME_4
        val textColor = if (isLightTheme) Color.BLACK else Color.WHITE
        
        updateChildTextColors(findViewById(R.id.settings_root), textColor)
        
        theme1Btn.isSelected = selectedTheme == AppTheme.THEME_1
        theme2Btn.isSelected = selectedTheme == AppTheme.THEME_2
        theme3Btn.isSelected = selectedTheme == AppTheme.THEME_3
        theme4Btn.isSelected = selectedTheme == AppTheme.THEME_4

        // Apply iOS specific card styling if needed
        val sectionIds = listOf(
            R.id.section_connection,
            R.id.section_heartbeat,
            R.id.section_mapping,
            R.id.section_theme,
            R.id.section_throttle,
            R.id.section_servo,
            R.id.section_gyro
        )

        sectionIds.forEach { id ->
            val section = findViewById<View>(id) ?: return@forEach
            if (isIOS) {
                section.setBackgroundResource(R.drawable.ios_card_bg)
                section.elevation = 0f
            } else {
                section.background = null
                section.elevation = 0f
            }
        }
    }

    private fun updateChildTextColors(view: View, color: Int) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateChildTextColors(view.getChildAt(i), color)
            }
        } else if (view is TextView) {
            // Don't change primary buttons as they have selectors
            if (view !is Button || view is RadioButton) {
                view.setTextColor(color)
                if (view is RadioButton) {
                    view.buttonTintList = android.content.res.ColorStateList.valueOf(color)
                }
            }
        }
    }

    private fun populateViews() {
        appConfig?.let { config ->
            ipInput.setText(config.connectionConfig.wifiIp)
            portInput.setText(config.connectionConfig.wifiPort.toString())
            connectionType = config.connectionConfig.connectionType
            selectedTheme = config.currentTheme

            sbGyroSensitivity.progress = (config.controlConfig.gyroSensitivity * 100).toInt()
            tvGyroSensitivityValue.text = String.format("%.1f", config.controlConfig.gyroSensitivity)

            sbSmoothing.progress = (config.controlConfig.smoothingFactor * 100).toInt()
            tvSmoothingValue.text = String.format("%.1f", config.controlConfig.smoothingFactor)

            sbCenterDeadzone.progress = (config.controlConfig.centerDeadzone * 100).toInt()
            tvCenterDeadzoneVal.text = String.format("%.2f", config.controlConfig.centerDeadzone)

            sbEndDeadzone.progress = (config.controlConfig.endDeadzone * 100).toInt()
            tvEndDeadzoneVal.text = String.format("%.2f", config.controlConfig.endDeadzone)

            sbHeartbeat.progress = config.controlConfig.heartbeatIntervalMs.toInt()
            tvHeartbeatVal.text = config.controlConfig.heartbeatIntervalMs.toString()
            
            val timeoutMinutes = (config.controlConfig.inactivityTimeoutMs / 60000).toInt()
            sbTimeout.progress = timeoutMinutes
            tvTimeoutVal.text = timeoutMinutes.toString()

            sbServoOffset.progress = config.controlConfig.servoCenterOffset + 100
            tvServoOffsetVal.text = config.controlConfig.servoCenterOffset.toString()
            
            if (config.controlConfig.throttleCurve == ThrottleCurve.EXPONENTIAL) {
                rbCurveExp.isChecked = true
            } else {
                rbCurveLinear.isChecked = true
            }

            if (config.connectionConfig.connectionType == ConnectionType.WIFI_UDP) {
                rbProtoUdp.isChecked = true
            } else {
                rbProtoTcp.isChecked = true
            }

            channelSources = config.controlConfig.channelSources.map { it.toMutableList() }.toMutableList()
            spinners.forEachIndexed { index, _ ->
                updateChannelTextView(index)
            }

            updateConnectionUI()
            updateThemeUI()
        }
    }

    private fun saveConfig() {
        appConfig?.let { config ->
            val finalConnectionType = if (connectionType == ConnectionType.BLUETOOTH) {
                ConnectionType.BLUETOOTH
            } else {
                if (rbProtoUdp.isChecked) ConnectionType.WIFI_UDP else ConnectionType.WIFI_TCP
            }

            val newConfig = config.copy(
                connectionConfig = config.connectionConfig.copy(
                    connectionType = finalConnectionType,
                    wifiIp = ipInput.text.toString(),
                    wifiPort = portInput.text.toString().toIntOrNull() ?: 2000
                ),
                controlConfig = config.controlConfig.copy(
                    channelSources = channelSources,
                    gyroSensitivity = sbGyroSensitivity.progress / 100f,
                    smoothingFactor = sbSmoothing.progress / 100f,
                    centerDeadzone = sbCenterDeadzone.progress / 100f,
                    endDeadzone = sbEndDeadzone.progress / 100f,
                    heartbeatIntervalMs = sbHeartbeat.progress.toLong(),
                    inactivityTimeoutMs = sbTimeout.progress.toLong() * 60000,
                    servoCenterOffset = sbServoOffset.progress - 100,
                    throttleCurve = if (rbCurveExp.isChecked) ThrottleCurve.EXPONENTIAL else ThrottleCurve.LINEAR
                ),
                currentTheme = selectedTheme
            )

            AppConfig.save(this, newConfig)
            appConfig = newConfig
        }
    }

    private fun scanBluetoothDevices() {
        // ... (Keep existing bluetooth scanning logic if possible, or simplify for now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }
}
