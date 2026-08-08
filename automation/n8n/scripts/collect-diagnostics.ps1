param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Root,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$FailedComponent,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputPath
)

# PowerShell 자체 오류는 실패 처리
$ErrorActionPreference = "Stop"

# UTF-8
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)


# ============================================================
# Native Command 실행 함수
# ============================================================

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [string[]]$Arguments = @()
    )

    $commandInfo = Get-Command $Command -ErrorAction SilentlyContinue

    if (-not $commandInfo) {
        return [PSCustomObject]@{
            exitCode = 127
            output   = "$Command command not found in PATH"
        }
    }

    $previousErrorActionPreference = $ErrorActionPreference

    try {
        # Native 프로그램의 stderr가 PowerShell 전체를 죽이지 않게 함
        $ErrorActionPreference = "Continue"

        $rawOutput = & $commandInfo.Source @Arguments 2>&1

        $exitCode = $LASTEXITCODE

        $output = (
            $rawOutput |
            ForEach-Object {
                $_.ToString()
            } |
            Out-String
        ).Trim()

        return [PSCustomObject]@{
            exitCode = $exitCode
            output   = $output
        }
    }
    catch {
        return [PSCustomObject]@{
            exitCode = 1
            output   = $_.Exception.Message
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}


# ============================================================
# 로그 읽기 함수
# ============================================================

function Get-LogTail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [int]$Lines
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return "$(Split-Path $Path -Leaf) not found"
    }

    try {
        return (
            Get-Content `
                -LiteralPath $Path `
                -Encoding UTF8 `
                -Tail $Lines `
                -ErrorAction Stop |
            Out-String
        ).Trim()
    }
    catch {
        return "Failed to read $(Split-Path $Path -Leaf): $($_.Exception.Message)"
    }
}


# ============================================================
# 1. Project Root 검증
# ============================================================

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    throw "Project root not found: $Root"
}

$Root = (Resolve-Path -LiteralPath $Root).Path


# ============================================================
# 2. Output 디렉터리 준비
# ============================================================

$outputDirectory = Split-Path $OutputPath -Parent

if ([string]::IsNullOrWhiteSpace($outputDirectory)) {
    throw "Invalid OutputPath: $OutputPath"
}

if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item `
        -ItemType Directory `
        -Force `
        -Path $outputDirectory |
    Out-Null
}


# 이전 실행 결과는 절대 재사용하지 않는다.

if (Test-Path -LiteralPath $OutputPath) {
    Remove-Item `
        -LiteralPath $OutputPath `
        -Force `
        -ErrorAction Stop
}


# ============================================================
# 3. Docker Compose 파일 탐색
# ============================================================

$composeCandidates = @(
    "docker-compose.yml",
    "docker-compose.yaml",
    "compose.yml",
    "compose.yaml"
)

$composeFile = $null

foreach ($candidate in $composeCandidates) {

    $candidatePath = Join-Path $Root $candidate

    if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
        $composeFile = $candidatePath
        break
    }
}


# ============================================================
# 4. Docker 상태 수집
#
# Docker 실패는 diagnostics 실패가 아니다.
# 실패 내용 자체를 결과로 저장한다.
# ============================================================

if ($composeFile) {

    $dockerResult = Invoke-NativeCommand `
        -Command "docker" `
        -Arguments @(
            "compose",
            "-f",
            $composeFile,
            "ps"
        )

}
else {

    $dockerResult = [PSCustomObject]@{
        exitCode = 2
        output   = "Docker Compose file not found under: $Root"
    }

}


# ============================================================
# 5. Git 상태
# ============================================================

$gitStatusResult = Invoke-NativeCommand `
    -Command "git" `
    -Arguments @(
        "-C",
        $Root,
        "-c",
        "core.quotepath=false",
        "status",
        "--short"
    )


# ============================================================
# 6. 최근 Commit
# ============================================================

$recentCommitsResult = Invoke-NativeCommand `
    -Command "git" `
    -Arguments @(
        "-C",
        $Root,
        "-c",
        "i18n.logOutputEncoding=utf-8",
        "log",
        "-5",
        "--pretty=format:%h %s"
    )


# ============================================================
# 7. Git Diff Stat
# ============================================================

$gitDiffStatResult = Invoke-NativeCommand `
    -Command "git" `
    -Arguments @(
        "-C",
        $Root,
        "-c",
        "core.quotepath=false",
        "diff",
        "--stat"
    )


# ============================================================
# 8. Application Logs
# ============================================================

$logDir = Join-Path $Root "automation\n8n\logs"

$backendLogPath = Join-Path $logDir "backend.log"
$frontendLogPath = Join-Path $logDir "frontend.log"

$backendLog = Get-LogTail `
    -Path $backendLogPath `
    -Lines 200

$frontendLog = Get-LogTail `
    -Path $frontendLogPath `
    -Lines 300


# ============================================================
# 9. Diagnostics 결과 구성
# ============================================================

$result = [PSCustomObject]@{

    collectedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")

    failedComponent = $FailedComponent

    projectRoot = $Root

    composeFile = $composeFile


    # --------------------------------------------------------
    # Docker
    # --------------------------------------------------------

    dockerStatus = $dockerResult.output

    dockerExitCode = $dockerResult.exitCode


    # --------------------------------------------------------
    # Git
    # --------------------------------------------------------

    gitStatus = $gitStatusResult.output

    gitStatusExitCode = $gitStatusResult.exitCode


    recentCommits = $recentCommitsResult.output

    recentCommitsExitCode = $recentCommitsResult.exitCode


    gitDiffStat = $gitDiffStatResult.output

    gitDiffStatExitCode = $gitDiffStatResult.exitCode


    # --------------------------------------------------------
    # Logs
    # --------------------------------------------------------

    backendLog = $backendLog

    frontendLog = $frontendLog
}


# ============================================================
# 10. JSON 변환
# ============================================================

$json = $result |
    ConvertTo-Json -Depth 6


# ============================================================
# 11. UTF-8 BOM 없이 저장
# ============================================================

$utf8 = [System.Text.UTF8Encoding]::new($false)

[System.IO.File]::WriteAllText(
    $OutputPath,
    $json,
    $utf8
)


# ============================================================
# 12. n8n stdout
# ============================================================

Write-Output "Diagnostics collected successfully."
Write-Output "Output: $OutputPath"

exit 0
