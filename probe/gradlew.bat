@echo off
setlocal enabledelayedexpansion

set SCRIPT=%~f0
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set TMP_ZIP=%APP_HOME%gradle\wrapper\gradle-dist.zip
set DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
set LAUNCHER_PATH=gradle-8.10.2\lib\gradle-launcher-8.10.2.jar

if not exist "%APP_HOME%gradle\wrapper" (
    mkdir "%APP_HOME%gradle\wrapper"
)

if not exist "%WRAPPER_JAR%" (
    echo Gradle wrapper jar missing, downloading from %DISTRIBUTION_URL%
    where curl >nul 2>nul
    if %errorlevel%==0 (
        curl -fL "%DISTRIBUTION_URL%" -o "%TMP_ZIP%"
    ) else (
        powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TMP_ZIP%'" || goto downloadError
    )
    powershell -Command "Expand-Archive -Path '%TMP_ZIP%' -DestinationPath '%APP_HOME%gradle\wrapper\dist' -Force" || goto downloadError
    copy "%APP_HOME%gradle\wrapper\dist\%LAUNCHER_PATH%" "%WRAPPER_JAR%" >nul || goto downloadError
    rmdir /s /q "%APP_HOME%gradle\wrapper\dist" >nul 2>&1
    del "%TMP_ZIP%"
)

goto execute

downloadError:
    echo Failed to download Gradle wrapper jar. Ensure curl or PowerShell is available.
    exit /b 1

:execute
set JAVA_EXEC=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_EXEC%" (
    for %%i in (java.exe) do set JAVA_EXEC=%%~$PATH:i
)
if "!JAVA_EXEC!"=="" (
    echo Unable to locate Java executable. Set JAVA_HOME or adjust PATH.
    exit /b 1
)

"!JAVA_EXEC!" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
