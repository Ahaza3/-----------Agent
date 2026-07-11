@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows (cmd.exe / PowerShell)
@REM ----------------------------------------------------------------------------

@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%CD%"
set "MVNW_VERBOSE=false"

if not defined MAVEN_HOME (
    set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
)

set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%WRAPPER_JAR%'"
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Failed to download maven-wrapper.jar
        exit /b 1
    )
)

set "MAVEN_OPTS=-Xmx1024m"

@REM Execute Maven
java %MAVEN_OPTS% -jar "%WRAPPER_JAR%" %*
