# 微信聊天页 MD 化分析

> 目标：分析微信 8.0.79 聊天页如何做 Material 3 Expressive 化，先分析，不实现。
> 结论基于：反编译源码（classes8/11/14 等）、旧会话分析、设备日志与截图。

## 1. 结论先行

微信聊天页可以做 MD 化，但**不能按正常 Android 布局思路改**：

- 消息 item、文本 View 大量是运行时创建或混淆类；
- 没有稳定的 `bkj/bkl` 气泡容器；
- `bkj` 只存在于部分旧布局，普通文本运行时并不使用；
- 旧会话也确认：资源名混淆、类名混淆，静态 id 不可靠。

可行路线按风险从低到高：

1. **聊天页绘制层**：在 `ScrollControlRecyclerView` 的 item 下面画气泡（推荐）。
2. **运行时对象 hook**：捕获真实消息 TextView，在它的父容器上刷背景（需要先定位真实对象）。
3. **替换 item 布局/适配器**：风险最高，不建议。

---

## 2. 微信聊天页结构

### 2.1 Activity / Fragment

- `com.tencent.mm.ui.chatting.ChattingUI` 在 `classes11`。
- 继承 `MMSecDataFragmentActivity`，使用 edge-to-edge wrapper。
- 聊天内容在 `ChattingUIFragment`，布局大致为：

```text
ss.xml
 └─ ChattingUILayout
      ├─ ActionBar 区域（AppCompat + 自定义 view）
      ├─ MMChattingListView / ScrollControlRecyclerView
      └─ ChatFooter（底部输入栏）
```

### 2.2 消息列表

- 运行时列表类：`com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView`。
- 旧源码中 adapter 在 `classes8`：`com.tencent.mm.ui.chatting.adapter.k`。
- item 工厂：`viewitems` 下的 `b0`/`tt` 等，按消息类型创建不同的 holder。
- 消息 item 根类在运行时表现为 `com.tencent.mm.ui.chatting.viewitems.kh`。
- `kh` 的真实身份是 **`ChattingItemContainer extends RelativeLayout`**。
- 旧源码 `kh` 构造器会动态创建整个 item：
  - 顶部状态文字（X2CTextView）
  - 复选框
  - **inflate 具体消息布局，并 `addView` 到 `kh`，id = `2131365770`**
  - 覆盖层 View
- `2131365770` 的十六进制是 `0x7f0a0f8a`，也就是旧源码里的 **`R.id.bkj`**。

因此真实结构是：

```text
kh (ChattingItemContainer, RelativeLayout)
 ├─ 状态 X2CTextView
 ├─ 复选框
 ├─ bkj (0x7f0a0f8a)  ← 具体消息布局的根
 │    └─ ... 文本/图片/语音 ...
 └─ 覆盖层
```

`bkj` 不是固定 XML 里的气泡，而是 **`ChattingItemContainer` 运行时给消息布局根设置的 id**。

这也解释了：

- 通话消息 item 没有 bkj：它的布局根自带 id，覆盖了默认 bkj
- 文本/部分消息的布局根会被设为 bkj
- `yo` 里 `findViewById(R.id.bkj)` 找的是消息内容根
- `bkl` 是文本布局内部的 `MMNeat7extView`

所以之前“完全找不到 bkj”是因为扫描到的 15 个 item 都是通话/系统类型，而不是没有 bkj。

### 2.3 文本气泡的真实情况

旧源码 `ChattingItemAppMsgPat` 中：

```java
View view = new MMNeat7extView(activity);
view.setPadding(...);
view.setBackgroundResource(...);
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
idVar.b.addView(view, lp);
```

说明：

- 文本 View 可能是运行时 `new` 的；
- 没有 XML id；
- 背景由代码 `setBackgroundResource`；
- 外层 `LinearLayout` 也是运行时/无 id 的。

因此不能依赖：

```text
R.id.bkj
R.id.bkl
MMNeat7extView
X2CTextView
```

这些在不同消息类型/版本中并不稳定。

### 2.4 运行时陷阱

调试发现：

```text
CB setText 干嘛 class=android.widget.TextView
stack=LayoutListenerView#onLayout <- WxViewPager#onLayout
```

这个“干嘛”是**首页会话列表的预览文本**，不是聊天消息。  
这解释了为什么之前会误伤：

- home 会话列表
- top bar 名字
- 底部导航

所以任何文字 hook 必须先限定：

```text
ChattingUI active
且 TextView 在 ScrollControlRecyclerView 内
```

---

## 3. 三种 MD 化路线

### 路线 A：直接改原生 `bkj` 容器

做法：

- 在 `kh` / `ChattingItemContainer` 中找到 `bkj` 子 View
- `bkj` 刷 20dp 背景 + 12/8 padding
- `bkl` / 文本 View 背景透明、padding 0
- 保留 `bkj` 内部的原生尾巴 View

优点：

- 不 reparent
- `bkj` 是 `ChattingItemContainer` 自己 add 的消息根
- 点击、长按、选中链路不变
- 可以保留原生小尾巴

