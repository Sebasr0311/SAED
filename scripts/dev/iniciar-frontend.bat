@echo off
title SAED - Frontend (React + Vite)
chcp 65001 >nul
echo ===================================================
echo   Iniciando Frontend SAED (React 18 + Vite)
echo ===================================================
cd /d "%~dp0..\..\frontend"
npm run dev
pause
