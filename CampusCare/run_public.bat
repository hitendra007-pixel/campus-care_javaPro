@echo off
cd /d "%~dp0"
title CampusCare - Public Online Server
echo ========================================================
echo          CAMPUSCARE - PUBLIC ONLINE SERVER
echo ========================================================
echo.
echo 1. Starting CampusCare Java backend...
start "CampusCare Backend" java CampusComplaintSystem
timeout /t 2 /nobreak >nul
echo.
echo 2. Connecting to public HTTPS tunnel...
echo Look for the https://...lhr.life link below.
echo Send that link to anyone on any laptop or phone!
echo.
ssh -R 80:localhost:8090 -o StrictHostKeyChecking=no nokey@localhost.run
