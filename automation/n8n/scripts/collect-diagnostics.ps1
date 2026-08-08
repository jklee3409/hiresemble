param(
    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [string]$Root,

    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [string]$FailedComponent,

    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# 이전 실행의 diagnostics.json을 절대 재사용하지 않음
if (Test-Path $OutputPath) {
    Remove-Item $OutputPath -Force
}

# --------------------------------------------------
# Docker Compose 파일
# --------------------------------------------------

$composeCandidates = @(
    "docker-compose.yml",
    "docker-compose.yaml",
    "compose.yml",
    "compose.yaml"
)

$composeFile = $null

foreach ($candidate in $composeCandidates) {

    $candidatePath = Join-Path $Root $candidate

    if (Test-Path $candidatePath) {
        $composeFile = $candidatePath
        break
    }
}

if (-not $composeFile) {
    throw "Docker Compose file not found under: $Root"
}

# --------------------------------------------------
# Docker
# cwd에 의존하지 않고 compose 파일을 명시
# --------------------------------------------------

$dockerStatus = (
    & docker compose -f $composeFile ps 2>&1 |
    Out-String
).Trim()

# --------------------------------------------------
# Git
# cwd에 의존하지 않고 repository를 명시
# --------------------------------------------------

$gitStatus = (
    & git -C $Root status --short 2>&1 |
    Out-String
).Trim()

$recentCommits = (
    & git `
        -C $Root `
        -c i18n.logOutputEncoding=utf-8 `
        log `
        -5 `
        --pretty=format:"%h %s" `
        2>&1 |
    Out-String
).Trim()

$gitDiffStat = (
    & git -C $Root diff --stat 2>&1 |
    Out-String
).Trim()

# --------------------------------------------------
# Logs
# --------------------------------------------------

$logDir = Join-Path $Root "automation\n8n\logs"

$backendLogPath = Join-Path $logDir "backend.log"
$frontendLogPath = Join-Path $logDir "frontend.log"

if (Test-Path $backendLogPath) {
    $backendLog = (
        Get-Content $backendLogPath -Tail 200 |
        Out-String
    ).Trim()
}
else {
    $backendLog = "backend.log not found"
}

if (Test-Path $frontendLogPath) {
    $frontendLog = (
        Get-Content $frontendLogPath -Tail 300 |
        Out-String
    ).Trim()
}
else {
    $frontendLog = "frontend.log not found"
}

# --------------------------------------------------
# 결과
# --------------------------------------------------

$result = [PSCustomObject]@{
    failedComponent = $FailedComponent
    projectRoot     = $Root
    composeFile     = $composeFile
    dockerStatus    = $dockerStatus
    gitStatus       = $gitStatus
    recentCommits   = $recentCommits
    gitDiffStat     = $gitDiffStat
    backendLog      = $backendLog
    frontendLog     = $frontendLog
}

$json = $result | ConvertTo-Json -Depth 6

$outputDirectory = Split-Path $OutputPath -Parent

if (-not (Test-Path $outputDirectory)) {
    New-Item `
        -ItemType Directory `
        -Force `
        -Path $outputDirectory |
        Out-Null
}

$utf8 = [System.Text.UTF8Encoding]::new($false)

[System.IO.File]::WriteAllText(
    $OutputPath,
    $json,
    $utf8
)
