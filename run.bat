@echo off
echo ========================================================
echo Starting Aura Underground Parking Server (Java 25)
echo ========================================================
call build.bat
if %errorlevel% neq 0 exit /b %errorlevel%
echo.
echo Launching server at http://localhost:8080 ...
java -cp bin com.underground.parking.UndergroundParkingServer
