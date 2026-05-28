# SpeedTransfer（微传）

基于局域网的 Android 文件互传与即时通讯应用。Android 设备作为 HTTP/WebSocket 服务器，PC 或其他设备通过浏览器即可连接，实现同一 Wi-Fi 下的文件交换与文字聊天。

---

## 功能概述

| 功能 | 描述 |
|------|------|
| **文件分享 (Android → PC)** | 手机端选择文件（支持多选），PC 端通过浏览器浏览并下载 |
| **文件接收 (PC → Android)** | PC 端通过 Web 界面拖拽或选择文件上传到手机，聊天界面实时显示进度，自动检测存储空间（不足 50MB 提示 507 Insufficient Storage） |
| **文字聊天** | 手机与 PC 双向即时文本通讯，支持长按复制/删除/全选气泡消息 |
| **自动发现** | 通过 Android NSD（mDNS / DNS-SD）自动发现局域网内的 PC 服务（类型 `_pcservicewt._tcp.`），零配置连接 |
| **心跳保活** | UDP 心跳（每秒一次）维持连接状态，PC 可实时监测 Android 在线/离线状态 |
| **后台服务** | Android Foreground Service + WakeLock + WifiLock，确保传输过程中不被系统休眠中断 |
| **PC Web 界面** | 内置 521 行独立 HTML 页面，支持三套主题（粉/蓝/深色），拖拽上传，SVG 上传进度环，WebSocket 自动重连 |

---

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 开发语言 | Java | 11 |
| 最低 SDK | Android 8.0 (API 26) | — |
| 目标 / 编译 SDK | Android 14 (API 35) | — |
| 构建工具 | Gradle + AGP | 8.11.1 / 8.9.0 |
| HTTP / WebSocket 服务器 | NanoHTTPD + NanoWSD | 2.3.1 |
| HTTP 客户端 | OkHttp | 4.9.0 |
| 设备发现 | Android NsdManager (mDNS / DNS-SD) | 系统 API |
| 心跳机制 | java.net.DatagramSocket (UDP) | JDK |
| UI 框架 | AndroidX AppCompat + Material Design 3 + ConstraintLayout | — |
| 图片缓存 | LRU Cache（内存） | JDK |

---

## 项目结构

```
SpeedTransfer/
├── build.gradle                          # 根构建文件
├── settings.gradle                       # 项目设置（module: app）
├── gradle/
│   ├── libs.versions.toml                # Gradle 版本目录（AGP、依赖库版本号）
│   └── wrapper/                          # Gradle Wrapper（8.11.1）
├── gradle.properties                     # Gradle 全局属性
├── local.properties                      # 本地 SDK 路径
└── app/
    ├── build.gradle                      # 应用构建配置
    ├── proguard-rules.pro                # 混淆规则
    └── src/
        └── main/
            ├── AndroidManifest.xml       # 清单文件（权限、Service、Activity）
            ├── assets/
            │   └── example_beauty_05.html # PC 端 Web 操作界面
            ├── java/com/loader/speedtransfer/
            │   ├── field/
            │   │   └── CustomField.java           # 常量定义（端口 8099、上传/下载目录）
            │   ├── net/
            │   │   ├── AndroidHttpServer.java     # 核心 HTTP + WebSocket 服务器
            │   │   ├── TransferService.java       # Android 前台服务（包裹 HTTP 服务器）
            │   │   ├── NsdHelper.java             # mDNS 局域网服务发现
            │   │   └── UdpHeartbeatSender.java    # UDP 心跳发送器
            │   ├── ui/
            │   │   ├── MainActivity.java          # 主界面（唯一 Activity，533 行）
            │   │   ├── ChatAdapter.java           # 聊天列表 RecyclerView 适配器（4 种视图类型）
            │   │   ├── ChatCallback.java          # 网络层 → UI 层回调接口（6 个方法）
            │   │   ├── ChatMessage.java           # 聊天消息数据模型
            │   │   ├── FileShareAdapter.java      # 共享文件列表 RecyclerView 适配器
            │   │   ├── FilePickerUtil.java        # 系统文件选择器封装（支持多选）
            │   │   ├── PopupTextView.java         # 可拖拽选中文本的自定义 TextView（389 行）
            │   │   ├── SelectionOverlayMenu.java  # 文本选中浮动操作菜单（复制/全选/删除）
            │   │   └── CustomToast.java           # 队列式自定义 Toast（淡入淡出，避免重叠）
            │   └── utils/
            │       ├── NetworkUtils.java          # 获取本机 IPv4 地址
            │       ├── FileUtils.java             # 文件复制、大小格式化（B/KB/MB/GB/TB）
            │       ├── FileUiUtils.java           # 通过 FileProvider + Intent 打开文件
            │       ├── FileIconUtils.java         # 文件类型图标映射、缩略图加载、LRU 缓存
            │       └── DeviceSettingUtils.java    # 获取设备名（MANUFACTURER MODEL）
            └── res/                                # 布局、图标、主题、字符串等资源
```

