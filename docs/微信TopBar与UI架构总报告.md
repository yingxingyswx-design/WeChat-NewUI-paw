# 微信 8.0.79 Top Bar 与 UI 架构总报告

> 目的：把当前所有逆向分析、真机证据、调用链、结论和未确认点整理成一份可独立阅读的文档，方便继续分析或交给其他模型。
>
> 样本：真机安装的微信 8.0.79（versionCode 3182，targetSdk 35）；另参考 APKMirror 3200 版本资源。
>
> 工具：jadx 1.5.6、apktool、baksmali、adb、LSPosed 日志、真机 View dump。

---

## 0. 一句话结论

微信的 Top Bar 不是“一个 ActionBar”，而是：

```text
AppCompat WindowDecorActionBar 状态机
+ WeChat HomeUI / MainTabUI / MoreTabUI
+ TaskBarAnimController 滚动状态机
+ ConversationListView 的 attach/onScroll 绑定
+ ActionBarOverlayLayout 的 insets
```

共同操作同一棵 View 树：

```text
ActionBarOverlayLayout
└─ ActionBarContainer
   ├─ Toolbar
   │  ├─ navigation icon
   │  ├─ title/subtitle
   │  ├─ ActionMenuView
   │  └─ bl.xml customView
   ├─ ActionBarContextView
   └─ tabContainer
```

所以只改 `ActionBarContainer` 的可见性，会被其他状态机覆盖。

---

## 1. 约束与风险

原始约束：

- 只针对 `com.tencent.mm`；
- 只做 UI 美化；
- 不改消息、网络、账号、支付；
- 不关闭其他微信 LSPosed 模块（尤其 `io.github.liuran001.mmliquidglass`）；
- 真机测试，只有一个微信号，避免封号。

当前模块中的安全边界：

- `GlobalEdgeToEdge.force()` 只允许：
  - `com.tencent.mm.ui.LauncherUI`
  - `com.tencent.mm.ui.chatting.ChattingUI`
- 已移除全局 `android.app.Activity` hook；
- 已移除全局 `PhoneWindow.setStatusBarColor/setNavigationBarColor` hook；
- `ActionBarOverlayLayout` / `HomeUI` / `FindMoreFriendsUI` 的 hook 只在 Launcher 上下文生效；
- 模块源码没有 `Wallet`、`Pay`、`Transfer`、`LuckMoney`、消息发送/网络/账号类引用。

仍需注意：

- `ConversationListView` / `TaskBarAnimController` 属于微信 TaskBar/会话列表，不是支付，但属于核心 UI；
- 任何改动都可能影响 TaskBar 动画、滚动、布局，需真机验证。

---

## 2. 整体 UI 分层

```text
Application
├─ 插件层
│  ├─ com.tencent.mm.plugin.*.ui.*
│  ├─ com.tencent.mm.ui.base.*
│  └─ com.tencent.mm.ui.widget.*
│
├─ Activity 壳
│  ├─ MMActivity
│  │  └─ MMActivityController (ka/ea)
│  ├─ MMFragmentActivity
│  │  └─ MMFragment
│  └─ AppCompat
│
├─ 页面层
│  ├─ LauncherUI + HomeUI + MainTabUI
│  ├─ ChattingUI + ChattingUIFragment
│  ├─ SettingsUI / WebViewUI
│  └─ 各 plugin UI
│
└─ 组件层
   ├─ ActionBar / Toolbar / ActionBarOverlayLayout
   ├─ FrostedContentView
   ├─ EdgeToEdgeWrapperLayout
   ├─ ChatFooter / ChattingScrollLayout
   └─ WeImageView / MMViewPager
```

规模数据：

| 项目 | 数量 |
|---|---:|
| AndroidManifest `<activity>` | 2099 |
| 已反编译 `com.tencent.mm.ui` Java 文件 | 5574 |
| `res/layout` | 7133 |
| `res/values/styles.xml` style | 961 |

---

## 3. 主页 UI

### 3.1 调用链

```text
LauncherUI.onCreate
├─ setTheme(@style/lm)
├─ super.onCreate
│  └─ MMFragmentActivity/MMActivity
│     ├─ getController().j0 = 2131624053   // bk.xml
│     ├─ mController.e0()
│     ├─ new EdgeToEdgeWrapperLayout()
│     ├─ setActionbarColor()
│     └─ setMMTitleSize()
├─ decor.setSystemUiVisibility(1280)        // LAYOUT_STABLE|LAYOUT_FULLSCREEN
└─ HomeUI.doOnCreate
   ├─ setContentView(bvp.xml)
   ├─ HomeUI.o()                            // initActionBar
   ├─ HomeUI.g()/u()                        // target35 margin
   └─ MainTabUI 四页
```

