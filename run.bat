@echo off
title PARADOX - Simple Deadlock Detection System
rem Launcher: double-click this file to start the application.
cd /d "%~dp0"
echo Starting MySQL check and application...
call "C:\Users\sabya\tools\apache-maven-3.9.16\bin\mvn" javafx:run
pause
