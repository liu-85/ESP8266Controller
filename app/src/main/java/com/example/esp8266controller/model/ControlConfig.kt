package com.example.esp8266controller.model

data class ControlConfig(
    val throttleTemplate: String = "CH1:{value}",
    val steeringTemplate: String = "CH2:{value}",
    val servoLeftCommand: String = "SERVO:0",
    val servoCenterCommand: String = "SERVO:90",
    val servoRightCommand: String = "SERVO:180",
    val minChannelValue: Int = 1000,
    val maxChannelValue: Int = 2000,
    val centerValue: Int = 1500,
    val sendIntervalMs: Long = 30
)