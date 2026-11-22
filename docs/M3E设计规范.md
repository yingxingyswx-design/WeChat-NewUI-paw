# M3 Expressive 设计规范（微信 UI 模块）

> 依据：Material 3 官方站点与 Android 官方文档，配合 `androidx.compose.material3` 的
> token 数值（2026-07 版）整理。所有数值按「官方 token」使用，不凭感觉取整。
>
> - <https://m3.material.io>
> - <https://m3.material.io/blog/building-with-m3-expressive>
> - <https://m3.material.io/styles/color/dynamic-color/overview>
> - <https://m3.material.io/components/segmented-buttons/overview>
> - <https://developer.android.com/develop/ui/views/theming/dynamic-colors>
> - <https://developer.android.com/develop/ui/compose/designsystems/material3>
> - token 数值参考：<https://github.com/abhixv/google-m3-expressive-design-skill>

## 0. 范围

**做**：微信 / 通讯录 / 发现 / 我 四个主页面的 M3E 重绘；聊天页的悬浮顶栏、输入栏、
表情/加号面板；底部导航的 M3E 视觉；模块设置页跟随同一套 token。

**不做**：聊天消息列表、气泡、消息数据库、网络、账号、支付。聊天内容区仍由微信原生渲染，
模块只负责它自己的悬浮层和输入区。这样既保留微信的滚动/点击/长按能力，也不把封号风险
从「UI 层」推到「消息层」。

**回退**：保留「使用微信原生会话列表」开关；M3E 页面稳定前不删除原生路径。

## 1. 动态色彩

### 1.1 取色顺序

1. Android 12+（API 31+）：读取系统 Monet 调色板
   `android.R.color.system_accent1_*` / `system_accent2_*` / `system_accent3_*` /
   `system_neutral1_*` / `system_neutral2_*`，按 M3 的 tone 映射生成角色。
2. 系统没有 Monet / API < 31：用微信绿 `#07C160` 作为 seed，按 `Variant.FIDELITY`
   生成固定 scheme。这样 `primaryContainer` 仍然是微信绿，`primary` 则是可读性更好的深绿。

### 1.2 微信绿 fallback（FIDELITY，seed `#07C160`）

| 角色 | Light | Dark |
|---|---|---|
| primary | `#006D33` | `#45E17C` |
| onPrimary | `#FFFFFF` | `#003917` |
| primaryContainer | `#07C160` | `#07C160` |
| onPrimaryContainer | `#00471F` | `#00471F` |
| secondary | `#2F6A3F` | `#96D5A0` |
| secondaryContainer | `#B1F2BB` | `#16542B` |
| tertiary | `#A23D33` | `#FFB4AA` |
| tertiaryContainer | `#FF8475` | `#FF8475` |
| surface | `#F3FCF0` | `#0E150E` |
| onSurface | `#161D16` | `#DCE5D9` |
| surfaceVariant | `#D7E7D5` | `#3D4A3D` |
| onSurfaceVariant | `#3D4A3D` | `#BBCBBA` |
| surfaceContainerLowest | `#FFFFFF` | `#081009` |
| surfaceContainerLow | `#EDF6EA` | `#161D16` |
| surfaceContainer | `#E8F0E4` | `#1A221A` |
| surfaceContainerHigh | `#E2EBDF` | `#242C24` |
| surfaceContainerHighest | `#DCE5D9` | `#2F372F` |
| outline | `#6C7B6C` | `#869585` |
| outlineVariant | `#BBCBBA` | `#3D4A3D` |
| error | `#BA1A1A` | `#FFB4AB` |

### 1.3 Monet 角色映射

以 `system_accent1_*` 为例（`_400` = tone 40，`_1000` = tone 100）：

| 角色 | Light | Dark |
|---|---|---|
| primary | `accent1_400` | `accent1_800` |
| onPrimary | `accent1_1000` | `accent1_200` |
| primaryContainer | `accent1_900` | `accent1_300` |
| onPrimaryContainer | `accent1_100` | `accent1_900` |
| secondary / tertiary | accent2 / accent3 同上 | 同上 |
| surface | neutral1 近白 | neutral1 tone 10 |
| surfaceContainer* | neutral1 由白向 90 混合 8/12/16/20% | neutral1 由 10 向 100 混合 4/8/12/16% |
| outline | neutral2 tone 50 | neutral2 tone 60 |
| outlineVariant | neutral2 tone 80 | neutral2 tone 30 |

### 1.4 使用规则

- 文本/图标只使用 `on*` 配对色；`*Container` 只做填充，不直接写文字。
- 分隔线用 `outlineVariant`，输入框边界用 `outline`。
- 卡片内容区对比度按 3:1（大字）/ 4.5:1（小字）校验。
- 强调色只用于：选中态、主操作、未读、FAB。不要整屏绿色。

## 2. 排版

采用 30 个 style 的 baseline + emphasized；组件默认 baseline，选中/未读/主操作才用
emphasized。

