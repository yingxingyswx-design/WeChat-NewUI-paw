# WeChat-NewUI v1.1

- versionCode：`2`
- versionName：`1.1`
- Release tag：`2-1.1`（LSPosed Store 要求 `VersionCode-VersionName`）
- APK：`wechat-glass-1.1.apk`
- 作用域：仅 `com.tencent.mm`（微信）

## 这版有什么

- **M3 Expressive 设计底座**：动态色彩（系统 Monet，失败回退微信绿）、30 个 type style、
  10 级圆角、expressive 弹簧 token。
- **App 顶部运行状态**：显示 Root 是否授权、LSPosed 是否已注入微信、原模块是否仍勾选微信。
- **LiquidGlass 底部导航**：从 [WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)
  （MIT）移植的悬浮液态玻璃底栏、水滴选中态、拖拽与弹簧动效。
- **冲突检测**：检测到原版 `WeChat-LiquidGlass` 勾选微信时，只移除它的
  `com.tencent.mm` 作用域，**保留 QQ**，不全局停用。
- **崩溃修复**：微信 `BlurView` 用软件 Canvas 绘制 DecorView 时，AGSL 高光会抛
  `Software rendering doesn't support RuntimeShader`；现在软件路径自动跳过高光。

## 已知问题

- 消息列表/气泡的 M3E 重绘尚未完成，当前版本先发布设计底座、状态卡和底栏整合。
- 底栏在微信 `BlurView` 的软件采样路径下会自动退回较平的材质，这是有意的安全降级。
- 模块按微信 8.0.79 的类名适配，微信升级后可能失效。

## 安装

```powershell
adb install -r .\wechat-glass-1.1.apk
adb shell am force-stop --user 0 com.tencent.mm
adb shell am start --user 0 -n com.tencent.mm/.ui.LauncherUI
```

## 特别鸣谢

- [liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)（MIT）：
  底部导航液态玻璃、水滴拖拽与弹簧动效。完整许可见
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
