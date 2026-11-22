# WeChat-NewUI v0.3.322（Pre-release）

- 发布：2026-11-22
- 基线提交：`cb2002f`（important: 表情/加号面板液态玻璃）
- 签名：debug（`-DebugSigning`，SHA-1 `52:18:07:E2:46:84:9E:C2:38:82:47:64:EE:DD:DB:6E:5B:5E:89:5B`）
- APK：`wechat-glass-1.0.apk`
- 源码包：`WeChat-NewUI-v0.3.322-source.zip`

## 本版重点

### 表情面板 / 加号面板的液态玻璃

面板玻璃不再是"在面板上叠一层"，而是**画在面板自己的背景上**（`PanelGlassDrawable`），
不向微信的 footer 插入任何 View，因此不参与它的测量与高度计算。

效果栈与顶部胶囊同一套（KernelSU / miuix-blur 的移植）：

```text
采样壁纸 RenderNode
  → saturation 1.5
  → 高斯模糊（面板 18dp，胶囊 4dp）
  → AGSL 圆角透镜（24dp 边缘折射，Android 13+）
  → 40% 面层 0x66F2F2F7 / 0x662C2C2E
  → 1dp 高光描边
```

采样源是 `ChattingContent`（聊天壁纸 `ChattingImageBGView` 所在容器），
采样区域取"容器底部 h 像素、水平居中"，**不依赖面板上报的屏幕坐标**——
微信通过 `translationY` 与父级 canvas 平移滑动 footer，`getLocationOnScreen`
在中途会报出 `y=2381`（2400 高屏幕）这种不可用值。

### 其它

- 输入框在面板展开时底部圆角改为直角，和面板连成一张卡片。
- 补 hook `SmileyPanelImpl.setPanelBackgroundColor / setTabBackgroundColor /
  setTabBackgroundResource`，防止微信展开面板时重新刷不透明色。
- 删除了失效的 `ensureUnionLiquid()`（它把玻璃加到 ContentFrameLayout 第 0 个孩子，
  永远不可见，且每 250ms 把面板背景置空）。
- 修复构建脚本在本机 JDK 17 下被 javac 的 deprecation 提示中断的问题。

## 运行时调参（无需重编译）

```powershell
# 面层白度 0-255（默认 102 = 40%）
adb shell su -c "setprop debug.wxglass.panel.wash 60"
# 模糊半径 dp（默认 18）
adb shell su -c "setprop debug.wxglass.panel.blur 26"
# 排查：面板涂洋红 / 画未模糊采样
adb shell su -c "setprop debug.wxglass.panel.probe 1"
adb shell su -c "setprop debug.wxglass.panel.raw 1"
```

改完关闭再打开面板生效。

## 安装

```powershell
adb install -r wechat-glass-1.0.apk
# 工作空间（user 10）必须单独装，否则跑的是旧模块：
adb push wechat-glass-1.0.apk /data/local/tmp/wg.apk
adb shell su -c "pm install -r --user 10 /data/local/tmp/wg.apk"
adb shell su -c "am force-stop --user 10 com.tencent.mm"
```

## 已知问题

- 微信每次展开面板会重建面板实例，旧实例仍留在树里；本版用"遍历所有实例 +
  布局监听 + 600ms 轮询"兜底，但仍以首次可见那一刻的样式化为准。
- 大面积面板默认偏白（面层直接沿用参考工程的 40%），可用上面的 `wash` 调低。
- 状态栏为透明（Android 15 + targetSdk 35 下 `setStatusBarColor` 会被微信覆盖）。
