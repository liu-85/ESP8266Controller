package com.example.esp8266controller.joystick

import com.example.esp8266controller.model.ControlConfig
import com.example.esp8266controller.model.ControlMode
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
        // 转向始终由右摇杆 X 轴控制 (Horizontal)
        val steeringValue = mapToChannelValue(steeringAngle, steeringStrength, isVertical = false)
        
        // 油门始终由左摇杆 Y 轴控制 (Vertical)
        val throttleValue = mapToChannelValue(throttleAngle, throttleStrength, isVertical = true)

        return if (controlConfig.controlMode == ControlMode.MIXED) {
            // 混控模式：油门控制多个通道，通常是左右差速混控
            // 这里遵循标准差速算法: CH1 = Throttle + Steering, CH2 = Throttle - Steering
            // 但用户提到“转向不变”，这通常意味着物理转向机构在 CH2，
            // 如果是差速混控，转向逻辑会影响两路输出。
            
            val throttleOffset = throttleValue - controlConfig.centerValue
            val steeringOffset = steeringValue - controlConfig.centerValue
            
            val mixed1 = (controlConfig.centerValue + throttleOffset + steeringOffset).coerceIn(controlConfig.minChannelValue, controlConfig.maxChannelValue)
            val mixed2 = (controlConfig.centerValue + throttleOffset - steeringOffset).coerceIn(controlConfig.minChannelValue, controlConfig.maxChannelValue)
            
            ChannelData(mixed1, mixed2)
        } else {
            // 独立模式：左 Y 控制 CH1 (油门)，右 X 控制 CH2 (转向)
            ChannelData(throttleValue, steeringValue)
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
        val ch1: Int
        val ch2: Int

        if (controlConfig.controlMode == ControlMode.MIXED) {
            // 混控模式：根据设置的通道分配混控后的值
            // 如果 mixedChannel1 是 1，则 ch1 = throttleValue；如果是 2，则 ch1 = steeringValue
            ch1 = if (controlConfig.mixedChannel1 == 1) throttleValue else steeringValue
            ch2 = if (controlConfig.mixedChannel2 == 1) throttleValue else steeringValue
        } else {
            // 独立模式：默认 ch1=M1, ch2=M2 (已经由 processJoystickData 计算好)
            ch1 = throttleValue
            ch2 = steeringValue
        }

        return "SS2:$ch1,$ch2\n"
    }
}