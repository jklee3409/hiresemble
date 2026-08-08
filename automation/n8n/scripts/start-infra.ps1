param(
    [Parameter(Mandatory=$true)]
    [string]$Root
)

$ErrorActionPreference = "Stop"

Set-Location $Root

docker compose up -d postgres minio

if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed."
}

docker compose ps
