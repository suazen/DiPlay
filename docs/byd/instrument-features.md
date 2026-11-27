# 仪表特性与 `autoservice` 读写

比亚迪把仪表数据暴露成一组"特性 ID"，`autoservice`（`android.gui.BYDAutoServer`）提供
`getInt` / `setInt`。shell 可以直接调用，不需要 BYD 签名。

## 通用命令

```bash
# 写（设备类型 1007 = 仪表）
adb shell service call autoservice 6 i32 1007 i32 <feature> i32 <value>

# 读
adb shell service call autoservice 5 i32 1007 i32 <feature>
```

返回值形如 `Result: Parcel(00000000 00000002 '........')`，第二个字是值。

## 特性表（本车实测）

| 特性 | 十进制 | 作用 | 实测 |
|---|---|---|---|
| `0x43F01010` `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` | 1139806224 | 简易导航转向图标 | ✅ 可写可读 |
| `0x43F01018` | 1139806232 | 本段剩余距离 | ⚠️ 可写，回读恒为 `0xFFFFD8E5` |
| `0x43FA1008` `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` | 1140461576 | 下一段路名（`byte[]`，UTF-16LE） | 需 `app_process` 写 buffer |
| `0x43E0003A` `INSTRUMENT_SEND_NAVI_STATUS_SET` | 1138753594 | 导航状态（2=开始，1=结束） | ✅ 可写可读 |
| `0x43F02028` `NAVI_TRIP_INFO_MILEAGE_SET` | 1139810344 | 全程剩余距离 | 可写 |
| `0x43F02010/18` | — | 剩余时间 时/分 | 可写 |
| `0x43F09010/18/20` | — | 到达时间 天/时/分 | 可写 |
| `0x4C10A018` `INSTRUMENT_NAVI_TYPE_SET` | 1276157976 | 仪表导航显示类型 | ✅ 可写（读回 4） |
| `0x4C10E015` | 1276174357 | 副屏导航画面状态 | 可写 |
| `0x40C0B02C` | 1086369836 | 仪表主题日夜 | ❌ `getInt` 读不到（见下） |

有些字段读回是哨兵 `0xFFFFD8E5`（≈ -10011），表示"该字段不支持 `getInt` 回读"，
不代表写入失败；写完后仪表侧仍可能生效。

## `getInt` 读不到的字段：`get([I], Class)`

官方 App 的 `BydAutoInstrumentProxy.getMeterDayNightStatus()` 用的是设备 API：

```java
device.get(new int[]{0x40C0B02C}, Integer.TYPE).intValue
```

同样的 API 在 shell 下用 `app_process` 反射即可（见 [car-day-night.md](car-day-night.md)）：

```java
Class<?> cls = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice");
Object device = cls.getMethod("getInstance", Context.class).invoke(null, context);
Object event = cls.getMethod("get", int[].class, Class.class)
    .invoke(device, new int[]{0x40C0B02C}, Integer.TYPE);
```

## 仪表模式切换（`auto_container`）

`service list` 里服务名是小写的 **`auto_container`**（不是 `AutoContainer`）：

```bash
adb shell 'service call auto_container 2 i32 1000 i32 39 s16 ""'   # 简易导航
adb shell 'service call auto_container 2 i32 1000 i32 16 s16 ""'   # 全屏投影
adb shell 'service call auto_container 2 i32 1000 i32 17 s16 ""'   # 半屏投影
adb shell 'service call auto_container 2 i32 1000 i32 18 s16 ""'   # 恢复
```

注意：`s16 ""` 必须和整条命令一起交给 `adb shell`（用单引号包住），否则本地 shell 会把空串吃掉。

DiPlay 的 `BydAmapAdapter` 对两种 binder 名都做了兼容：

```kotlin
"service call auto_container 2 i32 1000 i32 $info s16 \"\" || " +
    "service call AutoContainer 2 i32 1000 i32 $info s16 \"\""
```

## 调试注意

- 写特性会**真的改变仪表状态**，调试完记得恢复
  （实测恢复值：`0x4C10A018=1`、`0x43E0003A=1`、图标/距离=0）；
- 本车的仪表简易导航卡**不读**这些特性（由 BydSR 的 SR SDK 驱动），
  所以写 `0x43F01010` 能读回成功但画面不变，不要用它来验证 DiPlay 的卡片。
