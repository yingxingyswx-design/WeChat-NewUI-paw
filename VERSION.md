# WeChat-NewUI v1.3.3

- versionCode：`10`
- versionName：`1.3.3.release`（开发版为 `1.3.4.dev`）
- Release tag：`10-1.3.3`
- APK：`wechat-glass-1.3.3.apk`
- 源码：`wechat-glass-1.3.3-source.zip`
- 作用域：仅 `com.tencent.mm`（微信）

## 1.3.3 新增

- **聊天页玻璃自动跟随微信壁纸**：不再需要手动选图。模块直接读取微信自己渲染的
  `ChattingImageBGView`（`BitmapDrawable`，如 520×960）作为玻璃底图；纯色背景时
  取 `ChattingContent` 的 `ColorDrawable` 颜色。零请求、零文件读取。
- **GUI**：壁纸选择按钮改为「已停用」（保留以备容错），红色「必须手动选择壁纸」提示移除。
- **GUI**：顶部显示版本号；「开启余额卡片显示」受 Material Design 门控（MD 关闭时禁用）。
- **构建**：`publish.ps1` 自动备份——Gradle 与 `build.ps1` 两条路径在编译成功后都会
  commit 到备份仓库；版本号在 `ModuleVersion.java` 与 `build.gradle.kts` 双处校验。

## 1.3.3 修复

- **通讯录/发现页概率性出现微信原生 ActionBar**：微信会在切换后再挂上新实例；
  现在 pre-draw 每帧重新解析 `ActionBarContainer` 并强制隐藏，600ms tick 兜底。
- **发现页条目不全**：条目数量变化即重建，重开的入口立刻出现。
- **消息时间标签**：从气泡上方移到气泡下方，不再遮挡群成员昵称。
- **群聊昵称被套气泡**：气泡容器若包含发送者昵称，改为只包内容视图
  （仍有少量场景未覆盖，后续继续收紧）。
- 移除"取相册最新图片当壁纸"的错误兜底（会把随便一张新照片当成壁纸）。

## 1.3.0 / 1.3.1 内容

- M3E 化的「通讯录 / 发现 / 我」三页；首页扫一扫按钮；首页长按转发原生会话菜单；
  首页状态栏透明；系统账号去重；四个 tab 统一背景。
- 微信余额卡片（默认关闭、开启前红色二次确认）：只被动采样已渲染的金额。
- 移除探测期遗留的 `hookFreGriest`（`hookAllMethods` + `setResult`，从未生效）。

## 已知问题（下一版）

- 从「服务」等原生页面返回后，首页可能掉回微信原生列表。
- 群聊仍有少量昵称被套气泡。
