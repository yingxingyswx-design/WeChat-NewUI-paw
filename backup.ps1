<#
.SYNOPSIS
    ???????????? zip ????? md ???

.DESCRIPTION
    1. git add -A / git commit -m <Message>?????????
    2. ? backups\<??>_<?SHA>\ ?? <???>-source.zip ? CHANGES.md?
    3. ? backups\INDEX.md ?????

.PARAMETER Message
    ?????

.PARAMETER NoCommit
    ???????? HEAD ?????

.EXAMPLE
    .\backup.ps1 -Message "fix: ?? TaskBar header ? 1px ???"
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
        throw ("git " + ($GitArgs -join ' ') + " ???exit $code??`n" + ($output -join "`n"))
    }
    return $output
}

function Get-Text {
    param([string[]]$Lines)
    if ($null -eq $Lines) { return '' }
    return (($Lines | ForEach-Object { [string]$_ }) -join "`n").TrimEnd()
}

# --- 0. ???? ---------------------------------------------------------
$null = Invoke-Git rev-parse --is-inside-work-tree
$branch = Get-Text (Invoke-Git rev-parse --abbrev-ref HEAD)

# --- 1. ?? -------------------------------------------------------------
$pending = Get-Text (Invoke-Git status --porcelain)
if (-not $NoCommit) {
    if ($pending) {
        $null = Invoke-Git add -A
        $null = Invoke-Git commit -m $Message
        Write-Host "[commit] $Message"
    }
    else {
        Write-Host '[commit] ???????????? HEAD ?????'
    }
}
elseif ($pending) {
    Write-Warning '????????-NoCommit ????????????? zip?'
}

# --- 2. ???? ---------------------------------------------------------
$sha = Get-Text (Invoke-Git rev-parse --short HEAD)
$fullSha = Get-Text (Invoke-Git rev-parse HEAD)
$subject = Get-Text (Invoke-Git log -1 --format=%s)
$commitDate = Get-Text (Invoke-Git log -1 --format=%ci)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$snapName = "${stamp}_${sha}"
$snapDir = Join-Path $repo "backups\$snapName"
New-Item -ItemType Directory -Force -Path $snapDir | Out-Null

# --- 3. ?? zip ---------------------------------------------------------
$zipPath = Join-Path $snapDir "$snapName-source.zip"
$items = @('app', 'docs', 'build.ps1', 'BACKUP.md', 'README.md', '.gitignore', 'debug.keystore', 'publish.ps1') |
    ForEach-Object { Join-Path $repo $_ } |
    Where-Object { Test-Path $_ }
Compress-Archive -Path $items -DestinationPath $zipPath -CompressionLevel Optimal -Force
$zipSize = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)

# --- 4. ???? md ------------------------------------------------------
$nameStatus = Get-Text (Invoke-Git show --name-status --format= HEAD)
$stat = Get-Text (Invoke-Git show --stat --oneline --no-renames --format= HEAD)
if ([string]::IsNullOrWhiteSpace($nameStatus)) { $nameStatus = '????????????' }

$md = @"
# ???? ${snapName}

| ?? | ? |
|---|---|
| ?? | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') |
| ?? | ${branch} |
| ?? | ${sha}?${fullSha}? |
| ???? | ${subject} |
| ???? | ${commitDate} |
| ?? | GitHub release v0.2 |
| ??? | ${snapName}-source.zip?${zipSize} MB? |

## ?????git show --name-status?

``````text
${nameStatus}
``````

## ?????git show --stat?

``````text
${stat}
``````

## ?????

``app/``?``docs/``?``build.ps1``?``BACKUP.md``?``README.md``?``.gitignore``?``debug.keystore``?
??? ``.git``?``tools/apktool.jar``?``out``?``build``?``.idea``?``backups``?

## ??

``````powershell
git checkout ${sha}
# ?
Expand-Archive "${snapName}-source.zip" -DestinationPath <????>
``````

## ????

- [TopBar ? UI ?????](../../docs/??TopBar?UI?????.md)
- [EtE ?????](../../docs/??EtE?????.md)
"@

$mdPath = Join-Path $snapDir 'CHANGES.md'
[System.IO.File]::WriteAllText($mdPath, $md, (New-Object System.Text.UTF8Encoding($false)))

# --- 5. ?? -------------------------------------------------------------
$indexPath = Join-Path $repo 'backups\INDEX.md'
if (-not (Test-Path $indexPath)) {
    $header = "# ????`n`n| ?? | ?? | ?? | ?? |`n|---|---|---|---|"
    [System.IO.File]::WriteAllText($indexPath, $header + "`n", (New-Object System.Text.UTF8Encoding($false)))
}
$row = "| $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') | ${sha} | $($subject -replace '\|', '\|') | [${snapName}](./${snapName}/CHANGES.md) |`n"
[System.IO.File]::AppendAllText($indexPath, $row, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host "[snapshot] $snapDir"
Write-Host "  - $snapName-source.zip ($zipSize MB)"
Write-Host '  - CHANGES.md'
Write-Host "[index] $indexPath"

