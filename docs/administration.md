# 管理指令与配置

当前全部 `/svcaddon` 子指令要求原版权限等级 2。`targets` 支持玩家名、UUID 与 `@a`、`@s`、`@p` 等原版选择器。控制台使用玩家名或 `@a`，因为控制台没有 `@s` 实体。

| 指令 | 行为 |
| --- | --- |
| `/svcaddon status` | 显示语音服务状态、动态距离开关与上下限 |
| `/svcaddon level <player>` | 显示当前平滑数字音量；超过 1 秒没有音频时显示 -96 dBFS |
| `/svcaddon transmit <targets> <0..1024>` | 固定传播距离；0 为禁言，包括群组语音 |
| `/svcaddon transmit <targets> default` | 恢复服务端动态／固定距离规则 |
| `/svcaddon receive <targets> <0..1024>` | 限制接收距离；0 为禁听，含群组和插件声音 |
| `/svcaddon receive <targets> default` | 移除个人接收限制 |
| `/svcaddon broadcast <targets> <true/false>` | 启用／关闭广播；仅接管非群组、非旁观者输入 |
| `/svcaddon effect <targets> <none/radio/robot>` | 原声、带通饱和电台音色、周期调制机械音色 |
| `/svcaddon reset <targets>` | 删除所选玩家全部附属语音覆盖，恢复默认 |
| `/svcaddon reload` | 重载服务端配置；非法配置保留原有效设置 |
| `/svcaddon group list` | 显示当前群组名称和 UUID |
| `/svcaddon group create <name> <isolated/normal/open>` | 创建指定模式群组；带空格的名称需要双引号 |
| `/svcaddon group join <targets> <group UUID>` | 将已连接语音的目标加入指定组；任何目标未连接时拒绝整个操作 |
| `/svcaddon group leave <targets>` | 将目标移出语音组 |
| `/svcaddon group remove <group UUID>` | 删除群组；由 Simple Voice Chat 判断能否删除 |

Simple Voice Chat 的 `normal` 群组只向组内发送声音，同时可以听近场；`open` 还向近场发送组员语音；`isolated` 只收发组内语音。附属广播不会把组内声音带到组外，也不向隔离组成员发送。退出群组后，仍启用的广播设置重新生效。

群组对象与成员关系由 Simple Voice Chat 管理。`persistent` 表示空组不立即消失，不保证服务器重启后恢复。跨重启群组保存不是当前实现的一部分。个人传播、接收、广播与效果设置则按世界持久化。

## 默认配置

文件：`config/svcaddon/server.properties`，UTF-8。首次启动自动生成带注释模板。支持部分键，其余沿用默认值；未知键、非有限数、反向范围、错误布尔值和未知 schema 会被拒绝。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `schema` | 1 | 配置格式版本 |
| `range.dynamic` | true | false 时普通传播使用 `range.maximum` |
| `range.minimum` | 4 | 最小传播格数，0～maximum |
| `range.maximum` | 48 | 最大传播格数，0.1～1024 |
| `range.crouch` | 6 | 蹲下时上限，0～maximum |
| `range.whisper` | 6 | Simple Voice Chat 耳语时上限，0～maximum |
| `level.quiet-dbfs` | -50 | 对应最小距离，-96～0 |
| `level.loud-dbfs` | -10 | 对应最大距离，须大于 quiet |
| `level.attack-ms` | 60 | 音量上升平滑时间常数，1～10000 ms |
| `level.release-ms` | 400 | 音量下降平滑时间常数，1～10000 ms |
| `broadcast.cross-dimension` | false | 广播是否允许跨维度 |
| `threshold.enabled` | false | 是否执行阈值命令 |
| `threshold.dbfs` | -8 | 平滑后触发音量，-96～0 |
| `threshold.hold-ms` | 500 | 连续超标时间，20～60000 ms |
| `threshold.cooldown-ms` | 5000 | 两次命中的最小间隔，1000～3600000 ms |
| `threshold.command` | 空 | 管理员预先配置的单行命令，最长 2048 字符 |

音量与距离在 quiet、loud 之间线性对应；超出后截断。固定的个人传播距离覆盖动态范围，随后再应用蹲下／耳语上限。禁言高于广播。个人接收距离与发送范围同时限制可听性，达到边界即不可听；有限个人接收范围不接收跨维度广播。

## 分贝触发示例

```properties
threshold.enabled=true
threshold.dbfs=-8
threshold.hold-ms=500
threshold.cooldown-ms=5000
threshold.command=execute as {uuid} run say 请降低麦克风音量
```

命令在服务端主线程以控制台权限执行。`{uuid}` 和 `{player}` 只替换为发言玩家的身份信息，原始音频不会成为命令文本。若需要其他执行者或目标，使用原版 `execute as`、`at` 和目标选择器。超过阈值只触发一次，低于阈值 3 dB 后重新待命，仍受冷却限制。

该检测反映麦克风增益、降噪与输入信号的共同结果。用于游戏规则前应使用 `/svcaddon level` 在实际客户端校准阈值。
