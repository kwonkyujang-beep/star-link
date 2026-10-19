#!/bin/bash
# =================================================================-
# 🏢 Starlink 큐알로 HQ - 가맹점 맞춤형 패키지 독립 빌더 앱
# =================================================================-
cd "$(dirname "$0")"

echo "=================================================="
echo "🏢 큐알로 HQ 가맹점 맞춤형 패키지 빌더 가동 중..."
echo "=================================================="

# 1. 이전 8888 포트 정리
lsof -ti:8888 | xargs kill -9 2>/dev/null

# 2. Ktor 서버 엔진 백그라운드 구동
./gradlew :server:run &

# 3. 서버가 8888 포트에서 완전 가동될 때까지 스마트 대기 (1~2초)
echo "⏳ 서버 엔진 가동 대기 중..."
while ! lsof -i :8888 > /dev/null 2>&1; do
    sleep 0.5
done

echo "✅ 서버 가동 완료! 본사 스튜디오 앱을 켭니다."

# 4. 서버 구동 완료 후 주소창 없는 전용 앱 창(App Window) 오픈
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://127.0.0.1:8888/hq_menu_composer"
else
    open "http://127.0.0.1:8888/hq_menu_composer"
fi
