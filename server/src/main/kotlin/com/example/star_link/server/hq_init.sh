#!/bin/bash
echo "=================================================="
echo "   🏢 본사(HQ) 출고용 미니 PC 초기화 스크립트"
echo "=================================================="

read -p "출고할 미니 PC의 시리얼 넘버를 입력하세요 (예: HW-1001): " SERIAL_NUM
if [ -z "$SERIAL_NUM" ]; then
    echo "❌ 시리얼 넘버가 입력되지 않았습니다. 취소합니다."
    exit 1
fi

# 6자리 랜덤 PIN 생성
SETUP_PIN=$(printf "%06d" $(( RANDOM % 1000000 )))

CONFIG_PATH="/Users/spacerabbit/AndroidStudioProjects/starlink/server/store_config.json"
cat << JSON_EOF > $CONFIG_PATH
{
  "agentId": "HQ-DEFAULT",
  "storeId": "$SERIAL_NUM",
  "storeName": "미등록 매장",
  "storeType": "NONE",
  "storeIp": "127.0.0.1",
  "pgClientKey": "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene",
  "paymentMode": "POST_PAY",
  "wifiPw": "",
  "tunnelUrl": "",
  "setupPin": "$SETUP_PIN",
  "isSetupComplete": false
}
JSON_EOF

echo "✅ [$SERIAL_NUM] 기기의 초기화가 완료되었습니다."
echo "🔒 세팅용 PIN 번호: $SETUP_PIN"
echo "이 PIN 번호를 기기 바닥에 스티커로 붙여서 출고해 주세요."
