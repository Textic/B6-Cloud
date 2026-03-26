@echo off
echo Iniciando despliegue de Funcion de Prestamos a Azure...
cd faas-prestamos
call mvn clean package azure-functions:deploy
if %errorlevel% neq 0 (
    echo Error en el despliegue de Prestamos.
    pause
    exit /b %errorlevel%
)
echo Despliegue de Prestamos completado con exito.
pause
