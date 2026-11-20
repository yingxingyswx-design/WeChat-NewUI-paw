<#
.SYNOPSIS
    提交当前改动，并生成源码 zip 与变更说明 md 快照。

.DESCRIPTION
    1. git add -A / git commit -m <Message>（无改动时跳过）；
    2. 在 backups\<时间>_<短SHA>\ 生成 <快照名>-source.zip 和 CHANGES.md；
    3. 在 backups\INDEX.md 追加索引。

.PARAMETER Message
    提交说明。

.PARAMETER NoCommit
    不提交，只为当前 HEAD 生成快照。

.EXAMPLE
    .\backup.ps1 -Message "fix: 清理 TaskBar header 的 1px 分割线"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Message,

    [switch]$NoCommit
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repo

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & git @GitArgs 2>&1 | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { $_ }
    }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($code -ne 0) {
        throw ("git " + ($GitArgs -join ' ') + " 失败（exit $code）：`n" + ($output -join "`n"))
    }
    return $output
}

function Get-Text {
    param([string[]]$Lines)
    if ($null -eq $Lines) { return '' }
    return (($Lines | ForEach-Object { [string]$_ }) -join "`n").TrimEnd()
}

# --- 0. 前置检查 ---------------------------------------------------------
$null = Invoke-Git rev-parse --is-inside-work-tree
$branch = Get-Text (Invoke-Git rev-parse --abbrev-ref HEAD)

# --- 1. 提交 -------------------------------------------------------------
$pending = Get-Text (Invoke-Git status --porcelain)
if (-not $NoCommit) {
    if ($pending) {
        $null = Invoke-Git add -A
        $null = Invoke-Git commit -m $Message
        Write-Host "[commit] $Message"
    }
    else {
        Write-Host '[commit] 没有待提交改动，仅为当前 HEAD 生成快照。'
    }
}
elseif ($pending) {
    Write-Warning '存在未提交改动；-NoCommit 模式下这些改动不会进入快照 zip。'
}

# --- 2. 快照信息 ---------------------------------------------------------
$sha = Get-Text (Invoke-Git rev-parse --short HEAD)
$fullSha = Get-Text (Invoke-Git rev-parse HEAD)
$subject = Get-Text (Invoke-Git log -1 --format=%s)
$commitDate = Get-Text (Invoke-Git log -1 --format=%ci)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$snapName = "${stamp}_${sha}"
$snapDir = Join-Path $repo "backups\$snapName"
New-Item -ItemType Directory -Force -Path $snapDir | Out-Null

# --- 3. 源码 zip ---------------------------------------------------------
$zipPath = Join-Path $snapDir "$snapName-source.zip"
$items = @('app', 'docs', 'build.ps1', 'BACKUP.md', 'README.md', '.gitignore', 'debug.keystore') |
    ForEach-Object { Join-Path $repo $_ } |
    Where-Object { Test-Path $_ }
Compress-Archive -Path $items -DestinationPath $zipPath -CompressionLevel Optimal -Force
$zipSize = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)

# --- 4. 变更说明 md ------------------------------------------------------
$nameStatus = Get-Text (Invoke-Git show --name-status --format= HEAD)
$stat = Get-Text (Invoke-Git show --stat --oneline --no-renames --format= HEAD)
if ([string]::IsNullOrWhiteSpace($nameStatus)) { $nameStatus = '（基线快照，无文件改动）' }

$md = @"
# 源码备份 ${snapName}

| 项目 | 值 |
|---|---|
| 时间 | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') |
| 分支 | ${branch} |
| 提交 | ${sha}（${fullSha}） |
| 提交说明 | ${subject} |
| 提交时间 | ${commitDate} |
| 基线 | GitHub release v0.2 |
| 源码包 | ${snapName}-source.zip（${zipSize} MB） |

## 本次改动（git show --name-status）

``````text
${nameStatus}
``````

## 提交统计（git show --stat）

``````text
${stat}
``````

## 源码包内容

``app/``、``docs/``、``build.ps1``、``BACKUP.md``、``README.md``、``.gitignore``、``debug.keystore``。
不包含 ``.git``、``tools/apktool.jar``、``out``、``build``、``.idea``、``backups``。

## 还原

``````powershell
git checkout ${sha}
# 或
Expand-Archive "${snapName}-source.zip" -DestinationPath <目标目录>
``````

## 参考文档

- [TopBar 与 UI 架构总报告](../../docs/微信TopBar与UI架构总报告.md)
- [EtE 真实性审计](../../docs/微信EtE真实性审计.md)
"@

$mdPath = Join-Path $snapDir 'CHANGES.md'
[System.IO.File]::WriteAllText($mdPath, $md, (New-Object System.Text.UTF8Encoding($false)))

# --- 5. 索引 -------------------------------------------------------------
$indexPath = Join-Path $repo 'backups\INDEX.md'
if (-not (Test-Path $indexPath)) {
    $header = "# 备份索引`n`n| 时间 | 提交 | 说明 | 快照 |`n|---|---|---|---|"
    [System.IO.File]::WriteAllText($indexPath, $header + "`n", (New-Object System.Text.UTF8Encoding($false)))
}
$row = "| $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') | ${sha} | $($subject -replace '\|', '\|') | [${snapName}](./${snapName}/CHANGES.md) |`n"
[System.IO.File]::AppendAllText($indexPath, $row, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "[snapshot] $snapDir"
Write-Host "  - $snapName-source.zip ($zipSize MB)"
Write-Host '  - CHANGES.md'
Write-Host "[index] $indexPath"
