@echo off
title SAED 2.0 - Backend Spring Boot (Puerto 8080)
chcp 65001 >nul
echo ===================================================
echo   Iniciando Backend SAED 2.0 (Spring Boot + Oracle)
echo ===================================================
cd /d "%~dp0..\..\backend"
set "JAVA_HOME=C:\Program Files\Java\jdk-24"
mvn spring-boot:run
pause
