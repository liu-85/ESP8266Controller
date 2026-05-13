package com.example.esp8266controller.joystick

import com.example.esp8266controller.model.ControlConfig
import kotlin.math.*

class JoystickDataProcessor(private val controlConfig: ControlConfig) {

    data class ChannelData(
        val throttle: Int,
        val steering: Int
    )

    fun processJoystickData(
        throttleAngle: Double,
        throttleStrength: Float,
        steeringAngle: Double,
        steeringStrength: Float
    ): ChannelData {
        return if (controlConfig.controlMode == ControlMode.MIXED) {
            val throttle = mapToChannelValue(throttleAngle, throttleStrength, isVertical = true)
            val steering = mapToChannelValue(steeringAngle, steeringStrength, isVertical = false)
            ChannelData(throttle, steering)
        } else {
            // SEPARATE 模式: 左摇杆 Y 轴控制 M1, 右摇杆 Y 轴控制 M2
            val m1 = mapToChannelValue(throttleAngle, throttleStrength, isVertical = true)
            val m2 = mapToChannelValue(steeringAngle, steeringStrength, isVertical = true)
            ChannelData(m1, m2)
        }
    }

    private fun mapToChannelValue(angle: Double, strength: Float, isVertical: Boolean): Int {
        val center = controlConfig.centerValue
        val minVal = controlConfig.minChannelValue
        val maxVal = controlConfig.maxChannelValue
        val range = (maxVal - minVal) / 2

        val normalizedStrength = strength.coerceIn(0f, 1f)

        if (normalizedStrength < 0.05f) {
            return center // Near center, return neutral value
        }

        val valueOffset = (range * normalizedStrength).toInt()

        return if (isVertical) {
            // Vertical: angle near 0° (top) -> forward, near 180° (bottom) -> backward
            val adjustedAngle = when {
                angle >= 0 && angle <= 90 -> 90 - angle // Top quadrant
                angle > 90 && angle <= 270 -> 90 - angle // Bottom half (negative values)
                angle > 270 -> 90 - (angle - 360) // Top-right quadrant
                else -> 0.0
            }

            // Map to forward/backward
            when {
                adjustedAngle >= 45 -> center + valueOffset // Forward
                adjustedAngle <= -45 -> center - valueOffset // Backward
                else -> center
            }
        } else {
            // Horizontal: angle near 90° (right) -> right, near 270° (left) -> left
            val adjustedAngle = when {
                angle >= 0 && angle <= 180 -> angle - 90 // Right half
                angle > 180 && angle <= 360 -> angle - 270 // Left half
                else -> 0.0
            }

            // Map to left/right
            when {
                adjustedAngle >= 45 -> center + valueOffset // Right
                adjustedAngle <= -45 -> center - valueOffset // Left
                else -> center
            }
        }
    }

    fun formatCommand(throttleValue: Int, steeringValue: Int): String {
        val throttleCommand = controlConfig.throttleTemplate.replace("{value}", throttleValue.toString())
        val steeringCommand = controlConfig.steeringTemplate.replace("{value}", steeringValue.toString())
        return "SS2:$throttleCommand,$steeringCommand\n"
    }
}