问题：

- 只有部分消息类型的布局根会被设为 `bkj`
- 通话/系统消息没有 bkj，需要跳过
- 原生 `setBackgroundResource` 可能再次覆盖文本背景
- 需要 hook `ChattingItemContainer` 构造或 `RecyclerView` child attach

结论：**如果目标只是文本消息，这是最稳的路线。**

### 路线 B：改 item 布局/适配器

做法：

- hook adapter `onCreateViewHolder`
- 替换消息 item 为自定义气泡布局

问题：

- 消息类型极多（文本/图片/语音/视频/引用/转账/小程序/系统）
- item 与原生点击/长按/转发/引用强绑定
- 适配器混淆、版本变化
- 风险最高，不建议

### 路线 C：聊天页绘制层（备选）

做法：

- 只拿 `ScrollControlRecyclerView`
- 在 item 绘制前，用 Canvas 画气泡背景 + 小尾巴
- 文本/头像仍由微信自己画
- 不改 View 树、不 reparent

实现位置二选一：

1. `RecyclerView.ItemDecoration.onDraw`
2. Hook `ViewGroup.drawChild`，在 `super.drawChild` 前画

推荐第 2 种，因为：

- 不依赖 item 类型
- 不依赖 `bkj/bkl`
- 不需要找父容器
- 不改变点击/长按/选中

绘制算法：

```text
找到 item 内第一个消息 TextView
计算文字在 RecyclerView 中的 bounds
外扩 12dp / 8dp
画 20dp 圆角矩形
按出/入方向画小尾巴
再让微信画 item 内容
```

需要处理：

- 清掉文字/祖先的原生 9-patch 背景
- 只对 `ScrollControlRecyclerView` 直接 child 生效
- 只对 ChattingUI active 生效
- 跳过时间、日期、系统提示
- 滚动/复用每帧重画，不需要保存 View 状态

优点：

- 最稳，不碰微信 View 树
- 尾巴可以自己控制
- 所有消息类型统一

缺点：

- 气泡不参与布局，文字 padding 要单独设
- 不能做 ripple/裁剪
- 需要精确计算坐标

---

## 4. 推荐实现架构

```text
HookEntry
 └─ ChatScope
      ├─ ChattingUI onResume → active = true
      ├─ ChattingUI onPause/onDestroy → active = false
      └─ 只允许 chat 范围内的 bkj / TextView / drawChild

主路线（文本消息）
 ├─ kh = ChattingItemContainer
 ├─ bkj = kh 的消息布局根 (0x7f0a0f8a)
 ├─ bkl = bkj 内文本 View
 ├─ bkj.setBackground(20dp 气泡)
 ├─ bkj.setPadding(12, 8, 12, 8)
 ├─ bkl.setBackground(null), padding=0
 └─ 保留 bkj 内原生尾巴 / 或自己画尾巴

备选路线（所有消息/无 bkj 类型）
 ├─ ScrollControlRecyclerView
 ├─ drawChild / ItemDecoration
 └─ 按文本 bounds 画气泡 + 尾巴

M3 tokens
 ├─ 20dp 圆角
 ├─ 12×8 padding
 ├─ 接收 surfaceContainerHighest
 ├─ 发送 mix(surface, primary, 0.18)
 └─ 尾巴同色
```

---

## 5. 风险

### 账号风险

- 只改本地绘制，不改消息数据/网络/支付，风险相对低
- 但微信用户协议禁止修改客户端，无法保证零风险
- 不要改包/签名/账号链路

### 技术风险

- 资源/类名混淆，版本升级容易失效
- `drawChild` 全局 hook 性能敏感，必须只处理聊天列表
- 文字 bounds 计算错误会导致气泡偏移
- 原生背景清除后，需要恢复/避免影响其他页面

### 兼容风险

- 图片/语音/视频/引用/转账/小程序/系统消息
- 深色/浅色/Monet
- 多语言/长文本/表情

---

## 6. 测试计划

- 文本：单行/多行/长文本/表情/链接
- 消息类型：图片、语音、视频、引用、转账、系统
- 交互：点击、长按、复制、转发、引用、选中
- 列表：滚动、复用、快速滑动、切换会话
- 主题：light/dark/Monet/色卡
- 范围：home/topbar/nav 不得被修改

---

## 7. 最终建议

优先用 **A：`bkj` 真实容器**。

```text
ChattingUI active
  → ChattingItemContainer (kh)
  → bkj (0x7f0a0f8a)
  → bkl 文本
```

在 `bkj` 上刷气泡、清 `bkl` 背景、保留尾巴：

- 不 reparent
- 不改点击链路
- 文本消息最稳

如果 `bkj` 不存在（通话/图片/系统等）：

- 不处理，保持原生
- 后续再按类型补

如果 `bkj` 方案在实机上被原生布局覆盖：

- 退回 C：`drawChild` / ItemDecoration 绘制层

最终原则：

> 只改聊天页内、只改消息容器、绝不碰 home/topbar/nav。
