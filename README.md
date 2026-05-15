# RC遥控器 (ESP8266 控制器)

这是一个功能强大的开源 Android 遥控器应用，专为模型车、船、无人机等 DIY 项目设计。通过 TCP (Wi-Fi) 或蓝牙连接 ESP8266/ESP32 设备。

## 🌟 主要增强功能

- **非线性平滑控制**：摇杆与陀螺仪均采用非线性平滑算法（指数移动平均），可在设置中精细调节平滑系数。
- **iOS 风格主题**：新增极简 iOS 风格主题，界面清爽且支持主题色动态切换。
- **高频 SS2 协议**：支持每秒 25 次的高频指令发送，延迟低，操控感真实。
- **失控与保活逻辑**：
  - **心跳机制**：定时发送心跳包（\n）维持连接。
  - **自动断开**：支持自定义超时时间（默认30分钟），无操作自动断开连接。
- **多通道映射**：每个通道（CH1-CH8）均可映射多个控制源（摇杆、按键、开关）。
- **物理反馈**：摇杆到达极限位置时手机轻微震动提示。

## 📱 使用指南

1. **连接设置**：
   - **WIFI**：确保手机与 ESP8266 处于同一局域网，在设置中输入 IP 和端口（默认 2000）。
   - **蓝牙**：在设置中点击“蓝牙”，从已配对列表中选择设备。
2. **操控校准**：
   - 可以在设置中调整“舵机中位修正”以补偿机械安装误差。
   - “操控平滑度”数值越小，操控越丝滑；数值越大，反应越灵敏。
3. **保存设置**：点击设置页右上角的“保存”按钮。

## 🛠️ 硬件端示例 (Arduino IDE)

以下是适配本应用的 ESP8266 核心代码示例。

```cpp
#include <ESP8266WiFi.h>
#include <Servo.h>

// WiFi配置
const char* ssid = "你的WiFi名称";
const char* password = "你的密码";

WiFiServer server(2000); // 监听 2000 端口
Servo steeringServo;

void setup() {
  Serial.begin(115200);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  server.begin();
  steeringServo.attach(13); // GPIO13 (D7)
}

void loop() {
  WiFiClient client = server.available();
  if (client) {
    while (client.connected()) {
      if (client.available()) {
        String line = client.readStringUntil('\n');
        line.trim();
        if (line.startsWith("SS2:")) {
          // 解析 SS2:油门,转向
          int comma = line.indexOf(',');
          int throttle = line.substring(4, comma).toInt(); // 1000-2000
          int steering = line.substring(comma + 1).toInt(); // 1000-2000
          
          // 映射到舵机角度
          int angle = map(steering, 1000, 2000, 0, 180);
          steeringServo.write(angle);
          
          Serial.printf("油门: %d, 转向: %d\n", throttle, steering);
        }
      }
    }
  }
}
```

## 🏗️ 编译与构建

### 自动编译
项目配置了 GitHub Actions，每次推送代码到 `main` 分支会自动触发构建并生成 Release。

### 手动编译 (Windows)
在根目录下运行 `build_manual.bat`：
- 直接运行：默认以当前日期（YYYYMMDD）作为版本号。
- 命令行运行：`build_manual.bat 1.0.5` 指定版本号。

## 📦 技术栈
- **语言**: Kotlin / Java
- **UI**: Android XML (iOS Style & V7RC Inspired)
- **网络**: Java Socket (TCP), Android Bluetooth API
- **算法**: 指数平滑滤波, 映射映射算法
