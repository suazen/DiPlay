# BYD 车机调研笔记

这组文档记录了 DiPlay 在比亚迪车机（DiLink 5.0 / Android 12）上做仪表导航适配时的实测结论：
官方仪表简易导航卡是怎么来的、仪表投影屏长什么样、车机日夜模式怎么读、CarPlay 车道数据怎么用，
以及 DiPlay 现在的浮窗转向卡是怎么实现的。

## 测试车型

| 项 | 值 |
|---|---|
| 固件指纹 | `OCEAN/Di100VCP_IVI/Di100VCP_IVI:12/SKQ1.230128.001/eng.build.20260830.020849:user/release-keys` |
| 系统 | Android 12（DiLink 5.0） |
| 仪表屏 | 1920×720，投影层 `shared_fission_bg_XDJAScreenProjection_0/1` |
| 调试方式 | 无线 adb（`adb connect <车机IP>:5555`） |

## 文档索引

| 文档 | 内容 |
|---|---|
| [official-simple-navigation.md](official-simple-navigation.md) | 官方高德如何向仪表投送"简易导航"：广播通道、仪表特性通道、自绘投屏通道，以及本车为什么走 BydSR + SR SDK |
| [instrument-features.md](instrument-features.md) | 仪表特性的读写命令、ID 表、回读哨兵值、`auto_container` 模式切换 |
| [cluster-displays.md](cluster-displays.md) | 仪表投影 Display、共享层、官方简易导航卡的实测几何 |
| [car-day-night.md](car-day-night.md) | 车机日夜模式：为什么 Android `uiMode` 不可用、仪表特性 `0x40C0B02C`、DiPlay 的读法，以及 CarPlay "自动外观"的坑 |
| [lane-guidance.md](lane-guidance.md) | CarPlay iAP2 `0x5204` 车道引导：字段、取值、样例与渲染 |
| [floating-turn-card.md](floating-turn-card.md) | DiPlay 仪表右侧浮窗转向卡：Presentation 方案、生命周期、样式与几何 |

## 一句话结论

- 本车的仪表简易导航卡由 **BydSR 绑定官方高德的 SR SDK** 渲染，第三方 App 无法冒充；
- 仪表特性（`0x43F0xxxx`）能写能读，但 BydSR 不读这些特性，所以写特性不会改变仪表画面；
- DiPlay 的可行路线是**自己在仪表投影屏上画一张透明浮窗卡**（不占整屏），数据取 CarPlay 的 iAP2 导航/车道；
- 车机日夜**不会**同步到 Android `uiMode`，需要读仪表特性 `0x40C0B02C`（2 = 夜）。
