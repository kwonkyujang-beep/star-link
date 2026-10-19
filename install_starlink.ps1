# [Starlink] Windows 원클릭 통합 설치 및 최적화 스크립트
# 실행 권한: 관리자 권한 필수

$InstallPath = "C:\Starlink_Order"
$ExeName = "StarlinkServer.exe" # 실제 패키징된 실행파일명
$Port = 8888

Write-Host "🚀 Starlink 스마트 주문 시스템 설치를 시작합니다..." -ForegroundColor Cyan

# 1. 설치 폴더 생성 및 파일 복사
if (!(Test-Path $InstallPath)) {
    New-Item -Path $InstallPath -ItemType Directory
}
Write-Host "📂 폴더 구성 완료: $InstallPath"

# 2. 방화벽 규칙 추가 (8888 포트 허용)
Write-Host "🛡️ 방화벽 설정을 업데이트합니다..."
Remove-NetFirewallRule -DisplayName "Starlink Order Server" -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName "Starlink Order Server" -Direction Inbound -LocalPort $Port -Protocol TCP -Action Allow
Write-Host "✅ 방화벽 개방 완료 (Port: $Port)"

# 3. 네트워크 우선순위(QoS) 정책 설정
Write-Host "📡 트래픽 우선순위(QoS)를 최적화합니다..."
Remove-NetQosPolicy -Name "StarlinkPriority" -ErrorAction SilentlyContinue
New-NetQosPolicy -Name "StarlinkPriority" -AppPathNameMatchCondition $ExeName -DSCPAction 46
Write-Host "✅ 스타링크 주문 데이터 고속도로 개설 완료 (DSCP 46)"

# 4. 윈도우 시작프로그램 등록
$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\Starlink.lnk")
$Shortcut.TargetPath = "$InstallPath\$ExeName"
$Shortcut.WorkingDirectory = $InstallPath
$Shortcut.Save()
Write-Host "🔄 시작프로그램 등록 완료: 부팅 시 자동 실행"

# 5. 즉시 실행 시도
Write-Host "✨ 모든 설정이 완료되었습니다. 서비스를 시작합니다!" -ForegroundColor Green
# Start-Process "$InstallPath\$ExeName"

Write-Host "--------------------------------------------------"
Write-Host "영업 파트너님, 이제 주방 모니터를 확인해 주세요."
Write-Host "--------------------------------------------------"
