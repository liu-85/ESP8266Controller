package com.example.esp8266controller.model

enum class ControlMode {
    MIXED,      // 混控模式 (油门+转向)
    SEPARATE    // 独立模式 (M1, M2 分开控制)
}

data class ControlConfig(
    val controlMode: ControlMode = ControlMode.MIXED,
    val throttleTemplate: String = "{value}",
    val steeringTemplate: String = "{value}",
    val servoLeftCommand: String = "SERVO_LEFT",
    val servoCenterCommand: String = "SERVO_CENTER",
    val servoRightCommand: String = "SERVO_RIGHT",
    val minChannelValue: Int = 1000,
    val maxChannelValue: Int = 2000,
    val centerValue: Int = 1500,
    val sendIntervalMs: Long = 30
)