& .\build.ps1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Starting Aura Underground Parking Server at http://localhost:8080 ..." -ForegroundColor Cyan
    & java -cp bin com.underground.parking.UndergroundParkingServer
}
