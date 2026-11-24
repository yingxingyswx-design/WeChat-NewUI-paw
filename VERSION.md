# WeChat-NewUI v1.2.1

- versionCode：`4`
- versionName：`1.2.1`
- Release tag：`v.1.2.1`
- APK：`wechat-glass-1.2.1.apk`
- 作用域：仅 `com.tencent.mm`（微信）

## 这一版新增

- **两个新开关**（依赖 Material Design）：
  - 「开启圆形头像」：关闭时头像用微信原生风格的圆角矩形；
  - 「媒体和表情包使用消息气泡」：控制图片/表情消息是否套 M3 气泡。
- **GUI 重排**：运行状态 → 操作 → 液态玻璃 → 聊天页液态玻璃背景壁纸 →
  Material Design（含上面两个开关 + 每条消息发送时间）→ 动态色彩 → 冲突检测。
- **GUI 细节**：顶部两行红色提示；透明状态栏；按钮加高；冲突检测里的原模块
  改称「液态玻璃导航栏模块（io.github.liuran001.mmliquidglass）」。
- **实时生效**：改圆形头像 / 媒体气泡开关会立即应用到首页与聊天页。

## 修复

- **GIF 表情包卡顿**：动态 drawable 不再被 `RoundedRectClipDrawable` 包装，恢复流畅播放。
- **Home 头像永久圆形**：自建列表头像现在跟随「开启圆形头像」开关。
- 聊天页液态玻璃面板每次打开立即重绘（不再只有首次是玻璃），去掉白色叠加。
- 媒体/表情消息气泡按面积 +25% 外扩、居中、保留小尾巴；贴纸圆角改为 drawable 裁剪。
- 每条消息时间胶囊开关真正生效（关闭即移除）。
- 自建会话列表未读/已读实时刷新（后台每 2.5s 重读会话表）。

## 已知问题

- 聊天页液态玻璃**必须先在模块 App 内手动选择壁纸**，否则聊天页没有玻璃效果。
- 模块按微信 8.0.79 的类名/资源 id 适配，微信升级后可能失效。
- 自建会话列表为实验功能，默认走微信原生列表。

## 安装

```powershell
adb install -r .\wechat-glass-1.2.1.apk
adb shell am force-stop --user 0 com.tencent.mm
adb shell am start --user 0 -n com.tencent.mm/.ui.LauncherUI
```

## 特别鸣谢

- [liuran001/WeChat-LiquidGlass](https://github.com/liuran001/WeChat-LiquidGlass)（MIT）：
  底部导航液态玻璃、水滴拖拽与弹簧动效。完整许可见
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
