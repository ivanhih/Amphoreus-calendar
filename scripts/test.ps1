param([string]$Jdk = 'C:\Program Files\Java\jdk-17')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$output=Join-Path $projectRoot 'build\core-tests'
New-Item -ItemType Directory -Path $output -Force | Out-Null
# Every script in this folder must stay ASCII-only. PowerShell 5.1 reads a non-BOM script as ANSI,
# so non-ASCII comments get mis-decoded and statements can silently stop executing: verify-fusion.ps1
# once had Chinese comments and never ran its verification loop while still printing PASS. Check it
# here, where it costs nothing, instead of debugging a script that "succeeds" without doing anything.
$violations=@()
foreach($script in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $bytes=[IO.File]::ReadAllBytes($script.FullName)
    if(@($bytes | Where-Object { $_ -gt 127 }).Count -gt 0) { $violations+=$script.Name }
}
if($violations.Count -gt 0) { throw ('These scripts must be ASCII-only: '+($violations -join ', ')) }
Write-Output 'PASS scripts are ASCII-only'
$files=@(Get-ChildItem -LiteralPath "$projectRoot\app\src\main\java\com\amphoreus\calendar\core" -Filter '*.java')+@(Get-ChildItem -LiteralPath "$projectRoot\app\src\test\java" -Recurse -Filter '*.java')
# javac rejects a UTF-8 BOM in an argfile: write it BOM-free with forward slashes. (ASCII-only script body.)
$list=[string[]]($files | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' })
[IO.File]::WriteAllLines("$output\sources.txt",$list,(New-Object System.Text.UTF8Encoding $false))
& "$Jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -d $output "@$output\sources.txt"
if($LASTEXITCODE -ne 0) { throw 'Core tests compilation failed' }
& "$Jdk\bin\java.exe" -cp $output com.amphoreus.calendar.core.CoreTests
if($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
