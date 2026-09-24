param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Java\jdk-17'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-35\android.jar'
foreach ($required in @("$Jdk\bin\javac.exe", "$buildTools\aapt2.exe", $androidJar)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build prerequisite: $required" }
}
function Run-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command exited with $LASTEXITCODE" }
}
$output = Join-Path $projectRoot 'build\offline'
$dist = Join-Path $projectRoot 'dist'
foreach ($dir in @($output,$dist,"$output\generated")) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
# Clean classes/dex every build: a deleted source file would otherwise leave a stale .class in the APK.
foreach ($dir in @("$output\classes","$output\dex")) { if (Test-Path $dir) { Remove-Item -LiteralPath $dir -Recurse -Force } ; New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[xml]$manifest=Get-Content -LiteralPath "$projectRoot\app\src\main\AndroidManifest.xml" -Raw
$manifest.DocumentElement.SetAttribute('package','com.amphoreus.calendar')
$manifest.manifest.application.SetAttribute('debuggable','http://schemas.android.com/apk/res/android','true') | Out-Null
$manifest.Save("$output\AndroidManifest.xml")
Run-Checked "$buildTools\aapt2.exe" @('compile','--dir',"$projectRoot\app\src\main\res",'-o',"$output\resources.zip")
Run-Checked "$buildTools\aapt2.exe" @('link','-o',"$output\base.apk",'-I',$androidJar,'--manifest',"$output\AndroidManifest.xml",'--java',"$output\generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code','1','--version-name','1.0.0',"$output\resources.zip")
$sourceFiles = @(Get-ChildItem -LiteralPath "$projectRoot\app\src\main\java" -Recurse -Filter '*.java') + @(Get-ChildItem -LiteralPath "$output\generated" -Recurse -Filter '*.java')
# javac rejects a UTF-8 BOM in an argfile; write it BOM-free. (ASCII comment on purpose: PowerShell 5.1 reads
# non-BOM scripts as ANSI and mangles them, so this file must stay ASCII-only.)
$sourceList = [string[]]($sourceFiles | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
[IO.File]::WriteAllLines("$output\sources.txt", $sourceList, (New-Object System.Text.UTF8Encoding $false))
Run-Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',"$output\classes","@$output\sources.txt")
Run-Checked "$Jdk\bin\jar.exe" @('cf',"$output\classes.jar",'-C',"$output\classes",'.')
Run-Checked "$buildTools\d8.bat" @('--lib',$androidJar,'--min-api','26','--output',"$output\dex","$output\classes.jar")
Copy-Item -LiteralPath "$output\base.apk" -Destination "$output\unsigned.apk" -Force
# The SDK's Windows AAPT2 can emit backslashes for nested assets. JAR emits portable ZIP paths.
Run-Checked "$Jdk\bin\jar.exe" @('uf',"$output\unsigned.apk",'-C',"$output\dex",'classes.dex','-C',"$projectRoot\app\src\main",'assets')
Run-Checked "$buildTools\zipalign.exe" @('-f','4',"$output\unsigned.apk","$output\aligned.apk")
$signingDirectory=Join-Path $projectRoot '.signing'
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$keystore = Join-Path $signingDirectory 'debug.keystore'
if (!(Test-Path -LiteralPath $keystore) -and (Test-Path -LiteralPath "$projectRoot\build\debug.keystore")) { Copy-Item -LiteralPath "$projectRoot\build\debug.keystore" -Destination $keystore }
if (!(Test-Path -LiteralPath $keystore)) {
    Run-Checked "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$keystore,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
}
$apk = Join-Path $dist 'amphoreus-calendar-debug.apk'
Run-Checked "$buildTools\apksigner.bat" @('sign','--ks',$keystore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$output\aligned.apk")
Run-Checked "$buildTools\apksigner.bat" @('verify','--verbose',$apk)
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  amphoreus-calendar-debug.apk" | Set-Content -LiteralPath "$dist\SHA256SUMS.txt" -Encoding utf8
Write-Output "APK: $apk"
