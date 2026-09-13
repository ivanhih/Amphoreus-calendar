param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Serial = 'emulator-5556',
    [string]$Size = '',
    [switch]$SkipBuild,
    [switch]$AllMonths,
    [string]$Year = '2026'
)
# One-command on-device check of the fused home page:
# build -> install -> launch -> dump view tree -> screenshot -> assert every region.
# Regions come from the view tree (tools/view_tree.py) instead of hard-coded pixels, so -Size
# can re-run the same assertions at another resolution (1080x1920 / 1440x3120 / tablet).
#
# TWO POWERSHELL TRAPS THIS FILE ALREADY FELL INTO -- do not undo either:
# 1. IT MUST STAY ASCII-ONLY. PowerShell 5.1 reads a non-BOM script as ANSI, so non-ASCII
#    comments get mis-decoded and statements silently stop executing: an earlier version with
#    Chinese comments never ran its verification loop while still printing PASS.
# 2. Never call a native tool with "2>&1" while ErrorActionPreference is Stop. adb and python
#    write progress to stderr, and 5.1 turns the first stderr line into a terminating
#    NativeCommandError. Invoke-Native relaxes the preference for the call and reports the exit
#    code, which is what we actually want to branch on.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$stage = Join-Path $projectRoot 'build\fusion'
$python = 'python'
New-Item -ItemType Directory -Path $stage -Force | Out-Null
if (!(Test-Path -LiteralPath $adb)) { throw "Missing adb: $adb" }
if (!(Test-Path -LiteralPath (Join-Path $projectRoot 'tools\view_tree.py'))) { throw 'Missing tools/view_tree.py' }

function Invoke-Native([string]$File, [string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = ''; $code = 0
    try {
        $output = (& $File @Arguments 2>&1 | Out-String)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return @{ output = $output; code = $code }
}
function Adb([string[]]$Arguments) {
    $result = Invoke-Native $adb (@('-s', $Serial) + $Arguments)
    if ($result.code -ne 0) { throw "adb $($Arguments -join ' ') failed: $($result.output)" }
    return $result.output
}
function Capture([string]$Path) {
    # PowerShell redirection writes a BOM / turns LF into CRLF and corrupts the PNG, so use cmd.exe.
    $command = '"' + $adb + '" -s ' + $Serial + ' exec-out screencap -p > "' + $Path + '"'
    $result = Invoke-Native 'cmd.exe' @('/c', $command)
    if ($result.code -ne 0) { throw "screencap failed: $($result.output)" }
    $signature = [byte[]](Get-Content -LiteralPath $Path -Encoding Byte -TotalCount 8)
    if ($signature.Length -lt 8 -or $signature[0] -ne 0x89 -or $signature[1] -ne 0x50) { throw "screenshot is not a PNG: $Path" }
}
function DumpTree([string]$Path) {
    Adb @('shell', 'uiautomator', 'dump', '/sdcard/fusion.xml') | Out-Null
    Adb @('pull', '/sdcard/fusion.xml', $Path) | Out-Null
}
function CurrentMonth {
    $value = (Adb @('shell', 'date', '+%Y-%m') | Out-String).Trim()
    if ($value -notmatch '^\d{4}-\d{2}$') { throw "cannot read the device month: $value" }
    return $value
}

$failures = @()
function Verify([string]$Shot, [string]$Tree, [string]$Month, [string]$Report, [string]$Label) {
    $result = Invoke-Native $python @(
        (Join-Path $projectRoot 'tools\verify_screenshot.py'),
        '--shot', $Shot, '--tree', $Tree, '--month', $Month, '--report', $Report)
    if ($result.code -ne 0) {
        Write-Output "FAIL $Label"
        if (Test-Path -LiteralPath $Report) {
            Get-Content -LiteralPath $Report -Encoding UTF8 | Select-String -Pattern '^FAIL' | ForEach-Object { Write-Output "    $_" }
        }
        $script:failures += $Label
    } else {
        Write-Output "PASS $Label"
    }
}

if (!$SkipBuild) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'build.ps1') -Sdk $Sdk | Out-Null
}
$apk = Join-Path $projectRoot 'dist\amphoreus-calendar-debug.apk'
if (!(Test-Path -LiteralPath $apk)) { throw "Missing $apk" }
Adb @('install', '-r', '-g', $apk) | Out-Null

