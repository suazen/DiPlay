# 官方仪表"简易导航"是怎么投送的

对象：`com.byd.launchermap`（官方高德，V3.16.881，apktool 反编译）。

"简易导航"是仪表上那种只显示 **转向箭头 + 距离 + 路名/剩余时间** 的极简 TBT，不投地图。
官方高德用了三条并存、按车型/环境择一的通道。

## 通道 A1：高德标准广播（有 `com.byd.amapservice` 时）

`NaviService.sendNaviInfoBroadcast()` 每 **1500 ms** 心跳重发一次，变化时立即发：

```java
action  = "AUTONAVI_STANDARD_BROADCAST_SEND"
package = "com.byd.amapservice"        // 未安装时用 "com.example.amapservice"
KEY_TYPE = 0x2711                      // 导航信息
EXTRA_IS_FOREGROUND, IS_BYD_MAP=true
```

| Extra | 含义 |
|---|---|
| `NEXT_ROAD_NAME` / `NEXT_NEXT_ROAD_NAME` | 下一段 / 下下段路名 |
| `NEW_ICON` / `NEXT_NEXT_TURN_ICON` | 当前 / 下下个转向图标（`adapterEasyNavi` 映射） |
| `SEG_REMAIN_DIS` / `SEG_REMAIN_DIS_AUTO` | 本段剩余距离（原始值 / 格式化文本） |
| `ROUTE_REMAIN_DIS` / `ROUTE_REMAIN_TIME` / `ETA_TEXT` | 全程剩余距离、剩余时间、到达时间 |
| `TRAFFIC_LIGHT_NUM`、`ROUNG_ABOUT_NUM`、`EXIT_*` | 红绿灯数、环岛出口、高速出口信息 |

接收方 `com.byd.amapservice` 自己把 TBT 画到仪表。

## 通道 A2：直接写仪表私有特性（无 `com.byd.amapservice` 时）

`NaviService.sendNavigateInfoToCAN()` 先检查 `com.byd.amapservice` 是否安装，装了就直接 return
（避免双驱动）；没装才走 `BydAutoInstrumentProxy.setEventValue(...)`：

| 事件 ID | BYD 常量 | 内容 |
|---|---|---|
| `0x43E0003A` | `INSTRUMENT_SEND_NAVI_STATUS_SET` | 导航状态（2=开始，1=结束） |
| `0x43F01010` | `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` | 简易导航：转向图标 |
| `0x43F01018` | 本段剩余距离 | 距下一动作的距离 |
| `0x43FA1008` | `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` | 下一段路名（UTF-16LE `byte[]`） |
| `0x43F02028` / `0x43F02010/18` | 全程剩余距离 / 剩余时、分 | 路线信息 |
| `0x43F09010/18/20` | ETA 天/时/分 | 到达时间 |
| `0x4C10A018` | `INSTRUMENT_NAVI_TYPE_SET` | 仪表导航显示类型 |
| `0x4C10E015` | 副屏导航画面状态 | |

图标和距离是**批量原子写**：一次 `setEventValue(int[], int[])` 同时写
`{距离, 图标, commit 事件}`，仪表收到 commit 才一起刷新，避免"图标换了距离没换"。

## 通道 B：自绘投屏（全屏/卡片模式才用）

- 前台服务 `DashBroadService` 绑定 BYD 投屏 SDK `IContentProjectionManager`；
- `ContentType`：`MAP_VIEW(4)`、`MINI_MAP_CARD(5)`、`TBT_CARD(8)`、`PIP_CARD(12)`、`PHUD_*`；
- 找屏：`DisplayManager.getDisplays()` 里按 `fission_` 前缀、`叠加视图 #1`、`displayId==1/2` 等挑；
- 全屏/小地图 → `PresentationView`（`extends Presentation`）；TBT/半屏卡 → `MeterTbtActivity` / `MeterSmallScreenActivity`；
- **仪表下发 `onNavTypeChanged(2)`（简易导航）时，App 会 `scheduleDestroyPresentation()` 销毁自绘投屏**，只保留通道 A 的数据推送。

模式值：`1=关闭`、`2=简易导航`、`3/4=全屏/其他`。

## 本车为什么第三方喂不进去

本车（OCEAN / Di100VCP_IVI）实测：

1. **没有 `com.byd.amapservice`**：只有 `com.example.amapservice`，而它是比亚迪的仪表/投屏服务
   （`AutoContainerService`、`ProjectionService`、`SomeipHudNaviInfoService`），**没有**
   `AUTONAVI_STANDARD_BROADCAST_SEND` 的 receiver。
2. **`com.byd.clusterdebug` 不是特性写入器**：`BroadcastReceiverCAN` 把 `normal` 字符串当原始
   CAN 测试帧写 `0xAA00020F`，仪表不认。
3. **仪表那块的真正来源是 BydSR + 官方高德的 SR SDK**：

   ```
   Intent().setAction("action.com.autosdk.sr.SDService")
       .setComponent(ComponentName("com.byd.launchermap", "com.autosdk.sr.service.SDService"))
   bindService(...)
   ```

   - `com.autosdk.sr.ISDAidlInterface`：`registerSDCallBack(ISDRouteCallBack)`、`sendRequestTBT()` …
   - `ISDRouteCallBack`：`onCruiseContent`、`onCrusieRoadName`、`onDirBitmap`、`onDriveSpeed`、`onGpsState` …
   - 官方 App 用 `SDSRUtils.setTbtInfo()` 填 `SdTBTViewContent{distance, nextRouteName, etaTime, arrivalTime, trafficLight…}` 推给 BydSR。

   BydSR **显式绑定 `com.byd.launchermap` 这个包**，第三方 App 无法冒充。

   所以 `am broadcast` 没人收、写 `0x43F0xxxx` 特性画面不变（能写能读，但 BydSR 不读）。

## 验证命令

```bash
# 仪表模式
adb shell service call autoservice 5 i32 1007 i32 1276157976    # 0x4C10A018
# 官方高德的发送日志
adb logcat | grep -E "sendNaviInfoBroadcast|sendNaviState|onNavTypeChanged|seedSimpleNavType"
# 手工注入广播（在本车不会被接收，仅用于对比其他车型）
adb shell am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND -p com.example.amapservice ...
```

日志锚点：`PlatformApiImpl: INSTRUMENT_GUIDE_INFO_SIMPLE_SET: 1139806224`（= `0x43F01010`）。

## 结论

- 简易导航在官方体系里主要是**数据投送**（广播或仪表特性），全屏/卡片模式才自绘投屏；
- 本车的仪表卡由 BydSR 渲染，第三方既不能冒充 SDService，也不能靠写特性改变它；
- DiPlay 的可行路线是**自己在仪表投影屏上画卡**，见 [floating-turn-card.md](floating-turn-card.md)。
