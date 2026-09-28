@echo off
setlocal
set GRADLE_VERSION=9.2.1
set ROOT=%~dp0
set TOOLS=%ROOT%.gradle-tools
set DIST=%TOOLS%\gradle-%GRADLE_VERSION%
set ZIP=%TOOLS%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%TOOLS%" mkdir "%TOOLS%"
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%TOOLS%'"
)
call "%DIST%\bin\gradle.bat" %*
endlocal
