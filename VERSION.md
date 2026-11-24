# WeChat-NewUI v1.2-pre

- versionCode：`3`
- versionName：`1.2-pre`
- Release tag：`3-1.2-pre`（LSPosed Store 要求 `VersionCode-VersionName`）
- APK：`wechat-glass-1.2-pre.apk`
- 作用域：仅 `com.tencent.mm`（微信）
- 发布形态：**Pre-Release**（预览版，非稳定版）

## 这一版新增

- **聊天页液态玻璃面板**：表情面板与「+」面板不再是被媒体盖住的纯色块；面板玻璃在每次
  打开时立即重绘（修掉"只有首次打开才是玻璃"），并去掉那层白色叠加。
- **消息时间胶囊**：GUI 可开关「每条消息时间显示」（默认关闭，标注功耗）；关闭时立即移除胶囊
  并还原行内边距，不再"开关无效"。
- **媒体/表情消息气泡**：气泡按**面积 +25%**外扩、居中于媒体、保留小尾巴；表情包圆角改为
  按 drawable 裁剪，避免与气泡外扩互相裁剪；只作用于聊天页消息行。
- **自建会话列表未读/已读**：会话未读数改为**实时刷新**（后台每 2.5s 重读 `rconversation`，
  仅更新角标/预览/时间，不重跑整表联系人查询）。

## 之前版本已有（v1.1）

- M3 Expressive 设计底座（Monet 动态色彩、30 type style、10 级圆角、expressive 弹簧）。
- App 顶部运行状态（Root / LSPosed 注入 / 原模块冲突）。
- LiquidGlass 底部导航（MIT 移植，水滴选中态、拖拽弹簧）。
- 冲突检测：只移除原模块的 `com.tencent.mm` 作用域，保留 QQ。
- 软件 Canvas 路径自动跳过 AGSL 高光，修 `RuntimeShader` 崩溃。

## 已知问题

- 模块按微信 8.0.79 的类名/资源 id 适配，微信升级后可能失效。
- 底栏在微信 `BlurView` 软件采样路径下会自动退回较平材质（有意降级）。
- 自建会话列表为实验功能，默认走微信原生列表。

## 安装

```powershell
adb install -r .\wechat-glass-1.2-pre.apk
adb shell am force-stop --user 0 com.tencent.mm
adb shell am start --user 0 -n com.tencent.mm/.ui.LauncherUI
```

## 特别鸣谢

- [liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)（MIT）：
  底部导航液态玻璃、水滴拖拽与弹簧动效。完整许可见
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
