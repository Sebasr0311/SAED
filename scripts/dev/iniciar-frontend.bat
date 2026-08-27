@echo off
title SAED 2.0 - Frontend Vite (Puerto 5173)
chcp 65001 >nul
echo ===================================================
echo   Iniciando Frontend SAED 2.0 (React + Vite)
echo ===================================================
cd /d "%~dp0..\..\frontend"
npm run dev
pause
