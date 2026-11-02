@echo off
cd /d "%~dp0"
title Push CampusCare to GitHub
echo ========================================================
echo          PUSH CAMPUSCARE TO GITHUB
echo ========================================================
echo.
echo Make sure you created a new empty repository on GitHub first!
echo (Go to: https://github.com/new and create 'CampusCare')
echo.
set /p REPO_URL="Paste your GitHub Repository URL here: "
if "%REPO_URL%"=="" (
    echo ERROR: No URL entered.
    pause
    exit /b 1
)
git remote remove origin 2>nul
git remote add origin %REPO_URL%
echo.
echo Pushing code to GitHub main branch...
git push -u origin main
if errorlevel 1 (
    echo.
    echo If push failed, make sure you are signed in to GitHub and the repository is empty.
) else (
    echo.
    echo ========================================================
    echo SUCCESS! Code is uploaded to GitHub.
    echo.
    echo Final step:
    echo 1. Open https://dashboard.render.com
    echo 2. Click 'New +' -> 'Web Service'
    echo 3. Choose your 'CampusCare' repository and click 'Deploy Web Service'!
    echo ========================================================
)
pause
