@echo off
title PARADOX - run tests
rem Runs the 9 unit/integration tests: double-click this file.
cd /d "%~dp0"
call "C:\Users\sabya\tools\apache-maven-3.9.16\bin\mvn" clean verify
pause
