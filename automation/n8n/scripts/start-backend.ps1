param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = "Stop"

$scriptDir = Join-Path $Root "automation\n8n\scripts"
$logDir = Join-Path $Root "automation\n8n\logs"

$runnerScript = Join-Path $scriptDir "run-backend.ps1"
$pidFile = Join-Path $logDir "backend-runner.pid"

$healthUrl = "http://127.0.0.1:8080/actuator/health"

# Backend 최대 기동 대기 시간
$maxWaitSeconds = 180
$checkIntervalSeconds = 2


New-Item `
    -ItemType Directory `
    -Force `
    -Path $logDir |
    Out-Null


# ---------------------------------------------------
# Backend health check 함수
# ---------------------------------------------------

function Test-BackendReady {

    try {

        $response = Invoke-WebRequest `
            -Uri $healthUrl `
            -TimeoutSec 2 `
            -UseBasicParsing `
            -ErrorAction Stop

        return ($response.StatusCode -eq 200)

    }
    catch {

        return $false

    }

}


# ---------------------------------------------------
# 이미 Backend가 정상 실행 중이면 아무것도 하지 않음
# ---------------------------------------------------

if (Test-BackendReady) {

    Write-Output '{"component":"backend","status":"UP","changed":false}'
    exit 0

}


# ---------------------------------------------------
# runner script 존재 확인
# ---------------------------------------------------

if (-not (Test-Path $runnerScript)) {

    [Console]::Error.WriteLine(
        "Backend runner script not found: $runnerScript"
    )

    exit 1

}


# ---------------------------------------------------
# Backend runner를 독립 프로세스로 실행
# ---------------------------------------------------

Write-Host "Backend runner starting..."

$arguments = @(
    "-NoProfile",
    "-NonInteractive",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$runnerScript`"",
    "-Root", "`"$Root`""
)

try {

    $process = Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -PassThru `
        -ErrorAction Stop

}
catch {

    [Console]::Error.WriteLine(
        "Failed to start backend runner: $($_.Exception.Message)"
    )

    exit 1

}


# ---------------------------------------------------
# runner PID 기록
# ---------------------------------------------------

Set-Content `
    -Path $pidFile `
    -Value $process.Id `
    -Encoding ASCII


# ---------------------------------------------------
# Backend가 실제로 준비될 때까지 대기
# ---------------------------------------------------

Write-Host "Waiting for Backend..."

$elapsedSeconds = 0

while ($elapsedSeconds -lt $maxWaitSeconds) {

    if (Test-BackendReady) {

        Write-Output '{"component":"backend","status":"UP","changed":true}'
        exit 0

    }


    Start-Sleep -Seconds $checkIntervalSeconds

    $elapsedSeconds += $checkIntervalSeconds

    Write-Host (
        "Backend starting... {0}/{1} sec" `
            -f $elapsedSeconds, $maxWaitSeconds
    )

}


# ---------------------------------------------------
# Timeout
# ---------------------------------------------------

[Console]::Error.WriteLine(
    "Backend did not become ready within $maxWaitSeconds seconds."
)

[Console]::Error.WriteLine(
    "Health check failed: $healthUrl"
)

[Console]::Error.WriteLine(
    "Backend runner PID: $($process.Id)"
)

exit 1
