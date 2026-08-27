@echo off
title SAED 2.0 - Launcher
chcp 65001 >nul
echo Iniciando entorno completo de desarrollo SAED 2.0...
start cmd /c "call %~dp0iniciar-backend.bat"
start cmd /c "call %~dp0iniciar-frontend.bat"
echo Entornos lanzados.
