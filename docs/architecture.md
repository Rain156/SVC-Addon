# 架构

## 模块边界

```text
api                         Java 21；音频处理、空间快照与频道公共类型
 ↑
core                        Java 21；音量、DSP、范围规则、配置、阈值与频道仲裁
 ↑
voicechat                   Java 21；唯一依赖 Simple Voice Chat API 的桥接模块
 ↑
platforms/fabric             Fabric 生命周期、元数据与打包
platforms/neoforge           NeoForge 生命周期、插件发现与打包
platforms/minecraft          两个加载器共用的游戏指令和主线程快照
 ├─ src/main                跨版本游戏逻辑
 ├─ src/1.21.1              Java 21／Mojang mappings 差异
 └─ src/26.2                Java 25／未混淆命名差异
```

公共 API 不依赖 Minecraft、加载器或 Simple Voice Chat。`AudioEffect` 为一条语音流持有的状态处理器，输入是 48 kHz 单声道 PCM16，可用于后续自定义效果实现。它目前是早期 API，尚未提供第三方运行时发现与注册服务，Alpha 阶段不承诺二进制稳定。

`Frequency` 与 `RadioArbiter` 是实际可测试的设备业务基础；本阶段没有注册设备物品，不能将这两个类型理解为游戏里已经能使用对讲机。

平台构建将 `api`、`core`、`voicechat` 的类合并进最终 JAR，只保留一个模组。Simple Voice Chat API 使用 `compileOnly`，由安装的 Simple Voice Chat 提供。开发运行配置也加入三个共享模块的类路径。

Fabric 1.21.1 的 Loom 重映射会移除依赖 JAR 内的嵌套模组声明，因此开发运行额外引用上游官方的 `voicechat-api:fabric-stub` 元数据构件。它只用于开发环境恢复 `voicechat_api` 的版本标识，不进入附属发行 JAR，也不替代真实语音实现。

## 语音流

1. Simple Voice Chat 检查发言权限后发出麦克风事件。附属先检查已有取消状态和服务端禁言。
2. 每位说话者独占 Opus 解码器、可选编码器、效果状态、音量平滑器与阈值状态。每个语音帧最多解码一次。
3. 计算 dBFS，经 attack／release 平滑后用于距离和阈值。选择 `none` 时直接复用原始 Opus 数据；需要变声时重新编码一次。
4. 普通声音通过 `VoiceDistanceEvent` 调整传播距离，然后继续交给 Simple Voice Chat 路由。它保留群组、旁观者附身、音量、权限与距离分发逻辑。
5. 显式广播使用稳定的独立声道 UUID 和非空间音频。它只接管非群组、非旁观者的输入，过滤禁听、隔离群组、维度和个人接收范围。发送依然经过 Simple Voice Chat 的监听权限检查。
6. 发往客户端的实体、位置和群组／插件音频统一经过接收策略，支持禁听和接收范围限制。

当音频为空或输入间隔超过 1 秒时，重置音频编码状态与平滑器，保留阈值冷却。异常语音帧丢弃，日志按 5 秒限频。dBFS 不用于宣称真实环境声压。

## 线程与生命周期

游戏线程每 tick 发布一次不可变玩家快照，只包含 UUID、维度、眼部位置、蹲下与旁观者状态。业务层和附属的音频处理代码不读取活的世界、实体或区块。Simple Voice Chat 的连接和发送访问限制在其官方 API 中。

音频回调只执行有界帧处理和内存规则。阈值命中进入最多 128 条的队列；每 tick 至多执行 8 条命令，丢弃离线玩家和超过 5 秒的旧事件。重载后不执行旧配置产生的事件。原始音频不会写入磁盘，也不会上传到外部服务。

`RuntimeHost` 仅拥有当前本地或独立服务器的会话。它兼容 Simple Voice Chat 比游戏适配先启动的情况。退出世界、停止服务器、玩家断开和语音服务停止时释放本会话资源，避免静态状态跨世界残留。

配置变更在游戏线程完成；玩家设置以临时文件与原子替换方式保存，成功之后才发布新的不可变映射。批量选择器修改作为一次写入处理。配置解析失败保留原文件与当前内存配置，第一次启动出现非法配置则明确拒绝启动。

## 版本策略

`gradle/targets/<minecraft>.properties` 是依赖版本的唯一来源。`-PgameVersion` 只接受明确验证的两个目标，`-Ploader` 只接受 `fabric`、`neoforge`、`all`。每个目标拥有独立输出目录。版本差异使用源目录隔离，不使用运行时反射探测 Minecraft 方法名。

1.21.1 使用 Loom remap 与官方 Mojang mappings；26.2 使用未混淆 Loom。两者使用相同的 NeoForge ModDevGradle 构建规则和独立 NeoForge 版本。CI 分别构建四个组合，失败组合不会上传构建产物。

## 扩展路径

通讯设备接入核心频道模型，再由版本适配注册物品、方块、数据组件与网络包。客户端包只表达操作意图；距离、库存持有、绑定目标和发射权限由服务端核实。

录音以不可变资源 ID 引用独立文件。后续 HTTP 识别由独立有界执行器承载，提供超时与取消。区域与方块声学使用游戏线程生成的快照和有预算的后台计算。这些约束已设计，但对应游戏功能尚未实现。

## 上游依据

- [Simple Voice Chat API 入门](https://modrepo.de/minecraft/voicechat/api/getting_started)
- [VoiceDistanceEvent](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/VoiceDistanceEvent.html)
- [Simple Voice Chat 26.2 源码](https://github.com/henkelmax/simple-voice-chat/tree/26.2)
- [Fabric 26.2 开发变化](https://www.fabricmc.net/2026/06/15/262.html)
- [NeoForge ModDevGradle](https://github.com/neoforged/ModDevGradle)
