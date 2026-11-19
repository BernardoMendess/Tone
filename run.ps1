param(
    [ValidateSet("dev", "postgres")]
    [string]$Profile = "dev",
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"
$JarPath = Join-Path $BackendDir "target\tone-backend-1.0.0.jar"

# Resolve Java executable (prefer JAVA_HOME to avoid Oracle javapath shim issues)
$JavaExe = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    "java"
}

function Find-Maven {
    if (Get-Command mvn -ErrorAction SilentlyContinue) { return "mvn" }
    $cached = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Depth 4 -Filter "mvn.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cached) { return $cached.FullName }
    throw "Maven nao encontrado no PATH nem no cache .m2. Instale o Maven ou compile o backend primeiro."
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Tone Marketplace - Launcher" -ForegroundColor Cyan
Write-Host "  Perfil ativo: $Profile" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. Start Docker infrastructure if using postgres + MinIO S3 profile
if ($Profile -eq "postgres") {
    Write-Host "`n[1/4] Subindo PostgreSQL 17 e MinIO (S3 privado) via Docker Compose..." -ForegroundColor Yellow
    docker compose -f (Join-Path $Root "docker-compose.yml") up -d
}

# 2. Build backend JAR if missing or -Rebuild requested
if ($Rebuild -or -not (Test-Path $JarPath)) {
    Write-Host "`n[2/4] Compilando backend Spring Boot..." -ForegroundColor Yellow
    $Mvn = Find-Maven
    & $Mvn -f (Join-Path $BackendDir "pom.xml") package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o backend." }
} else {
    Write-Host "`n[2/4] JAR do backend encontrado: $JarPath" -ForegroundColor Green
}

# 3. Ensure frontend dependencies are installed
if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
    Write-Host "`n[3/4] Instalando dependencias do frontend Angular..." -ForegroundColor Yellow
    npm --prefix $FrontendDir install
    if ($LASTEXITCODE -ne 0) { throw "Falha ao instalar dependencias do frontend." }
} else {
    Write-Host "`n[3/4] Dependencias do frontend ja instaladas." -ForegroundColor Green
}

# 4. Start backend and wait until ready, then start frontend (cleans up backend on Ctrl+C)
Write-Host "`n[4/4] Iniciando Backend (porta 8080)..." -ForegroundColor Yellow
$BackendProc = Start-Process -FilePath $JavaExe `
    -ArgumentList "-jar", $JarPath, "--spring.profiles.active=$Profile" `
    -WorkingDirectory $BackendDir `
    -PassThru `
    -NoNewWindow

try {
    Write-Host "Aguardando API responder em http://localhost:8080/api/products ..." -ForegroundColor Gray
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        if ($BackendProc.HasExited) {
            throw "O processo do backend encerrou inesperadamente (codigo $($BackendProc.ExitCode))."
        }
        try {
            $res = Invoke-RestMethod -Uri "http://localhost:8080/api/products" -TimeoutSec 2
            if ($null -ne $res) { $ready = $true; break }
        } catch {
            Start-Sleep -Milliseconds 1000
        }
    }

    if (-not $ready) {
        throw "Backend nao respondeu dentro do tempo limite."
    }

    Write-Host "`nBackend pronto em http://localhost:8080" -ForegroundColor Green
    Write-Host "Iniciando Frontend Angular em http://localhost:4200 (Ctrl+C para encerrar tudo)...`n" -ForegroundColor Green

    npm --prefix $FrontendDir start
}
finally {
    if ($BackendProc -and -not $BackendProc.HasExited) {
        Write-Host "`nEncerrando backend (PID $($BackendProc.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $BackendProc.Id -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Tone encerrado." -ForegroundColor Cyan
}