| Style | Size | Line | Tracking | Weight |
|---|---|---|---|---|
| displayLarge | 57sp | 64 | -0.2 | Regular |
| headlineLarge | 32sp | 40 | 0 | Medium |
| headlineMedium | 28sp | 36 | 0 | Medium |
| titleLarge | 22sp | 28 | 0 | Medium |
| titleMedium | 16sp | 24 | 0.15 | Bold (emph.) |
| titleSmall | 14sp | 20 | 0.1 | Bold (emph.) |
| bodyLarge | 16sp | 24 | 0.15 | Medium (emph.) |
| bodyMedium | 14sp | 20 | 0.25 | Medium (emph.) |
| bodySmall | 12sp | 16 | 0.4 | Medium (emph.) |
| labelLarge | 14sp | 20 | 0.1 | Bold (emph.) |
| labelMedium | 12sp | 16 | 0.5 | Bold (emph.) |
| labelSmall | 11sp | 16 | 0.5 | Bold (emph.) |

**页面层级**

- 大标题：`headlineLarge` 32sp（可随滚动收成 `titleLarge` 22sp）。
- 分区标题：`titleSmall` 14sp bold，颜色 `onSurfaceVariant`，**不要灰色大色块**。
- 列表主标题：`titleMedium` 16sp，选中/未读用 emphasized。
- 列表副标题：`bodyMedium` 14sp，`onSurfaceVariant`。
- 时间/角标：`labelSmall` 11sp。
- 按钮：`labelLarge` 14sp bold。

## 3. 形状

10 级圆角，按组件语义取值：

| Token | dp | 用途 |
|---|---|---|
| extraSmall | 4 | 小控件、列表默认态 |
| small | 8 | chip、输入框 |
| medium | 12 | 卡片、按钮方形态 |
| large | 16 | 列表选中态、卡片 |
| largeIncreased | 20 | 强调卡片 |
| extraLarge | 28 | 底部面板、对话框 |
| extraLargeIncreased | 32 | 大卡片 |
| extraExtraLarge | 48 | hero |
| full | 50% | 按钮、开关、胶囊、头像 |

**嵌套规则**：内圆角 = 外圆角 − padding。例如外层 28dp、内边距 8dp → 内层 20dp。

## 4. 动效

M3E 用弹簧替代 duration/easing。**几何属性用 spatial，颜色/透明度用 effects**；
effects 永远 damping 1.0，不弹。

| Token | Damping | Stiffness |
|---|---|---|
| expressive default spatial | 0.8 | 380 |
| expressive fast spatial | 0.6 | 800 |
| expressive slow spatial | 0.8 | 200 |
| expressive default effects | 1.0 | 1600 |
| expressive fast effects | 1.0 | 3800 |
| expressive slow effects | 1.0 | 800 |

**编排**

- 页面切换：fade-through（effects）+ 0.96→1 scale（spatial），250–300ms 等效弹簧。
- 列表进入：前 6 行按 30ms 阶梯淡入 + 8dp 上移；退出统一淡出，不阶梯。
- 行按压：state layer 10% + scale 0.98（fast spatial）。
- 分段按钮：选中块位置用 default spatial，按压 shape 从 full → 12dp 再回 full。
- 容器变形：卡片→详情用圆角/尺寸插值（28dp→16dp），内容延迟 80ms 淡入。
- 不做：透明度弹跳、全屏 each-item 弹性、超过 600ms 的常驻动画。

## 5. 页面蓝图

### 5.1 微信（会话）

```text
[large top app bar 112dp，滚动收成 64dp]
  微信 (headlineLarge 32)                 [搜索] [＋]
[segmented button 40dp，全圆角，2dp 内边距]
  好友 | 群聊 | 微信
[列表，16dp 页边距]
  ── 置顶 (titleSmall, onSurfaceVariant)
  行 72dp：40dp 圆角方头像 / 名字 16 / 预览 14 / 时间 11 / 未读角标
  ── 未置顶
  行 72dp ...
[底部 M3 navigation bar 80dp]
```

- 行背景：默认透明；按压/选中 `surfaceContainerHigh` 16dp 圆角；不画分隔线。
- 分组间距 16dp；分区标题上下 8/4dp。
- 无会话好友/群显示「无聊天记录」，预览 `onSurfaceVariant`。
- 空态：居中图标 + `titleMedium` + 说明，不用灰色大块。

### 5.2 通讯录

```text
[large top app bar 112dp]
  通讯录                                  [搜索] [＋]
[搜索框 56dp，full 圆角，surfaceContainerHigh]
[A–Z 分区，sticky header 28dp]
  行 64dp：40dp 头像 / 名字 / 备注或签名
[右侧字母索引，24dp 宽，选中 primary]
```

- 数据：`rcontact` 只读；按备注/昵称拼音排序；群聊单独分组「群聊」。
- 点击：打开微信原生联系人详情/聊天，不伪造数据。

### 5.3 发现

```text
[large top app bar 112dp]
  发现
[2 列卡片网格，16dp 间距，28dp 圆角]
  朋友圈 / 视频号 / 扫一扫 / 摇一摇
  搜一搜 / 小程序 / 直播 / 购物
```

