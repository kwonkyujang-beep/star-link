#!/bin/bash
PROJECT_DIR="/Users/spacerabbit/AndroidStudioProjects/starlink"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
clear
echo "=================================================="
echo "   🍻 Starlink [주점 모드] 철저한 단계별 동기화 가동"
echo "=================================================="

# [1단계] 이전 캐시 및 잔여 프로세스(서버, 터널, 로그) 완벽 소각 (함정 방지)
echo "🧹 [1단계] 이전 실행 캐시 및 잔여 프로세스 완벽 청소 중..."
lsof -ti:8888 | xargs kill -9 2>/dev/null
pkill -f cloudflared 2>/dev/null
rm -f cloudflared.log server_run.log
sleep 2
echo "✅ 이전 세션 잔해 완벽 소각 완료!"

# [2단계] 서버 엔진 기동 (Gradle :server:run) 및 8888 포트 완벽 대기
echo "🚀 [2단계] 서버 엔진 시동 거는 중 (Gradle :server:run)..."
STORE_MODE="PUB" ./gradlew :server:run > server_run.log 2>&1 &

echo "⏳ 서버 엔진 8888 포트 활성화 대기 중..."
for i in {1..30}; do
    if lsof -i :8888 > /dev/null 2>&1; then
        echo "✅ 서버 8888 포트 가동 확인 완료!"
        break
    fi
    sleep 1
done

# [3단계] 5G 보안 터널 개통 및 유효 주소 수신 확정 (타임아웃 & 딜레이 보장)
TUNNEL_URL=""
if command -v cloudflared &> /dev/null; then
    echo "🌐 [3단계] 5G 보안 터널(Cloudflare) 개통 및 안정화 대기 중..."
    cloudflared tunnel --url http://localhost:8888 > cloudflared.log 2>&1 &

    # 터널이 완전히 뚫리고 유효한 https 주소가 찍힐 때까지 확실하게 루프 대기
    for i in {1..15}; do
        sleep 2
        if [ -f "cloudflared.log" ]; then
            TUNNEL_URL=$(grep -a -o 'https://[a-zA-Z0-9.-]*\.trycloudflare.com' cloudflared.log 2>/dev/null | head -n 1)
            if [ -n "$TUNNEL_URL" ]; then
                echo "✅ 5G 터널 주소 획득 성공: $TUNNEL_URL"
                break
            fi
        fi
        echo "⏳ 터널 연결 대기 중... ($i초)"
    done

    # [핵심 방어막] 주소가 잡힌 후에도 터널이 외부 요청을 완벽히 받을 수 있도록 4초 추가 안정화 타임 보장
    if [ -n "$TUNNEL_URL" ]; then
        echo "⏳ 5G 터널 외부 라우팅 및 헬스체크 안정화 중 (4초)..."
        sleep 4
        echo "✅ 5G 터널 완전 가동 준비 완료!"
    else
        echo "⚠️ [경고] 터널 개통 시간 초과. 로컬 와이파이 전용 모드로 전환됩니다."
    fi
fi

# [4단계] 최신 로컬 IP 및 확정된 터널 URL을 store_config.json에 완벽 동기화 반영
echo "📌 [4단계] 매장 설정 파일(store_config.json) 자동 동기화 중..."
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
data['storeId'] = data.get('storeId', 'P-A101-001')
data['storeName'] = data.get('storeName', '스타링크 수제맥주 & 펍')
data['storeType'] = 'PUB'
data['storeIp'] = local_ip
data['pgClientKey'] = data.get('pgClientKey', 'test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene')
data['paymentMode'] = 'POST_PAY'
data['wifiPw'] = data.get('wifiPw', '')
data['tunnelUrl'] = '$TUNNEL_URL'
data['tableCount'] = data.get('tableCount', 20)

with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print(f'📌 [동기화 완료] 로컬 IP: {local_ip}, 터널 URL: $TUNNEL_URL')
"

# [5단계] 주방 대시보드 및 QR 보드 브라우저 런칭
echo "🖥️ [5단계] 주방 대시보드 및 QR 보드 실행 중..."
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/kitchen" &
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/qr_board" &
else
    open "http://localhost:8888/kitchen"
    open "http://localhost:8888/qr_board"
fi

echo "=================================================="
echo "   🎊 주점 모드 완벽 동기화 및 1033 방어 가동 완료!"
echo "=================================================="
