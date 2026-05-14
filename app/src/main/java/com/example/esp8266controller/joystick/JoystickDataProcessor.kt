package com.example.esp8266controller.joystick

import com.example.esp8266controller.model.ControlConfig
import com.example.esp8266controller.model.ControlSource
import kotlin.math.*

class JoystickDataProcessor(private val controlConfig: ControlConfig) {

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

    fun formatCommand(
        leftY: Int, leftX: Int,
        rightY: Int, rightX: Int,
        buttons: List<Boolean>,
        switches: List<Boolean>
    ): String {
        val channelValues = controlConfig.channelSources.map { source ->
            when (source) {
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
                ControlSource.NONE -> 1500
            }
        }

        return "SS2:${channelValues.joinToString(",")}\n"
    }
}
