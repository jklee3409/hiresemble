param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = "Stop"

$scriptDir = Join-Path $Root "automation\n8n\scripts"
$logDir = Join-Path $Root "automation\n8n\logs"

$runnerScript = Join-Path $scriptDir "run-backend.ps1"
$pidFile = Join-Path $logDir "backend-runner.pid"


New-Item `
    -ItemType Directory `
    -Force `
    -Path $logDir |
    Out-Null


# ---------------------------------------------------
# 이미 Backend가 정상 실행 중이면 아무것도 하지 않음
# ---------------------------------------------------

try {

    $response = Invoke-WebRequest `
        -Uri "http://127.0.0.1:8080/actuator/health" `
        -TimeoutSec 2 `
        -UseBasicParsing

    if ($response.StatusCode -eq 200) {
        exit 0
    }

}
catch {
    # 실행 중이 아니므로 아래에서 시작
}


# ---------------------------------------------------
# Backend runner를 독립 프로세스로 실행
# ---------------------------------------------------

$arguments = @(
    "-NoProfile",
    "-NonInteractive",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$runnerScript`"",
    "-Root", "`"$Root`""
)

$process = Start-Process `
    -FilePath "powershell.exe" `
    -ArgumentList $arguments `
    -WindowStyle Hidden `
    -PassThru


# runner PID 기록
Set-Content `
    -Path $pidFile `
    -Value $process.Id `
    -Encoding ASCII


exit 0
