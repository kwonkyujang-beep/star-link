# 🖥️ Starlink 가맹점 미니 PC 설치 및 현장 세팅 매뉴얼 (Field Manual)

본 매뉴얼은 스타링크(Starlink) 스마트 오더 시스템을 가맹점 현장(미니 PC)에 설치, 세팅 및 운영하기 위한 **현장 실무 가이드**입니다.

---

## 1. 📋 사전 준비물 (Prerequisites)
1. **미니 PC (또는 포스기)**: Windows 또는 macOS 탑재, 전원 및 인터넷(LAN선/Wi-Fi) 연결 필수.
2. **Starlink 서버 패키지**: 프로젝트 소스 및 실행 스크립트(`.command` 또는 `.bat`).
3. **주방용 태블릿 / 보조 모니터**: 주방 KDS 접속용 (`http://[포스IP]:8888/kitchen`).
4. **손님용 주문 태블릿**: 업종별 APK가 설치된 태블릿.

---

## 2. ⚙️ 가맹점 맞춤형 세팅 3단계 (Step-by-Step Setup)

### [Step 1] 업종별 설정 파일 (`store_config.json`) 확인 및 수정
미니 PC 내 `server/store_config.json` 파일에서 해당 가맹점의 정보를 올바르게 설정합니다.

```json
{
  "agentId": "A101",
  "storeId": "C-A101-001",
  "storeName": "스타링크 감성 카페",
  "storeType": "CAFE",
  "storeIp": "127.0.0.1",
  "pgClientKey": "test_ck_D5Ge2x3M8pv991005b6VgL1N0Ene",
  "paymentMode": "PRE_PAY",
  "wifiPw": "",
  "tunnelUrl": "",
  "tableCount": 20
}
```
* **`storeType`**: `CAFE` (카페), `PUB` (주점), `RESTAURANT` (식당) 중 택일.
* **`tableCount`**: 매장 실제 테이블 개수 설정 (최대 20개, 주방 대시보드와 QR 보드에 즉시 반영).

---

### [Step 2] 미니 PC 서버 및 5G 보안 터널 런칭
가맹점 업종에 맞는 실행 스크립트를 더블클릭하여 서버를 가동합니다.

* **카페 매장**: `run_cafe.command` 실행
* **주점 매장**: `run_pub.command` 실행
* **식당 매장**: `run_restaurant.command` 실행
* **통합 가동 (설정 유지)**: `starlink_start.command` 실행

> [!NOTE]
> 스크립트 실행 시 자동으로 다음 작업이 수행됩니다:
> 1. 기존 백엔드 프로세스 및 터널링 종료 후 클린 재시작
> 2. Cloudflare `cloudflared`를 통한 5G 보안 터널(`trycloudflare.com`) 자동 개통
> 3. Ktor 백엔드 서버 엔진 (`:server:run`) 구동 (포트 8888)
> 4. 크롬 브라우저로 **주방 KDS (`/kitchen`)** 및 **지능형 QR 보드 (`/qr_board`)** 자동 팝업

---

### [Step 3] 파트너 CRM 앱(`rep_app`) 연동 및 자가진단
1. 영업/설치 파트너의 스마트폰에 **스타링크 파트너 CRM 앱 (`rep_app`)**을 실행하고 로그인합니다.
2. **`[🛠️ 2. 현장 설치 & 미니 PC 세팅 모드]`**로 진입합니다.
3. 미니 PC의 로컬 네트워크 IP가 자동으로 탐색되면, 화면 하단의 **`[🚀 1-Touch 시스템 자가진단 실행]`** 버튼을 누릅니다.
4. 핑 테스트 및 통신 검증이 **`ALL PASSED`**로 통과되면, **`[🚀 설치 완료 및 서비스 개시(LIVE) 승인]`**을 완료합니다.

---

## 3. 🔍 트러블슈팅 및 비상 대응 (Troubleshooting)

* **Q: 5G 외부 접속이 안 되거나 터널 주소가 열리지 않아요.**
  - **A**: 미니 PC의 인터넷 랜선 연결 상태를 확인하고, 서버 스크립트(`cloudflared.log`)가 정상 작동 중인지 확인하세요. 서버가 켜질 때 자체 자가치유(Self-Healing) 로직이 작동하여 터널 주소를 자동으로 복구합니다.
* **Q: 주방 대시보드의 테이블 개수가 안 맞아요.**
  - **A**: `http://localhost:8888/qr_board` (테이블 QR 보드) 상단의 테이블 개수 입력란에서 테이블 수를 조절하면 주방 대시보드와 실시간 동기화됩니다.

---
**작성일**: 2026년 10월
**상태**: 정식 배포 준비 완료 (Ready for Field Deployment)
