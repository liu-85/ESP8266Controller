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
        val throttle = mapToChannelValue(throttleAngle, throttleStrength, isThrottle = true)
        val steering = mapToChannelValue(steeringAngle, steeringStrength, isThrottle = false)
        return ChannelData(throttle, steering)
    }

    private fun mapToChannelValue(angle: Double, strength: Float, isThrottle: Boolean): Int {
        val center = controlConfig.centerValue
        val minVal = controlConfig.minChannelValue
        val maxVal = controlConfig.maxChannelValue
        val range = (maxVal - minVal) / 2

        // For throttle: 0° (top) = forward, 180° (bottom) = backward
        // For steering: 90° (right) = right, 270° (left) = left

        val normalizedStrength = strength.coerceIn(0f, 1f)

        if (normalizedStrength < 0.05f) {
            return center // Near center, return neutral value
        }

        val valueOffset = (range * normalizedStrength).toInt()

        return if (isThrottle) {
            // Throttle: angle near 0° (top) -> forward, near 180° (bottom) -> backward
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
            // Steering: angle near 90° (right) -> right, near 270° (left) -> left
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
        return "$throttleCommand,$steeringCommand\n"
    }
}