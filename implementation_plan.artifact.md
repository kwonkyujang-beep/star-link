# 스타링크 업종별 맞춤형 고도화 및 확장 계획 (카페, 주점, 식당)

기존 스타링크(Starlink) POS/주문 시스템의 탄탄한 공통 워크프레임(Ktor 서버, DB, 주문 관리)을 바탕으로, 공통 코어와 업종별 특화 기능을 분리하여 **카페**, **주점**, **식당** 3가지 전문 프로그램으로 확장 및 고도화하는 계획입니다.

## User Review Required

> [!IMPORTANT]
> **아키텍처 전략 선택**:
> 1. **멀티 모듈 구조**: 공통 로직을 `:core` 모듈로 두고, 업종별 UI 및 비즈니스 로직을 `:feature:cafe`, `:feature:pub`, `:feature:restaurant` 모듈로 분리하여 관리합니다.
> 2. **단계적 구현**: 공통 프레임워크를 먼저 정립(Refactoring)한 뒤, 카페 ➔ 식당 ➔ 주점 순서로 업종별 특화 기능을 순차적으로 고도화합니다.

## Open Questions

- 각 업종별 프로그램이 하나의 통합 앱 안에서 모드(업종) 선택형으로 구동되길 원하시나요, 아니면 각각 완전히 독립된 빌드/앱으로 분리되길 원하시나요? (기본적으로 멀티 모듈 기반으로 하되, 빌드 Variant 또는 독립 모듈로 구성 가능합니다.)

## Proposed Changes

### 1. 공통 프레임워크 정립 (Core Framework)
기존 `app` 및 `server`의 공통 주문, 결제, 데이터베이스, 네트워크 통신 로직을 모듈화 및 재구조화합니다.

#### [MODIFY] [ServerMain.kt](file:///Users/spacerabbit/AndroidStudioProjects/starlink/server/src/main/kotlin/com/example/star_link/server/ServerMain.kt)
- 업종별 맞춤형 API 및 데이터 스키마(옵션, 테이블 레이아웃, 주류/재고 등)를 수용할 수 있도록 서버 구조 확장성 확보

#### [MODIFY] [OrderModels.kt](file:///Users/spacerabbit/AndroidStudioProjects/starlink/app/src/main/java/com/example/star_link/data/OrderModels.kt)
- 카페 옵션(샷, 시럽, 얼음), 주점 테이블 탭/더치페이, 식당 테이블 상태/코스 순서 등을 담을 수 있도록 공통 데이터 모델 확장

---

### 2. 업종별 특화 모듈 및 고도화 [NEW / FEATURE]

#### ☕ [카페 (Café) 프로그램 고도화]
- **음료 옵션 선택 화면**: 샷 추가, 시럽, 얼음량, 테이크아웃 여부 선택 UI
- **진동벨 및 쿠폰 시스템**: 진동벨 번호 지정 및 스탬프/쿠폰 적립 로직 구현

#### 🍽️ [식당 (Restaurant) 프로그램 고도화]
- **테이블 배치도 관리**: 홀, 룸, 테라스 등 구역별 테이블 시각화 및 착석/인원 관리
- **주방 제어 및 코스 순서**: 주방 디스플레이(KDS) 연동 및 코스 요리/후식 타이밍 제어

#### 🍻 [주점 (Pub) 프로그램 고도화]
- **주류 및 재고 관리**: 병맥주, 생맥주, 안주류 카테고리 분리 및 재고 추적
- **정산 및 더치페이**: N분의 1 계산, 메뉴별 개별 정산 및 합석/테이블 이동 기능

---

## Verification Plan

### Automated Tests
- Gradle 빌드 검증 (`gradle_build`)
- 서버 및 앱 유닛 테스트 실행

### Manual Verification
- 각 업종별 모드/화면 전환 및 시뮬레이터 동작 확인
- 주문 접수, 옵션 선택, 정산 흐름 검증
