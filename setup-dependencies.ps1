$ErrorActionPreference = 'Stop'
$driverVersion = '9.4.0'
$driverDirectory = Join-Path $PSScriptRoot '.runtime/lib'
New-Item -ItemType Directory -Force $driverDirectory | Out-Null
$driverTarget = Join-Path $driverDirectory "mysql-connector-j-$driverVersion.jar"
if (-not (Test-Path -LiteralPath $driverTarget)) {
    $driverUrl = "https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/$driverVersion/mysql-connector-j-$driverVersion.jar"
    Invoke-WebRequest $driverUrl -OutFile "$driverTarget.download"
    Move-Item -LiteralPath "$driverTarget.download" -Destination $driverTarget
}
