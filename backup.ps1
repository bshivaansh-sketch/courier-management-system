param([ValidateSet('export','import')][string]$Operation = 'export', [string]$Path = 'data/backups/records.json')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/build.ps1"
& $javaExe -cp $runtimeClasspath courier.Backup $Operation $Path
if ($LASTEXITCODE -ne 0) { throw 'Backup operation failed.' }
