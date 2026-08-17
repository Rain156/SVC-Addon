# SVC Addon 测试记录 / Test Report

测试日期：2026-08-17  
目标：Minecraft 26.1.2、Java 25、Fabric、NeoForge、Simple Voice Chat 2.6.22+26.1.2

## 已实际执行

### 自动测试

命令：

```bash
./gradlew :common:test --rerun-tasks
```

结果：**24 tests, 24 passed**（最终干净构建会再次执行）。

| 测试范围 | 覆盖内容 |
|---|---|
| PCM 数学 | RMS、-6.0206 dBFS 半满刻度、静音、null、零样本、削波计数、平方和无溢出 |
| 时间窗口 | 48 kHz 短 RMS 窗口和旧帧淘汰 |
| 包络 | Noise Gate、3 dB 迟滞、Attack 快于 Release |
| 距离曲线 | gate 关闭、min/max 钳制、连续 Smoothstep+指数映射 |
| 配置 | 非法/非有限值修正、布尔别名、默认配置完整渲染 |
| 声学路径 | 直线路径、完整墙无出口、墙上门洞、L 形走廊、防对角穿墙 |
| 缓存/调度 | TTL、移动键替换、玩家/维度失效、跨 Tick 增量快照、世界变更失效、旁观者状态快照 |
| 无路策略 | 默认取消、可选 5% 极弱阻隔衰减 |

### 严格人工复审

当前环境没有可调用的 GrillMe Skill，因此没有虚构调用记录；改为执行同等范围的手工架构与代码复审。复审后实际修正了：

- 在取消原包前先成功构建替换包，避免构建异常导致静音；
- 为 Opus 会话创建/关闭增加读写生命周期锁和错误日志限速；
- 让路径缓存每个玩家对只保留最新键，并用 generation 阻止重载前的异步结果回填；
- 将世界碰撞快照改为按主线程预算跨 Tick 增量采集；
- 将直线 supercover 检查置于完整 A* 快照之前；
- 显式排除 SVC 的旁观者特殊语音；
- 为无路但不取消的配置实现真实极弱衰减，而不是无修改放行；
- 对三个模块启用 `-Xlint:all -Werror`，并审计发布 JAR 未捆绑运行时依赖。

### 完整构建

命令：

```bash
./gradlew clean assembleRelease
```

结果：

- Fabric 二进制 JAR：成功
- NeoForge 二进制 JAR：成功
- 公共测试：成功
- Java `-Xlint:all -Werror`：三个模块均通过
- JAR 内容审计：未包含 `de/maxhenkel`、`net/minecraft`、`net/fabricmc` 或 `net/neoforged` 的依赖类
- 包名审计：所有项目 Java 包均以 `datura` 为根

### Fabric 专用服务器冒烟测试

实际运行 `./gradlew :fabric:runServer`：

- Fabric Loader 0.19.3、Fabric API 0.155.2+26.1.2、SVC 2.6.22+26.1.2 和 SVC Addon 1.0.0 均被加载；
- SVC 报告加载并初始化 `svc_addon` 插件；
- Minecraft 服务器到达 `Done`；
- SVC Addon 报告 `broadcast_range=48.00`；
- Simple Voice Chat UDP 服务在 24454 启动；
- 控制台实际执行并成功返回：
  - `svcaddon pathtracing global status`
  - `svcaddon reload`
  - `svcaddon pathtracing global off`
  - `svcaddon pathtracing global on`
- `stop` 后正常保存并退出。

### NeoForge 专用服务器冒烟测试

实际运行 `./gradlew :neoforge:runServer`：

- NeoForge 26.1.2.95、SVC 2.6.22+26.1.2 和 SVC Addon 1.0.0 均被加载；
- SVC 报告加载并初始化 `svc_addon` 插件；
- Minecraft 服务器到达 `Done`；
- SVC Addon 报告 `broadcast_range=48.00`；
- Simple Voice Chat UDP 服务在 24454 启动。

测试容器禁止访问部分 Mojang 身份验证/版本检查主机，因此启动日志含外部网络失败警告；这不影响两个服务器到达 `Done`、插件注册或本地 UDP 语音服务启动。

## 尚未在本容器执行的真人游戏内验收

自动化环境没有两套带真实麦克风的 Minecraft 客户端。以下项目不能诚实地标记为“已通过”，应在发布服务器/局域网中按表复测：

| # | 场景 | 操作 | 预期 |
|---:|---|---|---|
| 1 | 空地响度 | 说话者依次轻声、正常说话、喊叫 | 近处保留原始音量差异，传播距离连续增加 |
| 2 | 拉远距离 | 听者缓慢远离持续说话者 | 无三档突变，越过当前动态范围后听不见 |
| 3 | 背景噪声 | 不说话，仅保留麦克风底噪 | gate 不产生远距离广播 |
| 4 | 完整墙 | 两人隔着无出口厚墙 | 缓存完成后默认取消近距离语音 |
| 5 | 打开门 | 墙附近打开门，两人错开门洞 | 声音经门洞绕行且比直线更弱 |
| 6 | L 形走廊 | 两人位于拐角两侧 | 可沿走廊传播，路径更长并衰减 |
| 7 | 移动说话 | 双方移动中持续说话 | 缓存按量化位置/TTL 更新，无明显卡 Tick |
| 8 | 多人同时说话 | 三名以上玩家同时发言 | 每名说话者独立解码/包络，互不串扰 |
| 9 | 个人开关 | 听者执行 on/off/default/status | 自己收到的近距离语音立即按设置变化并持久化 |
| 10 | 群组语音 | 建立 SVC 群组通话 | 不受动态距离和路径追踪影响 |
| 11 | 耳语 | 高声使用 SVC 耳语 | 仍受原始耳语、比例和上限三重约束 |
| 12 | Fabric 客户端/服 | 两端使用兼容 SVC | 正常连接、发言、停止和退出 |
| 13 | NeoForge 客户端/服 | 两端使用兼容 SVC | 正常连接、发言、停止和退出 |
| 14 | 服务器端单装附属 | 服务端装 SVC Addon；客户端只装 SVC | 核心动态距离、路径和命令正常，无自定义客户端协议要求 |

建议测试时打开 `debug_enabled=true`，观察速率受限的 `raw_dbfs`、`smoothed_dbfs`、动态距离、直距、声学路径长度、缓存命中、节点数和搜索耗时；完成后关闭，避免日志噪声。
