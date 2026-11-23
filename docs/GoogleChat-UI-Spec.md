# Google Chat UI 解析（微信移植基准）

来源：`Google Chat 2026.11.16.1024037795.Release`（base APK），JADX + apktool 资源解析，以及与设备截图的对照。

## 1. 架构

- Google Chat 是 View + Compose 混合：
  - `MergedWorldFragment` 返回 `ComposeView`。
  - `MessageStreamFragment` 使用 `ComposeView` + `LoggableRecyclerView` + `MaterialToolbar`。
  - `MainActivity` 使用 `DrawerLayout` + `NavigationBarView`。
- 不能把 Compose 代码直接移植到微信；只复用可观察的尺寸、颜色、形状和交互。

## 2. 聊天气泡

| 项目 | 值 |
|---|---|
| 大圆角 | 20dp |
| 小圆角 | 4dp |
| 文本内边距 | 12dp × 8dp |
| 消息字号 | 16sp / 24sp |
| 接收方底色 | `neutral95`，light = `#F2F2F2` |
| 发送方底色 | `primary90`，light = `#D3E3FD` |
| 文本色 | `onSurface` |
| 链接色 | `primary` |

连续消息：发送方在右上/右下用 4dp，接收方在左上/左下用 4dp；单条四角 20dp。

## 3. 首页会话列表

| 项目 | 值 |
|---|---|
| 页面底色 | `#F0EDEF`（设备截图采样） |
| 卡片底色 | `#FCF8F9` |
| 卡片圆角 | 16dp |
| 卡片外留白 | 16–20dp |
| 头像 | 40dp，DM 为圆形；空间/群组可用 10dp 圆角 |
| 会话名 | 16sp，medium |
| 预览 | 14sp |
| 时间 | 12sp |
| 列表项状态层 | 16dp 圆角 |

卡片内文字/头像必须离卡片左右边缘约 16dp，不能贴边。

## 4. 顶部与底部

- 顶部：标题 TitleLarge、头像 32dp、搜索栏 56dp。
- 底部：48dp 高、30dp 激活指示器；Google Chat 的 tab 是 Home / DMs / Mentions / Sections / 更多。
- **微信移植范围：微信底部导航和 top bar 保持原样，不照搬。**

## 5. 字体

- Google Chat 使用 Google Sans Text / Google Sans。
- 微信模块不打包字体，统一使用系统 `sans-serif` / `sans-serif-medium`。

## 6. 微信映射

- 聊天气泡：在原生 RecyclerView item 上改背景、padding、字阶，不改消息数据和点击链路。
- 首页：自建 ListView 的每行改为 16dp 圆角卡片，卡片内左右 20dp 留白，头像 40dp。
- 联系人 / 发现 / 我的：共用列表样式，保留原生适配器。
- 动态色：接收气泡 = `surfaceContainerHighest`；发送气泡 = `mix(surface, primary, 0.18)`。
- 状态栏 / 系统导航栏 = `primaryContainer`，随色卡/Monet 变化。

## 7. 明确不做

- 不照搬 Google Chat 的底部导航、top bar、搜索栏。
- 不引入 Google Sans、Google drawable、Compose。
- 不做像素级 1:1。
