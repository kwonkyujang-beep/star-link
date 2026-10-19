#!/bin/bash
PROJECT_DIR="/Users/spacerabbit/AndroidStudioProjects/starlink"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
clear
echo "=================================================="
echo "   🏢 Starlink [본사 메뉴 컴포저] 가동"
echo "=================================================="

# 서버가 켜져있는지 확인 (8888 포트)
if ! lsof -i :8888 > /dev/null 2>&1; then
    echo "⚠️ 서버가 켜져있지 않습니다!"
    echo "먼저 미니 PC 서버를 가동해 주세요. (예: run_cafe.command 실행)"
    exit 1
fi

echo "✅ 메뉴 컴포저 화면을 엽니다..."

# 크롬 브라우저로 본사 컴포저 오픈
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="http://localhost:8888/hq_menu_composer" &
else
    open "http://localhost:8888/hq_menu_composer"
fi
