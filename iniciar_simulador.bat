@echo off
title SYSTEM - Simulador de Gestion de Procesos
echo ========================================================
echo     SYSTEM - SIMULADOR DE GESTION DE PROCESOS
echo     Universidad de Cordoba - Sistemas Operativos
echo ========================================================
echo.
echo [1/3] Verificando entorno Java...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] No se encontro Java instalado en el PATH.
    pause
    exit /b 1
)

echo [2/3] Iniciando Sistema Operativo Simulator...
echo       - Aplicacion 1 (Escritorio Swing): Se abrira en pantalla.
echo       - Aplicacion 2 (Web Round Robin):  Disponible en http://localhost:8080
echo       - Servicio Web REST (XML):          http://localhost:8080/api/catalogos/xml
echo.

cd /d "%~dp0"
if exist "simulador-procesos" cd simulador-procesos
call mvnw.cmd spring-boot:run

pause
