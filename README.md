# Hermes Trading Broker Server

자율 주식 매매 에이전트(Hermes)의 손과 발이 되어주는 **백엔드 중계(Broker) 서버**입니다. 
외부의 AI 에이전트(두뇌)가 주식 시장의 데이터를 조회하고 매매 명령을 내릴 수 있도록 돕는 REST API 역할을 수행하며, 한국투자증권(KIS) OpenAPI와의 통신, 트랜잭션 관리, 매매 로그 기록 및 장 마감 데이터 정산(회고) 로직을 전담합니다.

## 🏛 Architecture & Paradigm
본 프로젝트는 복잡한 오버엔지니어링을 지양하고 직관적이며 견고한 설계를 바탕으로 한 **실용주의적 도메인 주도 설계(DDD)** 와 **헥사고날 아키텍처(Hexagonal Architecture)** 를 채택하고 있습니다.

- **순수 비즈니스 로직 격리:** 도메인 로직과 어플리케이션(UseCase) 계층이 프레임워크나 외부 DB 기술에 종속되지 않습니다.
- **Port and Adapters:** Web(Controller)과 Scheduler 등 외부 입력을 `Inbound Adapter`로, JPA(DB)와 외부 KIS API 호출을 `Outbound Adapter`로 엄격하게 분리했습니다.
- **단일 책임:** LLM 통신이나 AI 의사결정 로직은 포함하지 않고, 오직 '중계'와 '기록'에 집중합니다.

## 🛠 Tech Stack
- **Framework:** Spring Boot 3.x
- **Language:** Java 17 (JDK 17)
- **Database:** PostgreSQL (Neon DB Serverless)
- **ORM:** Spring Data JPA (Hibernate 6)
- **HTTP Client:** Spring RestClient (Spring Boot 3.2+ 표준)
- **Infra/CI/CD:** Docker, Docker Compose, GitHub Actions, Google Cloud Platform (VM / Cloud Run)

## 📌 Key Features
1. **증권사 OpenAPI 통신 연동:** KIS(한국투자증권) API 토큰 자동 발급 및 갱신 (24시간 주기)
2. **에이전트 전용 REST API:** 시세 조회, 매매 주문, 매매 로그 조회 등
3. **영속성 및 트랜잭션 관리:** PostgreSQL을 통한 `TradingLog`(주문 내역), `DailySummary`(일간 회고) 및 에이전트 파라미터(`AgentSkill`) 데이터 적재
4. **주문 안전 경계:** 환경·자율성·Kill Switch·시장 시간·최신가·Risk·중복 주문을 하나의 주문 파이프라인에서 검증
5. **중복/동시 실행 방지:** idempotency key와 PostgreSQL 계좌 advisory lock으로 재시도 및 동시 주문 직렬화
6. **시장 운영 시간 검증:** Asia/Seoul 및 America/New_York 기준 주문 가능 세션 검증
7. **장 마감 일괄 정산 배치:** 매일 오후 4시, 당일 거래 내역 및 수익률을 정산하여 데일리 리포트 데이터 생성

전체 엔드포인트, 요청·응답 스키마, 주문 idempotency 및 Hermes 재시도 규칙은
[Broker API 명세](docs/api-spec.md)를 참고합니다.

서버·DB·API의 절대 시각은 UTC로 저장·응답합니다. 시장의 거래일과 세션 판정만
`Asia/Seoul` 또는 `America/New_York`으로 변환하며, 미국 DST는 IANA time-zone 규칙으로 자동 반영됩니다.
미국 휴장일과 조기 폐장일은 NYSE가 게시한 2026–2028 캘린더 범위만 지원하고, 범위 밖 연도는
`CALENDAR_UNAVAILABLE`로 신규 주문을 fail-closed 차단합니다.

## 📂 Project Structure (DDD + Hexagonal)
```text
com.hermes.broker/
├── agent/      # 에이전트 스킬 파라미터 및 주요 진입점
├── market/     # 증권사 시세 연동 및 시장 시간/공휴일 검증 도메인
├── trading/    # 주문 실행 및 매매 일지 기록 도메인
└── summary/    # 장 마감 후 수익률 및 거래 회고록 정산 도메인
```

## 🚀 Getting Started (Local Development)

### 1. 환경 변수 세팅
루트 디렉토리의 `.env.template` 파일을 복사하여 `.env` 파일을 생성하고 환경에 맞는 값을 입력합니다.
```bash
cp .env.template .env
```
*(참고: IDE에서 실행 시 `application-mock.yml` 혹은 `.env`의 환경변수들을 Run Configuration에 등록해주어야 정상 구동됩니다.)*

