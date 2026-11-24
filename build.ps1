param(
    [switch]$DebugSigning
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$stage = 'C:\wcg_build'
$app = Join-Path $stage 'app\src\main'
$build = Join-Path $stage 'build'
$out = Join-Path $root 'out'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$androidJar = Join-Path $sdk 'platforms\android-36\android.jar'
$bt = Join-Path $sdk 'build-tools\36.0.0'
if (!(Test-Path $androidJar)) { throw "android.jar not found: $androidJar" }

Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item (Join-Path $root 'app') (Join-Path $stage 'app') -Recurse -Force
New-Item -ItemType Directory -Force -Path $build, "$build\classes", "$build\dex", $out | Out-Null

$xposedApi = Join-Path $stage 'app\libs\api-82.jar'
if (!(Test-Path $xposedApi)) { throw "xposed api not found: $xposedApi" }

# The manifest is shared with the Gradle build, which takes the package name
# from `namespace` and therefore has no package="" attribute. aapt2 link still
# requires one, so add it to the staged copy only.
$manifestPath = Join-Path $app 'AndroidManifest.xml'
$manifestText = [System.IO.File]::ReadAllText($manifestPath)
if ($manifestText -notmatch '\spackage\s*=') {
    $manifestText = $manifestText -replace '<manifest\s', '<manifest package="io.github.yingxingyswx_design.wechatglass" '
    [System.IO.File]::WriteAllText($manifestPath, $manifestText,
        (New-Object System.Text.UTF8Encoding($false)))
}

Write-Host '=== aapt2 compile ==='
& "$bt\aapt2.exe" compile --dir "$app\res" -o "$build\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '=== aapt2 link ==='
& "$bt\aapt2.exe" link -o "$build\base.apk" -I $androidJar `
    --manifest "$app\AndroidManifest.xml" `
    -R "$build\res.zip" `
    --auto-add-overlay `
    -A "$app\assets" `
    --min-sdk-version 24 --target-sdk-version 34 `
    --version-code 2 --version-name 1.1
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '=== javac ==='
$sources = Get-ChildItem "$app\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
# javac writes deprecation notes to stderr; PowerShell 5.1 turns a native
# command's stderr into a terminating error under $ErrorActionPreference='Stop',
# which aborted the build before the exit code was ever looked at.
$pref = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 --release 8 -nowarn `
    -cp "$androidJar;$xposedApi" -d "$build\classes" $sources
$javacCode = $LASTEXITCODE
$ErrorActionPreference = $pref
if ($javacCode -ne 0) { throw 'javac failed' }

Write-Host '=== d8 ==='
$classes = Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
# D8 accepts an argument file.  Passing every generated class on the command
# line works until the source tree crosses Windows' command-line limit.
$d8Args = Join-Path $build 'd8.args'
@('--lib', $androidJar, '--min-api', '24', '--output', "$build\dex") + $classes |
    Set-Content -LiteralPath $d8Args -Encoding ASCII
& "$bt\d8.bat" "@$d8Args"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '=== package classes.dex ==='
$py = 'C:\Users\paw_C\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$dex = Join-Path $build 'dex\classes.dex'
$apk = Join-Path $build 'base.apk'
& $py -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'classes.dex'); z.close()" $apk $dex
if ($LASTEXITCODE -ne 0) { throw 'adding classes.dex failed' }

Write-Host '=== zipalign ==='
& "$bt\zipalign.exe" -f 4 $apk "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

# Signing: a real release key by default. The debug keystore is public (it is
# committed), so anyone could sign an APK that Android would accept as an update
# for debug-signed installs. New builds use release.keystore instead.
$storePass = 'android'
$keyPass = 'android'
$alias = 'androiddebugkey'

if ($DebugSigning) {
    $projectKeystore = Join-Path $root 'debug.keystore'
    $keystore = Join-Path $stage 'debug.keystore'
    if (Test-Path $projectKeystore) {
        Copy-Item $projectKeystore $keystore -Force
    } else {
        Write-Host '=== create debug keystore ==='
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $keystore `
            -storepass $storePass -alias $alias -keypass $keyPass `
            -keyalg RSA -keysize 2048 -validity 10000 `
            -dname "CN=Android Debug,O=Android,C=US"
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
        Copy-Item $keystore $projectKeystore -Force
    }
} else {
    $projectKeystore = Join-Path $root 'release.keystore'
    $propsFile = Join-Path $root 'release.keystore.properties'
    $keystore = Join-Path $stage 'release.keystore'
    $alias = 'wechatnewui'

    if (Test-Path $propsFile) {
        $line = Get-Content $propsFile |
            Where-Object { $_ -like 'storePass=*' } |
            Select-Object -First 1
        if ($line) { $storePass = $line.Substring('storePass='.Length).Trim() }
    } elseif (Test-Path $projectKeystore) {
        throw "release.keystore exists but $propsFile is missing; cannot recover its password."
    }

    if (!(Test-Path $projectKeystore)) {
        Write-Host '=== create release keystore (back this file up!) ==='
        $bytes = New-Object byte[] 18
        $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
        $rng.GetBytes($bytes)
        $rng.Dispose()
        $storePass = ([Convert]::ToBase64String($bytes) -replace '[+/=]', 'x')
        "storePass=$storePass" | Set-Content -Encoding ASCII $propsFile
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $projectKeystore `
            -storepass $storePass -alias $alias -keypass $storePass `
            -keyalg RSA -keysize 2048 -validity 10000 `
            -dname "CN=WeChat NewUI,O=Paw,C=CN"
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    }
    $keyPass = $storePass
    Copy-Item $projectKeystore $keystore -Force
}

Write-Host '=== sign ==='
$final = Join-Path $build 'wechat-glass-1.1.apk'
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass "pass:$storePass" `
    --key-pass "pass:$keyPass" --out $final "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '=== verify ==='
& "$bt\apksigner.bat" verify --print-certs $final
$published = Join-Path $out 'wechat-glass-1.1.apk'
Copy-Item $final $published -Force
Write-Host "APK: $published"
Get-Item $published | Select-Object Length, FullName
