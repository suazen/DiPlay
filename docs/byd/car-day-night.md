# 车机日夜模式：为什么不能读 Android `uiMode`

## 现象

比亚迪车机的白天/黑夜切换**不会**修改 Android 的 `Configuration.uiMode`（本车实测
`cmd uimode night` 恒为 `yes`，`settings get system ui_night_mode` 为 `null`）。
所以任何"跟随系统"的实现如果读 `uiMode`，就会永远停在夜晚。

## 官方怎么读

官方高德 `BydAutoInstrumentProxy.getMeterDayNightStatus()` 读的是仪表特性：

```java
// meterThemeDayNightFunId = 0x40C0B02C
device.get(new int[]{0x40C0B02C}, Integer.TYPE).intValue
```

`BydAutoInstrumentProxy$NavTypeListener.onMeterDayNightChanged(int)` 的消费方
（`MeterSmallScreenActivity`、`PhudTbtActivity` 等）统一按：

```java
NightModeGlobal.setNightExtra(value == 2);   // 2 = 夜，其它 = 日
```

本车实测值：`2`（夜）、`1`（日）。

## 为什么 `autoservice` 读不到

```bash
adb shell service call autoservice 5 i32 1007 i32 1086369836    # 0x40C0B02C
# Result: Parcel(00000000 ffffd8e5 ...)  ← 哨兵，不是真实值
```

`getInt` 这一路对该特性返回哨兵 `0xFFFFD8E5`，必须走官方的批量事件 API
`get([I], Class)`，并且要在车机进程里以 shell 身份调用。

## DiPlay 的实现

### 1. shell 侧读取

`shared/.../hud/BydDayNight.kt`：

- `BydDayNightTool`：`app_process` 入口，反射
  `BYDAutoInstrumentDevice.get([0x40C0B02C], Integer.TYPE)`，打印 `daynight=N`；
  先 `Looper.prepareMainLooper()`，`getInstance` 失败时回退到私有构造函数；
- `BydDayNight.refresh(context)`：30 秒节流，通过 `BydAdbShell` 执行
  `CLASSPATH=<apk> app_process /system/bin <tool>`，解析输出；
- `BydDayNight.parse()`：`daynight=2` → 夜；`0/1` → 日；解析失败 → null（保持未知）。

### 2. 接入主题

| 位置 | 行为 |
|---|---|
| `CarPlayHostActivity.pollCarDayNight` | 前台每 60 秒读一次，喂给 `nightModeController.systemChanged(night)` |
| `CarPlayHostActivity.onResume` | `systemNight` 优先取 `BydDayNight.night`，避免回到前台先闪一下夜 |
| `CarPlayHostActivity.onConfigurationChanged` | 同上 |
| `DiPlayActivity.onResume` | 后台线程刷新一次并重绘设置页 |
| `AppAppearance.resolveAppNightNow()` | `systemNight = BydDayNight.night ?: uiMode` |

这样 CarPlay 的"日夜模式 → 跟随系统"、App 界面外观（设为"自动"时）都会跟随车机。

### 3. CarPlay 的"自动外观"坑

即使 DiPlay 把日夜算对了，CarPlay 仍可能不跟随：DiPlay 连接时会在 `/info` 里声明

```
uiAppearanceMode=0, uiAppearanceSetting=0, mapAppearanceMode=0
```

`0` = 自动，iPhone 会按**它自己的光线传感器**决定 CarPlay 外观，DiPlay 的
`setNightMode` 就被忽略了（对应 DiPlay 设置项 #1159）。

处理：只要读到了车机日夜，就不再声明 automatic appearance，让 CarPlay 跟随 DiPlay：

```kotlin
declareAutomaticAppearance = AirPlayPersistence.loadDeclareAutomaticAppearance(this) &&
    BydDayNight.night == null
```

（用户仍可在设置里关闭"告诉 iPhone CarPlay 外观为自动"来达到同样效果。）

## 验证

```bash
adb logcat -s DiPlay-DayNight
# car day/night=day / car day/night=night

# 直接读原始值
adb push probe.jar /data/local/tmp/ && adb shell \
  "CLASSPATH=/data/local/tmp/probe.jar app_process /system/bin Probe"
# daynight=1
```

注意：App 界面自己的"外观"是独立设置（深色 / 浅色 / 自动）。
如果选的是"深色"，界面会一直是深色，这是预期行为；要跟随车机请设为"自动"。
