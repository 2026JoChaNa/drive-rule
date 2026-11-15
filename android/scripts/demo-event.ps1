# Demo video only. Sends a stand-in vehicle event to a debug build made with -PdriveRuleDemoMode=true.
# The app comes forward, opens the matching Figma card (A/B/H/I) and speaks its sample message.
# Nothing appears on screen to trigger it, so the recording shows only the app.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('school', 'parking', 'turn', 'highway')]
    [string]$Event,
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

# Single-top + clear-top: a running app receives the event in onNewIntent instead of a second
# screen. am then prints "Activity not started, its current task has been brought to the front"
# on stderr; that is expected, so stderr must not stop this script.
$ErrorActionPreference = 'Continue'
$output = & $AdbPath -s $Serial shell am start --user $currentUser --activity-single-top `
    --activity-clear-top -n com.jochana.driverule/.MainActivity --es demo_event $Event 2>&1 |
    ForEach-Object { "$_" }
$exitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($exitCode -ne 0 -or ($output -join "`n") -match 'Error:') {
    $output | Write-Output
    throw 'Launch failed. Confirm the demo build is installed for the current user.'
}
Write-Output "Demo event '$Event' sent to $Serial (user $currentUser)."
