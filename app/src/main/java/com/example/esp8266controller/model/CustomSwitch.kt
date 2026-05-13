package com.example.esp8266controller.model

data class CustomSwitch(
    val id: Int,
    var name: String,
    var onCommand: String,
    var offCommand: String,
    var isOn: Boolean = false
) {
    companion object {
        fun createDefaultSwitches(): List<CustomSwitch> {
            return listOf(
                CustomSwitch(1, "灯光", "SW1_ON", "SW1_OFF"),
                CustomSwitch(2, "蜂鸣器", "SW2_ON", "SW2_OFF"),
                CustomSwitch(3, "开关3", "SW3_ON", "SW3_OFF"),
                CustomSwitch(4, "开关4", "SW4_ON", "SW4_OFF")
            )
        }
    }
}