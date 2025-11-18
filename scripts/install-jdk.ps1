# Installs Eclipse Temurin JDK 17 via winget (Windows)
# Run as Administrator if needed.

Write-Host "Checking Java version..."
try {
    $javaVersionOutput = & java -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Java is already installed:"`n$javaVersionOutput
        return
    }
}
catch {}

Write-Host "Java not found or not in PATH. Attempting to install Temurin 17 via winget..."

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host "winget is not available on this machine. Please install JDK 17 manually or enable winget." -ForegroundColor Yellow
    exit 1
}

# Exact id may vary; this attempts to install the common Temurin 17 package
$pkgId = 'EclipseAdoptium.Temurin.17'
Write-Host "Running: winget install --id $pkgId -e"
winget install --id $pkgId -e

if ($LASTEXITCODE -ne 0) {
    Write-Host "winget install failed. Please install JDK 17 manually from https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/" -ForegroundColor Red
    exit 1
}

Write-Host "JDK 17 installation attempted. Please restart your shell and ensure `java -version` shows Java 17." -ForegroundColor Green
