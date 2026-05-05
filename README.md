# 音书 - 听障人士沟通辅助 App

## 项目概述

一个专为听障老人设计的 Android 沟通辅助 App，支持离线语音转文字。

- **核心功能**：实时语音转文字显示 + 常用语快速展示
- **适老化设计**：大字体、高对比度背景、极简操作
- **离线运行**：使用 Vosk 引擎，无需联网

## 项目结构

```
app/src/main/
├── assets/
│   └── vosk-model-small-cn-0.22/   # 内置语音模型（自动复制）
├── java/com/myyinshu/
│   ├── app/
│   │   └── MainActivity.kt          # 入口，权限处理，主题配置
│   ├── voice/
│   │   ├── VoiceRecognitionEngine.kt # 语音引擎接口（可切换引擎）
│   │   ├── VoskVoiceEngine.kt       # Vosk 引擎实现 + 模型自动复制
│   │   └── VoiceEngineFactory.kt    # 引擎工厂（预留讯飞接口）
│   ├── data/
│   │   ├── SettingsRepository.kt    # 设置存储（字号/主题/背景）
│   │   └── CommonPhrasesRepository.kt # 常用语 CRUD
│   └── ui/
│       ├── screens/
│       │   ├── AppNavigation.kt      # 导航路由
│       │   ├── CommunicationScreen.kt # 主沟通页
│       │   ├── SettingsScreen.kt     # 设置页
│       │   └── CommonPhrasesScreen.kt # 常用语管理页
│       └── theme/
│           ├── Color.kt              # 4 套颜色方案
│           └── Type.kt               # 动态字号
└── res/                              # 资源文件
```

## 构建步骤

### 1. 环境要求

- Android Studio（推荐 Ladybug 2024.2 或更新版本）
- JDK 17
- Android SDK 35 (compileSdk), minSdk 24

### 2. 语音模型

语音模型 `vosk-model-small-cn-0.22` 已内置到 `app/src/main/assets/` 目录中，
首次启动 App 时会自动复制到设备存储并加载，无需手动操作。

如需更换模型：
- 替换 `app/src/main/assets/vosk-model-small-cn-0.22/` 下的文件
- 或删除设备上 App 数据后重新打开，会重新复制

### 3. 构建运行

```bash
# 在项目根目录
./gradlew assembleDebug
# 或直接在 Android Studio 中点击 Run
```

## 当前实现状态

- [x] 项目基础结构（Gradle + Compose）
- [x] 语音识别抽象层（Vosk 实现，预留讯飞）
- [x] 语音模型内置 + 首次启动自动复制
- [x] 主沟通页（大字显示 + 聆听按钮 + 常用语）
- [x] 设置页（字号/背景/主题/语言）
- [x] 常用语管理页（增删改排序）
- [x] 4 套颜色方案（白/黑/黄/深蓝）
- [x] 竖屏手机 + Pad 适配
- [ ] 模型加载进度条
- [ ] 讯飞引擎接入
- [ ] 震动反馈

## 切换为讯飞引擎

在 `VoiceEngineFactory.kt` 中，已有预留接口。接入讯飞需要：

1. 在讯飞开放平台注册账号，下载离线 SDK
2. 将讯飞 SDK 放入 `app/libs/`
3. 创建 `XunfeiVoiceEngine.kt` 实现 `VoiceRecognitionEngine` 接口
4. 修改 `VoiceEngineFactory.createEngine()` 返回讯飞引擎实例
