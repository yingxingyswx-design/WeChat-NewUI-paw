# 源码备份与提交流程

本仓库 `C:\git\wechat_chat_ui` 是 WeChat NewUI 模块的**带版本备份仓库**。

- 基线：GitHub release **v0.2**（tag `v0.2`，commit `89fd040`）
- 分支：`release-v0.2`（从 `v0.2` 检出，后续所有改动提交到这里）
- 上游：`https://github.com/yingxingyswx-design/WeChat-NewUI-paw.git`

## 每次改动只做一件事

```powershell
cd C:\git\wechat_chat_ui
.\backup.cmd -Message "fix: 移除 TaskBar header 的 1px 分割线"
```

`backup.cmd` 只是用 `-ExecutionPolicy Bypass` 转调 `backup.ps1`，规避本机“禁止运行脚本”的策略。也可以手动执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backup.ps1 -Message "..."
```

脚本会依次完成：

1. `git add -A` + `git commit -m "<Message>"`（没有改动时跳过提交）；
2. 在 `backups\<yyyyMMdd-HHmmss>_<短SHA>\` 生成：
   - `<快照名>-source.zip`：源码压缩包（`app/`、`docs/`、`build.ps1`、`BACKUP.md`、`README.md`、`.gitignore`、`debug.keystore`；不含 `.git`、`tools/apktool.jar`、`out`、`build`、`.idea`）；
   - `CHANGES.md`：本次提交的 SHA、分支、改动文件清单、diffstat、还原命令和文档链接；
3. 向 `backups\INDEX.md` 追加一行索引。

`backups/` 已在 `.gitignore` 中，zip 只落在磁盘上，不进入 git 历史（避免每次 20MB+ 的二进制膨胀）；代码本身由 git 提交保全。

## 参数

| 参数 | 说明 |
|---|---|
| `-Message` | 必填，提交说明 |
| `-NoCommit` | 不提交，只为当前 HEAD 生成快照 |

## 还原某个快照

```powershell
git checkout <短SHA>          # 回到某次提交
# 或
Expand-Archive backups\<快照名>\<快照名>-source.zip -DestinationPath <目标目录>
```

## 签名密钥

- `build.ps1` 默认使用 `release.keystore` + `release.keystore.properties`（随机口令），首次构建自动生成；
- 两者已在 `.gitignore` 中，**不会进入 git，也不会进入源码 zip**；
- 请单独离线备份这两个文件：丢失后无法再给已安装用户发更新，只能让用户卸载重装；
- 需要复现旧的 debug 签名时用 `build.ps1 -DebugSigning`。

## 与 `C:\codes\模块\wechat_chat_ui` 的关系

`C:\codes\模块\wechat_chat_ui` 是历史工作副本（含未提交的实验性改动）。**从本仓库建立基线后，以本仓库为准**：新改动直接在本仓库编辑并 `backup.ps1` 提交。

如果确实在旧副本里改过文件，先同步过来再提交（不要覆盖 `docs/`、`backups/`）：

```powershell
robocopy "C:\codes\模块\wechat_chat_ui\app" "C:\git\wechat_chat_ui\app" /MIR /XD .git
robocopy "C:\codes\模块\wechat_chat_ui" "C:\git\wechat_chat_ui" build.ps1 README.md debug.keystore /XO
```

## 构建验证

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

依赖 Android SDK 36 与 JDK 17；产物在 `out\wechat-glass-1.0.apk`。
