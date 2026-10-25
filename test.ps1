$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
. "$PSScriptRoot/build.ps1"
& $javacExe -encoding UTF-8 -cp $runtimeClasspath -d build (Get-Item tests/courier/WorkflowTest.java).FullName
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& $javaExe -cp $runtimeClasspath courier.WorkflowTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
if (Get-Command node -ErrorAction SilentlyContinue) {
    & node --check public/app.js
    if ($LASTEXITCODE -ne 0) { throw 'Frontend syntax check failed' }
    & node tests/frontend-api.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    if (Test-Path '.runtime/ui-check/node_modules/jsdom/package.json') {
        & node tests/frontend-motion.test.cjs
        if ($LASTEXITCODE -ne 0) { throw 'UI motion tests failed' }
        & node tests/frontend-theme.test.cjs
        if ($LASTEXITCODE -ne 0) { throw 'Theme tests failed' }
    }
} else { Write-Host 'Node.js unavailable; frontend tests skipped.' }
