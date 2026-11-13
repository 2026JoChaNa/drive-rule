[CmdletBinding()]
param(
    [string]$Serial,
    [string]$AdbPath = 'adb'
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command $AdbPath -ErrorAction SilentlyContinue)) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $AdbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $AdbPath)) {
        throw 'adb not found. Set ANDROID_HOME or pass -AdbPath.'
    }
}

if (-not $Serial) {
    $deviceList = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) { throw 'Unable to list adb devices.' }
    $connected = @($deviceList | ForEach-Object {
        if ($_ -match '^(\S+)\s+device$') { $Matches[1] }
    })
    if ($connected.Count -ne 1) {
        throw 'Connect exactly one device or pass -Serial from adb devices.'
    }
    $Serial = $connected[0]
}

$currentUser = (& $AdbPath -s $Serial shell am get-current-user | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $currentUser -notmatch '^\d+$') {
    throw 'Unable to resolve the current Android user.'
}

# Recreate only this app task. Room profiles and DataStore settings are retained.
& $AdbPath -s $Serial shell am force-stop --user $currentUser com.jochana.driverule
if ($LASTEXITCODE -ne 0) { throw 'Unable to stop DRIVE RULE.' }
$launchOutput = & $AdbPath -s $Serial shell am start -W --user $currentUser `
    -n com.jochana.driverule/.MainActivity `
    -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>&1
$launchExitCode = $LASTEXITCODE
$launchOutput | Write-Output
if ($launchExitCode -ne 0 -or ($launchOutput -join "`n") -notmatch 'Status: ok') {
    throw 'Launch failed. Confirm the debug app is installed for the current user.'
}
Write-Output "DRIVE RULE launched on $Serial (user $currentUser). Verify the Pleos app window is visible."
