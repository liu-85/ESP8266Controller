package com.example.esp8266controller.joystick

import com.example.esp8266controller.model.ControlConfig
import com.example.esp8266controller.model.ControlSource
import com.example.esp8266controller.model.ThrottleCurve
import kotlin.math.*

class JoystickDataProcessor(private val controlConfig: ControlConfig) {

    fun applyThrottleCurve(value: Int): Int {
        if (controlConfig.throttleCurve == ThrottleCurve.LINEAR) return value
        
        val center = controlConfig.centerValue
        val range = (controlConfig.maxChannelValue - controlConfig.minChannelValue) / 2
        val input = (value - center).toDouble() / range // -1.0 to 1.0
        
        // Exponential curve: y = x^3 or similar
        val output = input.pow(3.0)
        return (center + output * range).toInt()
    }

    fun mapToChannelValue(angle: Double, strength: Float, isVertical: Boolean): Int {
        val center = controlConfig.centerValue
        val minVal = controlConfig.minChannelValue
        val maxVal = controlConfig.maxChannelValue
        val range = (maxVal - minVal) / 2

        val normalizedStrength = strength.coerceIn(0f, 1f)

        if (normalizedStrength < 0.05f) {
            return center // Near center, return neutral value
        }

        val valueOffset = (range * normalizedStrength).toInt()

        val rawValue = if (isVertical) {
            // Vertical: angle near 0° (top) -> forward, near 180° (bottom) -> backward
            val adjustedAngle = when {
                angle >= 0 && angle <= 90 -> 90 - angle // Top quadrant
                angle > 90 && angle <= 270 -> 90 - angle // Bottom half (negative values)
                angle > 270 -> 90 - (angle - 360) // Top-right quadrant
                else -> 0.0
            }

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

            when {
                adjustedAngle >= 45 -> center + valueOffset // Right
                adjustedAngle <= -45 -> center - valueOffset // Left
                else -> center
            }
        }
        
        // Apply throttle curve if it's a throttle source (simplified: always apply to Y axis for now)
        return if (isVertical) applyThrottleCurve(rawValue) else rawValue
    }

    fun formatCommand(
        leftY: Int, leftX: Int,
        rightY: Int, rightX: Int,
        buttons: List<Boolean> = emptyList(),
        switches: List<Boolean> = emptyList()
    ): String {
        // SS2 format: SS2:throttle,steering
        // Use CH1 (throttle) and CH2 (steering) from config
        val throttle = getChannelValue(0, leftY, leftX, rightY, rightX, buttons, switches)
        val steering = getChannelValue(1, leftY, leftX, rightY, rightX, buttons, switches)
        
        return "SS2:$throttle,$steering\n"
    }

    private fun getChannelValue(
        channelIndex: Int,
        leftY: Int, leftX: Int,
        rightY: Int, rightX: Int,
        buttons: List<Boolean>,
        switches: List<Boolean>
    ): Int {
        val sources = controlConfig.channelSources.getOrNull(channelIndex) ?: return controlConfig.centerValue
        var finalValue = controlConfig.centerValue
        
        for (source in sources) {
            val value = when (source) {
                ControlSource.LEFT_JOYSTICK_Y -> leftY
                ControlSource.LEFT_JOYSTICK_X -> leftX
                ControlSource.RIGHT_JOYSTICK_Y -> rightY
                ControlSource.RIGHT_JOYSTICK_X -> rightX
                ControlSource.BUTTON_1 -> if (buttons.getOrElse(0) { false }) 2000 else 1000
                ControlSource.BUTTON_2 -> if (buttons.getOrElse(1) { false }) 2000 else 1000
                ControlSource.BUTTON_3 -> if (buttons.getOrElse(2) { false }) 2000 else 1000
                ControlSource.BUTTON_4 -> if (buttons.getOrElse(3) { false }) 2000 else 1000
                ControlSource.SWITCH_1 -> if (switches.getOrElse(0) { false }) 2000 else 1000
                ControlSource.SWITCH_2 -> if (switches.getOrElse(1) { false }) 2000 else 1000
                ControlSource.NONE -> controlConfig.centerValue
            }
            if (value != controlConfig.centerValue) {
                finalValue = value
                break
            }
        }
        
        // Apply servo center offset if it's steering (CH2)
        if (channelIndex == 1) {
            finalValue = (finalValue + controlConfig.servoCenterOffset).coerceIn(1000, 2000)
        }
        
        return finalValue
    }

    fun formatButtonCommand(command: String): String {
        return "$command\n"
    }
}
