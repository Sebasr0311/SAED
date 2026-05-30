@echo off
title SAED - Configuracion Local
chcp 65001 >nul

echo ====================================================
echo    SAED - Configuracion de Base de Datos Local
echo ====================================================
echo.
echo Requisitos:
echo   - Oracle XE 18c instalado (https://www.oracle.com/xe/)
echo   - Java JDK 17+ instalado
echo   - Maven instalado
echo.

:: Buscar sqlplus.exe
set SQLPLUS=
for %%D in (
  "%ProgramFiles%\..\app\%USERNAME%\product\18.0.0\dbhomeXE\bin\sqlplus.exe"
  "%ProgramFiles%\..\app\%USERNAME%\product\21c\dbhomeXE\bin\sqlplus.exe"
  "%ProgramFiles%\Oracle\18.0.0\dbhomeXE\bin\sqlplus.exe"
  "C:\oraclexe\app\oracle\product\18.0.0\dbhomeXE\BIN\sqlplus.exe"
  "C:\oracle\product\18.0.0\dbhomeXE\bin\sqlplus.exe"
) do if exist %%D set SQLPLUS=%%D

if not defined SQLPLUS (
  where sqlplus.exe >nul 2>nul && set SQLPLUS=sqlplus.exe
)

if not defined SQLPLUS (
  echo [ERROR] No se encontro sqlplus.exe
  echo.
  echo Instala Oracle XE 18c desde:
  echo   https://www.oracle.com/database/technologies/xe-downloads.html
  echo.
  echo Despues de instalar, busca la carpeta donde esta sqlplus.exe
  echo (ej: C:\app\tu-usuario\product\18.0.0\dbhomeXE\bin\)
  echo y agregala al PATH del sistema.
  pause
  exit /b 1
)

echo [OK] sqlplus.exe: %SQLPLUS%
echo.

set /P SYSPASS="Password de SYSTEM (el que pusiste al instalar Oracle XE): "

echo.
echo ====================================================
echo  1. Creando usuario RESIDENCIAL...
echo ====================================================

(
  echo ALTER SESSION SET CONTAINER=XEPDB1;
  echo CREATE USER RESIDENCIAL IDENTIFIED BY "Residencial2024#";
  echo GRANT CONNECT, RESOURCE, DBA TO RESIDENCIAL;
  echo GRANT UNLIMITED TABLESPACE TO RESIDENCIAL;
  echo EXIT;
) > "%TEMP%\crear_usuario.sql"

%SQLPLUS% -S SYSTEM/%SYSPASS%@localhost:1521/XEPDB1 @"%TEMP%\crear_usuario.sql"
echo [OK] Usuario RESIDENCIAL creado (o ya existia).

echo.
echo ====================================================
echo  2. Creando tablas y esquema...
echo ====================================================

echo Ejecutando modelo_relacional_v4_atp.sql...
%SQLPLUS% -S RESIDENCIAL/Residencial2024%@localhost:1521/XEPDB1 @"database\modelo_relacional_v4_atp.sql"
if %ERRORLEVEL% NEQ 0 (
  echo [ERROR] Fallo al crear el esquema.
  echo Revisa que Oracle XE este corriendo (servicio OracleServiceXE).
  pause
  exit /b 1
)
echo [OK] Esquema creado.

echo.
echo ====================================================
echo  3. Cargando datos de prueba...
echo ====================================================

echo Ejecutando datos_prueba_v2.sql...
%SQLPLUS% -S RESIDENCIAL/Residencial2024%@localhost:1521/XEPDB1 @"database\datos_prueba_v2.sql"
echo [OK] Datos de prueba cargados.

echo.
echo ====================================================
echo  4. Construyendo el backend...
echo ====================================================

call mvn package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
  echo [ERROR] Fallo la compilacion. Asegurate de tener:
  echo   - Java JDK 17+ (java --version)
  echo   - Maven 3.8+ (mvn --version)
  pause
  exit /b 1
)
echo [OK] Backend compilado.

echo.
echo ====================================================
echo  ¡Listo!
echo ====================================================
echo.
echo Para iniciar el servidor, ejecuta:
echo   java -jar target\admin-residencial-1.0-SNAPSHOT.jar
echo.
echo Luego abre en tu navegador:
echo   http://localhost:8080
echo.
echo Usuarios de prueba (password: admin123):
echo   admin        - ADMINISTRADOR
echo   portero1     - PORTERO
echo   carlos.perez - RESIDENTE
echo.
pause
