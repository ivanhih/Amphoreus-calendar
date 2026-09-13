param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AvdHome = 'E:\wengfaluosi\.tools\android\avd',
    [string]$Serial = 'emulator-5556',
    [int]$BootSeconds = 240,
    [switch]$SkipLaunch
)
$ErrorActionPreference = 'Continue'
$root = 'E:\wengfaluosi'
$stage = Join-Path $root 'build\stage'
$emuHome = Join-Path $stage 'emuhome'
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$emu = Join-Path $Sdk 'emulator\emulator.exe'
New-Item -ItemType Directory -Path $stage -Force | Out-Null

function Get-State() { (($(& $adb -s $Serial get-state 2>&1) + '') -join ' ').Trim() }

if ((Get-State) -ne 'device') {
    $env:TMP = $stage; $env:TEMP = $stage
    $env:ANDROID_EMULATOR_HOME = $emuHome
    $env:ANDROID_USER_HOME = $emuHome
    $env:ANDROID_AVD_HOME = $AvdHome
    $log = Join-Path $stage 'emu-run.log'
    Get-Process qemu-system-x86_64, emulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    $proc = Start-Process -FilePath $emu -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -ArgumentList @('-avd','CalendarTest','-no-window','-no-audio','-no-boot-anim','-no-metrics','-gpu','swiftshader_indirect','-no-snapshot-save','-port','5556','-no-snapshot-load')
    Write-Output "emulator pid=$($proc.Id) log=$log"
    $deadline = (Get-Date).AddSeconds($BootSeconds)
    while ((Get-State) -notmatch 'device' -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
}
if ((Get-State) -notmatch 'device') { throw "emulator $Serial not ready" }
& $adb -s $Serial wait-for-device | Out-Null
$deadline = (Get-Date).AddSeconds(180)
while ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim() -ne '1' -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
$boot = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
$bootanim = (& $adb -s $Serial shell getprop init.svc.bootanim 2>$null | Out-String).Trim()
$sdk = (& $adb -s $Serial shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
Write-Output "device=$Serial boot_completed=$boot bootanim=$bootanim sdk=$sdk"

if (-not $SkipLaunch) {
    $apk = Join-Path $root 'dist\amphoreus-calendar-debug.apk'
    & $adb -s $Serial install -r -d $apk
    & $adb -s $Serial shell pm grant com.amphoreus.calendar android.permission.READ_CALENDAR 2>$null | Out-Null
    & $adb -s $Serial shell pm grant com.amphoreus.calendar android.permission.WRITE_CALENDAR 2>$null | Out-Null
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell am start -W -n com.amphoreus.calendar/.ui.MainActivity
    Start-Sleep -Seconds 8
    $shot = Join-Path $stage 'shot.png'
    if (Test-Path $shot) { Remove-Item $shot -Force }
    # PowerShell redirection would corrupt the PNG with a BOM/CRLF; use cmd binary redirection instead.
    $cmd = '"' + $adb + '" -s ' + $Serial + ' exec-out screencap -p > "' + $shot + '"'
    & cmd.exe /c $cmd | Out-Null
    Copy-Item -LiteralPath $shot -Destination (Join-Path $root 'docs\screenshots\calendar-fused.png') -Force
    Write-Output "screenshot=$shot bytes=$((Get-Item $shot).Length)"
    $sig = [byte[]](Get-Content -LiteralPath $shot -Encoding Byte -TotalCount 8)
    Write-Output ("signature=" + (($sig | ForEach-Object { $_.ToString('x2') }) -join ' '))
    $dump = Join-Path $stage 'ui-main.xml'
    & $adb -s $Serial shell uiautomator dump /sdcard/ui.xml | Out-Null
    & $adb -s $Serial pull /sdcard/ui.xml $dump | Out-Null
    Write-Output "dump=$dump bytes=$((Get-Item $dump).Length)"
    Write-Output '--- crash buffer ---'
    & $adb -s $Serial logcat -d -b crash -t 40
    Write-Output '--- app log ---'
    & $adb -s $Serial logcat -d -t 60 | Select-String -Pattern 'amphoreus|CalendarWidget|AndroidRuntime|FATAL' | ForEach-Object { $_.Line }
}
