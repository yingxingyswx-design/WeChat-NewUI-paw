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

## 两个目录的分工

| 目录 | 角色 | 是否提交 |
|---|---|---|
| `C:\codes\模块\wechat_chat_ui` | **开发区**：编辑代码、构建、装 APK | 不是 git 仓库，只保留工作树 |
| `C:\git\wechat_chat_ui` | **备份区**：只接收快照提交 | 是 git 仓库，历史都在这里 |

**不要在备份区直接改代码**。日常流程是：

```powershell
cd C:\codes\模块\wechat_chat_ui
# 编辑 app/、docs/、build.ps1 ...
.\publish.ps1 -Message "fix: ..."
```

`publish.ps1` 会把开发区的 `app/`、`docs/`、`build.ps1`、`README.md`、`BACKUP.md`、`.gitignore`、`debug.keystore` 镜像到备份区，然后调用备份区的 `backup.ps1` 提交并生成 zip + `CHANGES.md` 快照。

## 构建验证

推荐 Gradle：

```powershell
cd C:\codes\模块\wechat_chat_ui
.\gradlew.bat :app:assembleRelease
```

依赖 Android SDK 36、JDK 17、AGP 9.4.1 / Gradle 9.8.2；`local.properties` 指向本机 SDK；产物在 `out\wechat-glass-1.1.apk`。

旧的 `build.ps1` 仍可用作离线回退：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -DebugSigning
```
