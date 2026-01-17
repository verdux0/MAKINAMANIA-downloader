@echo off
setlocal enabledelayedexpansion
pushd "%~dp0"

if not exist bin mkdir bin

set "SOURCES="
for /r src %%f in (*.java) do (
  set "SOURCES=!SOURCES! "%%f""
)

javac -d bin -cp "lib/*" %SOURCES%

popd