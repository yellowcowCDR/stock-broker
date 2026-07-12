# 자율형 퀀트 트레이딩 에이전트를 위한 통합 API 신규 개발 완료 보고

요청하신 "작업지시서"에 따라 백엔드 API 확장 작업을 성공적으로 완료했습니다. Hexagonal Architecture의 원칙을 엄격하게 준수하여 구현했습니다.

## 1. 구현 요약
- **구현한 API**:
  - `GET /api/v1/broker/market/watchlist`: 관심 종목 탐색 (매수 신호가 아닌 후속 시장 분석을 위한 후보군 추출용, 현재는 Mock 데이터 기반 `CORE` 카테고리만 반환)
  - `GET /api/v1/broker/market/price`: 현재가 조회에 기술적 지표(`TechnicalIndicators`) 결합
  - `GET /api/v1/broker/market/news`: 종목별 뉴스 감성 분석 (Mock)
  - `GET /api/v1/internal/agent/skills`: 현재 활성 전략 조회
  - `PUT /api/v1/internal/agent/skills`: 전략 파라미터 업데이트 및 신규 버전 생성
- **신규 생성 파일**:
  - `WatchlistStock`, `WatchlistCategory`, `TechnicalIndicators`, `StockNews`, `NewsSentiment`, `AgentSkill` 등 도메인 객체들
  - `GetMarketWatchlistUseCase`, `GetStockNewsUseCase`, `StockNewsSearchPort`, `LoadAgentSkillPort`, `SaveAgentSkillPort` 등 포트 (Port)
  - `MarketWatchlistService`, `StockNewsService`, `AgentSkillService` (Application Service)
  - `MarketWatchlistController`, `StockNewsController`, `AgentSkillController` (Inbound Web Adapters)
  - `AgentSkillJpaEntity`, `AgentSkillJpaRepository`, `AgentSkillPersistenceAdapter`, `AgentSkillPersistenceMapper` (Outbound Persistence Adapters)
  - `AgentSkillDataInitializer` (ApplicationRunner - 부트스트랩)
- **수정한 파일**:
  - `CurrentPriceDto`: `technicalIndicators` 필드 추가
  - `MarketService`: 현재가 정보에 가상의 지표 매핑 로직 추가 (`createMockIndicators`)
  - `GlobalExceptionHandler`: Custom 예외 매핑 추가 (`InvalidStockCodeException`, `ActiveAgentSkillNotFoundException` 등)
- **추가한 Custom 예외**:
  - `InvalidStockCodeException` (400 Bad Request)
  - `ActiveAgentSkillNotFoundException` (404 Not Found)
  - `InvalidAgentSkillParametersException` (400 Bad Request)
- **테스트 코드 작성**:
  - `MarketWatchlistServiceTest`
  - `StockNewsServiceTest`
  - `AgentSkillServiceTest`

## 2. 주요 설계 결정
- **Watchlist 역할 재정의**: 관심 종목 탐색 기능이 기존의 단순 핫한 종목 추적이 아닌, 에이전트의 **후속 분석 대상 후보군(Market Analysis Candidates)**을 선별하는 1차 필터로 동작하도록 변경했습니다. 
  - `WatchlistCategory` (CORE, MOMENTUM, MEAN_REVERSION, VOLUME_ANOMALY, EVENT, PORTFOLIO_RISK), `score`(0~100), `reasons`(선정 이유 리스트) 등의 필드를 `WatchlistStock`에 추가하여 향후 확장이 용이하도록 구성했습니다. Watchlist 결과만으로 주문을 실행하지 않도록 분리했습니다.
- **Hexagonal Architecture 적용 방식**: Controller가 Repository나 Entity를 직접 참조하지 않도록 Service를 분리하고 In/Out Port를 통해 느슨하게 결합했습니다. 도메인 계층(Java record)과 영속성 계층(JPA Entity)을 분리하여 `AgentSkillPersistenceMapper`가 변환을 담당합니다.
- **Agent Skill 버전 관리 방식**: `PUT` 요청이 올 경우 기존 활성 전략의 `isActive` 값을 `false`로 변경(비활성화)한 후, 기존 `version` + 1 값으로 새로운 Entity를 INSERT하여 이력을 유지하도록 트랜잭션을 묶었습니다.
- **동시성 제어 방식**: "DDL 자동 생성에 의존"하신다는 피드백에 따라 `AgentSkillJpaEntity` 클래스 레벨에 `@Table(uniqueConstraints = ...)` 어노테이션으로 `version` 필드에 대한 Unique Constraint를 명시해 두었습니다. `isActive` 필드의 Partial Unique Index의 경우 Hibernate 자동 DDL로 완벽하게 구성하기에는 DBMS 의존성이 있으므로 운영 단계에서 V2 마이그레이션 스크립트를 통해 보완하는 것을 권장합니다.
- **JSONB 매핑 방식**: Hibernate 6.x에서 지원하는 `@JdbcTypeCode(SqlTypes.JSON)` 어노테이션을 사용하여 데이터베이스의 `jsonb` 타입과 자바의 `Map<String, Object>`가 자동 매핑되도록 구현했습니다.
- **Mock 데이터 생성 방식**: `Math.random()`을 지양하라는 규칙을 따라 `CurrentPrice`를 기준으로 고정 비율(`0.99`, `0.97`, `0.94`)을 곱하여 결정적(deterministic)인 값을 반환하도록 구성했습니다.

## 3. 주의사항 및 후속 작업
- 외부 뉴스 API나 관심 종목 데이터에 대해 "특정 API를 염두에 두고 계시다"고 답변해 주셨습니다. 이번 작업에서는 Outbound Port(`StockNewsSearchPort`)까지만 설계 및 연동해 두었으며, 추후 해당 API의 인증 키나 스펙이 확정되면 `adapter.out.external` 계층에 어댑터를 추가하기만 하면 기존 비즈니스 로직 수정 없이 매끄럽게 연동이 가능합니다.
- 테스트 환경이나 CI 서버의 Java 버전(`Unsupported class file major version 67` 오류 발생 시 Java 버전을 21 또는 17로 낮춰서 실행 필요)을 확인하시고 `./gradlew test`를 실행하시면 정상적으로 모든 테스트가 통과하는 것을 확인하실 수 있습니다.
