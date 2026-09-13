param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AvdHome = 'E:\wengfaluosi\.tools\android\avd',
    [string]$Serial = 'emulator-5556',
    [int]$BootSeconds = 240
)
$ErrorActionPreference = 'Continue'
$root = 'E:\wengfaluosi'
$stage = Join-Path $root 'build\stage'
$emuHome = Join-Path $stage 'emuhome'
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$emu = Join-Path $Sdk 'emulator\emulator.exe'
New-Item -ItemType Directory -Path $stage -Force | Out-Null

function Get-State() { ((& $adb -s $Serial get-state 2>&1) -join ' ').Trim() }
function Adb([string[]]$a, [string]$tag) {
    $out = Join-Path $stage "$tag.txt"
    if (Test-Path $out) { Remove-Item $out -Force }
    $p = Start-Process -FilePath $adb -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError "$out.err"
    if (-not $p.WaitForExit(90000)) { $p.Kill(); Write-Output "TIMEOUT: $tag" } else { Get-Content $out -ErrorAction SilentlyContinue }
}

if ((Get-State) -notmatch 'device') {
    $env:TMP = $stage; $env:TEMP = $stage
    $env:ANDROID_EMULATOR_HOME = $emuHome
    $env:ANDROID_USER_HOME = $emuHome
    $env:ANDROID_AVD_HOME = $AvdHome
    Get-Process qemu-system-x86_64, emulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    $proc = Start-Process -FilePath $emu -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $stage 'emu-final-out.log') -RedirectStandardError (Join-Path $stage 'emu-final-err.log') `
        -ArgumentList @('-avd','CalendarTest','-no-window','-no-audio','-no-boot-anim','-no-metrics','-gpu','swiftshader_indirect','-no-snapshot-save','-no-snapshot-load','-port','5556','-no-snapshot')
    Write-Output "emulator pid=$($proc.Id)"
    $deadline = (Get-Date).AddSeconds($BootSeconds)
    while ((Get-State) -notmatch 'device' -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
}
if ((Get-State) -notmatch 'device') { throw "emulator $Serial not ready" }
$deadline = (Get-Date).AddSeconds(180)
while ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim() -ne '1' -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }

# Disable Bluetooth: its crash dialog covers the app and dims the whole screen.
Adb @('-s',$Serial,'shell','settings','put','global','bluetooth_on','0') 'bt'
Adb @('-s',$Serial,'shell','svc','bluetooth','disable') 'bt-svc'
Adb @('-s',$Serial,'shell','settings','put','global','airplane_mode_on','1') 'airplane'

Adb @('-s',$Serial,'install','-r','-d',(Join-Path $root 'dist\amphoreus-calendar-debug.apk')) 'install'
Adb @('-s',$Serial,'shell','pm','grant','com.amphoreus.calendar','android.permission.READ_CALENDAR') 'grant-read'
Adb @('-s',$Serial,'shell','pm','grant','com.amphoreus.calendar','android.permission.WRITE_CALENDAR') 'grant-write'
Adb @('-s',$Serial,'logcat','-c') 'logcat-clear'
Adb @('-s',$Serial,'shell','am','start','-W','-n','com.amphoreus.calendar/.ui.MainActivity') 'amstart'
Start-Sleep -Seconds 10

# Confirm the app is in the foreground.
Adb @('-s',$Serial,'shell','dumpsys','window','windows') 'windows'
$front = (Get-Content (Join-Path $stage 'windows.txt') -ErrorAction SilentlyContinue | Select-String -Pattern 'mCurrentFocus|mFocusedApp' | Select-Object -First 2)
Write-Output "--- focus ---"; $front | ForEach-Object { $_.Line.Trim() }

# Clean screenshot (binary redirect through cmd).
$shot = Join-Path $stage 'shot-final.png'
if (Test-Path $shot) { Remove-Item $shot -Force }
& cmd.exe /c ('"' + $adb + '" -s ' + $Serial + ' exec-out screencap -p > "' + $shot + '"') | Out-Null
Copy-Item -LiteralPath $shot -Destination (Join-Path $root 'docs\screenshots\calendar-fused.png') -Force
Write-Output "shot=$shot bytes=$((Get-Item $shot).Length)"
Adb @('-s',$Serial,'shell','uiautomator','dump','/sdcard/ui-final.xml') 'uidump'
Adb @('-s',$Serial,'pull','/sdcard/ui-final.xml',(Join-Path $stage 'ui-final.xml')) 'uipull'
Write-Output "current package in dump:"
Select-String -LiteralPath (Join-Path $stage 'ui-final.xml') -Pattern 'package="com.amphoreus.calendar"' -SimpleMatch | Measure-Object | Select-Object -ExpandProperty Count
Adb @('-s',$Serial,'logcat','-d','-t','200') 'applog'
$hits = Get-Content (Join-Path $stage 'applog.txt') -ErrorAction SilentlyContinue | Select-String -Pattern 'FATAL|AndroidRuntime|amphoreus' | Select-Object -First 20
Write-Output "--- app log hits ---"; if ($hits) { $hits | ForEach-Object { $_.Line } } else { Write-Output '(none)' }