`LauncherUI` 初始化时：

```java
homeUI2.setContentView(homeUI2.L);
homeUI2.o();
homeUI2.f2974c.L();   // ActionBar.show()
```

### 3.2 布局

| 文件 | ID | 内容 |
|---|---|---|
| `bvp.xml` | `0x7f0e14b2` | `FirstScreenFrameLayout` + `CustomViewPager` |
| `bl.xml` | `0x7f0e0076` | HomeUI ActionBar 自定义布局 |
| `bm.xml` | — | 标题 + 隐藏伞 `h0y` |
| `bk.xml` | `0x7f0e14b1`（参考） | 通用 ActionBar |

`bl.xml`：

```text
RelativeLayout id=hb
├─ include bm.xml
├─ LinearLayout actionbar_up_indicator
│  └─ WeImageView actionbar_up_indicator_btn
│     src=icons_multi_task_star_line_regular
└─ LinearLayout coz
   └─ LinearLayout coy
```

### 3.3 四个 tab

`MainTabUI.e`：

| e | Fragment | 页面 |
|---|---|---|
| 0 | `MainUI` | 微信 |
| 1 | `MvvmAddressUIFragment` | 通讯录 |
| 2 | `FindMoreFriendsUI` | 发现 |
| 3 | `MoreTabUI` | 我 |

`HomeUI.y()` 按 tab 改标题/状态栏；`HomeUI.j()` 改菜单；`HomeUI.a()` 改颜色。

---

## 4. Top Bar 全家桶

### 4.1 View 树

```text
DecorView
└─ FitSystemWindowLayoutView
   └─ ActionBarOverlayLayout
      ├─ ContentFrameLayout (android.R.id.content)
      │  └─ FirstScreenFrameLayout
      │     └─ CustomViewPager
      │        ├─ ConversationListView
      │        └─ ConversationRecyclerView
      └─ ActionBarContainer (0x7f0a00d3)
         ├─ Toolbar
         ├─ ActionBarContextView
         └─ bl.xml customView
```

关键点：

- `ActionBarContainer` 与 content 是**兄弟**；
- top bar 是 Activity 级，所有 tab 共用；
- 换 tab 只换 content。

### 4.2 AppCompat `WindowDecorActionBar`（y0）

字段：

```java
public ActionBarContainer d;         // 顶层容器
public ActionBarOverlayLayout f354c; // 外层 overlay
public z2 e;                        // ToolbarWidgetWrapper
public ActionBarContextView f;      // ActionMode
public View f355g;                  // decor content
public boolean p;                   // hidden
public boolean s;                   // showing
public boolean r;                   // action mode
```

show：

```java
public void L() {
    if (this.p) {
        this.p = false;
        R(false);
    }
}
```

`R()`：

```java
this.d.setVisibility(0);
this.d.setTranslationY(...);
this.d.setAlpha(...);
```

### 4.3 `z2` = ToolbarWidgetWrapper

```java
public class z2 implements x0 {
    public final Toolbar f511a;
    public View d;      // customView
    public View f512c;  // tab container
}
```

`HomeUI.o()` 的自定义 bar：

```java
f2974c.y(inflate(2131624054)); // bl.xml
```

`y0.y(View)`：

```java
((z2) this.e).c(view);
```

`z2.c(View)`：

```java
if (view2 != null && (this.b & 16) != 0) {
    toolbar.removeView(view2);
}
this.d = view;
if (view == null || (this.b & 16) == 0) return;
toolbar.addView(view);
```

所以 `bl.xml` 最终是 **Toolbar 的子 View**。

### 4.4 `ActionBarOverlayLayout` 的内部引用

`ActionBarOverlayLayout.e()`：

```java
this.f = (ContentFrameLayout) findViewById(2131362000);
this.f381g = (ActionBarContainer) findViewById(2131362003);
KeyEvent.Callback callbackFindViewById = findViewById(2131361999);

if (callbackFindViewById instanceof x0) {
    wrapper = (x0) callbackFindViewById;
} else if (callbackFindViewById instanceof Toolbar) {
    wrapper = ((Toolbar) callbackFindViewById).getWrapper();
}
this.f382h = wrapper;
```

字段关系：

