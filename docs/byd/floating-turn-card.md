# 仪表右侧浮窗转向卡（DiPlay 实现）

## 目标

导航开启时，在仪表**右侧、与官方简易导航同一区域**显示一张小卡片，
显示箭头、距离、路名、剩余路程/到达时间（临近路口再加车道），
**不占用整个仪表画面**，导航结束后消失。

## 方案：透明、不可触摸的 `Presentation`

`common/.../ClusterTurnCardPresentation.kt`

- 铺满仪表投影屏（`ClusterMapPresentation.findDisplay()` 选出的 presentation display）；
- 窗口背景透明，只添加一个 `ClusterTurnCardView`；
- 窗口 flags：`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`；
- 视觉上只有卡片，仪表的 SR 画面/表盘完全不受影响，触摸也会穿透。

## 生命周期（避免闪烁）

`CarPlayHostActivity.applyFloatingTurnCard()`：

- 总开关（`BydOutputSettings.enabled`）关闭 → 销毁；
- 找不到投影屏 → 销毁；
- 第一次有引导（`clusterTurnGuidance != null`）时创建并 `show()`；
- 之后**窗口常驻**，没有引导只把卡片视图设为 `GONE`；
- 仅在 Activity 销毁时清理。

历史坑：

1. 早期版本在 `guidance == null` 时 `dismiss()`、下一帧再 `show()`，路口之间引导短暂为空 → 窗口反复增删 → 闪；
2. `ensureClusterPresentation()` 每个 tick（1 秒）都会在"CarPlay 仪表地图关闭"时调用
   `dismissClusterPresentation()`，早期把浮窗的销毁也写进了这个函数 → **每秒销毁重建一次**。
   现在浮窗生命周期与 CarPlay 仪表地图完全解耦。

同时，总开关打开时 `ClusterMapPresentation` 不再画同一张转向卡，避免两张卡重叠/交替。

## 样式

`ClusterTurnCardView.Style.BYD_SIMPLE`（浮窗专用；CarPlay 地图上的玻璃卡保持原样）：

- 透明背景，无圆角深色底；
- 大号箭头：黑色（夜间白），无小方块底；
- 标题：`现在` / `200 米` / `1.2 公里`；
- 路名：灰色小字；
- 细分割线；
- 信息行：`17 公里　　13:43 到`（全程剩余距离 + 到达时间）；
- 有车道时：车道箭头行贴在分割线上方，推荐车道蓝色；主块整体上移。

几何与官方卡的实测对齐见 [cluster-displays.md](cluster-displays.md)。

## 数据来源

| 数据 | 来源 |
|---|---|
| 下一动作（图标/距离/路名） | `BydNavigationOutputs.setTurnOverlayListener`（iAP2 `0x5201/0x5202`） |
| 全程剩余距离/时间/到达时间 | `BydClusterFrame` → `ClusterTurnGuidance`（`arrivalEpochSeconds`、`routeRemainingMeters/Seconds`） |
| 车道 | `CarPlayGlance.laneListener`（iAP2 `0x5204`），见 [lane-guidance.md](lane-guidance.md) |
| 日/夜 | `CarPlayHostActivity.darkMode`（跟随车机日夜，见 [car-day-night.md](car-day-night.md)） |

## 涉及文件

| 文件 | 作用 |
|---|---|
| `common/.../ClusterTurnCardPresentation.kt` | 浮窗 Presentation（新） |
| `common/.../ClusterTurnCardView.kt` | 卡片绘制、`BYD_SIMPLE` 样式、车道行 |
| `common/.../CarPlayHostActivity.kt` | 创建/更新/销毁浮窗，数据接入 |
| `shared/.../hud/ClusterTurnGuidance.kt` | 卡片数据模型 |
| `shared/.../hud/BydClusterFrame.kt` | 从 iAP2 动作构造卡片数据 |
| `shared/.../hud/BydDayNight.kt` | 车机日夜读取（新） |