---

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                       Android 端                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │              TransferService (Foreground)         │   │
│  │  ┌──────────────────┐  ┌──────────────────────┐  │   │
│  │  │ AndroidHttpServer│  │    NsdHelper         │  │   │
│  │  │ (NanoHTTPD +    │  │   (mDNS 发现 PC)     │  │   │
│  │  │  NanoWSD)       │  └──────────────────────┘  │   │
│  │  │  端口: 8099      │  ┌──────────────────────┐  │   │
│  │  └──────────────────┘  │ UdpHeartbeatSender   │  │   │
│  │                        │  (UDP 心跳 1秒/次)   │  │   │
│  │                        └──────────────────────┘  │   │
│  └──────────────────────────────────────────────────┘   │
│                          ↕ bind + ChatCallback          │
│  ┌──────────────────────────────────────────────────┐   │
│  │               MainActivity (UI)                   │   │
│  │  ┌─────────────┐ ┌────────────┐ ┌─────────────┐ │   │
│  │  │ ChatAdapter │ │FileShare   │ │ FilePicker  │ │   │
│  │  │ (聊天列表)   │ │Adapter     │ │ (文件选择)  │ │   │
│  │  └─────────────┘ └────────────┘ └─────────────┘ │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                           ↕ Wi-Fi
┌─────────────────────────────────────────────────────────┐
│                        PC 端                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │              浏览器（Chrome / Edge / ...）         │   │
│  │         访问 http://<android_ip>:8099/            │   │
│  │  ┌────────────────────────────────────────────┐  │   │
│  │  │     example_beauty_05.html                  │  │   │
│  │  │  · 主题切换（粉 / 蓝 / 深色）               │  │   │
│  │  │  · 聊天面板（消息气泡）                      │  │   │
│  │  │  · 拖拽/选择文件上传 + 进度显示              │  │   │
│  │  │  · 文件列表查看与下载                        │  │   │
│  │  │  · WebSocket 实时推送 + 自动重连             │  │   │
│  │  └────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │          PC 端 mDNS 服务                          │   │
│  │      (广告 _pcservicewt._tcp. 服务)              │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 通信流程

### 1. 设备发现与连接

```
 PC (广告 mDNS 服务 _pcservicewt._tcp.)  ←── NSD 发现 ──  Android
 Android                                  ── UDP 心跳(1s/次) ──→  PC
```

- PC 端发布 mDNS 服务（类型 `_pcservicewt._tcp.`），附带心跳签名 `heart_beat_sign`
- Android 通过 `NsdManager` 发现服务后，解析 PC 的 IP/端口
- 开始发送 UDP 心跳包，格式：`heartBeatSign|设备名|Android_IP|8099`

### 2. HTTP API 端点

| 方法 | 路径 | 方向 | Content-Type | 说明 |
|------|------|------|-------------|------|
| `GET` | `/` | PC → Android | `text/html` | 返回 PC 端 Web 操作界面 |
| `GET` | `/fileList` | PC → Android | `application/json` | 返回可下载文件列表 `[{name, size}]` |
| `GET` | `/download?file=xxx` | PC → Android | `application/octet-stream` | 下载指定文件（含 Content-Disposition） |
| `POST` | `/upload` | PC → Android | `multipart/form-data` | PC 上传文件（含进度回调） |
| `POST` | `/chat` | PC → Android | `application/json` | PC 发送文本消息 `{"message":"..."}` |
| WebSocket | `/` | 双向 | — | Android ↔ PC 实时消息推送 |

