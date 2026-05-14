package com.example.esp8266controller.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.esp8266controller.model.ControlConfig
import com.example.esp8266controller.joystick.JoystickDataProcessor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI

class GyroController(
    private val context: Context,
    private val controlConfig: ControlConfig
) : SensorEventListener {

    data class ChannelData(val throttle: Int, val steering: Int)

    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var isGyroEnabled: Boolean = false
    private var sensitivity: Float = 1.0f

    private var zeroPointPitch: Float = 0f
    private var zeroPointRoll: Float = 0f
    private var isCalibrated: Boolean = false

    private var onDataUpdateListener: ((pitch: Float, roll: Float) -> Unit)? = null
    private var dataProcessor: JoystickDataProcessor = JoystickDataProcessor(controlConfig)

    private var lastPitch: Float = 0f
    private var lastRoll: Float = 0f

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (gyroSensor != null) {
            sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            isGyroEnabled = true
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        isGyroEnabled = false
    }

    fun calibrate() {
        zeroPointPitch = lastPitch
        zeroPointRoll = lastRoll
        isCalibrated = true
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.1f, 2.0f)
    }

    fun setOnDataUpdateListener(listener: (pitch: Float, roll: Float) -> Unit) {
        this.onDataUpdateListener = listener
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // Calculate pitch and roll from accelerometer data
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                lastPitch = atan2(y.toDouble(), sqrt((x * x + z * z).toDouble())).toFloat() * (180 / PI).toFloat()
                lastRoll = atan2((-x).toDouble(), z.toDouble()).toFloat() * (180 / PI).toFloat()

                // Apply calibration offset
                var adjustedPitch = lastPitch - zeroPointPitch
                var adjustedRoll = lastRoll - zeroPointRoll

                // Apply sensitivity
                adjustedPitch *= sensitivity
                adjustedRoll *= sensitivity

                // Clamp values
                adjustedPitch = adjustedPitch.coerceIn(-45f, 45f)
                adjustedRoll = adjustedRoll.coerceIn(-45f, 45f)

                onDataUpdateListener?.invoke(adjustedPitch, adjustedRoll)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    fun processGyroData(pitch: Float, roll: Float): ChannelData {
        // Convert pitch/roll to joystick-like values
        // Pitch: forward/backward (positive pitch = forward)
        // Roll: left/right (positive roll = right)

        val pitchStrength = abs(pitch) / 45f
        val rollStrength = abs(roll) / 45f

        val pitchAngle = if (pitch > 0) 0.0 else 180.0
        val rollAngle = if (roll > 0) 90.0 else 270.0

        val throttle = dataProcessor.mapToChannelValue(pitchAngle, pitchStrength, true)
        val steering = dataProcessor.mapToChannelValue(rollAngle, rollStrength, false)

        return ChannelData(throttle, steering)
    }

    fun isEnabled(): Boolean = isGyroEnabled
    fun isCalibrated(): Boolean = isCalibrated
    fun getLastPitch(): Float = lastPitch
    fun getLastRoll(): Float = lastRoll
}