# With no -Size, verify the resolution the device already has. With -Size, switch for each entry and
# reset afterwards, e.g.  -Size 1080x1920,1440x3120
# It is a [string], split here by hand, because invoking with -File does not split "a,b" into an
# array (it arrives as one literal string) and rejects a repeated -Size switch outright.
$sizes = @($Size -split '[,;\s]+' | Where-Object { $_ })
$explicit = $sizes.Count -gt 0
if (!$explicit) { $sizes = @('device') }
try {
    foreach ($size in $sizes) {
        if ($explicit) {
            Adb @('shell', 'wm', 'size', $size) | Out-Null
            Start-Sleep -Seconds 2
        }
        $label = $size
        Write-Output "verify-fusion: $label"

        if ($AllMonths) {
            foreach ($stamp in 1..12) {
                $month = '{0}-{1:d2}' -f $Year, $stamp
                $date = '{0}-{1:d2}-15' -f $Year, $stamp
                Adb @('shell', 'am', 'start', '-W', '-n', 'com.amphoreus.calendar/.ui.MainActivity', '--es', 'date', $date) | Out-Null
                Start-Sleep -Seconds 3
                $tree = Join-Path $stage ("ui-$label-$month.xml")
                $shot = Join-Path $stage ("shot-$label-$month.png")
                DumpTree $tree
                Capture $shot
                Copy-Item -LiteralPath $shot -Destination (Join-Path $projectRoot "docs\screenshots\fusion-$month.png") -Force
                Verify $shot $tree $month (Join-Path $stage "verify-$label-$month.txt") "$label $month"
            }
            & $python (Join-Path $projectRoot 'tools\contact_sheet.py') --out (Join-Path $projectRoot 'docs\screenshots\fusion-contact-sheet.png') --year $Year
            # Cross-month check: the contact sheet is for a human eye, this asserts the same property
            # machine-readably (12 distinct accents, each matching Ui.ACCENTS).
            $series = Invoke-Native $python @(
                (Join-Path $projectRoot 'tools\verify_screenshot.py'),
                '--series', (Join-Path $projectRoot 'docs\screenshots'), '--year', $Year,
                '--report', (Join-Path $stage 'verify-series.txt'))
            if ($series.code -ne 0) {
                Write-Output 'FAIL accent series'
                $script:failures += "accents $Year"
            } else {
                Write-Output 'PASS accent series'
            }
        } else {
            Adb @('shell', 'am', 'force-stop', 'com.amphoreus.calendar') | Out-Null
            Adb @('shell', 'am', 'start', '-W', '-n', 'com.amphoreus.calendar/.ui.MainActivity') | Out-Null
            Start-Sleep -Seconds 4
            $month = CurrentMonth
            $tree = Join-Path $stage "ui-$label.xml"
            $shot = Join-Path $stage "shot-$label.png"
            DumpTree $tree
            Capture $shot
            Verify $shot $tree $month (Join-Path $stage "verify-$label.txt") "$label $month"
        }
        $crash = (Adb @('logcat', '-d', '-b', 'crash', '-v', 'brief') | Out-String)
        if ($crash -match 'com\.amphoreus\.calendar') { Write-Output 'FAIL crash buffer mentions the app'; $failures += "crash $label" }
    }
} finally {
    Adb @('shell', 'wm', 'size', 'reset') | Out-Null
}
if ($failures.Count -gt 0) { throw ('Fusion verification failed: ' + ($failures -join ', ')) }
Write-Output 'PASS fusion verification'
