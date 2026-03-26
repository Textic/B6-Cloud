@echo off
echo Iniciando despliegue de Funcion de Usuarios a Azure...
cd faas-usuarios
call mvn clean package azure-functions:deploy
if %errorlevel% neq 0 (
    echo Error en el despliegue de Usuarios.
    pause
    exit /b %errorlevel%
)
echo Despliegue de Usuarios completado con exito.
pause
