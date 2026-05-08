@echo off
set RG=B6Grupo

echo ========================================================
echo ACTUALIZAR CONEXION ORACLE (NGROK) EN AZURE
echo ========================================================
echo.
echo Ejemplo de formato: 0.tcp.sa.ngrok.io:24749
set /p NGROK_ADDR="Introduce la direccion de NGROK: "

if "%NGROK_ADDR%"=="" (
    echo Error: No introdujiste ninguna direccion.
    pause
    exit /b
)

set DB_URL=jdbc:oracle:thin:@%NGROK_ADDR%/XE
set DB_USER=SYSTEM
set DB_PASS=library_pass

echo.
echo Configurando URL: %DB_URL%
echo.

echo === 1. Actualizando B6FuncionTex (Usuarios) ===
call az functionapp config appsettings set --name B6FuncionTex --resource-group %RG% --settings DB_URL="%DB_URL%" DB_USER="%DB_USER%" DB_PASS="%DB_PASS%"

echo === 2. Actualizando B6FuncionTex2 (Prestamos) ===
call az functionapp config appsettings set --name B6FuncionTex2 --resource-group %RG% --settings DB_URL="%DB_URL%" DB_USER="%DB_USER%" DB_PASS="%DB_PASS%"

echo === 3. Actualizando B6FuncionTex3 (Autores) ===
call az functionapp config appsettings set --name B6FuncionTex3 --resource-group %RG% --settings DB_URL="%DB_URL%" DB_USER="%DB_USER%" DB_PASS="%DB_PASS%"

echo === 4. Actualizando B6FuncionTex4 (Libros) ===
call az functionapp config appsettings set --name B6FuncionTex4 --resource-group %RG% --settings DB_URL="%DB_URL%" DB_USER="%DB_USER%" DB_PASS="%DB_PASS%"

echo.
echo === Proceso finalizado exitosamente ===
echo Las funciones ahora apuntan a tu DB local via Ngrok.
pause