```text
f      = ContentFrameLayout
f381g  = ActionBarContainer  (2131362003)
f382h  = DecorToolbar / Toolbar wrapper
```

`onMeasure()`：

```java
measureChildWithMargins(this.f381g, i, 0, i2, 0);

int measuredHeight =
    this.f381g.getVisibility() != 8
        ? this.f381g.getMeasuredHeight()
        : 0;

if (this.f383n || z3) {
    rect4.top += measuredHeight;
} else {
    rect2.top += measuredHeight;
}

a(this.f, this.v, true, true, true, true);
this.f.a(rect4);
```

也就是说：

```text
ActionBarContainer 的 visibility / measuredHeight
  → ActionBarOverlayLayout 的 content top inset
  → ContentFrameLayout 的 top margin
```

把 `ActionBarContainer` 设成 `GONE`，会让 `measuredHeight=0`，content inset 变化；这不仅是“隐藏一条 bar”，还会改变整个 content 的布局基准。

### 4.5 `ActionBarContainer.setVisibility()`

```java
@Override
public void setVisibility(int i) {
    super.setVisibility(i);
    boolean z3 = i == 0;
    Drawable drawable = this.f375h;
    if (drawable != null) drawable.setVisible(z3, false);
    Drawable drawable2 = this.i;
    if (drawable2 != null) drawable2.setVisible(z3, false);
    Drawable drawable3 = this.m;
    if (drawable3 != null) drawable3.setVisible(z3, false);
}
```

它只是改背景 Drawable 的可见性，真正布局由 `ActionBarOverlayLayout.onMeasure()` 读取。

### 4.6 `WindowDecorActionBar.N()`

ActionMode 开关会单独改 Toolbar：

```java
if (z3) {
    f511a.setVisibility(4); // Toolbar INVISIBLE
    f.setVisibility(0);
} else {
    f511a.setVisibility(0); // Toolbar VISIBLE
    f.setVisibility(8);
}
```

因此 Toolbar 有独立于 `ActionBarContainer` 的可见性。

---

## 5. WeChat 层 owner

### 5.1 `HomeUI`

```java
/* renamed from: c */
public b f2974c;    // ActionBar
public View f2975d; // action_bar_container
```

- `o()`：初始化 `bl.xml`；
- `s(i)`：设置颜色并 `customView.setVisibility(0/8)`；
- `g()/u()`：target35 `topMargin=statusBarHeight`；
- `y()`：标题/状态栏；
- `j()`：菜单；
- `a()`：tab 颜色。

### 5.2 `MainTabUI`

```java
homeUI.c.L();   // ActionBar.show()
```

在 `onPageScrolled` 内：

```java
if ((!bVar.q()) & (bVar != null)) {
    homeUI.c.L();
}
```

滚动/切 tab 会重新 show。

### 5.3 `MoreTabUI`

```java
g1(int i) {
    findViewById(2131362003).setVisibility(i);
}
```

调用点：

```text
380 / 414 / 671     -> g1(0)
708 / 735           -> g1(8)
2478 / 2530 / 3098  -> g1(0)
4185                -> g1(0)
```

### 5.4 `ConversationListView` / `ConversationRecyclerView`

`z()`：

```java
C = rootView.findViewById(0x7f0a00d3); // ActionBarContainer
C.addOnLayoutChangeListener(P);
```

`attachViewToParent()`：

```smali
iget-object p1, p0, ...->i:Lcom/tencent/mm/plugin/taskbar/ui/s;
iget-object p2, p0, ...->C:Landroid/view/View;

invoke-interface {p1, p2}, ...->setActionBar(Landroid/view/View;)V

iget-object p1, p0, ...->m:Lcom/tencent/mm/plugin/taskbar/ui/r;
iget-object p2, p0, ...->C:Landroid/view/View;

check-cast p1, Lcom/tencent/mm/plugin/taskbar/ui/b0;

iput-object p2, p1, Lcom/tencent/mm/plugin/taskbar/ui/b0;->V1:Landroid/view/View;
```

`onScroll()`：

```smali
if (g && m != null) {
    ((b0) m).e(...);
}
```

### 5.5 `TaskBarAnimController`（b0）

```java
public class b0 implements AbsListView.OnScrollListener,
                            View.OnTouchListener,
                            r {
    public View V1;                 // ActionBarContainer
    public final TaskBarContainer g;
}
```

`s(int)`：

```java
public final void s(int i) {
    View view = this.V1;
    if (view == null || view.getVisibility() == i) return;
    view.setVisibility(i);
}
```

调用者：

