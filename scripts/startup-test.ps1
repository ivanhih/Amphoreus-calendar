param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Serial = 'emulator-5556'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'dist\amphoreus-calendar-debug.apk'
if (!(Test-Path -LiteralPath $adb)) { throw "Missing adb: $adb" }
if (!(Test-Path -LiteralPath $apk)) { throw "Build the APK first: $apk" }
if ($Serial -notlike 'emulator-*') { throw 'startup-test.ps1 revokes calendar permissions; run it only against a test emulator.' }
function Run-Checked([string]$Command, [string[]]$Arguments) {
    $output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed ($LASTEXITCODE)`n$output" }
    return $output
}
$deviceState = (& $adb -s $Serial get-state 2>$null).Trim()
if ($deviceState -ne 'device') { throw "Device is not ready: $Serial" }
Run-Checked $adb @('-s',$Serial,'install','-r','-g',$apk) | Out-Null
Run-Checked $adb @('-s',$Serial,'shell','pm','revoke','com.amphoreus.calendar','android.permission.READ_CALENDAR') | Out-Null
Run-Checked $adb @('-s',$Serial,'shell','pm','revoke','com.amphoreus.calendar','android.permission.WRITE_CALENDAR') | Out-Null
Run-Checked $adb @('-s',$Serial,'logcat','-c') | Out-Null
Run-Checked $adb @('-s',$Serial,'shell','am','force-stop','com.amphoreus.calendar') | Out-Null
$launch = Run-Checked $adb @('-s',$Serial,'shell','am','start','-n','com.amphoreus.calendar/.ui.MainActivity')
Start-Sleep -Seconds 3
$rawPid = & $adb -s $Serial shell pidof com.amphoreus.calendar
$appPid = if ($null -eq $rawPid) { '' } else { ([string]$rawPid).Trim() }
$crash = (& $adb -s $Serial logcat -d -b crash -v brief) -join "`n"
if (($launch -join "`n") -match 'Error' -or [string]::IsNullOrWhiteSpace($appPid) -or ![string]::IsNullOrWhiteSpace($crash)) {
    Write-Output ($launch -join "`n")
    Write-Output "PID: $appPid"
    Write-Output $crash
    throw 'Startup regression: the application did not remain alive or emitted a crash.'
}
Write-Output ($launch -join "`n")
Write-Output "PID: $appPid"
Write-Output 'PASS: MainActivity cold start without calendar permissions remains alive and crash buffer is empty.'
