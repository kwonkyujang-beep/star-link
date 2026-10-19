@echo off
chcp 65001 > nul
set PROJECT_DIR=%~dp0
cd /d "%PROJECT_DIR%"

echo ==================================================
echo    ☕ Starlink [카페 모드] 서버 엔진 가동
echo ==================================================

:: 기존 포트 8888 종료 (Windows 방식)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8888') do taskkill /f /pid %%a >nul 2>&1
taskkill /f /im cloudflared.exe >nul 2>&1

:: 카페 전용 설정 덮어쓰기
echo { > server\store_config.json
echo   "agentId": "A101", >> server\store_config.json
echo   "storeId": "C-A101-001", >> server\store_config.json
echo   "storeName": "스타링크 감성 카페", >> server\store_config.json
echo   "storeType": "CAFE", >> server\store_config.json
echo   "storeIp": "127.0.0.1", >> server\store_config.json
echo   "pgClientKey": "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene", >> server\store_config.json
echo   "paymentMode": "PRE_PAY", >> server\store_config.json
echo   "wifiPw": "", >> server\store_config.json
echo   "tunnelUrl": "" >> server\store_config.json
echo } >> server\store_config.json

echo 🛠️  시스템 업데이트 중...
call gradlew.bat :server:installDist -q

echo 🚀 서버 시동을 거는 중입니다...
set STORE_MODE=CAFE
start /b "" server\build\install\server\bin\server.bat > server_run.log 2>&1

:: 서버 가동 대기
:WAITLOOP
netstat -aon | findstr :8888 >nul 2>&1
if errorlevel 1 (
    timeout /t 1 /nobreak >nul
    goto WAITLOOP
)

:: 크롬 브라우저 오픈
start chrome --app="http://localhost:8888/kitchen"
start chrome --app="http://localhost:8888/qr_board"

echo 🎊 카페 모드 가동 완료!
