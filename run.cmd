:; if [ -z 0 ]; then
  @echo off
  goto :WINDOWS
fi

# --- LINUX / MAC (Bash) ---
if [ ! -d "bin" ] || [ -z "$(ls -A bin)" ]; then
    echo "Bin directory is empty or missing. Building..."
    ./build.cmd
fi
java -cp "resources:bin:lib/*" makinamania.MainApp
exit

:WINDOWS
REM --- WINDOWS (Batch) ---
REM Verificar si el directorio "bin" no existe o está vacío
IF NOT EXIST "bin" (
    ECHO Bin directory is empty or missing. Building...
    CALL build.cmd
) ELSE (
    DIR /B "bin" >nul 2>&1
    IF %ERRORLEVEL% NEQ 0 (
        ECHO Bin directory is empty or missing. Building...
        CALL build.cmd
    )
)

REM Ejecutar la aplicación Java
REM En Windows, el separador de classpath es ; en lugar de :
java -cp "resources;bin;lib/*" makinamania.MainApp
