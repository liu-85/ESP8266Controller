package com.example.esp8266controller.model

data class ControlConfig(
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