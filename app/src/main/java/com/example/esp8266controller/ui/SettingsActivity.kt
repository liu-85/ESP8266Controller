package com.example.esp8266controller.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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

    private lateinit var theme1Btn: Button
    private lateinit var theme2Btn: Button
    private lateinit var theme3Btn: Button
    private lateinit var theme4Btn: Button

    private lateinit var spinners: List<Spinner>

    private var appConfig: AppConfig? = null
    private var selectedTheme: AppTheme = AppTheme.THEME_1
    private var connectionType: ConnectionType = ConnectionType.WIFI

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
        connectionType = appConfig?.connectionConfig?.connectionType ?: ConnectionType.WIFI
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
            connectionType = ConnectionType.WIFI
            updateConnectionUI()
        }

        btModeBtn.setOnClickListener {
            connectionType = ConnectionType.BLUETOOTH
            updateConnectionUI()
            scanBluetoothDevices()
        }

        saveConnectionBtn.setOnClickListener {
            saveConfig()
            connectionStatus.text = "配置已储存"
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

        closeSettings.setOnClickListener {
            saveConfig()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun updateConnectionUI() {
        wifiModeBtn.isSelected = connectionType == ConnectionType.WIFI
        btModeBtn.isSelected = connectionType == ConnectionType.BLUETOOTH
        wifiSettings.visibility = if (connectionType == ConnectionType.WIFI) View.VISIBLE else View.GONE
    }

    private fun updateThemeUI() {
        theme1Btn.isSelected = selectedTheme == AppTheme.THEME_1
        theme2Btn.isSelected = selectedTheme == AppTheme.THEME_2
        theme3Btn.isSelected = selectedTheme == AppTheme.THEME_3
        theme4Btn.isSelected = selectedTheme == AppTheme.THEME_4
    }

    private fun populateViews() {
        appConfig?.let { config ->
            ipInput.setText(config.connectionConfig.wifiIp)
            portInput.setText(config.connectionConfig.wifiPort.toString())
            connectionType = config.connectionConfig.connectionType
            selectedTheme = config.currentTheme

            sbGyroSensitivity.progress = (config.controlConfig.gyroSensitivity * 100).toInt()
            tvGyroSensitivityValue.text = String.format("%.1f", config.controlConfig.gyroSensitivity)

            config.controlConfig.channelSources.forEachIndexed { index, source ->
                if (index < spinners.size) {
                    spinners[index].setSelection(source.ordinal)
                }
            }

            updateConnectionUI()
            updateThemeUI()
        }
    }

    private fun saveConfig() {
        appConfig?.let { config ->
            val newSources = spinners.map { ControlSource.values()[it.selectedItemPosition] }
            
            val newConfig = config.copy(
                connectionConfig = config.connectionConfig.copy(
                    connectionType = connectionType,
                    wifiIp = ipInput.text.toString(),
                    wifiPort = portInput.text.toString().toIntOrNull() ?: 2000
                ),
                controlConfig = config.controlConfig.copy(
                    channelSources = newSources,
                    gyroSensitivity = sbGyroSensitivity.progress / 100f
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
