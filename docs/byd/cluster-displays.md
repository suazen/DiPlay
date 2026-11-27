# 仪表投影 Display 与官方卡几何

## 本车的 Display

`adb shell dumpsys display` / `dumpsys window displays` 里与本项目相关的：

| Display | 名称 | 尺寸 | 说明 |
|---|---|---|---|
| 2 | `fission_bg_XDJAScreenProjection` | 1920×720 | 仪表 SR 主画面（BydSR 渲染），官方简易导航卡叠在右侧 |
| 3 | `shared_fission_bg_XDJAScreenProjection_0` | 1920×720 | 第三方可用的 presentation display（owner `com.byd.containerservice`） |
| 4 | `shared_fission_bg_XDJAScreenProjection_1` | 1920×720 | 同上的第二个共享层 |

- 官方卡实际画在 **display 2** 的右侧；
- DiPlay 的 `ClusterMapPresentation` / `ClusterTurnCardPresentation` 通过
  `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)` 找到 display 3/4 并 `show()`；
- DiPlay 的浮窗窗口落在 **display 3**（`dumpsys window windows` 里
  `Window{... u0 com.shihab.diplay}`，surface 名 `bydAdd-com.shihab.diplay`）。

抓图：

```bash
adb shell screencap -d 2 /sdcard/d2.png   # SR 画面 + 官方卡（可看到右侧内容）
adb shell screencap -d 3 /sdcard/d3.png   # 共享层，通常是空的
```

## 官方简易导航卡的实测几何（1920×720）

从 display 2 的截图量出来的布局：

| 元素 | 位置 |
|---|---|
| 大箭头 | x ≈ 1430–1515，y ≈ 245–330（约 85×85） |
| 标题（"现在"） | x ≈ 1565，基线 y ≈ 310，约 55 px |
| 路名（"无名道路"） | 基线 y ≈ 340，约 24 px，灰色 |
| 细分割线 | y ≈ 355，x 1430 → 1814 |
| 信息行（"17 公里　13:43 到"） | 基线 y ≈ 396，约 28 px |

换算成面板百分比：

- 内容块左/右：`74.5%` / `94.5%`
- 垂直基准：`45%`
- 字号：标题 `7.6%`、路名 `3.4%`、信息 `3.9%`
- 箭头：`11.8%` 高，顶部 `45% - 11%`

DiPlay 的 `ClusterTurnCardView.Style.BYD_SIMPLE` 就是按这组数值画的；
有车道时主块整体上移 `13%`，分割线和信息行保持不动。

## 对 DiPlay 的意义

- 仪表投影层是**透明合成**的：铺满整屏的透明 `Presentation` 不会挡住 SR 画面，
  只有画了卡片的地方可见；
- 窗口必须加 `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`，否则会吃掉仪表/中控的触摸；
- 官方卡在右侧，所以浮窗也放在同一列，避免和仪表自己的表盘/菜单重叠；
- `ClusterMapPresentation.findDisplay()` 选的是 presentation display（3/4），不是 display 2；
  在 DiPlay 的浮窗实现里这是同一套选屏逻辑。