기본값은 `AUTONOMY_MODE=ANALYSIS_ONLY`, Kill Switch 활성 상태입니다. Paper 주문을 명시적으로 시험할 때만 모든 Risk 데이터가 준비되었는지 확인한 뒤 `PAPER_AUTO`로 변경해야 합니다.

`application-mock.yml`의 `mock`은 합성 데이터가 아니라 KIS 공식 모의투자 주문 환경을 뜻합니다.
실전 계좌로 주문하려면 `prod` 프로필과 실전 KIS 자격 증명을 사용해야 합니다. 어느 프로필에서도
고정 종목·임의 시세·가짜 뉴스·합성 기술지표를 실데이터 대신 반환하지 않습니다.

### 시장 후보와 뉴스 실데이터

- `GET /api/v1/broker/market/watchlist`는 KIS 국내 거래대금 순위와 NASDAQ·NYSE·AMEX 거래대금 순위로 후보군을 만듭니다.
- Watchlist 응답의 `candidateOnly=true`는 후보군이 매수 신호가 아님을 뜻합니다.
- `GET /api/v1/broker/market/news?stockCode=...`는 Naver 뉴스 검색 API의 기사만 사용하며 `dataSource`, `fetchedAt`, `complete`, `freshness`를 반환합니다.
- 기사 관련성·품질·감성은 `RULE_BASED_LEXICAL_V1`으로 표시되는 설명 가능한 규칙 점수이며, 외부 ML 모델의 결과인 것처럼 취급하지 않습니다.
- KIS 또는 Naver API가 설정되지 않았거나 불완전한 응답을 주면 요청은 실패합니다. Mock fallback은 없습니다.

Watchlist는 실시간 KIS 호출이므로 실전 분석 환경에서는 `SPRING_PROFILES_ACTIVE=prod`와
`KIS_PROD_*` 값을 사용하고, 뉴스에는 `NAVER_CLIENT_ID`와 `NAVER_CLIENT_SECRET`을 반드시 설정합니다.
템플릿 KIS 키나 `12345678-01` 계좌번호가 남아 있으면 서버 시작 자체가 실패합니다.

아직 실데이터 집계가 완성되지 않은 기능은 값을 꾸며 반환하지 않습니다.

- 미국 계좌의 USD 예수금·사용 가능액·종목별 매도 가능 수량과 거래소·종목·가격별 주문 가능 금액은 KIS 실데이터로 조회합니다.
- 해외 주문은 이 USD 데이터를 원화 총자산 Risk와 결합할 환율·수수료 모델 및 해외 시장 컨텍스트가 완성될 때까지 기본 차단됩니다.
- Reflection은 같은 시장 거래일의 Feature·Decision·시장 컨텍스트·주문 결과·KIS 비용 정산과 마감자산 요약이 모두 연결된 경우에만 생성합니다. 하나라도 빠지면 `503 Service Unavailable`로 실패합니다.
- 전략 성과는 `dataComplete=true`인 Broker Reflection만 집계합니다. 주문별 실현손익이나 반사실 가격이 없는 `winRate`, `holdAccuracy`, `riskBlockEffect`는 다른 지표로 대체하지 않고 `null`로 둡니다.
- 전략 새 버전은 즉시 활성화되지 않습니다. Candidate/Shadow 상태와 Broker DB 성과 검증, 명시적 승격 승인을 거칩니다.
- 일일 수익률 요약은 이전 실계좌 마감자산 기준값이 DB에 없으면 0%로 저장하지 않고 실패합니다.

### 주문 API

모든 주문은 클라이언트 재시도에도 동일하게 유지되는 `idempotencyKey`가 필요합니다.

```json
{
  "marketType": "DOMESTIC",
  "stockCode": "005930",
  "orderType": "BUY",
  "price": 70000,
  "quantity": 1,
  "idempotencyKey": "20260718-DOMESTIC-005930-BUY-CYCLE-1",
  "decisionId": "broker-decision-uuid",
  "featureId": "broker-feature-uuid",
  "strategyVersion": "strategy-v3"
}
```

자율 주문은 `decisionId`, `featureId`, `strategyVersion`을 함께 보내야 해당 거래일 Reflection에
포함될 수 있습니다. 이 감사 필드도 idempotency payload hash에 들어가므로 같은 키로 다른 판단을
재사용할 수 없습니다.

환경과 Risk Policy는 다음 API에서 조회할 수 있습니다.

- `GET /api/v1/broker/trading/environment`
- `GET /api/v1/broker/trading/risk-policy`

