@echo off
chcp 65001 > nul
echo ==================================================
echo    🏢 본사(HQ) 출고용 미니 PC 초기화 스크립트 (Windows용)
echo ==================================================

set /p SERIAL_NUM="출고할 미니 PC의 시리얼 넘버를 입력하세요 (예: HW-1001): "
if "%SERIAL_NUM%"=="" (
    echo ❌ 시리얼 넘버가 입력되지 않았습니다. 취소합니다.
    pause
    exit /b 1
)

:: 6자리 랜덤 PIN 생성 (Windows 방식)
set /a SETUP_PIN=%RANDOM% * 1000000 / 32768
:: 6자리를 맞추기 위해 0 패딩 (간이 방식)
set SETUP_PIN=000000%SETUP_PIN%
set SETUP_PIN=%SETUP_PIN:~-6%

set CONFIG_PATH="%~dp0..\..\..\..\..\..\store_config.json"

echo { > %CONFIG_PATH%
echo   "agentId": "HQ-DEFAULT", >> %CONFIG_PATH%
echo   "storeId": "%SERIAL_NUM%", >> %CONFIG_PATH%
echo   "storeName": "미등록 매장", >> %CONFIG_PATH%
echo   "storeType": "NONE", >> %CONFIG_PATH%
echo   "storeIp": "127.0.0.1", >> %CONFIG_PATH%
echo   "pgClientKey": "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene", >> %CONFIG_PATH%
echo   "paymentMode": "POST_PAY", >> %CONFIG_PATH%
echo   "wifiPw": "", >> %CONFIG_PATH%
echo   "tunnelUrl": "", >> %CONFIG_PATH%
echo   "setupPin": "%SETUP_PIN%", >> %CONFIG_PATH%
echo   "isSetupComplete": false >> %CONFIG_PATH%
echo } >> %CONFIG_PATH%

echo ✅ [%SERIAL_NUM%] 기기의 초기화가 완료되었습니다.
echo 🔒 세팅용 PIN 번호: %SETUP_PIN%
echo 이 PIN 번호를 기기 바닥에 스티커로 붙여서 출고해 주세요.
pause
