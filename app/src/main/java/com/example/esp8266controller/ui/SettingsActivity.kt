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

    private lateinit var rgConnectionType: RadioGroup
    private lateinit var rbWifi: RadioButton
    private lateinit var rbBluetooth: RadioButton

    private lateinit var wifiSettingsLayout: LinearLayout
    private lateinit var etWifiIp: EditText
    private lateinit var etWifiPort: EditText

    private lateinit var bluetoothSettingsLayout: LinearLayout
    private lateinit var btnScanBluetooth: Button
    private lateinit var tvBluetoothDevice: TextView

    private lateinit var etThrottleTemplate: EditText
    private lateinit var etSteeringTemplate: EditText
    private lateinit var etServoLeft: EditText
    private lateinit var etServoCenter: EditText
    private lateinit var etServoRight: EditText

    private lateinit var etSwitch1Name: EditText
    private lateinit var etSwitch1On: EditText
    private lateinit var etSwitch1Off: EditText
    private lateinit var etSwitch2Name: EditText
    private lateinit var etSwitch2On: EditText
    private lateinit var etSwitch2Off: EditText
    private lateinit var etSwitch3Name: EditText
    private lateinit var etSwitch3On: EditText
    private lateinit var etSwitch3Off: EditText
    private lateinit var etSwitch4Name: EditText
    private lateinit var etSwitch4On: EditText
    private lateinit var etSwitch4Off: EditText

    private lateinit var seekbarGyroSensitivity: SeekBar
    private lateinit var tvSensitivityValue: TextView

    private lateinit var btnSave: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button

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
        rgConnectionType = findViewById<RadioGroup>(R.id.rg_connection_type)
        rbWifi = findViewById<RadioButton>(R.id.rb_wifi)
        rbBluetooth = findViewById<RadioButton>(R.id.rb_bluetooth)

        wifiSettingsLayout = findViewById<LinearLayout>(R.id.wifi_settings_layout)
        etWifiIp = findViewById<EditText>(R.id.et_wifi_ip)
        etWifiPort = findViewById<EditText>(R.id.et_wifi_port)

        bluetoothSettingsLayout = findViewById<LinearLayout>(R.id.bluetooth_settings_layout)
        btnScanBluetooth = findViewById<Button>(R.id.btn_scan_bluetooth)
        tvBluetoothDevice = findViewById<TextView>(R.id.tv_bluetooth_device)

        etThrottleTemplate = findViewById<EditText>(R.id.et_throttle_template)
        etSteeringTemplate = findViewById<EditText>(R.id.et_steering_template)
        etServoLeft = findViewById<EditText>(R.id.et_servo_left)
        etServoCenter = findViewById<EditText>(R.id.et_servo_center)
        etServoRight = findViewById<EditText>(R.id.et_servo_right)

        etSwitch1Name = findViewById<EditText>(R.id.et_switch1_name)
        etSwitch1On = findViewById<EditText>(R.id.et_switch1_on)
        etSwitch1Off = findViewById<EditText>(R.id.et_switch1_off)
        etSwitch2Name = findViewById<EditText>(R.id.et_switch2_name)
        etSwitch2On = findViewById<EditText>(R.id.et_switch2_on)
        etSwitch2Off = findViewById<EditText>(R.id.et_switch2_off)
        etSwitch3Name = findViewById<EditText>(R.id.et_switch3_name)
        etSwitch3On = findViewById<EditText>(R.id.et_switch3_on)
        etSwitch3Off = findViewById<EditText>(R.id.et_switch3_off)
        etSwitch4Name = findViewById<EditText>(R.id.et_switch4_name)
        etSwitch4On = findViewById<EditText>(R.id.et_switch4_on)
        etSwitch4Off = findViewById<EditText>(R.id.et_switch4_off)

        seekbarGyroSensitivity = findViewById<SeekBar>(R.id.seekbar_gyro_sensitivity)
        tvSensitivityValue = findViewById<TextView>(R.id.tv_sensitivity_value)

        btnSave = findViewById<Button>(R.id.btn_save)
        btnConnect = findViewById<Button>(R.id.btn_connect)
        btnDisconnect = findViewById<Button>(R.id.btn_disconnect)
    }

    private fun setupListeners() {
        rgConnectionType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_wifi -> {
                    wifiSettingsLayout.visibility = android.view.View.VISIBLE
                    bluetoothSettingsLayout.visibility = android.view.View.GONE
                }
                R.id.rb_bluetooth -> {
                    wifiSettingsLayout.visibility = android.view.View.GONE
                    bluetoothSettingsLayout.visibility = android.view.View.VISIBLE
                }
            }
        }

        btnScanBluetooth.setOnClickListener {
            scanBluetoothDevices()
        }

        seekbarGyroSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = (progress + 1) / 10f // Range 0.1 to 2.1
                tvSensitivityValue.text = sensitivity.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            saveConfig()
            setResult(RESULT_OK)
            finish()
        }

        btnConnect.setOnClickListener {
            saveConfig()
            setResult(RESULT_OK)
            finish()
        }

        btnDisconnect.setOnClickListener {
            setResult(RESULT_OK)
            finish()
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

            tvBluetoothDevice.text = config.connectionConfig.bluetoothName

            // Control
            etThrottleTemplate.setText(config.controlConfig.throttleTemplate)
            etSteeringTemplate.setText(config.controlConfig.steeringTemplate)
            etServoLeft.setText(config.controlConfig.servoLeftCommand)
            etServoCenter.setText(config.controlConfig.servoCenterCommand)
            etServoRight.setText(config.controlConfig.servoRightCommand)

            // Custom switches
            config.customSwitches.forEachIndexed { index, switch ->
                when (index) {
                    0 -> {
                        etSwitch1Name.setText(switch.name)
                        etSwitch1On.setText(switch.onCommand)
                        etSwitch1Off.setText(switch.offCommand)
                    }
                    1 -> {
                        etSwitch2Name.setText(switch.name)
                        etSwitch2On.setText(switch.onCommand)
                        etSwitch2Off.setText(switch.offCommand)
                    }
                    2 -> {
                        etSwitch3Name.setText(switch.name)
                        etSwitch3On.setText(switch.onCommand)
                        etSwitch3Off.setText(switch.offCommand)
                    }
                    3 -> {
                        etSwitch4Name.setText(switch.name)
                        etSwitch4On.setText(switch.onCommand)
                        etSwitch4Off.setText(switch.offCommand)
                    }
                }
            }

            // Gyro
            val sensitivityProgress = ((config.gyroSensitivity * 10) - 1).toInt()
            seekbarGyroSensitivity.progress = sensitivityProgress
        }
    }

    private fun saveConfig() {
        appConfig?.let { config ->
            val newConfig = config.copy(
                connectionConfig = config.connectionConfig.copy(
                    connectionType = if (rbWifi.isChecked) ConnectionType.WIFI else ConnectionType.BLUETOOTH,
                    wifiIp = etWifiIp.text.toString(),
                    wifiPort = etWifiPort.text.toString().toIntOrNull() ?: 2000,
                    bluetoothAddress = selectedBluetoothDevice?.address ?: config.connectionConfig.bluetoothAddress,
                    bluetoothName = selectedBluetoothDevice?.name ?: config.connectionConfig.bluetoothName
                ),
                controlConfig = config.controlConfig.copy(
                    throttleTemplate = etThrottleTemplate.text.toString(),
                    steeringTemplate = etSteeringTemplate.text.toString(),
                    servoLeftCommand = etServoLeft.text.toString(),
                    servoCenterCommand = etServoCenter.text.toString(),
                    servoRightCommand = etServoRight.text.toString()
                ),
                customSwitches = listOf(
                    CustomSwitch(1, etSwitch1Name.text.toString(), etSwitch1On.text.toString(), etSwitch1Off.text.toString()),
                    CustomSwitch(2, etSwitch2Name.text.toString(), etSwitch2On.text.toString(), etSwitch2Off.text.toString()),
                    CustomSwitch(3, etSwitch3Name.text.toString(), etSwitch3On.text.toString(), etSwitch3Off.text.toString()),
                    CustomSwitch(4, etSwitch4Name.text.toString(), etSwitch4On.text.toString(), etSwitch4Off.text.toString())
                ),
                gyroSensitivity = (seekbarGyroSensitivity.progress + 1) / 10f
            )

            AppConfig.save(this, newConfig)
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