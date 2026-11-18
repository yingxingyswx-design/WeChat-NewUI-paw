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
    --version-code 1 --version-name 1.0
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '=== javac ==='
$sources = Get-ChildItem "$app\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 --release 8 `
    -cp "$androidJar;$xposedApi" -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '=== d8 ==='
$classes = Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --lib $androidJar --min-api 24 --output "$build\dex" $classes
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

$projectKeystore = Join-Path $root 'debug.keystore'
$keystore = Join-Path $stage 'debug.keystore'
if (Test-Path $projectKeystore) {
    Copy-Item $projectKeystore $keystore -Force
} else {
    Write-Host '=== create debug keystore ==='
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $keystore `
        -storepass android -alias androiddebugkey -keypass android `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    Copy-Item $keystore $projectKeystore -Force
}

Write-Host '=== sign ==='
$final = Join-Path $build 'wechat-glass-1.0.apk'
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --out $final "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '=== verify ==='
& "$bt\apksigner.bat" verify --print-certs $final
$published = Join-Path $out 'wechat-glass-1.0.apk'
Copy-Item $final $published -Force
Write-Host "APK: $published"
Get-Item $published | Select-Object Length, FullName
