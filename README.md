# SVC Addon

**SVC Addon**（Mod ID：`svc_addon`）是 [Simple Voice Chat](https://modrepo.de/minecraft/voicechat/) 的服务器端附属模组。它为近距离玩家语音增加两项彼此独立的能力：

- 根据每位说话者麦克风音频的数字响度，连续改变近距离语音的传播距离；
- 用真实的三维声学路径搜索处理墙体、门洞和走廊拐角造成的绕射与衰减。

作者：`datura`  
Minecraft：`26.1.2`  
Java：`25`  
加载器：Fabric、NeoForge  
许可证：MIT

> dBFS 是数字音频相对满刻度的响度，不等于未经校准的现实声压级（SPL）。本模组不会把 dBFS 虚构成分贝声压级。

## 已验证版本 / Verified versions

| 组件 | 版本 |
|---|---:|
| Minecraft | 26.1.2 |
| Java toolchain | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Fabric Loom | 1.17.19 |
| NeoForge | 26.1.2.95 |
| ModDevGradle | 2.0.144 |
| Simple Voice Chat runtime | 2.6.22+26.1.2 |
| Simple Voice Chat API | 2.6.20 |

没有降级到 1.21.x，也没有使用 Mixin。两个发布 JAR 都只包含 `datura.svcaddon` 下的实现，不会捆绑 Simple Voice Chat API、Opus 原生库、Minecraft 或加载器类。

## 安装

### 服务器

1. 安装 Minecraft 26.1.2 对应的 Fabric 或 NeoForge。
2. 安装匹配加载器的 Simple Voice Chat 2.6.22+26.1.2。
3. Fabric 服务器还需安装 Fabric API 0.155.2+26.1.2。
4. 将对应的 SVC Addon JAR 放入服务器 `mods/`。
5. 启动一次服务器；配置会生成在 `config/svc_addon.properties`，玩家偏好会保存到 `config/svc_addon-players.properties`。

### 客户端

核心功能由服务器执行。客户端只需安装兼容的 Simple Voice Chat；**无需安装 SVC Addon**。客户端也安装本模组不会提供额外界面或增益功能。

### Simple Voice Chat UDP

Simple Voice Chat 的语音网络使用 UDP。请正确设置其服务器配置（通常是 `config/voicechat/voicechat-server.properties`），并在宿主机、防火墙、容器和路由器上放行所配置的 UDP 端口；默认常用端口是 `24454/UDP`。只开放 Minecraft 的 `25565/TCP` 不足以让语音工作。

`max_distance` 不能突破 Simple Voice Chat 的 `broadcast_range`。例如 SVC Addon 的默认 `max_distance=48` 需要 `broadcast_range>=48`。如果前者更大，SVC Addon 会在语音服务器启动时警告；广播范围之外的玩家不会收到包，因此也不可能被本模组“扩远”。

## 工作原理

### 动态响度与传播距离

1. 在服务器端监听公开 API 的 `MicrophonePacketEvent`。
2. 每名说话者各自维护一个 `OpusDecoder`，每个麦克风包只解码一次。
3. 在 48 kHz、16-bit PCM 上维护短时 RMS 窗口：
   
   `normalizedRms = sqrt(sum(sample²) / sampleCount) / 32768`
   
   `dbfs = 20 × log10(max(normalizedRms, epsilon))`
4. 应用 Noise Gate、迟滞、Attack 和 Release 包络。
5. 用 Smoothstep 后再应用指数曲线，将平滑 dBFS 连续映射到 `min_distance ... max_distance`。
6. 在 `VoiceDistanceEvent` 中改变本次近距离麦克风语音的距离。

PCM/Opus 内容不会被归一化、放大或重编码。因此小声在近处仍然相对较轻，大声在近处仍然更响；传播距离不是对真实动态差异的替代。当前版本没有额外增益功能。

耳语会同时受到 SVC 原始耳语距离、`whisper_distance_scale` 和 `whisper_max_distance` 的约束，不会因高响度扩展到普通喊话距离。

空帧按静音处理；RMS 窗口和 Attack 抑制单帧峰值；满刻度样本被安全检测并钳制到最大距离。公开 `MicrophonePacket` 不提供音频序列号，因此本模组用可配置的包间隔检测中断并重置 Opus 状态，而不是声称可以精确识别每一个丢包。

### 声学路径追踪

路径追踪只处理 `EntitySoundPacketEvent` 中来源为 `SOURCE_PROXIMITY` 的实体近距离语音。群组语音、插件音频、静态音频、非位置音频、旁观者特殊语音和其他来源不会进入该逻辑；服务器 Tick 快照还会显式绕过说话者或听者为旁观者的路径处理。

- 主线程先按时间预算构造一个最小直线体素快照。
- Supercover DDA 声线检查所有边/角同时穿越的相邻体素，禁止利用对角缝隙穿墙。
- 直线受阻时，再增量构造带边界的三维搜索快照。
- 专用单线程工作器执行 6 邻接 A*；固体/关闭门不可通过，空气、空碰撞空间和带 `OPEN=true` 的打开门可通过。
- 路径长度加上拐弯与狭窄通道代价，得到有效声学距离。
- 对每个“说话者—听者—维度—量化位置—世界版本”缓存结果。
- 完全无路时默认取消该听者的包；若 `no_path_cancels_audio=false`，则按 `blocked_attenuation_factor` 发送极弱的阻隔声。
- 有绕行路径时，不修改原始 Opus；而是只为该听者替换包的有效范围，使客户端原有距离衰减近似对应声学路径长度。

这是真实路径搜索，不是“射线碰墙就静音”。

### 线程与高负载降级

- 语音线程只做一次每说话者 Opus 解码、无锁位置/缓存查询和有界队列提交；不读取 Minecraft 世界，也不执行 A*。
- Minecraft 世界碰撞状态只在服务器主线程读取，并按 `path_snapshot_budget_ms` 跨 Tick 分片；每个微批最多 64 个体素。
- A* 只在一个有界专用工作线程上运行，并受距离、节点、时间和队列上限约束。
- 移动、换维度、方块交互/破坏/放置、缓存 TTL 或配置重载会使缓存失效。
- 缓存每个有向玩家对只保留最新位置版本，避免移动造成无界增长。
- 缓存未准备好、区块未加载、快照过大、超时、节点上限或队列满时采用 fail-open：当前包保持 SVC 原行为，服务器不会等待语音线程。

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/svcaddon pathtracing on` | 普通玩家 | 自己听到的近距离语音启用路径追踪 |
| `/svcaddon pathtracing off` | 普通玩家 | 自己听到的近距离语音关闭路径追踪 |
| `/svcaddon pathtracing default` | 普通玩家 | 恢复服务器默认 |
| `/svcaddon pathtracing status` | 普通玩家 | 查看个人、全局和实际状态 |
| `/svcaddon pathtracing global on` | 管理员（gamemaster） | 全局开启并持久保存 |
| `/svcaddon pathtracing global off` | 管理员（gamemaster） | 全局关闭，优先级最高 |
| `/svcaddon pathtracing global status` | 管理员（gamemaster） | 查看全局状态和缓存数量 |
| `/svcaddon reload` | 管理员（gamemaster） | 原子重载配置和玩家偏好，重建工作队列 |

玩家设置按 UUID 写入 `svc_addon-players.properties`，重新登录后仍然有效。若 `allow_player_path_tracing_toggle=false`，普通玩家不能写入新的 on/off 选择。

## 配置

项目附带 [默认配置](default-config/svc_addon.properties)。非法、非有限或相互冲突的值会被修正，并在日志中给出明确警告。

| 配置项 | 默认值 | 合法范围/作用 |
|---|---:|---|
| `dynamic_range_enabled` | true | 独立控制动态传播距离 |
| `noise_gate_dbfs` | -55 | -96..-2 dBFS；背景噪声门限 |
| `noise_gate_hysteresis_db` | 3 | 0..20 dB；防止门限抖动 |
| `quiet_dbfs` | -42 | 必须高于 gate；曲线低端 |
| `loud_dbfs` | -12 | 必须高于 quiet，最大 0；曲线高端 |
| `min_distance` | 4 | 0.5..256 blocks |
| `max_distance` | 48 | min..512 blocks，同时受 SVC `broadcast_range` 限制 |
| `curve_exponent` | 1.4 | 0.1..8；Smoothstep 后的指数 |
| `rms_window_ms` | 100 | 20..1000 ms |
| `attack_ms` | 80 | 1..2000 ms |
| `release_ms` | 350 | 1..5000 ms |
| `decoder_reset_gap_ms` | 500 | 100..10000 ms；包间隔超过此值重置 Opus |
| `whisper_distance_scale` | 0.35 | 0.01..1 |
| `whisper_max_distance` | 10 | 0.5..max_distance |
| `path_tracing_enabled` | true | 全局独立开关 |
| `allow_player_path_tracing_toggle` | true | 是否允许个人 on/off |
| `path_cache_ttl_ms` | 300 | 50..5000 ms |
| `path_max_nodes` | 4096 | 64..100000 |
| `path_search_timeout_ms` | 8 | 1..100 ms；A* 墙钟上限 |
| `path_position_quantization` | 1 | 1..8 blocks；位置缓存量化 |
| `path_max_distance` | 64 | max_distance..512；直距与 A* 估计总代价上限 |
| `path_search_margin` | 6 | 1..32；水平绕行搜索边界 |
| `path_vertical_margin` | 3 | 1..16；垂直搜索边界 |
| `path_max_snapshot_blocks` | 65536 | 1024..2000000 |
| `path_requests_per_tick` | 2 | 1..16；每 Tick 最多启动的新玩家对 |
| `path_queue_capacity` | 256 | 16..4096 |
| `path_snapshot_budget_ms` | 1.5 | 0.1..20 ms；主线程增量快照预算 |
| `path_turn_penalty` | 0.35 | 0..10；每次转向附加代价 |
| `path_narrow_penalty` | 0.08 | 0..10；狭窄体素附加代价 |
| `no_path_cancels_audio` | true | true=无路取消；false=极弱阻隔声 |
| `blocked_attenuation_factor` | 0.05 | 0.001..0.25；无路且不取消时的近似剩余线性幅度 |
| `debug_enabled` | false | 输出速率受限的音频/路径诊断 |
| `debug_log_interval_ms` | 1000 | 100..60000 ms；每个玩家或玩家对的日志间隔 |

## 构建

要求 JDK 25：

```bash
./gradlew clean assembleRelease
```

构建会运行公共模块测试并生成：

- `fabric/build/libs/svc-addon-fabric-1.0.0+mc26.1.2.jar`
- `neoforge/build/libs/svc-addon-neoforge-1.0.0+mc26.1.2.jar`

开发运行：

```bash
./gradlew :fabric:runServer
./gradlew :neoforge:runServer
```

部分 ModDevGradle 环境在首次 `runServer` 下载资源时还会寻找 Java 21 辅助 toolchain；这只影响开发资源下载任务，发布编译和游戏运行仍使用 Java 25。

## 已知限制

1. 公开 API 可以安全替换实体语音包的范围，但不能在保留实体身份、说话图标和兼容性的同时把方向改到“最后一个拐点”。因此当前方向定位仍来自说话者实体；路径长度、衰减和可听范围是真实计算的。
2. 新玩家对或缓存失效后的首批包会 fail-open，直到主线程快照和异步 A* 完成。默认 TTL 为 300 ms。
3. 自动活塞、红石或其他模组造成且加载器事件未观察到的方块变化，最迟会由 TTL 刷新纠正。玩家破坏、放置和门/方块交互会立即使维度缓存失效。
4. 每个方块被保守地抽象为一个体素。非空碰撞的半砖、栅栏等默认阻挡整个体素；打开门/栅栏门和空碰撞空间可通过。
5. 区块未加载、快照超过上限、搜索超时/节点上限和队列饱和都 fail-open，而不是阻塞语音或服务器 Tick。
6. 当前版本不提供客户端 GUI、音频增益、limiter 或拐点方向重定位。

完整的自动测试结果、双加载器启动记录和仍需真人麦克风执行的验收矩阵见 [TESTING.md](TESTING.md)。

---

## English quick guide

SVC Addon is a **server-side Simple Voice Chat addon** for Minecraft 26.1.2. It decodes each speaker's Opus microphone stream once, measures short-window PCM RMS in dBFS, smooths it with a gate/hysteresis/attack/release envelope, and continuously maps it to proximity range. It never normalizes or boosts the original voice audio.

For proximity entity packets only, the server builds immutable collision snapshots on the main thread, performs a supercover direct-ray check, then runs bounded 3D 6-neighbor A* off-thread when obstructed. Per speaker/listener results are cached and converted to an equivalent packet range for attenuation. Group calls, plugin audio, static/non-positional sound, recording, mute behavior and unrelated channels are not modified.

Install SVC Addon and Simple Voice Chat on the server; Fabric also needs Fabric API. Clients need only compatible Simple Voice Chat. Open the configured Simple Voice Chat UDP port. Keep `broadcast_range >= max_distance`. dBFS is digital full-scale loudness, **not calibrated real-world SPL**.

Build with JDK 25 using `./gradlew clean assembleRelease`. See the command/config tables and known limitations above.
