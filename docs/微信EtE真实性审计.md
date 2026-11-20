# 微信 EtE 真实性审计

> 目标：不是“看起来像 EtE”，而是逐层证明：
> 1. Window 是否 full-bleed；
> 2. content 是否从 0 开始；
> 3. 顶上那条线/层到底属于 Window、ActionBar、ListView header，还是 TaskBarContainer；
> 4. 为什么“去掉了底层，还有垫底”。

---

## 1. 先定义：三种不同的 EtE

### A. Window EtE

```text
Window 扩展到状态栏/导航栏下面
setDecorFitsSystemWindows(false)
statusBarColor = TRANSPARENT
```

### B. Content EtE

```text
ContentFrameLayout / FirstScreenFrameLayout 从 0 开始
不被 ActionBarOverlayLayout 加 top margin
```

### C. Visual EtE

```text
第一屏内容真正画到状态栏下面
没有额外的 header / bar / divider / mask 挡住
```

微信现在最多做到 A + B，C 不一定成立。

---

## 2. Window 层证据

`LauncherUI`：

```java
getWindow().getDecorView().setSystemUiVisibility(1280);
// 1280 = LAYOUT_STABLE | LAYOUT_FULLSCREEN
```

`GlobalEdgeToEdge.force()`：

```java
window.setDecorFitsSystemWindows(false);
window.setStatusBarColor(Color.TRANSPARENT);
decor.setFitsSystemWindows(false);
decor.setOnApplyWindowInsetsListener(... status inset -> 0 ...);
```

所以：

```text
Window 层：是 EtE
```

---

## 3. Content 层证据

真机 dump：

```text
ActionBarOverlayLayout 0,0,1080,2400
ContentFrameLayout     0,0,1080,2400
FirstScreenFrameLayout 0,0,1080,2400
CustomViewPager        0,0,1080,2400
```

所以：

```text
Content 容器：full-bleed
```

但 `ActionBarOverlayLayout.onMeasure()`：

```java
measureChildWithMargins(this.f381g, i, 0, i2, 0);

int measuredHeight =
    this.f381g.getVisibility() != 8
        ? this.f381g.getMeasuredHeight()
        : 0;

rect4.top += measuredHeight;
a(this.f, this.v, true, true, true, true);
this.f.a(rect4);
```

含义：

```text
ActionBarContainer 可见
  → measuredHeight = 114
  → ContentFrameLayout top inset = 114

ActionBarContainer GONE
  → measuredHeight = 0
  → ContentFrameLayout top inset = 0
```

注意：`GONE` 和 `INVISIBLE` 不同：

- `GONE` 让 measuredHeight = 0；
- `INVISIBLE` 仍保留 measuredHeight。

`ActionBarContainer.setVisibility()`：

```java
super.setVisibility(i);
drawable.setVisible(i == 0, false);
```

它只改背景 Drawable，不改 inset；inset 由 `ActionBarOverlayLayout.onMeasure()` 读取。

---

## 4. List / Header 层：真正“垫底”的东西

### 4.1 `ConversationListView` 的 header

`ConversationListView.smali`：

```smali
iget-object v2, p0, ConversationListView;->i:Lcom/tencent/mm/plugin/taskbar/ui/s;
check-cast v2, Landroid/widget/RelativeLayout;
invoke-virtual {p0, v2}, Landroid/widget/ListView;->addHeaderView(Landroid/view/View;)V
```

`i` 是 `TaskBarContainer`。

所以：

```text
ConversationListView
└─ headerView = TaskBarContainer
```

`TaskBarContainer` 的布局是 `cxv.xml`：

```xml
<RelativeLayout>
    <FrameLayout id=a0i>
        <LinearLayout id=wn>

            <RelativeLayout>
                <TextView id=wp text="TaskBar" />
                ...
            </RelativeLayout>

            <View
                android:background="#1a000000"
                android:layout_width="fill_parent"
                android:layout_height="1.0px" />   <!-- 线 -->

            <LinearLayout>
                <TaskBarView id=wq />
            </LinearLayout>

        </LinearLayout>
    </FrameLayout>
</RelativeLayout>
```

`cxv.xml` 里的：

