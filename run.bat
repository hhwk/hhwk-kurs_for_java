@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0target\app.jar" %*
