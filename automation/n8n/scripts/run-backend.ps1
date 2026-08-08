param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = "Continue"

$backendDir = Join-Path $Root "backend"
$logDir = Join-Path $Root "automation\n8n\logs"

$logFile = Join-Path $logDir "backend.log"
$exitFile = Join-Path $logDir "backend-exit.txt"

New-Item `
    -ItemType Directory `
    -Force `
    -Path $logDir |
    Out-Null


# 이전 로그/종료 상태 제거
if (Test-Path $logFile) {
    Remove-Item $logFile -Force
}

if (Test-Path $exitFile) {
    Remove-Item $exitFile -Force
}


# 시작 정보 기록
$startedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

@"
============================================================
Hiressemble Backend Startup
Started At : $startedAt
Working Dir: $backendDir
Profile    : local
============================================================

"@ | Set-Content `
    -Path $logFile `
    -Encoding UTF8


Set-Location $backendDir


# ---------------------------------------------------
# Gradle / Spring Boot 실행
#
# cmd.exe에서 stdout + stderr를 하나의 파일로 합친다.
# ---------------------------------------------------

$command = @"
call gradlew.bat bootRun --args="--spring.profiles.active=local" --console=plain --stacktrace >> "$logFile" 2>&1
"@

& cmd.exe /d /s /c $command

$exitCode = $LASTEXITCODE


# 종료 상태 기록
$endedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

@"

============================================================
Backend Process Ended
Ended At : $endedAt
Exit Code: $exitCode
============================================================
"@ | Add-Content `
    -Path $logFile `
    -Encoding UTF8


Set-Content `
    -Path $exitFile `
    -Value $exitCode `
    -Encoding ASCII

exit $exitCode
