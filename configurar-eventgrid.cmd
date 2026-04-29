@echo off
set RG=B6Grupo
set TOPIC_NAME=BibliotecaTopic
set LOCATION=eastus
set SUB_NAME=SubNotificarPrestamo
set SUBSCRIBER_APP=B6FuncionTex
set FUNCTION_NAME=notificarPrestamo

echo === 1. Registrando proveedor de Event Grid ===
call az provider register --namespace Microsoft.EventGrid

echo === 2. Creando Custom Topic en Event Grid ===
call az eventgrid topic create --name %TOPIC_NAME% --location %LOCATION% --resource-group %RG%

echo === 3. Obteniendo Endpoint y Key ===
for /f "tokens=*" %%i in ('az eventgrid topic show --name %TOPIC_NAME% --resource-group %RG% --query "endpoint" --output tsv') do set TOPIC_ENDPOINT=%%i
for /f "tokens=*" %%i in ('az eventgrid topic key list --name %TOPIC_NAME% --resource-group %RG% --query "key1" --output tsv') do set TOPIC_KEY=%%i

echo.
echo ========================================================
echo COPIA ESTAS VARIABLES A TU CONFIGURACION DE AZURE (B6FuncionTex2):
echo EVENT_GRID_TOPIC_ENDPOINT=%TOPIC_ENDPOINT%
echo EVENT_GRID_TOPIC_KEY=%TOPIC_KEY%
echo ========================================================
echo.

echo === 4. Obteniendo ID de la suscripcion y de la funcion consumidora ===
for /f "tokens=*" %%i in ('az account show --query id --output tsv') do set SUB_ID=%%i
for /f "tokens=*" %%i in ('az functionapp function show --name %SUBSCRIBER_APP% --resource-group %RG% --function-name %FUNCTION_NAME% --query "id" --output tsv') do set FUNCTION_ID=%%i

echo === 5. Creando suscripcion de eventos ===
call az eventgrid event-subscription create ^
    --name %SUB_NAME% ^
    --source-resource-id "/subscriptions/%SUB_ID%/resourceGroups/%RG%/providers/Microsoft.EventGrid/topics/%TOPIC_NAME%" ^
    --endpoint %FUNCTION_ID% ^
    --endpoint-type azurefunction

echo.
echo === Proceso finalizado exitosamente ===
pause
