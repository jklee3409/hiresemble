$ErrorActionPreference = "Stop"

# 이미 Docker Engine이 살아있는지 확인
docker info *> $null

if ($LASTEXITCODE -eq 0) {
    Write-Output '{"component":"docker","status":"UP","changed":false}'
    exit 0
}

$dockerDesktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"

if (-not (Test-Path $dockerDesktop)) {
    throw "Docker Desktop executable not found: $dockerDesktop"
}

Start-Process $dockerDesktop

$deadline = (Get-Date).AddMinutes(2)

while ((Get-Date) -lt $deadline) {

    Start-Sleep -Seconds 3

    docker info *> $null

    if ($LASTEXITCODE -eq 0) {
        Write-Output '{"component":"docker","status":"UP","changed":true}'
        exit 0
    }
}

throw "Docker Desktop did not become ready within timeout."
