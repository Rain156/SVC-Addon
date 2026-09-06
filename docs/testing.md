# 验证与发布条件

## 自动验证

```powershell
.\gradlew.bat -PcoreOnly=true check
.\gradlew.bat '-PgameVersion=1.21.1' build
.\gradlew.bat '-PgameVersion=26.2' build
python scripts/verify_artifacts.py 1.21.1 fabric
python scripts/verify_artifacts.py 1.21.1 neoforge
python scripts/verify_artifacts.py 26.2 fabric
python scripts/verify_artifacts.py 26.2 neoforge
```

`core` 测试覆盖 RMS 与数字满幅、平滑的帧分割不变性、独立效果状态、距离边界、禁言与禁听优先级、跨维度、旁观者、阈值保持／重置／冷却、非法配置、持久化失败回滚，以及频道租约并发竞争。

`voicechat` 测试使用真实 API 类型与模拟传输，验证事件取消、解码次数、编码器释放、变声、广播与群组边界、输出端限制、配置热重载和启动顺序。这些测试不验证真实 Opus 原生库、Minecraft 运行时或实际听感。

产物检查验证最终 JAR 中的加载器入口、共享模块、语言文件、默认配置、版本约束与 Java 字节码版本；拒绝混入 Minecraft、Simple Voice Chat 或测试依赖的类。

## 本次重建记录

日期：2026-09-06。版本 `0.1.0-alpha.1`，Windows x64。1.21.1 与 26.2 的完整构建均通过；每个游戏版本分别运行 21 个核心测试和 11 个桥接测试，32 个用例均通过，无跳过。

| 目标 | 编译与自动测试 | JAR 检查 | 启动与指令 | 双玩家语音验收 |
| --- | --- | --- | --- | --- |
| Fabric 1.21.1 | 通过 | 通过 | 插件及事件注册成功；停止于 EULA 提示 | 未执行 |
| NeoForge 1.21.1 | 通过 | 通过 | 服务端、语音服务与管理指令通过 | 未执行 |
| Fabric 26.2 | 通过 | 通过 | 插件及事件注册成功；停止于 EULA 提示 | 未执行 |
| NeoForge 26.2 | 通过 | 通过 | 服务端、语音服务与管理指令通过 | 未执行 |

首轮 NeoForge 依赖下载因系统盘空间不足失败，后续验证使用工作区 `.work/gradle` 与 `.work/tmp`。这些目录不提交到仓库。

NeoForge 两版本的测试服务器绑定本机环回地址，实际执行 `status`、`group create`、`group list`、`reload` 和 `group remove`，检查返回结果后通过 `stop` 正常保存并关闭。语音服务状态均为 `true`。此次检查修复了开发运行缺少 Fabric API 标识构件，以及群组反馈未把 UUID 转为文本的问题。

Fabric 已验证加载器依赖解析、附属入口与 Simple Voice Chat 插件初始化，未代替所有者接受 EULA。NeoForge 开发启动环境自动跳过该检查。启动测试没有连接玩家，不构成音质、联网认证或多人可玩性验证。

## 游戏内验收清单

以下步骤需在每个目标组合上使用两名玩家和真实 Simple Voice Chat 连接执行。录制验证结果时记录 Minecraft、加载器、SVC、附属版本以及服务端配置。

1. 独立服务端与客户端启动成功，`/svcaddon status` 报告语音服务可用；非管理员不能执行管理指令。
2. 安静说话与大声说话在配置下限／上限附近得到不同可听距离，距离变化平滑；蹲下与耳语降低范围。
3. 管理员设置传播 0 后，近场、群组和广播均静音；恢复 `default` 后正常。接收 0 同样覆盖组内和插件音频。
4. 广播在当前维度内只播放一次；跨维度开关有效；隔离组不收广播，私密群组发言不外泄。旁观者保持 Simple Voice Chat 原本规则。
5. `normal`、`open`、`isolated` 的近场行为符合 SVC 定义。群组选择器操作遇到未连接语音的玩家时有明确错误。
6. `radio`、`robot` 只改变被选择玩家；开关效果后无重复音频；持续讲话、短暂停顿和丢包后的表现可接受。
7. 阈值保持时间拒绝短峰值，持续超标只触发一次；回落后仍遵守冷却；命令在主线程执行。关闭或重载规则后不执行旧队列。
8. 重启世界恢复玩家覆盖；切换另一个世界时无玩家设置、解码器或广播状态泄漏；错误配置不被静默覆盖。
9. 多人同时说话至少 15 分钟，观察 tick 时间、CPU、内存、队列和连接重建。将可接受的负载指标与硬件一起记录。

首次运行普通独立服务器需要服务器所有者接受 Minecraft EULA，并配置网络端口。自动构建不会代替用户接受许可。通过编译或 JAR 检查不能替代上述游戏内验收。

## 发布

CI 只上传开发构建产物与失败诊断，不自动发布到 Modrinth、CurseForge 或 GitHub Releases。稳定版本需要四组合构建、产物检查和上述语音验收全部通过，并附上明确的已实现功能清单。
