$ErrorActionPreference = "Stop"

# ---------------------------------------------------------
# docker info의 exit code 1이 PowerShell 자체를 죽이지 않게 한다.
# ---------------------------------------------------------

if (Test-Path Variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$dockerCommand = Get-Command "docker.exe" -ErrorAction SilentlyContinue

if (-not $dockerCommand) {
    [Console]::Error.WriteLine("docker.exe not found.")
    exit 1
}

$dockerExe = $dockerCommand.Source


function Test-DockerEngine {

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {

        $output = & $dockerExe info 2>&1 | Out-String
        $exitCode = $LASTEXITCODE

        return [PSCustomObject]@{
            Ready    = ($exitCode -eq 0)
            ExitCode = $exitCode
            Output   = $output.Trim()
        }

    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}


# =========================================================
# 1. Docker Engine이 이미 실행 중인지 확인
# =========================================================

$result = Test-DockerEngine

if ($result.Ready) {

    Write-Output '{"component":"docker","status":"UP","changed":false}'
    exit 0
}


# =========================================================
# 2. Docker Desktop 프로세스 확인
# =========================================================

$dockerDesktopPath =
    Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"

if (-not (Test-Path $dockerDesktopPath)) {

    [Console]::Error.WriteLine(
        "Docker Desktop executable not found: $dockerDesktopPath"
    )

    exit 1
}


$dockerDesktopProcess =
    Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue


$startedDockerDesktop = $false


# Docker Desktop 자체가 안 떠 있을 때만 실행
if (-not $dockerDesktopProcess) {

    try {

        Start-Process `
            -FilePath $dockerDesktopPath `
            -ErrorAction Stop

        $startedDockerDesktop = $true

    }
    catch {

        [Console]::Error.WriteLine(
            "Failed to start Docker Desktop: $($_.Exception.Message)"
        )

        exit 1
    }
}


# =========================================================
# 3. Docker Engine 준비 대기
# =========================================================

$maxWaitSeconds = 300
$checkIntervalSeconds = 3
$elapsedSeconds = 0

$lastResult = $result


while ($elapsedSeconds -lt $maxWaitSeconds) {

    Start-Sleep -Seconds $checkIntervalSeconds

    $elapsedSeconds += $checkIntervalSeconds

    $lastResult = Test-DockerEngine


    if ($lastResult.Ready) {

        if ($startedDockerDesktop) {

            Write-Output `
                '{"component":"docker","status":"UP","changed":true}'

        }
        else {

            Write-Output `
                '{"component":"docker","status":"UP","changed":false}'

        }

        exit 0
    }
}


# =========================================================
# 4. 진짜 실패한 경우에만 오류
# =========================================================

[Console]::Error.WriteLine(
    "Docker Engine did not become ready within $maxWaitSeconds seconds."
)

if ($lastResult.Output) {

    [Console]::Error.WriteLine("")
    [Console]::Error.WriteLine("Last docker info result:")
    [Console]::Error.WriteLine($lastResult.Output)
}

exit 1
