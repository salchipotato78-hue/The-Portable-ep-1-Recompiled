$ErrorActionPreference = "Stop"

# Repo root = parent of the android\ folder this script lives in (works for any clone location).
$Root = Split-Path -Parent $PSScriptRoot
$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$BuildTools = Get-ChildItem (Join-Path $Sdk "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$Platform = Get-ChildItem (Join-Path $Sdk "platforms") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$Ndk = Get-ChildItem (Join-Path $Sdk "ndk") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$AndroidJar = Join-Path $Platform.FullName "android.jar"
$Aapt2 = Join-Path $BuildTools.FullName "aapt2.exe"
$D8 = Join-Path $BuildTools.FullName "d8.bat"
$Zipalign = Join-Path $BuildTools.FullName "zipalign.exe"
$Apksigner = Join-Path $BuildTools.FullName "apksigner.bat"

$Work = Join-Path $Root "android\build-apk"
$Dist = Join-Path $Root "android\dist"
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force -Path $Work, $Dist | Out-Null

$Classes = Join-Path $Work "classes"
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
$SdlJava = Join-Path $Root "third_party\rexglue-sdk\thirdparty\sdl3\android-project\app\src\main\java"
$AppJava = Join-Path $Root "android\app\src\main\java"
$Sources = @(Get-ChildItem $SdlJava, $AppJava -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -classpath $AndroidJar -d $Classes @Sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$ClassFiles = @(Get-ChildItem $Classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& $D8 --min-api 28 --output $Work @ClassFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$ResZip = Join-Path $Work "resources.zip"
& $Aapt2 compile --dir (Join-Path $Root "android\app\src\main\res") -o $ResZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

$Unsigned = Join-Path $Work "unsigned.apk"
& $Aapt2 link -o $Unsigned -I $AndroidJar --manifest (Join-Path $Root "android\app\src\main\AndroidManifest.xml") --min-sdk-version 28 --target-sdk-version 35 $ResZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$LibDir = "lib/arm64-v8a"
$Libs = @(
    (Join-Path $Root "android\build-arm64\libmain.so"),
    (Join-Path $Root "android\build-arm64\librexgpu-xenos.so"),
    (Join-Path $Root "third_party\rexglue-sdk\out\android-aarch64\librexruntime.so"),
    (Join-Path $Ndk.FullName "toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\aarch64-linux-android\libc++_shared.so")
)
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip = [System.IO.Compression.ZipFile]::Open($Unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $Dex = Join-Path $Work "classes.dex"
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip, $Dex, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    foreach ($Lib in $Libs) {
        if (-not (Test-Path $Lib)) { throw "Missing native library $Lib" }
        $Name = "$LibDir/" + [System.IO.Path]::GetFileName($Lib)
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip, $Lib, $Name, [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
    }
} finally {
    $Zip.Dispose()
}

$Aligned = Join-Path $Work "aligned.apk"
& $Zipalign -P 16 -f 4 $Unsigned $Aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$Keystore = Join-Path $Root "android\debug.keystore"
if (-not (Test-Path $Keystore)) {
    & keytool -genkeypair -keystore $Keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Sonic4 Debug,O=S4EP1Recomp,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

$OutApk = Join-Path $Dist "Sonic4-arm64-v8a.apk"
& $Apksigner sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $OutApk $Aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
Write-Host "Wrote $OutApk"
