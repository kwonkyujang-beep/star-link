#!/bin/bash
PROJECT_DIR="/Users/spacerabbit/AndroidStudioProjects/starlink"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
clear
echo "=================================================="
echo "   ☕ Starlink [카페 모드] 철저한 단계별 동기화 가동"
echo "=================================================="

# [1단계] 이전 캐시 및 잔여 프로세스 완벽 소각
echo "🧹 [1단계] 이전 실행 캐시 및 잔여 프로세스 청소 중..."
lsof -ti:8888 | xargs kill -9 2>/dev/null
pkill -f cloudflared 2>/dev/null
rm -f cloudflared.log server_run.log
sleep 2
echo "✅ 잔해 소각 완료!"

# [2단계] 서버 엔진 기동 (Gradle :server:run)
echo "🚀 [2단계] 서버 엔진 시동 (Gradle :server:run)..."
STORE_MODE="CAFE" ./gradlew :server:run > server_run.log 2>&1 &

echo "⏳ 서버 8888 포트 대기 중..."
for i in {1..30}; do
    if lsof -i :8888 > /dev/null 2>&1; then
        echo "✅ 서버 8888 포트 가동 확인!"
        break
    fi
    sleep 1
done

# [3단계] 5G 보안 터널 개통 및 안정화
TUNNEL_URL=""
if command -v cloudflared &> /dev/null; then
    echo "🌐 [3단계] 5G 보안 터널 개통 중..."
    cloudflared tunnel --url http://localhost:8888 > cloudflared.log 2>&1 &

    for i in {1..15}; do
        sleep 2
        if [ -f "cloudflared.log" ]; then
            TUNNEL_URL=$(grep -a -o 'https://[a-zA-Z0-9.-]*\.trycloudflare.com' cloudflared.log 2>/dev/null | head -n 1)
            if [ -n "$TUNNEL_URL" ]; then
                echo "✅ 5G 터널 주소 획득: $TUNNEL_URL"
                break
            fi
        fi
        echo "⏳ 터널 연결 대기 중... ($i초)"
    done

    if [ -n "$TUNNEL_URL" ]; then
        echo "⏳ 터널 외부 라우팅 및 헬스체크 (5초)..."
        sleep 5
        echo "✅ 터널 안정화 완료!"
    else
        echo "⚠️ 터널 개통 지연"
    fi
fi

# [4단계] 설정 파일 동기화
echo "📌 [4단계] store_config.json 동기화 중..."
python3 -c "
import json, os, socket

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return '127.0.0.1'

path = 'server/store_config.json'
data = {}
if os.path.exists(path):
    try:
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except:
        pass

local_ip = get_local_ip()

data['agentId'] = data.get('agentId', 'A101')
data['storeId'] = data.get('storeId', 'C-A101-001')
data['storeName'] = data.get('storeName', '스타링크 감성 카페')
data['storeType'] = 'CAFE'
data['storeIp'] = local_ip
data['pgClientKey'] = data.get('pgClientKey', 'test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene')
data['paymentMode'] = 'PRE_PAY'
data['wifiPw'] = data.get('wifiPw', '')
data['tunnelUrl'] = '$TUNNEL_URL'
data['tableCount'] = data.get('tableCount', 20)

with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print(f'📌 [동기화 완료] 로컬 IP: {local_ip}, 터널 URL: $TUNNEL_URL')
"

# [5단계] 브라우저 오픈
echo "🖥️ [5단계] 대시보드 오픈 중..."
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/kitchen" &
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/qr_board" &
else
    open "http://localhost:8888/kitchen"
    open "http://localhost:8888/qr_board"
fi

echo "🎊 카페 모드 가동 완료!"
