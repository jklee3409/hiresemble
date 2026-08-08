$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

[System.Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$n8nUrl = "http://localhost:5678"
$webhookUrl = "http://localhost:5678/webhook/hiresemble-start"


# =========================
# 1. n8n 실행 여부 확인
# =========================

Write-Host "[1/4] n8n status check..."

$n8nRunning = $false

try {
    Invoke-WebRequest `
        -Uri $n8nUrl `
        -TimeoutSec 2 `
        -UseBasicParsing `
        -ErrorAction Stop | Out-Null

    $n8nRunning = $true
}
catch {
    $n8nRunning = $false
}


# =========================
# 2. n8n이 없으면 시작
# =========================

if (-not $n8nRunning) {

    Write-Host "[2/4] n8n start..."

    $n8nCommand = Get-Command "n8n.cmd" -ErrorAction SilentlyContinue

    if (-not $n8nCommand) {
        Write-Error "n8n.cmd not found. npm global install & path check."
        exit 1
    }

    Start-Process `
        -FilePath $n8nCommand.Source `
        -WindowStyle Hidden

}
else {
    Write-Host "[2/4] n8n is already start...."
}


# =========================
# 3. n8n 준비될 때까지 확인
# =========================

Write-Host "[3/4] n8n ready & waiting..."

$n8nReady = $false

for ($i = 0; $i -lt 30; $i++) {

    try {
        Invoke-WebRequest `
            -Uri $n8nUrl `
            -TimeoutSec 2 `
            -UseBasicParsing `
            -ErrorAction Stop | Out-Null

        $n8nReady = $true
        break
    }
    catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $n8nReady) {
    Write-Error "n8n run failed"
    exit 1
}

Write-Host "n8n ready complete."


# =========================
# 4. Workflow Webhook 호출
# =========================

Write-Host "[4/4] Workflow run..."

try {
    $response = Invoke-RestMethod `
        -Uri $webhookUrl `
        -Method POST `
        -ErrorAction Stop

    Write-Host "Workflow request complete."
}
catch {
    Write-Error "Webhook request failed: $($_.Exception.Message)"
    exit 1
}