### 3. 文件路径

| 场景 | 本地存储路径 |
|------|------------|
| Android 分享给 PC 的文件 | `Downloads/WeTransfer/上传文件/` |
| PC 上传到 Android 的文件 | `Downloads/WeTransfer/接收文件/` |
| HTTP 服务器临时文件 | `app_cache/nanohttpd_tmp/` |

---

## UI 特性

### 聊天界面

- **4 种视图类型**：发送者气泡（粉色右对齐）、接收者气泡（灰色左对齐）、文件接收卡片（图标 + 名称 + 大小 + 进度条）
- **发送状态指示**：发送中显示加载转轮，失败显示错误图标
- **上传状态**：UPLOADING / COMPLETED / FAILED，含进度条
- **文本气泡长按**：呼出自定义选中菜单（全选 / 复制 / 删除），带拖拽手柄的文本选中框
- **选中菜单**：悬浮覆盖层（替代 PopupWindow），智能定位锚点上方/下方，带过冲动画
- **键盘适配**：软键盘弹出时自动调整底部输入栏位置

### 文件共享面板

- **可折叠列表**：带动画高度过渡（ValueAnimator）
- **缩略图加载**：异步加载图片缩略图，LS 卡片背景
- **文件图标**：按扩展名映射图标（图片/视频/音频/PDF/压缩包/文本/代码/APK），无匹配时显示首字母彩色图标
- **图标缓存**：LRU 内存缓存

### 其他

- **自定义 Toast**：队列式顺序显示，淡入淡出动画，2 秒时长
- **IP 地址**：状态栏展示本机 IP 和端口，点击可复制到剪贴板
- **电池优化**：引导用户关闭电池优化，避免后台被终止
- **EdgeToEdge**：全屏边到边显示

---

## 构建与运行

### 环境要求

| 工具 | 版本要求 |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Gradle | 8.11.1 |
| AGP | 8.9.0 |
| JDK | 11+ |
| Android SDK | Platform 35 |

### 构建命令

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug

# 编译 Release 版本（ProGuard 未启用）
./gradlew assembleRelease
```

或直接在 Android Studio 中打开项目，点击 `Run`。

### 权限说明

应用在运行时请求以下权限：

| 权限 | Android 版本 | 用途 |
|------|-------------|------|
| `INTERNET` | 全部 | HTTP 服务器与 WebSocket 通信 |
| `ACCESS_NETWORK_STATE` | 全部 | 检测网络连接状态 |
| `ACCESS_WIFI_STATE` | 全部 | Wi-Fi 锁唤醒 |
| `NEARBY_WIFI_DEVICES` | 13+ | mDNS 局域网设备发现 |
| `READ_EXTERNAL_STORAGE` | ≤12 | 读取共享文件目录 |
| `WRITE_EXTERNAL_STORAGE` | ≤12 | 写入接收文件目录 |
| `POST_NOTIFICATIONS` | 13+ | 前台服务通知 |
| `FOREGROUND_SERVICE` | 全部 | 保持后台服务运行 |
| `WAKE_LOCK` | 全部 | 防止 CPU 休眠 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 全部 | 引导关闭电池优化 |

---

## 使用方式

1. 确保 Android 设备和 PC 连接在**同一 Wi-Fi** 下
2. PC 端启动 mDNS 服务广告（服务类型：`_pcservicewt._tcp.`，端口自定义，需附带 `heart_beat_sign` 属性）
3. 打开 Android 端 SpeedTransfer，顶部显示本机 IP 和端口，自动发现 PC 并建立连接
4. **发送文件给 PC**：点击左下角 `+` 按钮，选择文件 → 添加成功后在列表显示 → PC 浏览器访问 `http://手机IP:8099/` → 查看并下载文件
5. **PC 传文件到手机**：PC 浏览器访问 `http://手机IP:8099/` → 点击上传区域选择或拖拽文件 → 手机聊天界面实时显示上传进度
6. **文字聊天**：在输入框输入文字 → 点击发送按钮（纸飞机图标）→ PC 消息即时推送到手机聊天列表
7. **管理消息**：长按文字气泡可复制、全选或删除该条消息
8. **通知栏**：后台运行时显示"微传 · 传输中"通知，点击返回主界面
