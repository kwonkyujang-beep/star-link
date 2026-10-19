#!/bin/bash
PROJECT_DIR="/Users/spacerabbit/AndroidStudioProjects/starlink"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
clear
echo "=================================================="
echo "   🍽️ Starlink [식당 모드] 서버 엔진 가동"
echo "=================================================="

lsof -ti:8888 | xargs kill -9 2>/dev/null
pkill -f cloudflared 2>/dev/null

# 1. 서버 먼저 구동 (Gradle :server:run)
echo "🚀 서버 시동을 거는 중입니다 (Gradle :server:run)..."
STORE_MODE="RESTAURANT" ./gradlew :server:run > server_run.log 2>&1 &

echo "⏳ 서버 엔진 8888 포트 대기 중..."
while ! lsof -i :8888 > /dev/null 2>&1; do sleep 1; done
echo "✅ 서버 8888 포트 가동 완료!"

# 2. 5G 터널(Cloudflare) 개통 (서버가 뜬 후 실행)
TUNNEL_URL=""
if command -v cloudflared &> /dev/null; then
    echo "🌐 5G 보안 터널(Cloudflare) 개통 중..."
    cloudflared tunnel --url http://localhost:8888 > cloudflared.log 2>&1 &

    for i in {1..10}; do
        sleep 1
        TUNNEL_URL=$(grep -a -o 'https://[a-zA-Z0-9.-]*\.trycloudflare.com' cloudflared.log 2>/dev/null | head -n 1)
        if [ -n "$TUNNEL_URL" ]; then
            echo "✅ 5G 터널 개통 성공: $TUNNEL_URL"
            break
        fi
    done
fi

# 3. 설정 파일 업데이트 (실제 로컬 IP 및 터널 URL 반영)
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
data['storeId'] = data.get('storeId', 'R-A101-001')
data['storeName'] = data.get('storeName', '스타링크 명가 한식당')
data['storeType'] = 'RESTAURANT'
data['storeIp'] = local_ip
data['pgClientKey'] = data.get('pgClientKey', 'test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene')
data['paymentMode'] = 'POST_PAY'
data['wifiPw'] = data.get('wifiPw', '')
data['tunnelUrl'] = '$TUNNEL_URL'
data['tableCount'] = data.get('tableCount', 20)

with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print(f'📌 [설정 완료] 로컬 IP: {local_ip}, 터널 URL: $TUNNEL_URL')
"

if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/kitchen" &
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/qr_board" &
else
    open "http://localhost:8888/kitchen"
    open "http://localhost:8888/qr_board"
fi
echo "🎊 식당 모드 가동 완료!"
