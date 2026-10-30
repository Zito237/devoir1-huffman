@echo off
REM Compile tout le projet dans bin\ puis lance les tests (Windows).
cd /d "%~dp0"
if exist bin rmdir /s /q bin
dir /s /b src\*.java test\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
del sources.txt
java -Dstdout.encoding=UTF-8 -cp bin huffman.HuffmanTests
