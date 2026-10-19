#!/bin/bash
# =================================================-----------------
# 🚀 큐알로 Pro (v2.0) - 시험 매장 3초 원터치 자동 개설 키트
# =================================================-----------------
cd "$(dirname "$0")"

echo "=================================================="
echo "🚀 [큐알로 Pro] 시험 매장 3초 자동 개설 키트"
echo "=================================================="

# 1. 영업 대리점, 점포 ID, 매장명 및 업종(스토어 타입) 입력 받기
read -p "📌 영업 대리점 코드 (Agent ID) [기본값: A101]: " agent_id
agent_id=${agent_id:-A101}

read -p "📌 점포 분류 ID (Store ID)    [기본값: S-A101-001]: " store_id
store_id=${store_id:-S-A101-001}

read -p "📌 시험 매장명 (Store Name)    [기본값: 현장 테스트 매장]: " store_name
store_name=${store_name:-현장 테스트 매장}

echo ""
echo "📌 매장 업종 선택 (Store Type):"
echo "   1) 식당/음식점 (FOOD - 기본)"
echo "   2) 주점/포차/바 (PUB)"
echo "   3) 카페/디저트 (CAFE)"
echo "   4) 뷔페/무한리필 (BUFFET)"
echo "   5) 뷰티/미용 (BEAUTY)"
read -p "👉 업종 번호 선택 [1-5, 기본값: 1]: " type_choice

case $type_choice in
    2) store_type="PUB" ;;
    3) store_type="CAFE" ;;
    4) store_type="BUFFET" ;;
    5) store_type="BEAUTY" ;;
    *) store_type="FOOD" ;;
esac

echo ""
echo "⚙️ [설정 생성 중] Agent: $agent_id | Store: $store_id | Name: $store_name | Type: $store_type"

# 2. store_config.json 파일 생성
cat <<EOF > server/store_config.json
{
  "agentId": "$agent_id",
  "storeId": "$store_id",
  "storeName": "$store_name",
  "storeType": "$store_type",
  "pgClientKey": "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene",
  "paymentMode": "POST_PAY"
}
EOF

echo "✅ store_config.json 생성 완료!"

# 3. 맥북 바탕화면에 바로가기 생성 및 권한 부여
chmod +x starlink_start.command
cp starlink_start.command "$HOME/Desktop/큐알로 매장 오더.command"
chmod +x "$HOME/Desktop/큐알로 매장 오더.command"

echo "=================================================="
echo "🎉 시험 매장 개설 세팅 완료! (바탕화면에 바로가기 생성됨)"
echo "🚀 3초 후 서버 및 KDS/QR 보드를 가동시킵니다..."
echo "=================================================="
sleep 2

# 4. 서버 및 대시보드 구동
lsof -ti:8888 | xargs kill -9 2>/dev/null
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/qr_board" &
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/kitchen" &
else
    open http://localhost:8888/qr_board
    open http://localhost:8888/kitchen
fi
./gradlew :server:run