```text
b0.a(int)   -> s(0)
b0.e(...)   -> s(0) / s(4)
g0.run()    -> s(4)   // doOpenHeader
```

`e()` 由 `ConversationListView.onScroll` 直接调用。

### 5.6 `TaskBarContainer` / `TaskBarView`

```text
TaskBarView
  └─ TaskBarContainer q2
     └─ b0 d
        └─ V1
```

`TaskBarView` 调：

```java
b0.b(0L, i, false);
b0.b(0L, 15, false);
b0.b(100L, i2, true);
```

`b0.b()` 是延迟 closeHeader，最终到 `a()` → `s(0)`。

---

## 6. 可见性状态机

### 6.1 四套状态

```text
A. WindowDecorActionBar
   p / s / r
   d.setVisibility(0)
   d.setTranslationY
   d.setAlpha

B. TaskBarAnimController
   V1.setVisibility(0 / 4)

C. MoreTabUI
   g1(0 / 8)

D. HomeUI
   customView.setVisibility(0 / 8)
```

### 6.2 值的含义

```text
0 = VISIBLE
4 = INVISIBLE   // TaskBar 收起，保留布局
8 = GONE        // MoreTabUI / 强制隐藏
```

`b0.s()` 判断：

```java
if (view.getVisibility() == i) return;
```

把 `4` 改成 `8` 会让状态机永远不相等，反复执行，破坏 TaskBar 动画。

---

## 7. 关键调用链

### 7.1 LauncherUI.onResume

真机栈：

```text
ActionBarContainer.setVisibility(VISIBLE)
  at androidx.appcompat.app.y0.R
  at androidx.appcompat.app.y0.L
  at mv5.q0.L
  at com.tencent.mm.ui.LauncherUI.onResume
```

### 7.2 MainTabUI

```text
onPageScrolled
  → homeUI.c.L()
  → ActionBar.show()
```

### 7.3 ConversationListView.onScroll

```text
onScroll
  → b0.e
  → b0.s(0/4)
  → V1.setVisibility(0/4)
```

### 7.4 ConversationListView.attachViewToParent

```text
attachViewToParent
  → z()
  → C = ActionBarContainer
  → TaskBarContainer.setActionBar(C)
  → b0.V1 = C
  → C.addOnLayoutChangeListener(P)
```

### 7.5 MoreTabUI

```text
g1(0/8)
  → ActionBarContainer.setVisibility
```

### 7.6 HomeUI.s

```text
HomeUI.s(color)
  → f2974c.j().setVisibility(0/8)
```

---

## 8. Edge-to-Edge

### 8.1 Window 层

- `LauncherUI` 设置 `decor.setSystemUiVisibility(1280)`；
- `GlobalEdgeToEdge.force()` 设置：
  - `setStatusBarColor(TRANSPARENT)`
  - `setDecorFitsSystemWindows(false)`
  - `decor.setOnApplyWindowInsetsListener` 把 status inset 置 0；
- 仅 `LauncherUI` / `ChattingUI`。

### 8.2 微信 target35 修复

`HomeUI.g()` / `u()`：

```java
View actionBarContainer = activity.findViewById(2131362003);
int statusBarHeight = ol.h(activity);
MarginLayoutParams lp = view.getLayoutParams();
lp.topMargin = statusBarHeight;
```

实测：

```text
HomeUI.u before margin=107
```

### 8.3 内容根

真机 dump：

```text
ActionBarOverlayLayout 0,0,1080,2400
ContentFrameLayout     0,0,1080,2400
FirstScreenFrameLayout 0,0,1080,2400
CustomViewPager        0,0,1080,2400
```

真正让列表下移的是：

```text
ConversationListView.topMargin = actionBarHeight
NestedScrollView.paddingTop = 180
ConversationListView.paddingTop = 180
FindMoreFriendsUI root paddingTop = actionBarHeight
MoreTabUI PullDownListView.paddingTop
```

### 8.4 `FrostedContentView`

`com.tencent.mm.ui.FrostedContentView`：

```java
public final class FrostedContentView extends LayoutListenerView {
    boolean frostedEnabled;
    int blurRadius;          // 50
    int frostedColor;
    int topBlurAreaHeight;
    int bottomBlurAreaHeight;

    // dispatchDraw:
    // 1. 把 children 录进 content RenderNode
    // 2. 顶部条 RenderEffect blur
    // 3. 底部条 RenderEffect blur
}
```

ID `2131380723` / `0x7f0a49f3`。

