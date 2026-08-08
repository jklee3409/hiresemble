param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = "Stop"

$frontendDir = Join-Path $Root "frontend"
$logDir = Join-Path $Root "automation\n8n\logs"
$logFile = Join-Path $logDir "frontend.log"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

try {

    $response = Invoke-WebRequest `
        -Uri "http://localhost:5173" `
        -TimeoutSec 2

    if ($response.StatusCode -eq 200) {
        Write-Output '{"component":"frontend","status":"ALREADY_RUNNING"}'
        exit 0
    }

} catch {
}

$command = 'npm run dev > "' + $logFile + '" 2>&1'

Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c", $command `
    -WorkingDirectory $frontendDir `
    -WindowStyle Hidden

Write-Output '{"component":"frontend","status":"STARTING"}'
