$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
. "$PSScriptRoot/build.ps1"
Write-Host "Connecting to MySQL database '$(& $javaExe -cp $runtimeClasspath courier.Config)'..."
& $javaExe -cp $runtimeClasspath courier.Server
if ($LASTEXITCODE -ne 0) { throw 'Server stopped with an error. Check the output above and verify MySQL is running and .env credentials are correct.' }
