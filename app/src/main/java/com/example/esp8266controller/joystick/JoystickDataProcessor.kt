package com.example.esp8266controller.joystick

import com.example.esp8266controller.model.ControlConfig
import com.example.esp8266controller.model.ControlSource
import com.example.esp8266controller.model.ThrottleCurve
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class JoystickDataProcessor(private val controlConfig: ControlConfig) {

    private var smoothedLeftY = controlConfig.centerValue.toDouble()
    private var smoothedLeftX = controlConfig.centerValue.toDouble()
    private var smoothedRightY = controlConfig.centerValue.toDouble()
    private var smoothedRightX = controlConfig.centerValue.toDouble()

    fun applySmoothing(current: Int, previous: Double): Double {
        val factor = controlConfig.smoothingFactor.toDouble().coerceIn(0.01, 1.0)
        return previous + factor * (current - previous)
    }

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
        val centerDeadzone = controlConfig.centerDeadzone
        val endDeadzone = controlConfig.endDeadzone

        // 1. Center Deadzone handling
        if (normalizedStrength < centerDeadzone) {
            return center 
        }

        // 2. Linear mapping with End Deadzone
        // Rescale strength from [centerDeadzone, 1.0 - endDeadzone] to [0.0, 1.0]
        val usableRange = 1.0f - centerDeadzone - endDeadzone
        val scaledStrength = if (usableRange > 0) {
            ((normalizedStrength - centerDeadzone) / usableRange).coerceIn(0f, 1f)
        } else {
            1.0f
        }

        val rad = Math.toRadians(angle - 90.0)
        
        val rawValue = if (isVertical) {
            (center - sin(rad) * range * scaledStrength).toInt()
        } else {
            (center + cos(rad) * range * scaledStrength).toInt()
        }
        
        val constrainedValue = rawValue.coerceIn(minVal, maxVal)
        
        // Apply throttle curve if it's a throttle source
        return if (isVertical) applyThrottleCurve(constrainedValue) else constrainedValue
    }

    fun formatCommand(
        leftY: Int, leftX: Int,
        rightY: Int, rightX: Int,
        buttons: List<Boolean> = emptyList(),
        switches: List<Boolean> = emptyList()
    ): String {
        // Apply smoothing
        smoothedLeftY = applySmoothing(leftY, smoothedLeftY)
        smoothedLeftX = applySmoothing(leftX, smoothedLeftX)
        smoothedRightY = applySmoothing(rightY, smoothedRightY)
        smoothedRightX = applySmoothing(rightX, smoothedRightX)

        // SS2 format: SS2:throttle,steering
        // Use CH1 (throttle) and CH2 (steering) from config
        val throttle = getChannelValue(0, 
            smoothedLeftY.toInt(), smoothedLeftX.toInt(), 
            smoothedRightY.toInt(), smoothedRightX.toInt(), 
            buttons, switches)
        val steering = getChannelValue(1, 
            smoothedLeftY.toInt(), smoothedLeftX.toInt(), 
            smoothedRightY.toInt(), smoothedRightX.toInt(), 
            buttons, switches)
        
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

    fun isNeutral(): Boolean {
        val threshold = 2.0 // Small threshold for float comparison
        return abs(smoothedLeftY - controlConfig.centerValue) < threshold &&
               abs(smoothedLeftX - controlConfig.centerValue) < threshold &&
               abs(smoothedRightY - controlConfig.centerValue) < threshold &&
               abs(smoothedRightX - controlConfig.centerValue) < threshold
    }
}