시장 전체 분석은 다음 순서로 사용합니다.

1. `GET /api/v1/broker/market/overview?marketType=DOMESTIC`에서 KOSPI·KOSDAQ breadth와 투자자 수급을 조회합니다.
2. Hermes가 분석한 `entryPolicy`, `riskMultiplier`, 근거를 `POST /api/v1/internal/trading/market-contexts`에 저장합니다.
3. 저장 시 Broker가 KIS overview를 다시 조회하여 같은 레코드에 스냅샷으로 보존합니다.
4. 주문 시 Broker DB의 최신 컨텍스트를 다시 읽고 시장 일치, 유효기간, 진입 정책, 배수를 검사합니다.

```json
{
  "marketType": "DOMESTIC",
  "entryPolicy": "REDUCE_NEW_ENTRIES",
  "riskMultiplier": 0.5,
  "validUntil": "2026-07-20T01:05:00Z",
  "rationale": ["KOSPI·KOSDAQ 합산 breadth가 중립 이하"],
  "analyzedBy": "hermes-market-cron",
  "correlationId": "market-analysis-20260720-0100"
}
```

`validUntil`을 생략하면 Broker가 overview의 만료 시각을 사용합니다. 요청한 유효기간은 KIS overview
신선도와 `MARKET_CONTEXT_MAX_VALIDITY`를 넘을 수 없습니다. `riskMultiplier`는 0~1만 허용하며,
신규 주문의 최대 주문 금액을 줄일 수만 있습니다. 컨텍스트가 없거나 stale이거나
`BLOCK_NEW_ENTRIES`이면 신규 매수는 차단하지만 기존 포지션 매도는 허용합니다.

미국 계좌 데이터는 다음 API로 확인합니다.

- `GET /api/v1/broker/account/overseas/us`: USD 예수금, 외화 사용 가능액, 미국 보유 종목과 실제 매도 가능 수량
- `GET /api/v1/broker/account/overseas/order-capacity?stockCode=AAPL&exchangeCode=NASD&orderPrice=195.50`: 거래소·종목·주문가 기준 USD 주문 가능 금액과 최대 수량

응답은 KIS `체결기준현재잔고(CTRP6504R/VTRP6504R)` 및
`매수가능금액조회(TTTS3007R/VTTS3007R)` 필드를 사용합니다. 연속 조회가 필요한 응답이나 USD 필수
필드가 누락된 응답은 일부 값만 반환하지 않고 실패합니다.

`OVERSEAS_ORDER_ENABLED` 기본값은 `false`입니다. KIS 모의투자 프로필에서 이를 `true`로 바꾸면 미국
Paper 지정가 주문·미체결 조회·취소·체결 대사를 사용할 수 있습니다. 주문에는 `exchangeCode`(`NASD`,
`NYSE`, `AMEX`)가 필수이며, USD 주문 가능 금액·매도 가능 수량·종목/업종 비중을 실제 공급자 데이터로
검사합니다. 미국 신규 매수는 `ALPHA_VANTAGE_ENABLED=true`와 유효한 OVERSEAS Market Context도
필요합니다. 실전 해외 주문은 플래그와 무관하게 Broker가 계속 차단합니다.

미국 Paper 주문 활성화의 최소 조건은 `SPRING_PROFILES_ACTIVE=mock`, `AUTONOMY_MODE=PAPER_AUTO`,
`OVERSEAS_ORDER_ENABLED=true`, `ENTRY_KILL_SWITCH_ENABLED=false`입니다. 이 설정은 안전 검사를 생략하지
않으며, 데이터가 하나라도 불완전하면 주문을 KIS 전송 전에 차단합니다.

미국 재무·실적 데이터는 다음 API로 조회합니다.

- `GET /api/v1/broker/market/us-fundamentals?stockCode=AAPL`

이 API는 Alpha Vantage의 `OVERVIEW`, 세 재무제표, `EARNINGS`, `EARNINGS_CALENDAR` 원문 필드를
결합합니다. 공급자 제한 메시지나 필수 재무제표 누락 시 실패하며 값을 만들지 않습니다. 공식 실적
캘린더가 발표 날짜만 제공하고 정확한 발표 시각을 제공하지 않으면 `announcementTimePrecision=DATE_ONLY`,
`announcementTimeComplete=false`, 전체 `complete=false`로 반환하므로 Hermes는 신규 진입을 보류해야 합니다.

### Reflection과 전략 성과

