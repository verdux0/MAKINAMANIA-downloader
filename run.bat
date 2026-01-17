@echo off
REM Verificar si el directorio "bin" no existe o está vacío
IF NOT EXIST "bin" (
    ECHO Bin directory is empty or missing. Building...
    CALL build.bat
) ELSE (
    DIR /B "bin" >nul 2>&1
    IF %ERRORLEVEL% NEQ 0 (
        ECHO Bin directory is empty or missing. Building...
        CALL build.bat
    )
)

REM Ejecutar la aplicación Java
REM En Windows, el separador de classpath es ; en lugar de :
java -cp "resources;bin;lib/*" makinamania.MainApp