```xml
<View
    android:background="#1a000000"
    android:layout_width="fill_parent"
    android:layout_height="1.0px" />
```

就是最可疑的“线”。

### 4.2 `TaskBarContainer` 内部的 fake top bar

`TaskBarContainer.e(Context)`：

```java
View viewA = inflate(2131631251);   // cxv.xml
FrameLayout frameLayout2 = viewA.findViewById(2131362937);
View viewInflate = inflate(2131631252); // cxw.xml
this.o = viewInflate;
frameLayout2.addView(this.o, -1, -1);
```

`cxw.xml`：

```xml
<FrameLayout>
    <TaskBarBottomView height=56dp />
    <RelativeLayout id=cg7 height=56dp>
        <LinearLayout id=actionbar_up_indicator>
            <WeImageView
                id=actionbar_up_indicator_btn
                src=@raw/icons_outlined_multitask />
        </LinearLayout>
        <include layout=@layout/bn />   <!-- 标题 -->
        <WeImageView id=meb src=actionbar_icon_dark_search />
        <WeImageView id=plus_icon src=icons_outlined_add2 />
    </RelativeLayout>
</FrameLayout>
```

所以旧 bar 的：

```text
多任务 / 标题 / 搜索 / 加号
```

实际来自：

```text
TaskBarContainer.this.o = cxw.xml
```

不是 Activity ActionBar。

### 4.3 `MainUI` 的 status mask

`MainUI`：

```java
this.q = findViewById(2131387809); // nhm
this.o.L(viewFindViewById);
```

`MainUI.H0()`：

```java
View view = this.q;
view.getLayoutParams().height = statusBarHeight;
```

`bvn.xml`：

```xml
<View id=nhm
    android:layout_width="fill_parent"
    android:layout_height="0.0dip"
    android:layout_alignParentTop="true" />
```

它是 `MainUIView` 顶部的 status mask。

### 4.4 `MainUI` 的 list topMargin

`MainUI.H0()` 还会：

```java
RelativeLayout.LayoutParams params =
        (RelativeLayout.LayoutParams) this.o.v();
params.topMargin = i;   // i = actionBarHeight
```

以及：

```java
int statusBarHeight = ol.i(getContext(), -1);
int actionBarHeight = kb5.o1.a(...);
H0(showHeaderContainer ? 0 : actionBarHeight, statusBarHeight);
```

所以微信主页默认：

```text
ConversationListView.topMargin = actionBarHeight
```

### 4.5 `FindMoreFriendsUI` 的 root padding

```java
getView().setPadding(0, actionBarHeight, 0, tabBarHeight);
getView().postDelayed(runnable, 100L);
```

发现页每 100ms 重设 root padding。

### 4.6 `MoreTabUI` 的 PullDownListView padding

```java
pullDownListView.setPadding(..., this.u.getPaddingTop(), ..., 0);
```

---

## 5. 当前是不是“纯 EtE”

### 结论

```text
Window EtE：是
Content 容器 full-bleed：是
Visual EtE：否
```

原因：

1. `ConversationListView` 仍然有顶部 header（TaskBarContainer）；
2. `TaskBarContainer` 内部有 `cxw.xml` fake top bar；
3. `cxv.xml` 内部有一条 1px `#1a000000` 线；
4. 主页列表仍有 `topMargin = actionBarHeight`；
5. `MainUI` 还有 status mask；
6. `FindMoreFriendsUI` / `MoreTabUI` 还有自己的 root/list padding；
7. 旧 ActionBarContainer 可能仍参与 `ActionBarOverlayLayout` inset。

所以：

```text
不是“纯 EtE”
而是“Window/Content 已经 EtE，但列表顶部还有一条 TaskBar header 层”
```

---

## 6. 那条线为什么没消失

线有三个候选：

### 候选 1：`cxv.xml` 的 1px View

```xml
<View
    android:background="#1a000000"
    android:layout_height="1.0px" />
```

它在 `TaskBarContainer` header 里，随 ListView 滚动，特征最吻合“能滑下去”。

### 候选 2：`ActionBarOverlayLayout.i`

```java
public Drawable i; // windowContentOverlay
```

