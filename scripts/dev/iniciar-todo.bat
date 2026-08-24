@echo off
title SAED - Lanzador del Sistema Completo
chcp 65001 >nul
echo ===================================================
echo   Lanzando SAED (Backend + Frontend)
echo ===================================================
start "SAED Backend" "%~dp0iniciar-backend.bat"
timeout /t 2 >nul
start "SAED Frontend" "%~dp0iniciar-frontend.bat"
echo Sistema iniciado en terminales independientes.
