Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Compiling Aura Underground Parking System (Pure Java)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

$files = (Get-ChildItem -Recurse -Filter *.java src).FullName
& javac -d bin $files

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[SUCCESS] Java compilation completed into .\bin" -ForegroundColor Green
}
