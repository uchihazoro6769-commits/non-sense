@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_DIR=%APP_HOME%gradle\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
  echo Gradle wrapper JAR is missing. Downloading Gradle 8.9 wrapper JAR...
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar'; $o='%WRAPPER_JAR%'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $o"
  if errorlevel 1 (
    echo ERROR: Could not download gradle-wrapper.jar.
    echo Check your internet connection and run this command again.
    exit /b 1
  )
)

set CLASSPATH=%WRAPPER_JAR%
if not defined JAVA_HOME goto execute
"%JAVA_HOME%\bin\java.exe" -version >nul 2>&1
if errorlevel 1 goto execute
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
goto run

:execute
where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java was not found. Install/use JDK 21, preferably Android Studio's JBR 21.
  exit /b 1
)
set JAVA_EXE=java

:run
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set EXIT_CODE=%ERRORLEVEL%
endlocal & exit /b %EXIT_CODE%
