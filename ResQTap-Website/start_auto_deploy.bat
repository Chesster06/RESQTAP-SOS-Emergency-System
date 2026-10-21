@echo off
title ResQTap Auto-Deploy Watcher
cd /d "%~dp0"
echo Memulakan ResQTap Auto-Deploy Watcher...
node auto_deploy_watcher.js
pause
