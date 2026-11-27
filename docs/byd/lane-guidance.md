# CarPlay 车道引导（iAP2 `0x5204`）

## 消息与订阅

- iAP2 消息：`0x5204 LaneGuidanceInfoUpdate`（iPhone → 配件）；
- iPhone 只有在配件声明支持导航订阅后才会发，DiPlay 的 `Iap2IdentificationClient` / `Iap2Endpoint`
  已经声明；
- 解析器：`shared/.../iap2/message/Iap2LaneGuidance.kt`；
- 分发：`CarPlayGlance.onFrame()` 把 `0x5204` 单独处理，交给 `laneListener`（不影响路线/音乐状态）。

## 字段

`0x5204` 的外层 id `2` 是一条车道，内部 TLV：

| 字段 | 含义 |
|---|---|
| `0` index | 车道序号（从 0 开始，按 index 排序） |
| `1` status | `2` = 推荐车道（本车实测）；其它值 = 普通/不可用 |
| `2` angle | 可行驶方向：有符号角度，`0` 直行、正数向右、负数向左 |
| `3` angle-highlight | 推荐方向（可选），例如推荐车道 `angle=0`、`highlightAngle=90` 表示"该车道建议右转" |

真实样例（测试里保存的、Apple 地图三车道、第三车道推荐）：

```
id 2 lane0: index 0, status 0, angle -90
id 2 lane1: index 1, status 0, angle 0
id 2 lane2: index 2, status 2, angle 0, angle-highlight 90
```

解析结果：3 条车道，第 3 条 `recommended=true`。

## DiPlay 的渲染

`ClusterTurnCardView.drawBydLanes()`：

- 位置：**分割线正上方**，按车道数等距居中；
- 每条车道一个箭头（`ic_turn_card_straight` 旋转）；
- 角度：推荐车道优先用 `highlightAngle`（缺省用 `angle`），其它车道用 `angle`；
- 颜色：推荐车道蓝色（`#0A84FF`），其余黑色（夜间白色）；
- 有车道时，上方"箭头 + 标题 + 路名"主块整体上移 `13%` 面板高度，
  分割线和 `17 公里 / 13:43 到` 信息行位置不变；
- `0x5204` 空 payload → 车道行消失。

数据链路：

```
iAP2 0x5204
  └─ CarPlayGlance.onFrame → laneListener
       └─ CarPlayHostActivity.clusterLanes
            └─ ClusterTurnCardPresentation.setTurnCardLanes()
                 └─ ClusterTurnCardView.drawBydLanes()
```

## 验证

```bash
adb logcat -s DiPlay-Lane
# 0x5204 lanes=3 [i=0 st=0 a=-90 h=null; i=1 st=0 a=0 h=null; i=2 st=2 a=0 h=90] raw=...
```

车道信息只在临近路口时出现（官方卡也是这个行为），没有这行日志说明手机当前没发；
Apple 地图和部分第三方导航在 CarPlay 下都会发。
