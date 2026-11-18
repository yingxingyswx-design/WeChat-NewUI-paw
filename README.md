# WeChat Glass Bar

微信 8.0.79 聊天页的 LSPosed UI 模块：把顶部导航栏改成 Telegram 截图里的三段悬浮玻璃岛，把底部输入栏改成悬浮毛玻璃条。

只 Hook `com.tencent.mm` 的 `ChattingUI` 生命周期，只改 View 的背景、边距和绘制层；不碰消息、通讯、账号、支付、网络相关类。

## 产物

- 可安装 APK：[out/wechat-glass-1.0.apk](out/wechat-glass-1.0.apk)
- 模块源码：[app/src/main/java/com/wechatglass](app/src/main/java/com/wechatglass)
- 分析文档：[analysis/聊天界面与毛玻璃悬浮栏分析.md](analysis/聊天界面与毛玻璃悬浮栏分析.md)

## 已实现

- **真正的 edge-to-edge 聊天页**：
  - `ActionBarContainer` 从 `ChattingUILayout` 的纵向布局里移出，改挂到内容 `FrameLayout` 顶部，后添加，所以画在聊天内容之上。
  - `ChatFooter` 留在 `ChattingScrollLayout` 里，但 `topMargin = -footerHeight`、`bottomMargin = 0`，净占高为 0；`ChattingContent` 因此拿到全屏高度，消息列表从输入栏下面滚过。
  - `RecyclerView` 顶部 padding = ActionBar 高度 + 6dp，底部 padding = ChatFooter 高度 + 14dp，并 `clipToPadding=false`。
  - 不再使用假壁纸层；顶部玻璃直接采样真正的聊天内容。
- 顶部三段悬浮玻璃：
  - 返回按钮：圆形玻璃
  - 标题：圆角玻璃胶囊
  - 右侧菜单：圆形/胶囊玻璃
- 底部输入栏：
  - `ChatFooter` 背景按结构逐层清空（从 EditText 往上找祖先），不依赖易变的资源 ID
  - 左右 10dp 边距，26dp 圆角，底部 8dp 悬浮间隙
  - 输入区 `d9e` 的内层白底去掉，文字直接坐在玻璃上
  - 表情/更多/语音按钮保留原位，只做视觉透明化
- 模糊：
  - 捕获当前窗口，1/4 降采样，三次 box blur（stack blur），再按玻璃区域采样
  - 抓屏时把 ActionBar/ChatFooter 的 alpha 临时设为 0，只留下真正位于它们后面的聊天内容
  - 顶栏/输入栏共用同一个模糊源，思路参考 Telegram `du0`
- 夜间模式：自动切换浅色/深色 tint 和边框
- 横竖屏/深色模式切换：`onConfigurationChanged` 后重建玻璃层

## 安装

1. 安装 `out/wechat-glass-1.0.apk`。
2. 在 LSPosed 里启用“微信悬浮玻璃栏”。
3. 作用域只勾选 **微信**（`com.tencent.mm`）。
4. 强制停止微信，重新打开任意聊天页。

## 构建

本机没有可用的 Gradle Wrapper/AGP 组合，所以用 SDK 自带工具直接构建（aapt2 + javac + d8 + zipalign + apksigner）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

构建脚本会把工程复制到 `C:\wcg_build`（ASCII 路径），因为 aapt2 在中文路径下会报 `failed to open directory`。

依赖：

- Android SDK 36（`android.jar`、`build-tools/36.0.0`）
- JDK 17（`JAVA_HOME`）
- Xposed API 82（已放在 `app/libs/api-82.jar`，构建脚本直接使用）

## 调试状态

- 已做两轮静态 debug：
  1. 编译/API 检查：修复 `setClipToPadding` 编译错误、抓屏时用 `visibility` 导致布局循环的问题、底部 padding 被覆盖的问题。
  2. 构建/清单检查：校验 `xposed_init`、`xposedmodule`、`xposedscope`、`xposedminversion`、APK v2 签名和 zipalign；确认模块 dex 只引用 `ChattingUI`，没有引用任何通讯/支付类。
- 当前机器 `adb devices` 为空，**没有真机运行验证**。
- 已知限制：
  - 资源 ID 只对应微信 8.0.79；换版本会安全降级（找不到 View 就不改）。
  - 新版 edge-to-edge 改动依赖 `ChattingScrollLayout` 的负 margin 行为；如果微信某个版本重新布局 footer，可能出现输入栏位置偏移，需要按真机截图继续调。
  - 抓屏模糊在滚动时约 11fps 更新，低端机可能掉帧；可把 `GlassRenderer.CAPTURE_DELAY_MS` 调大或 `SCALE` 调小。
  - 设备已连接但 `adb` 掉线时无法验证；当前 APK 需要重新安装后强制停止微信再测。

## 文件

```text
app/src/main/
  AndroidManifest.xml              LSPosed 元数据 + xposedscope
  assets/xposed_init               com.wechatglass.HookEntry
  res/values/arrays.xml            xposedscope = com.tencent.mm
  java/com/wechatglass/
    HookEntry.java                 Hook ChattingUI onCreate/onDestroy/onConfigurationChanged
    ChatGlassHook.java             真 edge-to-edge 重构 + 顶栏三段岛 + 底栏悬浮
    GlassRenderer.java             抓屏/降采样/模糊/按区域绘制
    GlassChipView.java             顶部玻璃岛
    GlassBarDrawable.java          底部玻璃背景
    BlurUtil.java                  纯 Java stack blur
    Ui.java                        dp/夜间/查找 View/清理 ActionBar 背景
build.ps1                          无 Gradle 构建脚本
```
