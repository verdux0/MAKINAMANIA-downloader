#!/bin/bash
mkdir -p bin
javac -d bin -cp "lib/*" $(find src -name "*.java")
