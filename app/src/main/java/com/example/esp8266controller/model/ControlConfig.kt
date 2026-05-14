package com.example.esp8266controller.model

enum class ControlSource {
    LEFT_JOYSTICK_Y,   // 左摇杆垂直 (油门)
    LEFT_JOYSTICK_X,   // 左摇杆水平 (转向)
    RIGHT_JOYSTICK_Y,  // 右摇杆垂直 (油门)
    RIGHT_JOYSTICK_X,  // 右摇杆水平 (转向)
    BUTTON_1,
    BUTTON_2,
    BUTTON_3,
    BUTTON_4,
    SWITCH_1,
    SWITCH_2,
    NONE               // 中性值 1500
}

data class ControlConfig(
    // 8个通道的来源配置，默认：
    // CH1: 左Y, CH2: 右X, 其他: NONE
    val channelSources: List<ControlSource> = listOf(
        ControlSource.LEFT_JOYSTICK_Y,  // CH1
        ControlSource.RIGHT_JOYSTICK_X, // CH2
        ControlSource.NONE,             // CH3
        ControlSource.NONE,             // CH4
        ControlSource.NONE,             // CH5
        ControlSource.NONE,             // CH6
        ControlSource.NONE,             // CH7
        ControlSource.NONE              // CH8
    ),
    val minChannelValue: Int = 1000,
    val maxChannelValue: Int = 2000,
    val centerValue: Int = 1500,
    val sendIntervalMs: Long = 50, // UI.txt 建议定时发送，默认 50ms
    val isTimerEnabled: Boolean = false,
    val isGyroEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f
)
