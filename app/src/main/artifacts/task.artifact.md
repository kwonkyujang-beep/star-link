# 실시간 QR 매장 주문 시스템 개발 체크리스트

- [x] **1단계: 도메인 데이터 모델 및 가상 서버 인프라 구축**
  - [x] [OrderModels.kt](file:///Users/spacerabbit/AndroidStudioProjects/starlink/app/src/main/java/com/example/star_link/data/OrderModels.kt) 데이터 객체 작성
  - [x] [OrderManager.kt](file:///Users/spacerabbit/AndroidStudioProjects/starlink/app/src/main/java/com/example/star_link/data/OrderManager.kt) 리얼타임 `StateFlow` 중앙 매니저 구현
- [x] **2단계: UI 컴포넌트 통합 구현 (이미지 기반 태블릿 고급 메뉴판)**
  - [x] Coil 라이브러리 추가 및 비동기 고해상도 요리 사진 그리드화 성공
  - [x] 에뮬레이터 UI에서 주방 포스 전환 기능을 전면 걷어내어 손님 전용 화면으로 정형화
- [/] **3단계: 맥북 백엔드 외부 독립 서버 실시간 물리 연동망 완성**
  - [ ] 에뮬레이터 내 로컬 Ktor 엔진 중복 기동 소스 영구 완전 매장 제거
  - [ ] 손님이 주문 전송 시 맥북 호스트 PC 서버(`10.0.2.2:8080`)의 웹소켓 파이프라인으로 다이렉트 푸시 전송 로직 탑재
