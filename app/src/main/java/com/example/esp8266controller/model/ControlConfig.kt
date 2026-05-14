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

enum class ThrottleCurve {
    LINEAR,     // 线性
    EXPONENTIAL // 指数
}

data class ControlConfig(
    // 8个通道的来源配置，默认：
    // CH1: 左Y, CH2: 右X, 其他: NONE
    val channelSources: List<List<ControlSource>> = listOf(
        listOf(ControlSource.LEFT_JOYSTICK_Y),  // CH1
        listOf(ControlSource.RIGHT_JOYSTICK_X), // CH2
        listOf(ControlSource.NONE),             // CH3
        listOf(ControlSource.NONE),             // CH4
        listOf(ControlSource.NONE),             // CH5
        listOf(ControlSource.NONE),             // CH6
        listOf(ControlSource.NONE),             // CH7
        listOf(ControlSource.NONE)              // CH8
    ),
    val minChannelValue: Int = 1000,
    val maxChannelValue: Int = 2000,
    val centerValue: Int = 1500,
    val servoCenterOffset: Int = 0, // 舵机中位偏移
    val throttleCurve: ThrottleCurve = ThrottleCurve.LINEAR,
    val sendIntervalMs: Long = 40, // 操作时的发送间隔
    val heartbeatIntervalMs: Long = 1000, // 心跳保活间隔，默认1秒
    val inactivityTimeoutMs: Long = 1800000, // 超时断开时间，默认30分钟 (30 * 60 * 1000)
    val isTimerEnabled: Boolean = false, // 改为默认关闭，只有操作才动作
    val isGyroEnabled: Boolean = false,
    val gyroSensitivity: Float = 1.0f,
    val enableVibration: Boolean = true // 极限位置震动反馈
)