```java
int bottom = f381g.getVisibility() == 0
        ? f381g.getBottom() + f381g.getTranslationY()
        : 0;

i.setBounds(0, bottom, width, intrinsicHeight + bottom);
i.draw(canvas);
```

当 `ActionBarContainer` GONE 时 `bottom=0`，阴影会画在顶部。

它**不随 ListView 滚动**。

### 候选 3：`ConversationListView` 自身的 divider/header

ListView 的 divider 或 header 背景也可能形成一条线。

---

## 7. 为什么“去掉底层还有垫底”

因为有两层完全不同的东西：

```text
底层：Activity ActionBarContainer
  └─ WindowDecorActionBar
  └─ TaskBarAnimController.V1

垫底层：ConversationListView header
  └─ TaskBarContainer (cxv.xml)
     ├─ cxw.xml fake top bar
     ├─ 1px divider
     └─ TaskBarView
```

只去掉 `ActionBarContainer`，垫底的 `TaskBarContainer` header 还在。

只去掉 `TaskBarContainer`，`ActionBarContainer` 仍可能由 WindowDecorActionBar show。

---

## 8. 完整的顶部层清单

```text
1. StatusBar
   system UI

2. DecorView / FitSystemWindowLayoutView
   0..2400

3. ActionBarOverlayLayout
   ├─ ContentFrameLayout
   └─ ActionBarContainer

4. ActionBarContainer
   ├─ Toolbar
   │  ├─ navigation
   │  ├─ title/subtitle
   │  ├─ ActionMenuView
   │  └─ bl.xml customView
   └─ windowContentOverlay shadow

5. ConversationListView
   ├─ headerView = TaskBarContainer (cxv)
   │  ├─ fake top bar (cxw)
   │  ├─ 1px divider
   │  └─ TaskBarView
   ├─ status mask nhm
   └─ list items

6. MainUI
   ├─ ConversationListView.topMargin = actionBarHeight
   └─ status mask height = statusBarHeight

7. FindMoreFriendsUI
   └─ root.paddingTop = actionBarHeight

8. MoreTabUI
   └─ PullDownListView.paddingTop
```

---

## 9. 判断是否纯 EtE 的检查表

### Window

- [x] `setDecorFitsSystemWindows(false)`
- [x] `statusBarColor=TRANSPARENT`
- [x] `decor` full 0..2400

### Content

- [x] `ContentFrameLayout` 0..2400
- [x] `FirstScreenFrameLayout` 0..2400
- [ ] `ActionBarOverlayLayout` content top inset = 0
- [ ] `ActionBarContainer measuredHeight = 0`

### List

- [ ] `ConversationListView.topMargin = 0`
- [ ] `ConversationListView` 没有 TaskBarContainer header
- [ ] `TaskBarContainer` 没有 `cxw` fake top bar
- [ ] `cxv` 1px line 移除
- [ ] `MainUI` status mask 不挡
- [ ] `FindMoreFriendsUI` root padding 清掉
- [ ] `MoreTabUI` PullDownListView padding 清掉

### Visual

- [ ] 第一条会话能从 0 开始
- [ ] 状态栏下直接是内容，不是 bar/线/header
- [ ] 顶部没有不可交互的残留层

---

## 10. 下一步处理顺序

1. 定位 `cxv.xml` 的 1px View，移除；
2. 定位 `TaskBarContainer.o`（`cxw.xml`），移除/隐藏；
3. 从 `ConversationListView` header 中移除 `TaskBarContainer`；
4. 清 `ConversationListView.topMargin`；
5. 清 `MainUI` status mask；
6. 清 `FindMoreFriendsUI` / `MoreTabUI` padding；
7. 再判断 Window/Content 是否真的 full-bleed；
8. 最后才做其他 hook。

---

## 11. 未确认点

- `TaskBarContainer` 是否在 `decor` 的可见 View 树里，还是只在 ListView header 的 `FixedViewInfo` 中；
- `cxv` 的 1px View 在运行时的 LayoutParams 高度是否仍为 1；
- `ActionBarOverlayLayout.i` 是否在 WeChat 中被重新设置；
- `TaskBarContainer` header 移除后，滚动/动画是否仍正常；
- `ConversationRecyclerView` 分支是否有同样的 header。
