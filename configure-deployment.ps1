param([Parameter(Mandatory = $true)][string]$BackendUrl)
$ErrorActionPreference = 'Stop'
$backendUri = $null
if (-not [Uri]::TryCreate($BackendUrl, [UriKind]::Absolute, [ref]$backendUri) -or
    $backendUri.Scheme -ne 'https' -or $backendUri.UserInfo -or
    $backendUri.Query -or $backendUri.Fragment -or $backendUri.AbsolutePath -ne '/') {
    throw 'Supply the HTTPS origin of your Render service, such as https://parcelly-api.onrender.com'
}
$configPath = Join-Path $PSScriptRoot 'vercel.json'
$deploymentConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$deploymentConfig.rewrites[0].destination = $backendUri.GetLeftPart([UriPartial]::Authority) + '/api/:path*'
$deploymentConfig | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $configPath -Encoding UTF8
Write-Host 'Vercel API proxy configured. Set ALLOWED_ORIGINS on Render to your Vercel frontend origin.'
