param([string]$Sdk="$env:LOCALAPPDATA\Android\Sdk",[string]$Jdk='C:\Program Files\Java\jdk-17',[string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:JAVA_HOME=$Jdk
$env:PATH="$Jdk\bin;$env:PATH"
$buildTools=Join-Path $Sdk 'build-tools\35.0.0'
$androidJar=Join-Path $Sdk 'platforms\android-35\android.jar'
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
$output=Join-Path $projectRoot 'build\device-tests'
foreach($dir in @($output,"$output\classes","$output\dex")) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
function Run-Checked([string]$Command,[string[]]$Arguments) { & $Command @Arguments; if($LASTEXITCODE -ne 0) { throw "$Command failed ($LASTEXITCODE)" } }
[xml]$manifest=Get-Content -LiteralPath "$projectRoot\app\src\androidTest\AndroidManifest.xml" -Raw
$manifest.DocumentElement.SetAttribute('package','com.amphoreus.calendar.test')
$manifest.Save("$output\AndroidManifest.xml")
Run-Checked "$buildTools\aapt2.exe" @('link','-o',"$output\base.apk",'-I',$androidJar,'--manifest',"$output\AndroidManifest.xml",'--min-sdk-version','26','--target-sdk-version','35')
# javac rejects a UTF-8 BOM in an argfile: write it BOM-free with forward slashes.
# Set-Content -Encoding utf8 would prepend EF BB BF and javac dies with
# "MalformedInputException: Input length = 1" before it ever reads a single source file.
$testSources=[string[]](Get-ChildItem -LiteralPath "$projectRoot\app\src\androidTest\java" -Recurse -Filter '*.java' | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' })
[IO.File]::WriteAllLines("$output\sources.txt",$testSources,(New-Object System.Text.UTF8Encoding $false))
Run-Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-classpath',"$androidJar;$projectRoot\build\offline\classes.jar",'-d',"$output\classes","@$output\sources.txt")
Run-Checked "$Jdk\bin\jar.exe" @('cf',"$output\classes.jar",'-C',"$output\classes",'.')
Run-Checked "$buildTools\d8.bat" @('--lib',$androidJar,'--classpath',"$projectRoot\build\offline\classes.jar",'--min-api','26','--output',"$output\dex","$output\classes.jar")
Copy-Item -LiteralPath "$output\base.apk" -Destination "$output\unsigned.apk" -Force
Run-Checked "$Jdk\bin\jar.exe" @('uf',"$output\unsigned.apk",'-C',"$output\dex",'classes.dex')
Run-Checked "$buildTools\zipalign.exe" @('-f','4',"$output\unsigned.apk","$output\aligned.apk")
Run-Checked "$buildTools\apksigner.bat" @('sign','--ks',"$projectRoot\.signing\debug.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',"$output\tests.apk","$output\aligned.apk")
Run-Checked $adb @('-s',$Serial,'install','-r','-g',"$projectRoot\dist\amphoreus-calendar-debug.apk")
Run-Checked $adb @('-s',$Serial,'install','-r',"$output\tests.apk")
Run-Checked $adb @('-s',$Serial,'shell','appops','set','com.amphoreus.calendar','SCHEDULE_EXACT_ALARM','allow')
& $adb -s $Serial shell am instrument -w com.amphoreus.calendar.test/com.amphoreus.calendar.DeviceTests | Tee-Object -FilePath "$output\results.txt"
if($LASTEXITCODE -ne 0 -or !(Select-String -LiteralPath "$output\results.txt" -Pattern 'PASS: .* device assertions' -Quiet)) { throw 'Device integration tests failed; see build/device-tests/results.txt' }
