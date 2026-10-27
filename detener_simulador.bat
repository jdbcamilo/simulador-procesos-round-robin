@echo off
title SYSTEM - Detener Simulador de Procesos
echo ========================================================
echo     SYSTEM - DETENER SIMULADOR DE PROCESOS
echo     Universidad de Cordoba - Sistemas Operativos
echo ========================================================
echo.

setlocal enabledelayedexpansion
set "ENCONTRADO=0"

echo [1/2] Buscando proceso activo en el puerto 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8080.*LISTENING"') do (
    set "PID=%%a"
    if defined PID (
        if "!PID!" neq "0" (
            set "ENCONTRADO=1"
            echo [INFO] Proceso detectado con PID: !PID!
            echo [2/2] Deteniendo proceso y liberando puerto 8080...
            taskkill /F /T /PID !PID! >nul 2>&1
            if !errorlevel! equ 0 (
                echo [OK] Simulador con PID !PID! finalizado correctamente.
            ) else (
                echo [AVISO] Se intento detener el PID !PID!.
            )
        )
    )
)

if "%ENCONTRADO%"=="0" (
    echo [INFO] No se encontro ningun simulador en ejecucion (puerto 8080 libre).
)

echo.
echo ========================================================
echo     El simulador ha sido detenido.
echo ========================================================
echo.
pause
