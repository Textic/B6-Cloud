@echo off
set RG=B6Grupo
set TOPIC_NAME=BibliotecaTopic
set LOCATION=eastus

:: APPs Consumidoras
set APP_USUARIOS=B6FuncionTex
set APP_LIBROS=B6FuncionTex4
set APP_PRESTAMOS=B6FuncionTex2

echo === 1. Registrando proveedor de Event Grid ===
call az provider register --namespace Microsoft.EventGrid

echo === 2. Creando Custom Topic en Event Grid ===
call az eventgrid topic create --name %TOPIC_NAME% --location %LOCATION% --resource-group %RG%

echo === 3. Obteniendo Endpoint y Key ===
for /f "tokens=*" %%i in ('az eventgrid topic show --name %TOPIC_NAME% --resource-group %RG% --query "endpoint" --output tsv') do set TOPIC_ENDPOINT=%%i
for /f "tokens=*" %%i in ('az eventgrid topic key list --name %TOPIC_NAME% --resource-group %RG% --query "key1" --output tsv') do set TOPIC_KEY=%%i

echo.
echo ========================================================
echo CONFIGURACION EVENT GRID:
echo TOPIC_ENDPOINT=%TOPIC_ENDPOINT%
echo TOPIC_KEY=%TOPIC_KEY%
echo ========================================================
echo.

for /f "tokens=*" %%i in ('az account show --query id --output tsv') do set SUB_ID=%%i

echo === 4. Creando Suscripcion: Notificar Prestamo (Usuarios) ===
for /f "tokens=*" %%i in ('az functionapp function show --name %APP_USUARIOS% --resource-group %RG% --function-name notificarPrestamo --query "id" --output tsv') do set FUNC_ID_NOTIF=%%i
call az eventgrid event-subscription create --name SubNotificarPrestamo --source-resource-id "/subscriptions/%SUB_ID%/resourceGroups/%RG%/providers/Microsoft.EventGrid/topics/%TOPIC_NAME%" --endpoint %FUNC_ID_NOTIF% --endpoint-type azurefunction

echo === 5. Creando Suscripcion: Procesar Prestamo (Libros) ===
for /f "tokens=*" %%i in ('az functionapp function show --name %APP_LIBROS% --resource-group %RG% --function-name procesarPrestamo --query "id" --output tsv') do set FUNC_ID_LIBROS=%%i
call az eventgrid event-subscription create --name SubProcesarPrestamo --source-resource-id "/subscriptions/%SUB_ID%/resourceGroups/%RG%/providers/Microsoft.EventGrid/topics/%TOPIC_NAME%" --endpoint %FUNC_ID_LIBROS% --endpoint-type azurefunction

echo === 6. Creando Suscripcion: Limpiar Prestamos (Prestamos) ===
for /f "tokens=*" %%i in ('az functionapp function show --name %APP_PRESTAMOS% --resource-group %RG% --function-name limpiarPrestamos --query "id" --output tsv') do set FUNC_ID_PRESTAMOS=%%i
call az eventgrid event-subscription create --name SubLimpiarPrestamos --source-resource-id "/subscriptions/%SUB_ID%/resourceGroups/%RG%/providers/Microsoft.EventGrid/topics/%TOPIC_NAME%" --endpoint %FUNC_ID_PRESTAMOS% --endpoint-type azurefunction

echo.
echo === Proceso finalizado exitosamente ===
pause
