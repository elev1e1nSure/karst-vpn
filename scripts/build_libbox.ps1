param(
    [string]$SingBoxTag = "v1.13.14",
    [string]$WorkDir = "$PSScriptRoot\..\build\sing-box",
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$AndroidNdk = $env:ANDROID_NDK_HOME
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path -LiteralPath "$PSScriptRoot\.."
$workPath = [System.IO.Path]::GetFullPath($WorkDir)
$outputPath = Join-Path $projectRoot "app\libs\libbox.aar"

if ([string]::IsNullOrWhiteSpace($AndroidSdk)) {
    $localProperties = Join-Path $projectRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1
        $AndroidSdk = ($sdkLine -replace "^sdk.dir=", "").Replace("\\", "\")
    }
}

if ([string]::IsNullOrWhiteSpace($AndroidSdk) -or !(Test-Path -LiteralPath $AndroidSdk)) {
    throw "Android SDK path not found. Set ANDROID_HOME or sdk.dir in local.properties."
}

if ([string]::IsNullOrWhiteSpace($AndroidNdk)) {
    $ndkRoot = Join-Path $AndroidSdk "ndk"
    $AndroidNdk = Get-ChildItem -LiteralPath $ndkRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

if ([string]::IsNullOrWhiteSpace($AndroidNdk) -or !(Test-Path -LiteralPath (Join-Path $AndroidNdk "source.properties"))) {
    throw "Android NDK path not found. Install an NDK or set ANDROID_NDK_HOME."
}

$goPath = (& go env GOPATH).Trim()
$goBin = Join-Path $goPath "bin"
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_NDK_HOME = $AndroidNdk
$env:PATH = "$goBin;$env:PATH"

go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.12
& (Join-Path $goBin "gomobile.exe") init

if (!(Test-Path -LiteralPath $workPath)) {
    git clone --depth 1 --branch $SingBoxTag https://github.com/SagerNet/sing-box.git $workPath
} else {
    Push-Location -LiteralPath $workPath
    git fetch --depth 1 origin "refs/tags/$SingBoxTag`:refs/tags/$SingBoxTag"
    git checkout --detach $SingBoxTag
    Pop-Location
}

Push-Location -LiteralPath $workPath
go run ./cmd/internal/build_libbox -target android
Pop-Location

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
Copy-Item -LiteralPath (Join-Path $workPath "libbox.aar") -Destination $outputPath -Force
Get-Item -LiteralPath $outputPath