- 卡片：`surfaceContainerLow`，图标 28dp 放在 48dp `primaryContainer` 圆角容器里。
- 点击转发微信原生入口，不自己实现业务。
- 未读红点用 `error`，`labelSmall` 数字。

### 5.4 我

```text
[large top app bar 112dp]
  我
[个人信息卡 28dp 圆角，surfaceContainerLow]
  64dp 头像 / 名字 headlineSmall / 微信号 bodyMedium / [二维码]
[设置列表，卡与卡之间 12dp]
  服务 / 收藏 / 朋友圈 / 卡包 / 表情 / 设置
```

- 头像/昵称/微信号只读；点击进入微信原生「个人信息」。
- 列表行 56dp，leading 24dp 图标，trailing chevron。

### 5.5 聊天

- 顶栏：返回圆 / 标题胶囊 / 菜单圆，保持悬浮；尺寸改为 40dp，圆角 full，
  `surfaceContainer` 80% + blur，描边 `outlineVariant`。
- 输入栏：56dp 高，full 圆角，左右 12dp，底部 8dp 悬浮；发送按钮用 `primary`。
- 面板：28dp 顶部圆角，`surfaceContainerHigh` + blur；与输入栏拼成一张卡片。
- 消息列表/气泡：保持微信原生，只调列表 padding，保证内容从悬浮层下穿过。

## 6. 组件规格（落地用）

| 组件 | 规格 |
|---|---|
| 小按钮 | 高 40dp，full 圆角，labelLarge，左右 16dp |
| 中按钮 | 高 56dp，full 圆角，labelLarge，左右 24dp |
| 图标按钮 | 40dp 容器，24dp 图标，full 圆角 |
| 分段按钮 | 高 40dp，full 圆角，2dp 内边距，选中 `secondaryContainer` |
| 卡片 | 圆角 12/16/28，elevation 0/1，`surfaceContainerLow` |
| 列表行 | 1 行 56dp，2 行 72dp，3 行 88dp；左右 16dp |
| 顶栏 | small 64dp，medium 112dp，large 152dp |
| 底部导航 | 80dp 高，64dp 内容，活动指示 64×32，图标 24 |
| 开关 | 52×32，拇指 24，选中 `primary` |
| 搜索框 | 56dp 高，full 圆角，`surfaceContainerHigh` |
| 徽标 | 6dp 点 / 16dp 数字，full 圆角，`error` |
| 对话框 | 28dp 圆角，elevation 3（6dp） |
| 底部面板 | 28dp 顶部圆角，拖柄 32×4 |

## 7. 代码结构

```text
com.wechatglass.m3
  M3ColorScheme.java     // Monet / 微信绿 fallback 的 26+ 角色
  M3Theme.java           // 当前 scheme、dp、夜间判断
  M3Type.java            // 30 style 的 TextView 应用
  M3Motion.java          // expressive/standard 弹簧工厂
  M3Shape.java           // 10 级圆角常量
  M3Drawable.java        // fill/outline/ripple/state layer
  M3ListItem.java        // 72dp 会话行、64dp 联系人行
  M3SectionHeader.java   // 14sp bold 分区标题
  M3SegmentedButton.java // 40dp，full，spring 指示块
  M3TopAppBar.java       // 64/112dp，滚动收起
  M3BottomNav.java       // 80dp，桥接微信 setTo
  M3HomePage.java        // 微信页
  M3ContactsPage.java    // 通讯录
  M3DiscoverPage.java    // 发现
  M3MePage.java          // 我
```

## 8. 分轮实施

| 轮次 | 内容 | 验收 |
|---|---|---|
| R1 | 设计系统底座（本文档 + M3ColorScheme/M3Theme/M3Type/M3Motion/M3Shape/M3Drawable） | 编译通过，不改变现有 UI |
| R2 | 微信页：大标题 + 分段按钮 + M3 会话行 + 分组 | 30 好友/15 无会话显示正确；切 tab 10 次无重影；点击偏移 0 |
| R3 | 通讯录页：搜索 + A–Z + 联系人行 | 点击进原生详情；滚动 60fps；字母索引可用 |
| R4 | 发现页：2 列卡片 | 入口全部能打开微信原生页面 |
| R5 | 我页：个人信息卡 + 设置列表 | 昵称/头像/微信号正确；入口可用 |
| R6 | 聊天页悬浮层 + 底部导航 M3E | 顶栏/输入栏/面板材质统一；底部 tab 点击不丢 |

每轮：Gradle 构建 → 真机验证 → `publish.ps1` 提交。

## 9. 验收清单

- [ ] 日间/夜间两套 scheme，颜色角色不串。
- [ ] Monet 开/关都能用；关时是微信绿 FIDELITY。
- [ ] 四页顶栏、字号、间距、圆角同一套 token。
- [ ] 分段按钮/导航栏有 spring 动效，透明度不弹。
- [ ] 列表点击打开正确会话/联系人；无偏移。
- [ ] 切 tab 10 次、冷启动 3 次无重影/抽搐。
- [ ] 聊天页消息仍由微信原生渲染，模块不碰消息内容。
