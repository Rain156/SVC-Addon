# SVC Addon

Simple Voice Chat 附属模组。为地图作者与服务器提供可配置的声音传播、通讯设备和语音管理能力。

当前版本：`0.1.0-alpha.1`，从零重建后的第一阶段开发版本。已接入动态传播距离、蹲下与耳语距离、禁言／禁听、全体广播、变声预设、分贝触发指令和群组管理。设备物品、方块、录音、区域音域与头像界面尚在后续开发计划中。完整需求和验收条件见 [需求清单](docs/requirements.md)。

## 支持目标

| Minecraft | Fabric | NeoForge | 游戏运行环境 |
| --- | --- | --- | --- |
| 1.21.1 | 独立构建 | 独立构建 | Java 21 |
| 26.2 | 独立构建 | 独立构建 | Java 25 |

每个组合生成单独的 JAR。固定依赖版本见 `gradle/targets/`，实际验证记录见 [测试说明](docs/testing.md)。编译通过与多人游戏内验收分别记录，Alpha 产物不代表整份功能表已完成。

依赖 Simple Voice Chat（提供 `voicechat_api >= 2.6.20`）；Fabric 还需要 Fabric API。当前阶段的逻辑在服务端运行，客户端需要匹配游戏版本的 Simple Voice Chat。客户端也安装本附属可获得中文指令反馈；未安装时使用英文后备文本。未来设备内容加入后会要求两端同时安装。

## 构建

安装 JDK 21 与 JDK 25，用 JDK 25 启动 Gradle。Gradle 自动发现不到 JDK 时，可设置 `JAVA21_HOME` 和 `JAVA25_HOME`。开发环境中的 Simple Voice Chat 运行依赖已配置。

```powershell
# Windows。参数加引号，避免 PowerShell 拆分带点的版本号。
.\gradlew.bat '-PgameVersion=1.21.1' build
.\gradlew.bat '-PgameVersion=26.2' build

# 只构建一个加载器
.\gradlew.bat '-PgameVersion=26.2' '-Ploader=neoforge' build

# 不下载 Minecraft 的业务与桥接测试
.\gradlew.bat -PcoreOnly=true check
```

Linux/macOS 将 `gradlew.bat` 换成 `./gradlew`。最终模组位于 `platforms/<loader>/build/<minecraft>/libs/`，选择不含 `sources`、`dev` 的 JAR。可用 `:fabric:runClient`、`:fabric:runServer`、`:neoforge:runClient`、`:neoforge:runServer` 启动开发实例。

需要将缓存留在工作区时，使用 [Windows 构建脚本](scripts/build.ps1)，例如：

```powershell
.\scripts\build.ps1 -GameVersion 1.21.1 -Loader all
```

## 使用

默认根据麦克风数字音量，将传播距离在 4～48 格之间调整；蹲下或耳语时上限为 6 格。音量单位是 **dBFS**，表示数字音频相对满幅的强度，不是经过声压计校准的现实分贝。语音阈值指令默认关闭，配置位于 `config/svcaddon/server.properties`。

所有管理指令需要原版权限等级 2。玩家设置保存到当前世界的 `data/svcaddon/players.properties`，支持目标选择器。完整参数、群组行为和配置说明见 [管理指南](docs/administration.md)。

```mcfunction
/svcaddon status
/svcaddon transmit @a 0
/svcaddon transmit @a default
/svcaddon receive @a 32
/svcaddon broadcast @s true
/svcaddon effect @s radio
/svcaddon group create "工程队" isolated
/svcaddon level @s
/svcaddon reload
```

## 开发约定

业务规则放在纯 Java 核心，Simple Voice Chat 访问集中在桥接模块，Minecraft 版本差异集中在平台目录。详见 [架构](docs/architecture.md) 和 [贡献约定](CONTRIBUTING.md)。

项目原创代码使用 MIT 协议。第三方美术必须记录来源、作者、许可证与修改情况；目前未引入第三方美术，见 [资源登记](docs/assets.md)。Minecraft、Simple Voice Chat 及加载器遵循各自许可，不随本项目源码重新授权。
