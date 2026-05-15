# RC遥控器 (ESP8266 控制器)

这是一个功能强大的开源 Android 遥控器应用，专为模型车、船、无人机等 DIY 项目设计。通过 TCP/UDP (Wi-Fi) 或蓝牙连接 ESP8266/ESP32 设备。

## 🌟 主要增强功能

- **双协议 Wi-Fi 支持**：
  - **TCP 模式**：数据传输稳定可靠，适合环境干扰较小的场景。
  - **UDP 模式**：极低延迟，适合高速遥控或对实时性要求极高的场景（需硬件端支持）。

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
   - **WIFI**：确保手机与 ESP8266 处于同一局域网，在设置中输入 IP 和端口（默认 2000）。可切换 TCP 或 UDP 协议。
   - **蓝牙**：在设置中点击“蓝牙”，从已配对列表中选择设备。
2. **操控校准**：
   - 可以在设置中调整“舵机中位修正”以补偿机械安装误差。
   - “操控平滑度”数值越小，操控越丝滑；数值越大，反应越灵敏。
3. **保存设置**：点击设置页右上角的“保存”按钮。

## 🛠️ 硬件端示例 (Arduino IDE)

### 方案 A: TCP 接收 (稳定)
```cpp
#include <ESP8266WiFi.h>

WiFiServer server(2000); 

void setup() {
  WiFi.begin("SSID", "PASSWORD");
  server.begin();
}

void loop() {
  WiFiClient client = server.available();
  if (client) {
    while (client.connected()) {
      if (client.available()) {
        String line = client.readStringUntil('\n');
        if (line.startsWith("SS2:")) {
          // 解析 SS2:油门,转向
        }
      }
    }
  }
}
```

### 方案 B: UDP 接收 (低延迟)
```cpp
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>

WiFiUDP Udp;
unsigned int localUdpPort = 2000;
char packetBuffer[255];

void setup() {
  WiFi.begin("SSID", "PASSWORD");
  Udp.begin(localUdpPort);
}

void loop() {
  int packetSize = Udp.parsePacket();
  if (packetSize) {
    int len = Udp.read(packetBuffer, 255);
    if (len > 0) packetBuffer[len] = 0;
    String line = String(packetBuffer);
    if (line.startsWith("SS2:")) {
       // 解析 SS2:油门,转向
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
- **网络**: Java Socket (TCP), Java DatagramSocket (UDP), Android Bluetooth API
- **算法**: 指数平滑滤波, 映射映射算法
