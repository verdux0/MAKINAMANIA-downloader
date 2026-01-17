#!/bin/bash
if [ ! -d "bin" ] || [ -z "$(ls -A bin)" ]; then
    echo "Bin directory is empty or missing. Building..."
    ./build.sh
fi
java -cp "resources:bin:lib/*" makinamania.MainApp
