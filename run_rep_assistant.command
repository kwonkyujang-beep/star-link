#!/bin/bash
PROJECT_DIR="/Users/spacerabbit/AndroidStudioProjects/starlink"
cd "$PROJECT_DIR"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
clear
echo "=================================================="
echo "   🤝 Starlink [영업맨 현장 미팅 챗봇] 가동"
echo "=================================================="

# 브라우저로 영업맨 미팅 수집기 오픈
if [ -f "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]; then
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --app="file://$PROJECT_DIR/rep_meeting_assistant.html" &
else
    open "rep_meeting_assistant.html"
fi
echo "🎊 영업맨 현장 미팅 챗봇 가동 완료!"
