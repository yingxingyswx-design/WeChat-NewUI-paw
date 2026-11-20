param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Message
)

$ErrorActionPreference = 'Stop'
$dev = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = 'C:\git\wechat_chat_ui'
if (!(Test-Path (Join-Path $repo 'backup.ps1'))) {
    throw "backup repo not found: $repo"
}

# Mirror the source tree into the backup repo.
foreach ($dir in 'app', 'docs') {
    robocopy (Join-Path $dev $dir) (Join-Path $repo $dir) /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed: $dir ($LASTEXITCODE)" }
}
foreach ($file in 'build.ps1', 'README.md', 'BACKUP.md', '.gitignore', 'debug.keystore') {
    $src = Join-Path $dev $file
    if (Test-Path $src) { Copy-Item $src (Join-Path $repo $file) -Force }
}
Copy-Item (Join-Path $dev 'publish.ps1') (Join-Path $repo 'publish.ps1') -Force

# Commit + snapshot in the backup repo (same flow as before).
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'backup.ps1') -Message $Message
exit $LASTEXITCODE
