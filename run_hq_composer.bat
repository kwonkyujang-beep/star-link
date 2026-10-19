@echo off
chcp 65001 > nul
set PROJECT_DIR=%~dp0
cd /d "%PROJECT_DIR%"

echo ==================================================
echo    🏢 Starlink [본사 메뉴 컴포저] 가동
echo ==================================================

:: 서버가 켜져있는지 확인 (8888 포트)
netstat -aon | findstr :8888 >nul 2>&1
if errorlevel 1 (
    echo ⚠️ 서버가 켜져있지 않습니다!
    echo 먼저 미니 PC 서버를 가동해 주세요. (예: run_cafe.bat)
    pause
    exit /b 1
)

:: 크롬 브라우저로 본사 컴포저 오픈
echo ✅ 메뉴 컴포저 화면을 엽니다...
start chrome --app="http://localhost:8888/hq_menu_composer"
