@echo off
title SAED - Backend REST (Puerto 8080)
chcp 65001 >nul
echo ===================================================
echo   Iniciando Backend SAED (Java REST + Oracle)
echo ===================================================
cd /d "%~dp0..\..\backend"
mvn compile exec:java -Dexec.mainClass="com.edificio.admin.RestServerMain"
pause