`MMActivityController.ka.o()`：

```java
FrostedContentView v = (FrostedContentView) kaVar.g;
v.setBlurRadius(radius);
v.setTopBlurAreaHeight(kaVar.actionbarView.getHeight());
v.setFrostedColor(hl.a(colorRes, alpha));
v.setFrostedEnabled(true);
```

这是微信原生 60fps 毛玻璃，比自录 decor 的 CPU blur 正确。

---

## 9. 命名与资源

- `ka` = `MMActivityController`；
- `ea` = `MMActivityController` 子类；
- `y0` = `WindowDecorActionBar`；
- `z2` = `ToolbarWidgetWrapper`；
- `b0` = `TaskBarAnimController`；
- `f2974c` = `HomeUI.c` = ActionBar；
- `bl.xml` = HomeUI ActionBar；
- `bvp.xml` = LauncherUI content；
- `2131362003` / `0x7f0a00d3` = `action_bar_container`；
- `2131624054` / `0x7f0e0076` = `bl.xml`；
- `2131380723` / `0x7f0a49f3` = `FrostedContentView`。

不是纯 R8 混淆，更多是历史短名 + 模块代号 + 构建压缩 + 资源短名混合。

---

## 10. 为什么旧 bar 一直“保活”

### 10.1 直接原因

1. `LauncherUI.onResume → ActionBar.show()`
2. `MainTabUI.onPageScrolled → ActionBar.show()`
3. `MoreTabUI.g1(0) → ActionBarContainer.setVisibility(VISIBLE)`
4. `ConversationListView.onScroll → b0.e → b0.s(0)`
5. `ConversationListView.attachViewToParent → b0.V1 = C`
6. `HomeUI.s() → customView.setVisibility(0)`
7. `TaskBarView` 动画结束 → `b0.b → a → s(0)`

### 10.2 结构原因

```text
ActionBarContainer 是 Activity 级，和 content 是兄弟
ConversationListView 把它注入 TaskBarAnimController
TaskBarAnimController 又监听 ConversationListView 滚动
HomeUI/ToolBar 又由 AppCompat WindowDecorActionBar 控制
```

四套状态机共同操作同一个 View，互不知道对方状态。

### 10.3 为什么 `GONE` 不够

- TaskBar 用 `INVISIBLE`，不是 GONE；
- `s()` 判断 `getVisibility()==i`；
- `GONE` 破坏布局/动画基准；
- `WindowDecorActionBar` 仍按自己的 `p/s` 状态 show；
- `MoreTabUI` 直接写 container；
- `HomeUI.s` 直接写 customView；
- `Toolbar` 自己还有独立可见性。

---

## 11. 诊断日志与证据

真机 LSPosed 日志：

```text
WeChatGlassEdge: HomeUI.u before margin=107

WeChatGlassBar:
VISIBLE androidx.appcompat.widget.ActionBarContainer
  at androidx.appcompat.app.y0.R
  at androidx.appcompat.app.y0.L
  at com.tencent.mm.ui.LauncherUI.onResume

WeChatGlassFrost:
find frost=com.tencent.mm.ui.FrostedContentView
frost top=221 enabled=true
```

精确 hook 日志：

```text
WeChatGlass exact visibility hook skipped:
  androidx.appcompat.widget.Toolbar

WeChatGlass exact visibility hook skipped:
  androidx.appcompat.widget.ActionMenuView
```

说明 Toolbar/ActionMenuView 没有重写 `setVisibility`，精确 hook 挂不上。

---

## 12. 当前模块实现状态（摘要）

- 作用域：`com.tencent.mm`；
- 入口：`LauncherUI` / `ChattingUI`；
- `GlobalEdgeToEdge` 白名单；
- `HomeGlassHook`：
  - 找 ActionBarContainer；
  - 隐藏 legacy bar；
  - 创建 overlay chips（小微/标题/搜索/加号）；
  - 尝试 list top inset / 高度限制；
- `ChatGlassHook`：
  - 聊天页顶部/底部 glass；
- `FrostedContentView` 原生 blur 已找到，但当前在主页曾临时关闭。

已知问题：

- 旧 bar 的多个 owner 仍在；
- `Toolbar` / `ActionMenuView` 精确 hook skip；
- TaskBarAnimController 的 `V1`、ConversationListView 的 `C` 是深层绑定；
- `INVISIBLE`/`GONE` 语义冲突；
- 顶部高度/EtE 仍在调试。

---

## 13. 建议的下一步（按优先级）