- `POST /api/v1/internal/trading/reflections/run?marketType=DOMESTIC&tradingDate=2026-07-19`
- `POST /api/v1/internal/agent/skills/{version}/performance/evaluate`

Reflection 거래일 경계는 시장별 IANA zone을 UTC `Instant` 범위로 변환합니다. 국내는
`Asia/Seoul`, 미국은 `America/New_York`이며 DST 전환일도 자동으로 23시간/25시간 범위를 사용합니다.
KIS 국내 일별 주문체결의 `prsm_tlex_smtl`은 수수료·세금을 분리하지 않은 추정 제비용 합계이므로
Broker도 `ESTIMATED_COMBINED` 출처로 그대로 보존하고 임의 분할하지 않습니다. 체결 주문에 이 비용이
아직 정산되지 않았으면 Reflection은 생성되지 않습니다. 미국 Reflection 역시 미국 시장용 마감자산
요약과 비용·환율 정산이 준비되지 않은 상태에서는 fail-closed됩니다.

KIS 시장별 거래대금 값은 응답에 `tradingValueUnit=KIS_API_NATIVE`로 표시합니다. 공급자가 반환한 값을
임의로 원화나 백만원 단위로 환산하지 않습니다.

국내 신규 매수의 업종 집중도는 KIS `종목정보조회`의 실제 업종 분류를 사용합니다. 보유 종목 또는
매수 대상 중 하나라도 업종을 확인할 수 없으면 업종 비중을 추정하지 않고 신규 매수를 차단합니다.
KIS `주식잔고조회`가 제공하는 전일 총자산, 자산 증감액, 자산 증감 수익률도 함께 조회하며,
필수 필드가 누락되면 일일 손실 한도 검증이 불가능하므로 신규 매수를 차단합니다. 이 데이터 장애는
기존 포지션의 매도까지 막지는 않습니다.

현재 `DAILY_MAX_LOSS_RATE`는 KIS의 자산 증감 수익률이 음수일 때 그 절댓값과 비교합니다. 입출금이
있는 날에는 순수 매매손익과 다를 수 있으므로, 실전 자동매매 활성화 전에는 운영 계좌에서 이 필드의
정산 시각과 입출금 영향을 확인해야 합니다. 서버는 이 차이를 임의 계산이나 합성값으로 보정하지 않습니다.

취소 요청에는 `Idempotency-Key` HTTP 헤더가 필요합니다.

기존 PostgreSQL의 `timestamp without time zone` 컬럼을 운영 데이터와 함께 전환할 때는
값이 UTC로 기록됐는지 먼저 확인한 뒤 `docs/postgresql_utc_migration.sql`을 유지보수 시간에 적용합니다.

### 전략 Candidate와 Shadow

`PUT /api/v1/internal/agent/skills`는 호환을 위해 유지하지만 이제 ACTIVE 전략을 교체하지 않고
CANDIDATE만 생성합니다. `activate=true`는 거부됩니다. 새 연동은 아래 명시적 경로를 사용합니다.

1. `GET /api/v1/internal/agent/skills/versions?status=CANDIDATE,SHADOW`로 이전 실행의 진행 버전 확인
2. 진행 버전이 없을 때만 `POST /api/v1/internal/agent/skills/candidates`
3. `POST /api/v1/internal/agent/skills/{version}/shadow/start`
4. ACTIVE와 같은 Feature를 SHADOW 전략으로 평가해 `POST /api/v1/internal/trading/shadow/decisions`
5. 각 시장 장 마감 후 `POST /api/v1/internal/trading/shadow/samples/settle`
6. `POST /api/v1/internal/agent/skills/{version}/performance/evaluate`
7. `POST /api/v1/internal/agent/skills/{version}/shadow/evaluate`
8. 사람이 검토한 뒤 `POST /api/v1/internal/agent/skills/{version}/promote` 또는 `/reject`

버전 목록은 최신 버전부터 반환하며 `status`를 생략하면 전체 lifecycle을 조회합니다. 따라서 매 실행이
새 세션인 Self-Improvement Cron도 기존 Candidate나 Shadow를 찾아 중복 생성 없이 이어서 처리할 수 있습니다.

Shadow Decision은 KIS 주문을 호출하지 않고 시작·마감 KIS quote를 별도 표본 테이블에 저장합니다.
성과 집계는 완료된 quote 표본만 사용하고, Shadow 평가는 `agent_skill_performance`에 Broker가 저장한
해당 버전의 성과만 읽습니다. 최소 거래 표본과 평가일을 충족하지 못하거나 성과가 없으면
승격할 수 없습니다. 모든 상태 변경 요청은 `actor`와 `reason`이 필요합니다. 승격과 Rollback은
현재 `PAPER` 모드에서만 허용하며 `LIVE`에서는 별도 운영 승인 경계가 구현될 때까지 차단합니다.

