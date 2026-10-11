@echo off
echo ========================================================
echo Compiling Aura Underground Parking System (Pure Java)
echo ========================================================
if not exist bin mkdir bin
javac -d bin src\main\java\com\underground\parking\*.java src\main\java\com\underground\parking\model\*.java src\main\java\com\underground\parking\service\*.java src\main\java\com\underground\parking\util\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed!
    exit /b %errorlevel%
)
echo [SUCCESS] Compilation completed into .\bin
