@echo off
if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -Descr.workspace="%~dp0." "%~dp0tools\build\BazelBootstrap.java" %*
) else (
    java -Descr.workspace="%~dp0." "%~dp0tools\build\BazelBootstrap.java" %*
)
exit /b %errorlevel%
