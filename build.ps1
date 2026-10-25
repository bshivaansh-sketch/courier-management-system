$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& "$PSScriptRoot/setup-dependencies.ps1"
$localJdk = Get-ChildItem "$PSScriptRoot/.runtime" -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $javaExe = "$($localJdk.FullName)/bin/java.exe"; $javacExe = "$($localJdk.FullName)/bin/javac.exe" }
else { $javaExe = 'java'; $javacExe = 'javac' }
$runtimeClasspath = "build$([IO.Path]::PathSeparator).runtime/lib/*"
New-Item -ItemType Directory -Force build | Out-Null
& $javacExe -encoding UTF-8 -cp '.runtime/lib/*' -d build (Get-ChildItem src/courier/*.java).FullName
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed. Install JDK 21 or later.' }
