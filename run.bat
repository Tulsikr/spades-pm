@echo off
setlocal
cd /d "%~dp0"
if /I "%SPADES_STORAGE%"=="mysql" goto mysql
javac *.java
if errorlevel 1 goto failed
java Main
goto end
:mysql
where mvn >nul 2>nul
if errorlevel 1 (
  echo MySQL mode requires Maven to download MySQL Connector/J.
  echo Install Maven, create the spades_pm database, then set SPADES_DB_USER and SPADES_DB_PASSWORD.
  goto failed
)
mvn -q compile exec:java
if errorlevel 1 goto failed
goto end
:failed
echo.
echo Spades PM did not start. Review the error above.
pause
:end
endlocal