### 13.1 先断 TaskBar 绑定

在 `ConversationListView.attachViewToParent()` / `ConversationRecyclerView.attachViewToParent()` 后：

```java
b0.V1 = null;
```

或 hook `TaskBarAnimController.s(int)`，对 LauncherUI 直接 return。

### 13.2 处理 ActionBarContainer

不要只 `GONE` 一次；要么：

- 从 parent `removeView`；
- 要么 hook `ActionBarContainer.setVisibility`，只允许 `INVISIBLE`，不允许 `VISIBLE`；
- 同时处理 `Toolbar` / `ActionMenuView` 独立可见性。

### 13.3 处理 HomeUI.s

hook `HomeUI.s(int)`，不要执行 `customView.setVisibility(0)`。

### 13.4 保持 EtE 与顶部高度

- window: `setDecorFitsSystemWindows(false)`、透明状态栏；
- content root: 0..2400；
- list: 通过 `topMargin` 或 `paddingTop` 保留原 top bar 高度；
- 不要用整体 translation 下移，否则底部 tab 会被推出屏幕；
- 不要用 `GONE` 破坏 TaskBar 的 INVISIBLE 状态机。

### 13.5 原生 blur

- 使用 `FrostedContentView`；
- `setTopBlurAreaHeight(status + actionBarHeight)`；
- `setFrostedColor(半透明)`；
- `setFrostedEnabled(true)`；
- 不再自录 decor 做 CPU blur（会导致递归/SIGSEGV）。

### 13.6 安全

- 保持 `GlobalEdgeToEdge` 白名单；
- 不 hook 支付/转账/红包 Activity；
- 不改消息/网络/账号。

---

## 14. 未确认点

1. 当前旧 bar 仍能看到时，屏幕上到底是：
   - 两套完全相同的 `小微/微信/搜索/加号`？
   - 还是旧 `Toolbar` 的标题/图标？
2. `TaskBarAnimController.V1` 被置空后，TaskBar 动画/滚动是否仍正常？
3. `ActionBarContainer` 从 parent remove 后，`ActionBarOverlayLayout` 的 inset 是否重新计算正确？
4. `HomeUI.s()` 被拦截后，搜索/加号菜单是否仍可用？
5. `FrostedContentView` 的 top blur 与 list inset 是否冲突？

---

## 15. 证据文件

```text
analysis/02-微信主页UI与导航栏解析.md
analysis/03-微信整体UI架构解析.md
analysis/04-微信UI整体调用链.md
analysis/05-微信类名与资源命名考古.md
analysis/06-四Tab顶栏与支付通讯依赖考古.md
analysis/07-topbar全家桶.md

smali17/com/tencent/mm/ui/conversation/ConversationListView.smali
smali17/com/tencent/mm/ui/conversation/recycler/ConversationRecyclerView.smali
smali17/com/tencent/mm/plugin/taskbar/ui/b0.smali
src_b0/.../taskbar/ui/b0.java
src_taskbar/.../TaskBarContainer.java
src_taskbar/.../TaskBarView.java
src_ab/.../androidx/appcompat/app/y0.java
src_ab2/.../androidx/appcompat/widget/z2.java

apktool/res/layout/bvp.xml
apktool/res/layout/bl.xml
apktool/res/layout/bm.xml
apktool/res/values/public.xml
```

---

## 16. 最终结论

微信 Top Bar 是：

```text
AppCompat WindowDecorActionBar
  + ActionBarContainer/Toolbar/ActionMenuView/customView
  + HomeUI
  + MainTabUI
  + MoreTabUI
  + ConversationListView
  + TaskBarAnimController
  + ActionBarOverlayLayout
```

它不是一个可简单隐藏的 View，而是 **多个状态机共同操作同一棵 View 树**。

旧 bar 的根源是：

```text
ConversationListView.attachViewToParent()
  → C = ActionBarContainer
  → TaskBarContainer.setActionBar(C)
  → TaskBarAnimController.V1 = C

ConversationListView.onScroll()
  → TaskBarAnimController.e()
  → s(0/4)
  → C.setVisibility(...)
```

以及：

```text
LauncherUI.onResume
  → WindowDecorActionBar.show()
  → ActionBarContainer.setVisibility(0)
```

要彻底解决，必须同时处理：

```text
b0.V1 / b0.s()
ActionBarContainer
Toolbar/ActionMenuView
HomeUI.s()
MoreTabUI.g1()
MainTabUI.onPageScrolled
```

而不是继续单点 `GONE`。
