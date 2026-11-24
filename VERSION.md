# WeChat-NewUI v1.3.1

- versionCode：`7`
- versionName：`1.3.1.release`（开发版为 `1.3.1.dev`）
- Release tag：`7-1.3.1`（发布时）
- APK：`wechat-glass-1.3.1.apk`
- 源码：`wechat-glass-1.3.1-source.zip`
- 作用域：仅 `com.tencent.mm`（微信）

## 1.3.1 新增：版本号熔断（circuit breaker）

版本号里带 `release` 的构建，会在启动时**自动拒绝安装探测型高风险 hook**
（钱包页采样 hook：`MallIndexUIv2.onResume` / `UIPageFragmentActivity.onResume`），
只保留常规 UI hook。日志会打印：

```
WeChatGlass wallet probe disabled (release build)<versionName>
```

命令：

```
# 开发版（允许探测 hook）
versionName = "1.3.2.dev"

# 发布版（自动熔断，探测 hook 不安装）
versionName = "1.3.2.release"
```

对应代码：`BuildFlags.devProbesAllowed()`，在 `HookEntry` 安装 wallet hook 时与
`HomeGlassHook` 的被动采样循环处各判断一次。

## 由此产生的行为差异

- **dev 构建**：余额卡片可被动采样（用户打开钱包页后读取金额并缓存）。
- **release 构建**：不装采样 hook，余额卡片只能显示已有缓存（通常是“—”），
  不会读取任何支付页面。这是刻意为之：发布包绝不允许带支付域 hook。

## 1.3.0 / 1.3.0.1 的内容

- M3E 化的「通讯录 / 发现 / 我」三页；首页扫一扫按钮；首页长按转发原生会话菜单；
  首页状态栏透明；系统账号去重；四个 tab 统一背景。
- 微信余额卡片（默认关闭、开启前红色二次确认）：只被动采样已渲染的金额。
- 1.3.0.1：移除探测期遗留的 `hookFreGriest`（`hookAllMethods` + `setResult`，从未生效）；
  发现页条目随「发现页管理」设置变化重建。

## 已知问题（下一版修复）

- 从「服务」等原生页面返回后，首页可能掉回微信原生列表。
