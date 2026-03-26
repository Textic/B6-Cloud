@echo off
set "APP_NAME=B6FuncionTex2"
set "RG_NAME=B6Grupo"

echo [1/4] Seleccionando suscripcion Azure...
call az account set --subscription f620bf86-f52f-4e36-861f-d1d94b342485

echo [2/4] Compilando y Preparando archivos locales...
cd faas-prestamos
call mvn clean package -DskipTests
if %errorlevel% neq 0 (echo Error en compilacion && pause && exit /b 1)

echo [3/4] Comprimiendo archivos para Azure...
powershell -Command "Compress-Archive -Path target/azure-functions/%APP_NAME%/* -DestinationPath target/azure-functions/%APP_NAME%/%APP_NAME%.zip -Force"
if %errorlevel% neq 0 (echo Error al comprimir && pause && exit /b 1)

echo [4/4] Subiendo codigo a %APP_NAME% en Azure...
call az functionapp deployment source config-zip -g %RG_NAME% -n %APP_NAME% --src target/azure-functions/%APP_NAME%/%APP_NAME%.zip
if %errorlevel% neq 0 (echo Error en el despliegue CLI && pause && exit /b 1)

echo DESPLIEGUE DE PRESTAMOS COMPLETADO CON EXITO
pause
