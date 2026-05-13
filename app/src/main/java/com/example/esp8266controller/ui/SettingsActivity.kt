package com.example.esp8266controller.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.esp8266controller.R
import com.example.esp8266controller.model.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvSettingsTitle: TextView

    private lateinit var rgConnectionType: RadioGroup
    private lateinit var rbWifi: RadioButton
    private lateinit var rbBluetooth: RadioButton

    private lateinit var rgControlMode: RadioGroup
    private lateinit var rbMixed: RadioButton
    private lateinit var rbSeparate: RadioButton
    private lateinit var mixedChannelsLayout: LinearLayout
    private lateinit var spinnerMixedCh1: Spinner
    private lateinit var spinnerMixedCh2: Spinner

    private lateinit var wifiSettingsLayout: LinearLayout
    private lateinit var etWifiIp: EditText
    private lateinit var etWifiPort: EditText

    private lateinit var bluetoothSettingsLayout: LinearLayout
    private lateinit var btnScanBluetooth: Button
    private lateinit var tvBluetoothDevice: TextView

    private lateinit var etThrottleTemplate: EditText
    private lateinit var etSteeringTemplate: EditText

    private var appConfig: AppConfig? = null
    private var selectedBluetoothDevice: BluetoothDevice? = null

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
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        tvSettingsTitle = findViewById(R.id.tv_settings_title)

        rgConnectionType = findViewById(R.id.rg_connection_type)
        rbWifi = findViewById(R.id.rb_wifi)
        rbBluetooth = findViewById(R.id.rb_bluetooth)

        rgControlMode = findViewById(R.id.rg_control_mode)
        rbMixed = findViewById(R.id.rb_mixed)
        rbSeparate = findViewById(R.id.rb_separate)
        mixedChannelsLayout = findViewById(R.id.mixed_channels_layout)
        spinnerMixedCh1 = findViewById(R.id.spinner_mixed_ch1)
        spinnerMixedCh2 = findViewById(R.id.spinner_mixed_ch2)

        wifiSettingsLayout = findViewById(R.id.wifi_settings_layout)
        etWifiIp = findViewById(R.id.et_wifi_ip)
        etWifiPort = findViewById(R.id.et_wifi_port)
        btnSaveConfig = findViewById(R.id.btn_save_config)

        btnScanBluetooth = findViewById(R.id.btn_scan_bluetooth)
        tvBluetoothDevice = findViewById(R.id.tv_bluetooth_device)

        etThrottleTemplate = findViewById(R.id.et_throttle_template)
        etSteeringTemplate = findViewById(R.id.et_steering_template)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        rgControlMode.setOnCheckedChangeListener { _, checkedId ->
            mixedChannelsLayout.visibility = if (checkedId == R.id.rb_mixed) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        btnScanBluetooth.setOnClickListener {
            scanBluetoothDevices()
        }

        btnSaveConfig.setOnClickListener {
            saveConfig()
        }
    }

    private fun populateViews() {
        appConfig?.let { config ->
            // Connection
            if (config.connectionConfig.connectionType == ConnectionType.WIFI) {
                rbWifi.isChecked = true
                wifiSettingsLayout.visibility = android.view.View.VISIBLE
            } else {
                rbBluetooth.isChecked = true
                bluetoothSettingsLayout.visibility = android.view.View.VISIBLE
            }

            etWifiIp.setText(config.connectionConfig.wifiIp)
            etWifiPort.setText(config.connectionConfig.wifiPort.toString())

            if (config.controlConfig.controlMode == ControlMode.MIXED) {
                rbMixed.isChecked = true
                mixedChannelsLayout.visibility = android.view.View.VISIBLE
            } else {
                rbSeparate.isChecked = true
                mixedChannelsLayout.visibility = android.view.View.GONE
            }

            spinnerMixedCh1.setSelection(config.controlConfig.ch1Source.ordinal)
            spinnerMixedCh2.setSelection(config.controlConfig.ch2Source.ordinal)

            tvBluetoothDevice.text = config.connectionConfig.bluetoothName
            etThrottleTemplate.setText(config.controlConfig.throttleTemplate)
            etSteeringTemplate.setText(config.controlConfig.steeringTemplate)
        }
    }

    private fun saveConfig() {
        appConfig?.let { config ->
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val bluetoothAddress = if (hasPermission) selectedBluetoothDevice?.address else null
            val bluetoothName = if (hasPermission) selectedBluetoothDevice?.name else null

            val newConfig = config.copy(
                connectionConfig = config.connectionConfig.copy(
                    connectionType = if (rbWifi.isChecked) ConnectionType.WIFI else ConnectionType.BLUETOOTH,
                    wifiIp = etWifiIp.text.toString(),
                    wifiPort = etWifiPort.text.toString().toIntOrNull() ?: 2000,
                    bluetoothAddress = bluetoothAddress ?: config.connectionConfig.bluetoothAddress,
                    bluetoothName = bluetoothName ?: config.connectionConfig.bluetoothName
                ),
                controlConfig = config.controlConfig.copy(
                    controlMode = if (rbMixed.isChecked) ControlMode.MIXED else ControlMode.SEPARATE,
                    ch1Source = ChannelSource.values()[spinnerMixedCh1.selectedItemPosition],
                    ch2Source = ChannelSource.values()[spinnerMixedCh2.selectedItemPosition],
                    throttleTemplate = etThrottleTemplate.text.toString(),
                    steeringTemplate = etSteeringTemplate.text.toString()
                )
            )

            AppConfig.save(this, newConfig)
            Toast.makeText(this, "配置已储存", Toast.LENGTH_SHORT).show()
            
            // Also trigger reconnect by setting result
            setResult(RESULT_OK)
        }
    }

    private fun scanBluetoothDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), BLUETOOTH_PERMISSION_REQUEST)
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), BLUETOOTH_PERMISSION_REQUEST)
                return
            }
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices

        if (pairedDevices.isNotEmpty()) {
            val deviceNames = pairedDevices
                .map { device -> device.name ?: device.address ?: "device" }
                .toTypedArray()
            val devicesArray = pairedDevices.toList()

            android.app.AlertDialog.Builder(this)
                .setTitle("选择蓝牙设备")
                .setItems(deviceNames) { _, which ->
                    selectedBluetoothDevice = devicesArray[which]
                    tvBluetoothDevice.text = "已选择: ${selectedBluetoothDevice?.name}"
                }
                .show()
        } else {
            Toast.makeText(this, "没有已配对的蓝牙设备", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            scanBluetoothDevices()
        } else {
            Toast.makeText(this, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST = 100
    }
}