기존 운영 DB에는 배포 전에 [전략 수명주기 마이그레이션](docs/strategy_lifecycle_migration.sql)을 적용합니다.
주문 감사 연결·시장별 일일 요약·Reflection 완전성 컬럼은
[P1 Reflection 마이그레이션](docs/p1_reflection_audit_migration.sql)을 적용합니다.
Hermes Feature/Decision 생성과 Shadow 표본 컬럼은
[Decision·Shadow 마이그레이션](docs/hermes_decision_shadow_migration.sql)을 적용합니다.

`POST /api/v1/internal/agent/reset`은 `PAPER` 모드에서만 허용하며 다음 감사·확인 본문을 요구합니다.
`LIVE`에서는 삭제 쿼리 실행 전에 차단됩니다. 자가개선 Cron에는 이 확인문을 제공하지 마십시오.
또한 `ADMIN_RESET_ENABLED=false`가 기본이므로 평상시에는 Reset Controller 자체가 등록되지 않습니다.

```json
{
  "actor": "paper-operator",
  "reason": "approved test reset",
  "correlationId": "reset-20260719-001",
  "confirmation": "RESET_PAPER_LEARNING_DATA"
}
```

모든 `/api/v1/internal/agent/**` 및 `/api/v1/internal/trading/**` 변경 요청에는
`X-Actor`와 `X-Correlation-ID` 헤더도 필요합니다.
이는 인증 토큰이 아니라 Broker DB 감사 로그에 호출 주체와 실행 상관관계를 남기기 위한 값입니다.

### 2. 로컬 서버 실행
```bash
# Spring Boot 프로젝트 빌드 및 실행
./gradlew bootRun
```

## 🐳 Deployment (Docker & CI/CD)

본 프로젝트는 Docker 및 GitHub Actions 기반의 자동화된 무중단 배포를 지원합니다.

Broker API 자체 인증은 두지 않는 대신 Compose의 호스트 포트는 `127.0.0.1`에만 바인딩합니다.
Hermes가 같은 Docker 네트워크에 있다면 `http://stock-broker:8080`으로 접속하고, 호스트 프로세스라면
`http://127.0.0.1:8080`으로 접속합니다. Linux에서 `network_mode: host`인 Hermes 컨테이너도
`http://127.0.0.1:8080`을 사용합니다. 방화벽이나 리버스 프록시로 이 포트를 외부에 다시 노출하면 안 됩니다.
API 호출 본문과 헤더는 Broker 감사 로그에 기록되므로 내부 관리 요청의 `actor`와 `correlationId`도
DB에 남습니다. 인증이 없는 현재 구성은 이 loopback/전용 Docker 네트워크 경계를 유지할 때만 안전합니다.

운영 상태는 `GET /api/v1/internal/operations/status`, `/actuator/health`,
`/actuator/prometheus`에서 확인합니다. Hermes Cron은 실행 단계별 heartbeat를
`POST /api/v1/internal/operations/cron-heartbeats`에 기록해야 누락 감지가 가능합니다.
heartbeat의 `expectedNextAt`에는 고정 주기가 아니라 주말과 장 마감 경계를 반영한 실제 다음 실행 슬롯의
UTC 시각을 보내야 합니다.
전체 설정과 Alertmanager 규칙은 [운영 모니터링 문서](docs/operational_monitoring.md)를 참고합니다.

### 1. 수동 Docker Compose 배포
서버에 프로젝트를 클론한 뒤 설정된 `.env` 파일과 함께 백그라운드로 구동합니다.
```bash
docker compose up -d --build
```

### 2. 자동 배포 (GitHub Actions CD)
메인 브랜치(`main`)에 코드가 Push되면, GitHub Actions가 이미지를 빌드하여 GHCR(GitHub Container Registry)에 업로드한 뒤, 운영 서버에 SSH로 접속하여 최신 이미지를 자동으로 Pull & Restart 합니다.

**GitHub Secrets 필수 등록 항목:**
- `SERVER_HOST`: 운영 서버 IP
- `SERVER_USERNAME`: 운영 서버 SSH 접속 유저명
- `SERVER_SSH_KEY`: 운영 서버 접속용 Private Key
- `CR_PAT`: GitHub Container Registry 이미지 Pull을 위한 Personal Access Token (권한: `read:packages`)

## 📜 License
This project is proprietary and confidential.
