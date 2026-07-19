# Hermes Trading Broker API 명세

- API 버전: `v1`
- 기준 구현: Spring Boot Broker Server
- 문서 기준일: 2026-07-19
- 기본 포맷: `application/json`

이 문서는 현재 Broker Controller와 요청·응답 모델을 기준으로 작성한 Hermes 연동 계약이다.
런타임 OpenAPI 원문은 `GET /v3/api-docs`, Swagger UI는 `/swagger-ui/index.html`에서 확인할 수 있다.

## 1. 접속 및 공통 규약

### 1.1 Base URL

| 실행 위치 | Base URL |
|---|---|
| Broker와 동일한 Docker 네트워크 | `http://stock-broker:8080` |
| Broker 호스트 또는 Linux host-network Hermes | `http://127.0.0.1:8080` |

Compose의 호스트 포트는 `127.0.0.1:8080:8080`으로만 바인딩한다. 별도 인증을 사용하지 않으므로
8080 포트를 공인 인터페이스, 방화벽 포트 포워딩 또는 외부 리버스 프록시에 노출하지 않는다.

### 1.2 인증과 감사 헤더

API 인증 토큰은 요구하지 않는다. 다만 `/api/v1/internal/agent/**`와
`/api/v1/internal/trading/**`의 `POST`, `PUT`, `PATCH`, `DELETE` 요청에는 아래 감사 헤더가 필수다.

| 헤더 | 필수 | 제약 | 의미 |
|---|---:|---|---|
| `X-Actor` | 예 | 1~100자, 제어문자 금지 | 호출 주체 |
| `X-Correlation-ID` | 예 | 1~160자, 제어문자 금지 | 실행 추적 ID |

헤더가 없거나 유효하지 않으면 `400`과 아래 응답을 반환한다.

```json
{
  "error": "X-Actor and X-Correlation-ID headers are required for internal trading mutations."
}
```

이 헤더는 인증 자격 증명이 아니라 호출 주체와 실행 상관관계를 감사 로그에 남기기 위한 값이다.
`/api/v1/internal/operations/**`에는 이 필터가 적용되지 않으며 heartbeat의 `executionId`를 사용한다.

### 1.3 시간과 시장 구분

- `Instant`는 ISO 8601 UTC 형식으로 반환한다. 예: `2026-07-19T01:23:45Z`.
- `LocalDate`는 `YYYY-MM-DD` 형식이다.
- 서버·DB의 절대 시각 기준은 UTC다.
- 국내 거래일과 세션은 `Asia/Seoul`, 미국은 `America/New_York` 기준이다.
- 미국 DST는 IANA time-zone 규칙으로 자동 반영된다.
- `MarketType`: `DOMESTIC`, `OVERSEAS`.

### 1.4 공통 열거형

| 타입 | 값 |
|---|---|
| `MarketType` | `DOMESTIC`, `OVERSEAS` |
| `OrderType` | `BUY`, `SELL` |
| `OrderStatus` | `VALIDATING`, `REJECTED`, `SUBMITTING`, `PENDING`, `SUBMITTED`, `PARTIALLY_EXECUTED`, `EXECUTED`, `CANCEL_REQUESTED`, `CANCELED`, `PARTIALLY_EXECUTED_CANCELED`, `UNKNOWN`, `FAILED` |
| `TradingDecisionType` | `BUY`, `SELL`, `HOLD`, `BLOCK` |
| `TradingDecisionMode` | `ACTIVE`, `SHADOW` |
| `ShadowSampleStatus` | `PENDING`, `COMPLETED` |
| `MarketEntryPolicy` | `ALLOW_NEW_ENTRIES`, `REDUCE_NEW_ENTRIES`, `BLOCK_NEW_ENTRIES` |
| `AgentSkillStatus` | `CANDIDATE`, `SHADOW`, `ACTIVE`, `REJECTED`, `ROLLED_BACK` |
| `CronHeartbeatPhase` | `STARTED`, `SUCCEEDED`, `FAILED` |
| `AlertSeverity` | `WARNING`, `CRITICAL` |

### 1.5 일반 오류 응답

대부분의 오류는 아래 형식이다.

```json
{
  "timestamp": "2026-07-19T01:23:45Z",
  "status": 400,
  "error": "Bad Request",
  "message": "오류 설명",
  "path": "/api/v1/..."
}
```

| HTTP 상태 | 의미 |
|---:|---|
| `200` | 정상 처리. 주문은 본문의 `success`와 `status`를 추가 확인해야 함 |
| `201` | 시장 컨텍스트, Feature, Decision 또는 Shadow 표본 생성 성공 |
| `400` | 요청값, 감사 헤더 또는 상태 전이 오류 |
| `404` | 리소스 없음 또는 비활성화된 Reset Controller |
| `502` | KIS, Naver, OpenDART 등 외부 API 오류 |
| `503` | 필수 실데이터 파이프라인 또는 DB 사용 불가 |
| `500` | 처리되지 않은 서버 오류 |

시장 데이터 Controller의 일부 외부 API 오류는 다음 축약 형식을 사용한다.

```json
{
  "error": "Real market data unavailable",
  "message": "상세 원인"
}
```

## 2. 전체 엔드포인트

### 2.1 Broker 시장·주문 API

| Method | Path | 응답 | 설명 |
|---|---|---|---|
| `GET` | `/api/v1/broker/market/price` | `CurrentPrice` | 시장별 현재가 조회 |
| `POST` | `/api/v1/broker/market/order` | `OrderResponse` | 공통 안전 파이프라인을 통한 주문 |
| `GET` | `/api/v1/broker/market/daily-logs` | `TradingLog[]` | 현재 UTC 일자의 주문 감사 로그 |
| `GET` | `/api/v1/broker/market/fundamentals` | `FundamentalsResponse` | OpenDART 국내 기업·공시·재무 |
| `GET` | `/api/v1/broker/market/us-fundamentals` | `UsFundamentalsSnapshot` | Alpha Vantage 미국 재무·실적 |
| `GET` | `/api/v1/broker/market/news` | `NewsResponse` | Naver 뉴스 및 규칙 기반 분석 |
| `GET` | `/api/v1/broker/market/intelligence` | `IntelligenceResponse` | 국내 Fundamentals와 뉴스 집계 |
| `GET` | `/api/v1/broker/market/status` | `MarketStatus` | 국내·미국 정규장 상태 |
| `GET` | `/api/v1/broker/market/overview` | `MarketOverview` | 시장 breadth와 투자자 수급 |
| `GET` | `/api/v1/broker/market/watchlist` | `MarketWatchlistResult` | KIS 실데이터 기반 분석 후보군 |
| `GET` | `/api/v1/broker/trading/environment` | `TradingEnvironment` | 주문 환경과 Kill Switch 조회 |
| `GET` | `/api/v1/broker/trading/risk-policy` | `RiskPolicy` | 적용 중인 Risk 정책 조회 |
| `POST` | `/api/v1/broker/trading/cycle/{stockCode}` | `TradingCycleResult` | Feature·Decision·공통 주문 파이프라인 실행 |
| `GET` | `/api/v1/broker/account/portfolio` | `PortfolioSummary` | KIS 계좌·보유·업종 비중 조회 |
| `GET` | `/api/v1/broker/account/overseas/us` | `OverseasAccountSnapshot` | 미국 계좌와 매도 가능 수량 |
| `GET` | `/api/v1/broker/account/overseas/order-capacity` | `OverseasOrderCapacity` | 미국 주문 가능 금액·수량 |
| `GET` | `/api/v1/broker/orders/open` | `OpenOrder[]` | KIS 미체결 주문 조회 |
| `POST` | `/api/v1/broker/orders/{orderId}/cancel` | 본문 없음 | 주문 취소 요청 |

### 2.2 내부 Trading API

| Method | Path | 응답 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/internal/trading/market-contexts` | `MarketContext` | 시장 분석 컨텍스트 생성 |
| `GET` | `/api/v1/internal/trading/market-contexts/latest` | `MarketContext` | 최신 시장 컨텍스트 조회 |
| `GET` | `/api/v1/internal/trading/market-contexts` | `MarketContext[]` | 시장 컨텍스트 이력 조회 |
| `POST` | `/api/v1/internal/trading/features` | `TradingFeatureSnapshot` | Hermes 판단 입력 Feature 저장 |
| `GET` | `/api/v1/internal/trading/features/latest` | `TradingFeatureSnapshot` | 종목별 최신 Feature 조회 |
| `GET` | `/api/v1/internal/trading/features` | `TradingFeatureSnapshot[]` | 일자별 Feature 조회 |
| `POST` | `/api/v1/internal/trading/decisions` | `TradingDecision` | ACTIVE 전략 판단 저장 |
| `GET` | `/api/v1/internal/trading/decisions` | `TradingDecision[]` | 일자별 Decision 조회 |
| `POST` | `/api/v1/internal/trading/shadow/decisions` | `ShadowDecisionResult` | SHADOW 판단과 시작 quote 표본 저장 |
| `POST` | `/api/v1/internal/trading/shadow/samples/settle` | `ShadowPerformanceSample[]` | 장 마감 후 실제 quote로 표본 결산 |
| `GET` | `/api/v1/internal/trading/shadow/samples` | `ShadowPerformanceSample[]` | 전략 버전별 Shadow 표본 조회 |
| `GET` | `/api/v1/internal/trading/reflections` | `TradingReflection[]` | 일자별 Reflection 조회 |
| `POST` | `/api/v1/internal/trading/reflections/run` | `TradingReflection[]` | 시장별 일일 Reflection 실행 |

### 2.3 내부 Agent 관리 API

변경 요청에는 `X-Actor`, `X-Correlation-ID`가 필요하다.

| Method | Path | 응답 | 설명 |
|---|---|---|---|
| `GET` | `/api/v1/internal/agent/skills` | `AgentSkillResponse` | 현재 ACTIVE 전략 조회 |
| `GET` | `/api/v1/internal/agent/skills/versions` | `AgentSkillResponse[]` | 전체 또는 lifecycle 상태별 버전 목록 |
| `PUT` | `/api/v1/internal/agent/skills` | `AgentSkillResponse` | 호환용 Candidate 생성; 즉시 활성화 금지 |
| `POST` | `/api/v1/internal/agent/skills/candidates` | `AgentSkillResponse` | Candidate 생성 |
| `GET` | `/api/v1/internal/agent/skills/{version}` | `AgentSkillResponse` | 버전 조회 |
| `POST` | `/api/v1/internal/agent/skills/{version}/shadow/start` | `AgentSkillResponse` | Candidate를 SHADOW로 전환 |
| `POST` | `/api/v1/internal/agent/skills/{version}/shadow/evaluate` | `AgentSkillResponse` | Broker DB 성과로 Shadow 평가 |
| `POST` | `/api/v1/internal/agent/skills/{version}/promote` | `AgentSkillResponse` | 적격 Shadow를 ACTIVE로 승격 |
| `POST` | `/api/v1/internal/agent/skills/{version}/reject` | `AgentSkillResponse` | Candidate 또는 Shadow 거절 |
| `POST` | `/api/v1/internal/agent/skills/{version}/performance/evaluate` | `AgentSkillPerformance` | 전략 성과 계산·저장 |
| `GET` | `/api/v1/internal/agent/skills/{version}/performance` | `AgentSkillPerformance` | 저장된 성과 조회 |
| `GET` | `/api/v1/internal/agent/skills/{version}/rollback-evaluation` | `StrategyRollbackEvaluation` | Rollback 필요성 평가 |
| `POST` | `/api/v1/internal/agent/skills/rollback` | `AgentSkillResponse` | 승인된 대상 버전으로 Rollback |
| `POST` | `/api/v1/internal/agent/reset` | 문자열 | Paper 학습 데이터 초기화 |

### 2.4 운영 API

| Method | Path | 응답 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/internal/operations/cron-heartbeats` | `CronHeartbeat` | Hermes Cron 시작·성공·실패 기록 |
| `GET` | `/api/v1/internal/operations/status` | `OperationalStatus` | 데이터 소스, Cron, Kill Switch, 경보 통합 상태 |

Actuator는 `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`,
`/actuator/scheduledtasks`를 제공한다.

## 3. 주문 및 취소

### 3.1 주문 생성

`POST /api/v1/broker/market/order`

#### 요청

```json
{
  "marketType": "OVERSEAS",
  "stockCode": "AAPL",
  "exchangeCode": "NASD",
  "orderType": "BUY",
  "price": 195.50,
  "quantity": 1,
  "idempotencyKey": "20260719-OVERSEAS-AAPL-NASD-BUY-V3-CYCLE1",
  "decisionId": "a42307cc-9733-4f4e-87a7-b621efa4a198",
  "featureId": "07899153-e0aa-425e-8e6a-f07e4f010313",
  "strategyVersion": "3",
  "decisionReason": "시장 컨텍스트와 전략 조건 충족",
  "snapshotIndicators": {
    "signal": "example"
  }
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `marketType` | enum | 예 | `DOMESTIC`, `OVERSEAS` |
| `stockCode` | string | 예 | 공백 불가, 최대 20자 |
| `exchangeCode` | string | 해외만 예 | `NASD`, `NYSE`, `AMEX`; quote 별칭도 정규화 |
| `orderType` | enum | 예 | `BUY`, `SELL` |
| `price` | decimal | 예 | `>= 0.0001` |
| `quantity` | integer | 예 | `>= 1` |
| `idempotencyKey` | string | 예 | 공백 불가, 최대 160자, 전체 주문에서 unique |
| `decisionId` | string | 예 | Broker DB에 존재하는 ACTIVE Decision ID, 최대 36자 |
| `featureId` | string | 예 | 해당 Decision이 참조하는 Feature ID, 최대 36자 |
| `strategyVersion` | string | 예 | 현재 ACTIVE 전략 버전, 최대 50자 |
| `decisionReason` | string | 아니오 | 감사용 판단 근거 |
| `snapshotIndicators` | object | 아니오 | 감사용 추가 지표 |

세 연결 ID는 선택적 감사값이 아니라 서버 강제 필드다. Broker는 주문 전에 Decision·Feature 존재 여부,
종목·시장·방향·가격·수량의 일치, Decision의 `mode=ACTIVE`, 전략 버전의 현재 ACTIVE 여부를 검사한다.
BUY는 Feature의 `marketContextId`가 주문 시점 최신 Market Context와 같아야 한다.
SHADOW, HOLD, BLOCK, stale 전략 또는 이미 다른 주문이 연결된 Decision은 KIS 호출 전에 차단한다.
`decisionReason`은 호환용이며 주문 감사 로그에는 요청값이 아니라 저장된 Decision의 reason을 기록한다.

#### 응답

```json
{
  "success": true,
  "brokerOrderId": 152,
  "orderId": "NASD-0000123456",
  "message": "Order accepted",
  "status": "SUBMITTED",
  "replayed": false
}
```

| 필드 | 타입 | 의미 |
|---|---|---|
| `success` | boolean | KIS 접수 또는 후속 유효 상태 여부 |
| `brokerOrderId` | integer | Broker DB 주문 로그 ID |
| `orderId` | string/null | KIS 외부 주문번호 |
| `message` | string/null | 거절·접수·실패 설명 |
| `status` | `OrderStatus` | Broker 주문 상태 |
| `replayed` | boolean | 동일 idempotency 요청의 기존 결과인지 여부 |

안전 정책으로 거절된 주문도 감사 로그를 남긴 뒤 보통 HTTP `200`과
`success=false`, `status=REJECTED`를 반환한다. 따라서 HTTP 상태만으로 주문 성공을 판단하면 안 된다.
KIS 전송 후 결과를 확정할 수 없는 예외는 `status=UNKNOWN`이며 주문 대사가 필요하다.

#### Idempotency 계약

- 같은 key와 같은 payload: 새 주문 없이 기존 결과 반환, `replayed=true`.
- 같은 key와 다른 payload: HTTP `400`.
- payload hash에는 거래소를 포함한 주문 필드와 Feature·Decision 감사 연결값이 포함된다.
- 타임아웃 후에도 새로운 key를 만들지 말고 원래 key로 재조회성 재시도한다.

#### KIS 호출 전 검증 순서

1. 계좌 advisory lock 획득
2. idempotency key와 payload hash 확인
3. Broker DB의 Feature·ACTIVE Decision·현재 ACTIVE 전략 연결 및 one-decision/one-order 확인
4. KIS 환경, 거래 모드, 자율성 모드, 해외 주문 허용 여부 확인
5. 신규 매수 Kill Switch 확인
6. 시장 개장 및 캘린더 완전성 확인
7. 최신 현재가 재조회와 주문가 편차 확인
8. Broker DB와 KIS의 동일 종목·동일 방향 미체결 확인
9. 신규 매수 시장 컨텍스트·업종 데이터 확인
10. 매도 가능 수량 또는 매수 가능 금액 확인
11. 주문 금액, 거래 횟수, 종목 수, 종목·업종 비중 확인. 국내는 일일 손실 데이터도 필수 검사
12. KIS 주문 전송 및 상태 저장

미국 주문은 KIS `MOCK` + Broker `PAPER/PAPER_AUTO`에서만 지원한다. `OVERSEAS_ORDER_ENABLED=true`여도
PRODUCTION 해외 주문은 차단된다. BUY는 `VTTT1002U`, SELL은 `VTTT1001U`를 사용하고 KIS 모의투자에서
허용되는 지정가(`ORD_DVSN=00`)만 전송한다. USD 일일 손실 데이터는 현재 공식 Risk 입력으로 제공되지
않으므로 정책 응답의 `overseasDailyLossLimitAvailable=false`로 명시한다. 대신 1회 USD 금액, 일일 제출
수, 보유 수, 종목·업종 비중을 강제하며 실전 해외 주문은 이 데이터가 완성될 때까지 허용하지 않는다.

KIS 연동 필드는 한국투자증권 공식 샘플의
[해외주식 주문](https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/overseas_stock/order/order.py),
[정정취소](https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/overseas_stock/order_rvsecncl/order_rvsecncl.py),
[주문체결내역](https://github.com/koreainvestment/open-trading-api/blob/main/examples_llm/overseas_stock/inquire_ccnl/inquire_ccnl.py)을 기준으로 한다.

시장 컨텍스트가 없거나 stale이면 신규 `BUY`만 차단한다. `SELL`은 시장 컨텍스트를 요구하지 않지만
시장 개장, 환경, 최신가, 중복, 보유·매도 가능 수량 검증은 계속 적용된다.

### 3.2 주문 취소

`POST /api/v1/broker/orders/{orderId}/cancel`

| 위치 | 이름 | 필수 | 예 |
|---|---|---:|---|
| path | `orderId` | 예 | 국내 `지점-주문번호`, 해외 `NASD-주문번호` |
| query | `stockCode` | 예 | `005930` 또는 `AAPL` |
| query | `marketType` | 예 | `DOMESTIC` 또는 `OVERSEAS` |
| header | `Idempotency-Key` | 예 | `cancel-0000123456-v1` |

성공 응답은 HTTP `200`, 본문 없음이다. 같은 취소 key의 재시도는 중복 KIS 호출 없이 성공 처리한다.
다른 key로 이미 취소 요청된 주문, 계좌·시장·종목 불일치, 취소 불가능 상태는 `400`이다.
KIS 취소 접수는 최종 취소 완료를 뜻하지 않으며 주문 대사에서 최종 상태를 갱신한다.

### 3.3 미체결과 일지

`GET /api/v1/broker/orders/open`은 다음 배열을 반환한다.

```json
[
  {
    "orderId": "0000123456",
    "stockCode": "005930",
    "exchangeCode": null,
    "marketType": "DOMESTIC",
    "orderType": "BUY",
    "price": 70000,
    "quantity": 1,
    "executedQuantity": 0,
    "orderedAt": "2026-07-19T01:10:00Z"
  }
]
```

해외 미체결 항목은 `exchangeCode`와 `NASD-주문번호` 형식의 `orderId`를 반환한다.
`GET /api/v1/broker/market/daily-logs`는 현재 UTC 날짜 범위의 `TradingLog[]`를 반환한다.
주요 감사 필드는 `id`, `marketType`, `stockCode`, `exchangeCode`, `accountKey`, `idempotencyKey`, `requestHash`,
`externalOrderId`, `marketContextId`, `decisionId`, `featureId`, `strategyVersion`, `orderType`,
`orderPrice`, `orderQuantity`, `executionPrice`, `executedQuantity`, `transactionCost`, `costCurrency`,
`costSource`, `costDataComplete`, `slippageAmount`, `reconciledAt`, `status`, `createdAt`, `submittedAt`,
`responseMessage`, `riskPolicyVersion`, `snapshotIndicators`, `decisionReason`이다.

## 4. 환경과 Risk Policy

### 4.1 주문 환경

`GET /api/v1/broker/trading/environment`

```json
{
  "kisEnvironment": "MOCK",
  "tradingMode": "PAPER",
  "autonomyMode": "ANALYSIS_ONLY",
  "realOrderEnabled": false,
  "entryKillSwitchEnabled": true,
  "overseasOrderEnabled": false,
  "overseasPaperOrderEnabled": false,
  "overseasLiveOrderEnabled": false
}
```

`kisEnvironment=MOCK`은 고정 가짜 데이터가 아니라 KIS 공식 모의투자 환경을 의미한다.
실전 주문 전에는 `PRODUCTION`, `LIVE`, `LIVE_AUTO`, `realOrderEnabled=true`,
`entryKillSwitchEnabled=false` 및 Risk Policy의 `liveTradingEnabled=true`가 함께 충족되어야 한다.
이 실전 조건은 현재 국내 주문에만 적용된다. 해외 실전은 별도로 항상 false이다.

### 4.2 Risk Policy

`GET /api/v1/broker/trading/risk-policy`

| 필드 | 타입 | 의미 |
|---|---|---|
| `version` | string | 주문 감사에 저장되는 정책 버전 |
| `dailyMaxLossRate` | decimal | 일일 최대 손실률 |
| `maxOrderAmount` | decimal | 1회 최대 주문 금액; 매수 시 시장 배수 적용 |
| `maxDailyTrades` | integer | 시장 거래일 기준 최대 제출 주문 수 |
| `maxPositionCount` | integer | 최대 보유 종목 수 |
| `maxSectorExposureRate` | decimal | 최대 업종 비중 |
| `maxStockExposureRate` | decimal | 최대 단일 종목 비중 |
| `maxPriceDeviationRate` | decimal | 요청가와 최신가 최대 편차 |
| `allowAveragingDown` | boolean | 손실 보유 종목 추가 매수 허용 여부 |
| `allowMarginTrading` | boolean | 주문 가능 금액 초과 허용 여부 |
| `liveTradingEnabled` | boolean | Risk 정책의 실전 주문 허용 여부 |
| `requireSectorData` | boolean | 신규 매수 시 업종 데이터 필수 여부 |
| `requireDailyLossData` | boolean | 신규 매수 시 일일 손실 데이터 필수 여부 |
| `overseasMaxOrderAmountUsd` | decimal | 미국 Paper 1회 최대 USD 주문 금액 |
| `overseasMaxDailyTrades` | integer | 미국 시장일 기준 최대 제출 주문 수 |
| `overseasMaxPositionCount` | integer | 미국 최대 보유 종목 수 |
| `overseasMaxStockExposureRate` | decimal | 미국 단일 종목 최대 비중 |
| `overseasAllowAveragingDown` | boolean | 미국 손실 종목 추가 매수 허용 여부 |
| `overseasDailyLossLimitAvailable` | boolean | 현재 `false`; 일일 손실 공식 입력 미완성 표시 |
| `overseasLiveTradingEnabled` | boolean | 현재 항상 `false` |

## 5. 시장 데이터

### 5.1 조회 파라미터

| Endpoint | Query | 기본값/제약 | 데이터 소스와 주의사항 |
|---|---|---|---|
| `/market/price` | `stockCode`, `marketType`, `exchangeCode` | 해외는 거래소 필수 | 시장·거래소별 KIS 현재가 |
| `/market/status` | `marketType` | `DOMESTIC` | 국내 KIS 휴장 확인, 미국 내장 NYSE 캘린더 |
| `/market/fundamentals` | `stockCode` | 국내 6자리 코드 | OpenDART |
| `/market/us-fundamentals` | `stockCode` | 미국 symbol 1~20자 | Alpha Vantage; 재무 불완전 시 `503` |
| `/market/news` | `stockCode` | 검색어로 사용 | Naver 뉴스, 중복 제목 제거 |
| `/market/intelligence` | `stockCode` | 국내 종목 | OpenDART와 Naver 집계 |
| `/market/overview` | `marketType` | `DOMESTIC`/`OVERSEAS` | 미국은 KIS SPY·QQQ·DIA 실가격 benchmark proxy이며 full breadth가 아님 |
| `/market/watchlist` | 없음 | - | KIS 거래대금 후보, 매수 신호 아님 |

### 5.2 현재가 응답

`CurrentPrice` 필드:

- `stockCode`, `currentPrice`, `changeRate`, `accumulatedVolume`
- `technicalIndicators`: `ma5`, `ma20`, `ma60`, `rsi14`

필수 실데이터를 얻지 못하면 임의 가격이나 기술지표로 대체하지 않는다.

### 5.3 시장 상태 응답

`MarketStatus` 필드:

| 필드 | 타입 | 의미 |
|---|---|---|
| `marketType` | string | 요청 시장 |
| `open` | boolean | 정규장 주문 가능 여부; JSON 속성명은 `open` |
| `status` | string | `REGULAR_MARKET`, `PRE_MARKET`, `AFTER_MARKET`, `CLOSED`, `CLOSED_WEEKEND`, `CLOSED_HOLIDAY`, `CALENDAR_UNAVAILABLE`, `ERROR` 등 |
| `reason` | string | 판정 사유 |
| `marketTimeZone` | string | `Asia/Seoul` 또는 `America/New_York` |
| `marketDate` | date | 시장 현지 거래일 |
| `earlyClose` | boolean | 조기 폐장일 여부 |
| `complete` | boolean | 캘린더 판정 완전성 |
| `calendarSource` | string | 캘린더 출처 |
| `sessionOpensAt` | instant/null | 정규장 개장 UTC 시각 |
| `sessionClosesAt` | instant/null | 정규장 폐장 UTC 시각 |
| `checkedAt` | instant | 확인 시각 |

`complete=false`이면 주문 API는 fail-closed한다.

### 5.4 Watchlist 응답

`MarketWatchlistResult`:

- `stocks[]`: `stockCode`, `stockName`, `market`, `category`, `score`, `reasons[]`
- `dataSource`, `fetchedAt`, `complete`, `freshness`, `candidateOnly`

`candidateOnly`는 항상 후보군 의미이며 주문 신호가 아니다. `complete=false` 또는 빈 후보군이면 정상
응답 대신 `502`가 반환된다.

### 5.5 뉴스 응답

`NewsResponse.result`:

- `stockCode`, `query`, `totalAvailable`, `totalAnalyzed`
- `dataSource`, `fetchedAt`, `complete`, `freshness`
- `analysisMethod`: 현재 `RULE_BASED_LEXICAL_V1`
- `articles[]`: `title`, `description`, `url`, `originalUrl`, `source`, `publishedAt`,
  `qualityScore`, `relevanceScore`, `sentimentScore`, `sentiment`

`sentiment`는 `POSITIVE`, `NEGATIVE`, `NEUTRAL`이다. 점수는 설명 가능한 어휘 규칙 결과이며
LLM 또는 외부 감성 모델 결과가 아니다.

### 5.6 Fundamentals 응답

국내 `FundamentalsResponse`:

- `stockCode`
- `profile`: `corpName`, `corpNameEng`, `stockName`, `stockCode`, `ceoName`, `corpClass`,
  `setupDate`, `settlementMonth`
- `recentDisclosures[]`: `receiptNumber`, `corpName`, `reportName`, `submitterName`, `receiptDate`, `remarks`
- `recentFinancials[]`: `businessYear`, `reportCode`, `accountName`, `amount`, `currency`

미국 `UsFundamentalsSnapshot`:

- `symbol`, `companyOverview`
- `financialReports[]`: `statementType`, `periodType`, `fiscalDateEnding`, `reportedCurrency`, `values`
- `earningsHistory[]`: `fiscalDateEnding`, `reportedDate`, `reportedEps`, `estimatedEps`, `surprise`,
  `surprisePercentage`, `reportTime`
- `upcomingEarnings`: `symbol`, `name`, `reportDate`, `fiscalDateEnding`, `estimate`, `currency`,
  `announcementTimeUtc`, `announcementTimePrecision`, `dataSource`
- `dataSources`, `fetchedAt`, `validUntil`
- `financialDataComplete`, `earningsCalendarComplete`, `announcementTimeComplete`, `complete`, `warnings`

`financialDataComplete=false`이면 API가 `503`을 반환한다. 정확한 실적 발표 시각이 없으면
스냅샷 자체는 반환될 수 있지만 `announcementTimeComplete=false`, `complete=false`이므로 Hermes는
신규 진입을 보류해야 한다.

### 5.7 Market Overview 응답

`MarketOverview`:

- `marketType`, `advancingIssues`, `decliningIssues`, `unchangedIssues`, `breadthScore`
- `foreignNetBuyTradingValue`, `individualNetBuyTradingValue`, `institutionNetBuyTradingValue`
- `tradingValueUnit`, `dataSource`, `fetchedAt`, `validUntil`, `complete`, `freshness`
- `segments[]`: `segment`, `indexCode`, `indexValue`, `indexChangeRate`, `accumulatedTradingValue`,
  상승·하락·보합·상한·하한 종목 수, `breadthScore`, 투자자별 순매수, `tradingValueUnit`,
  `observedMarketDate`

`complete=true`이고 `validUntil` 이전인 실데이터만 반환한다.

## 6. 계좌 API

### 6.1 통합 포트폴리오

`GET /api/v1/broker/account/portfolio`

`PortfolioSummary` 필드:

- 자산: `totalAssetAmount`, `cashAmount`, `buyingPower`, `usdCash`, `usdBuyingPower`,
  `totalEvaluationAmount`, `totalProfitLossAmount`
- 일일 손실 데이터: `previousTotalAssetAmount`, `dailyAssetChangeAmount`, `dailyAssetChangeRate`,
  `dailyAssetChangeDataComplete`, `dailyAssetChangeDataSource`
- 구성: `cashRate`, `positionCount`, `sectorDataComplete`, `sectorDataSource`, `calculatedAt`
- `positions[]`: `stockCode`, `stockName`, `marketType`, `sector`, `quantity`, `availableQuantity`,
  `averagePurchasePrice`, `currentPrice`, `evaluationAmount`, `profitLossAmount`, `profitLossRate`,
  `portfolioWeight`
- `sectorExposures[]`: `sector`, `evaluationAmount`, `exposureRate`

계좌 잔액·보유·국내 주문 가능 금액 중 하나라도 필수값이 없으면 `503`이다. 업종 데이터가 일부
누락되면 조회는 가능하지만 `sectorDataComplete=false`가 되며 신규 매수 Risk 검증은 차단된다.

### 6.2 미국 계좌

`GET /api/v1/broker/account/overseas/us`

`OverseasAccountSnapshot`:

- `countryCode`, `currency`, `cashBalance`, `availableForUse`
- `positions[]`: `stockCode`, `exchangeCode`, `currency`, `quantity`, `sellableQuantity`,
  `averagePurchasePrice`, `currentPrice`, `evaluationAmount`, `profitLossAmount`, `profitLossRate`
- `dataSource`, `fetchedAt`, `complete`

### 6.3 미국 주문 가능 수량

`GET /api/v1/broker/account/overseas/order-capacity`

| Query | 필수 | 제약 |
|---|---:|---|
| `stockCode` | 예 | `[A-Z0-9.-]{1,20}` |
| `exchangeCode` | 아니오 | 기본 `NASD`; `NASD`, `NAS`, `NYSE`, `AMEX` |
| `orderPrice` | 예 | 양수 |

응답 필드: `stockCode`, `exchangeCode`, `currency`, `requestedPrice`, `orderableForeignAmount`,
`overseasOrderableAmount`, `maximumOrderableQuantity`, `orderableQuantity`, `exchangeRate`, `dataSource`,
`fetchedAt`, `complete`.

## 7. 시장 컨텍스트

### 7.1 생성

`POST /api/v1/internal/trading/market-contexts`

```json
{
  "marketType": "DOMESTIC",
  "entryPolicy": "REDUCE_NEW_ENTRIES",
  "riskMultiplier": 0.5,
  "validUntil": "2026-07-19T02:05:00Z",
  "rationale": ["시장 breadth 약화", "기관 순매도 확대"],
  "analyzedBy": "hermes-market-cron",
  "correlationId": "market-20260719-0200"
}
```

| 필드 | 필수 | 제약 |
|---|---:|---|
| `marketType` | 예 | `DOMESTIC`, `OVERSEAS` |
| `entryPolicy` | 예 | `MarketEntryPolicy` |
| `riskMultiplier` | 예 | 0~1 |
| `validUntil` | 아니오 | 현재 이후, overview 및 서버 최대 유효기간 이내 |
| `rationale` | 예 | 1~20개, 항목당 최대 500자 |
| `analyzedBy` | 예 | 최대 100자 |
| `correlationId` | 아니오 | 최대 100자; 없으면 Broker가 생성 |

정책별 배수 규칙:

- `BLOCK_NEW_ENTRIES`: 반드시 `0`.
- `REDUCE_NEW_ENTRIES`: `0 < value < 1`.
- `ALLOW_NEW_ENTRIES`: `value > 0`; `1` 이하.

Broker는 생성 시 최신 overview를 다시 조회하여 `overviewSnapshot`으로 함께 저장한다. 성공은 `201`이다.

### 7.2 조회

- `GET /api/v1/internal/trading/market-contexts/latest?marketType=DOMESTIC`: 최신 1건, 없으면 `404`.
- `GET /api/v1/internal/trading/market-contexts?marketType=DOMESTIC`: 최신순 이력, 기본 최대 100건.

`MarketContext` 필드: `contextId`, `marketType`, `entryPolicy`, `riskMultiplier`, `overviewSnapshot`,
`rationale`, `analyzedBy`, `correlationId`, `analyzedAt`, `validUntil`.

## 8. Trading Cycle, Feature, Decision, Reflection

### 8.1 Trading Cycle

`POST /api/v1/broker/trading/cycle/{stockCode}`

현재 Cycle은 국내 시장 Feature와 Decision을 저장한다. Broker 기본 Decision 어댑터는 fail-closed
`BLOCK`을 반환한다. 향후 `BUY` 또는 `SELL` 판단기가 연결되더라도 주문은 반드시
`POST /market/order`와 동일한 `AgentTradingUseCase` 안전 파이프라인을 통과한다.

응답 `TradingCycleResult`: `stockCode`, `success`, `message`, `decision`, `executedAt`.
Cycle 내부 오류는 HTTP `200`, `success=false`로 반환될 수 있으므로 본문을 확인해야 한다.

### 8.2 Hermes Feature 생성

`POST /api/v1/internal/trading/features`

```json
{
  "stockCode": "005930",
  "marketType": "DOMESTIC",
  "technicalFeatures": {
    "rsi14": 43.2,
    "ma20DeviationRate": -0.7
  },
  "newsFeatures": {
    "sentimentScore": 0.15,
    "complete": true
  },
  "riskFeatures": {
    "marketContextId": "23fb30b7-5ed1-40d1-b66c-f82f3ea421b8"
  },
  "idempotencyKey": "feature-20260720-005930-active-v3-cycle1"
}
```

Broker가 `featureId`와 UTC `snapshotAt`을 생성한다. `riskFeatures.marketContextId`를 보내면
생성 시점에 존재하고 같은 시장이며 complete·fresh인 Market Context여야 한다. 신규 BUY Decision에는
이 값이 필수지만, 컨텍스트 장애가 기존 포지션 SELL 기록 자체를 막지 않도록 Feature는 값 없이도 저장할 수 있다. 같은 idempotency key와
같은 payload는 기존 Feature를 반환하고, 다른 payload 재사용은 `400`이다. 성공은 `201`이다.

### 8.3 ACTIVE Decision 생성과 주문 연결

`POST /api/v1/internal/trading/decisions`

```json
{
  "featureId": "07899153-e0aa-425e-8e6a-f07e4f010313",
  "decisionType": "BUY",
  "strategyVersion": 3,
  "reason": "ACTIVE v3 조건과 시장 진입 정책 충족",
  "recommendedPrice": 70000,
  "recommendedQuantity": 1,
  "idempotencyKey": "decision-20260720-005930-active-v3-cycle1"
}
```

Broker가 `decisionId`, Feature의 `stockCode`, UTC `decidedAt`, `mode=ACTIVE`를 저장한다.
`strategyVersion`은 실제 현재 `ACTIVE` 상태여야 한다. BUY/SELL에는 양수 가격·수량이 필요하고,
HOLD/BLOCK에는 두 필드를 보내면 안 된다. 동일 Feature·전략·mode에는 Decision을 하나만 허용한다.

Hermes의 정확한 주문 흐름은 다음과 같다.

1. 분석에 사용한 값을 `POST /features`로 저장하고 Broker 생성 `featureId`를 받는다.
2. Hermes가 ACTIVE 전략으로 BUY·SELL·HOLD·BLOCK을 판단한다.
3. 판단을 `POST /decisions`로 저장하고 Broker 생성 `decisionId`를 받는다.
4. HOLD/BLOCK이면 종료한다.
5. BUY/SELL이면 저장된 값과 동일한 가격·수량·방향 및 두 ID로 `POST /broker/market/order`를 호출한다.
6. 주문 API가 DB 연결과 전체 안전 정책을 다시 검증한 후에만 KIS로 전송한다.

`/broker/trading/cycle/{stockCode}`의 기본 어댑터가 BLOCK인 것은 정상적인 fail-closed 동작이다.
외부 Hermes 판단은 이 Cycle API가 아니라 위 명시적 Feature/Decision 생성 경로를 사용한다.

### 8.4 SHADOW 판단과 실제 quote 표본

Candidate를 `/skills/{version}/shadow/start`로 SHADOW 전환한 뒤, ACTIVE와 같은 Feature를 해당 버전으로
별도 평가하여 아래 API에 제출한다.

`POST /api/v1/internal/trading/shadow/decisions`

```json
{
  "featureId": "07899153-e0aa-425e-8e6a-f07e4f010313",
  "decisionType": "BUY",
  "strategyVersion": 4,
  "reason": "SHADOW v4 counterfactual BUY",
  "recommendedPrice": 70000,
  "recommendedQuantity": 1,
  "idempotencyKey": "decision-20260720-005930-shadow-v4-cycle1",
  "exchangeCode": null
}
```

전략 상태는 반드시 `SHADOW`여야 한다. Broker는 시장 개장을 확인하고 KIS 현재가를 다시 조회해
`referencePrice`로 저장하며 `PENDING` 표본을 만든다. 미국은 `exchangeCode`가 필수다. 이 경로에는
주문 UseCase나 KIS 주문 Client가 없으며 생성된 Decision은 `mode=SHADOW`라서 일반 주문 API에 넣어도
KIS 전송 전에 차단된다.

각 시장 정규장 마감 후 같은 시장 현지 거래일에 한 번 호출한다.

```text
POST /api/v1/internal/trading/shadow/samples/settle
  ?marketType=DOMESTIC
  &tradingDate=2026-07-20
```

Broker는 장 마감과 캘린더 완전성을 확인하고 KIS 현재가를 다시 조회해 `observedPrice`,
`rawReturnRate`, `actionReturnRate`, `observedAt`, `status=COMPLETED`를 저장한다. BUY의 action return은
가격 수익률, SELL은 그 반대, HOLD/BLOCK은 무포지션 `0`이다. 이 값은 실제 체결손익이 아니라
두 Broker quote로 관측한 counterfactual Shadow 표본이다. 과거 종가를 합성하지 않기 때문에 현재 시장
거래일만 결산할 수 있으며 Cron 누락 시 `503/400`으로 실패한다.

표본 조회:

```text
GET /api/v1/internal/trading/shadow/samples?strategyVersion=4&status=COMPLETED
```

`POST /api/v1/internal/agent/skills/4/performance/evaluate`는 버전 4가 여전히 SHADOW이고 ACTIVE Reflection이
없을 때 이 완료 표본을 거래일별로 집계해 `agent_skill_performance`에 저장한다. 그 다음
`/api/v1/internal/agent/skills/4/shadow/evaluate`가 최소 거래 수·평가일을 확인한다.
즉 `shadow/evaluate` 자체는 표본을 생성하지 않고 이미 결산·집계된 Broker DB 성과만 판정한다.

### 8.5 조회 API

| Endpoint | Query | 응답/주의사항 |
|---|---|---|
| `/internal/trading/features/latest` | `stockCode`, `marketType` | 최신 Feature, 없으면 `404` |
| `/internal/trading/features` | `date` | `TradingFeatureSnapshot[]` |
| `/internal/trading/decisions` | `date` | `TradingDecision[]` |
| `/internal/trading/reflections` | `date` | `TradingReflection[]` |

`date`는 현재 서비스 계약상 문자열이며 호출자는 `YYYY-MM-DD`를 사용한다.

`TradingFeatureSnapshot`: `featureId`, `stockCode`, `marketType`, `technicalFeatures`, `newsFeatures`,
`riskFeatures`, `snapshotAt`, `idempotencyKey`.

`TradingDecision`: `decisionId`, `featureId`, `stockCode`, `decisionType`, `strategyVersion`, `reason`,
`recommendedPrice`, `recommendedQuantity`, `decidedAt`, `mode`, `idempotencyKey`.

### 8.6 Reflection 실행

`POST /api/v1/internal/trading/reflections/run`

| Query | 필수 | 기본값/형식 |
|---|---:|---|
| `marketType` | 아니오 | `DOMESTIC` |
| `tradingDate` | 아니오 | 시장 현지 `YYYY-MM-DD`; 없으면 해당 시장 현재 거래일 |

응답은 전략 버전별 `TradingReflection[]`이다. `TradingReflection` 필드:

- `reflectionId`, `tradingDate`, `marketType`, `marketZoneId`, `strategyVersion`
- `dailyReturnRate`, `marketReturnRate`, `totalTransactionCost`, `totalSlippageAmount`
- `decisionCount`, `holdCount`, `blockCount`, `dataComplete`
- `reviews[]`, `overallFeedback`, `improvementPlan`, `createdAt`

각 `TradeReview`에는 종목·시장, Feature·Decision·시장 컨텍스트 ID, 전략 버전, 판단·주문 상태,
기대가·체결가·수량, 비용·통화·출처·슬리피지, 각 이벤트 시각, 완전성, 근거가 포함된다.
필수 연결 또는 실제 비용 데이터가 불완전하면 합성값을 만들지 않고 `503`으로 실패한다.

## 9. 전략 수명주기

### 9.1 Candidate 생성

`POST /api/v1/internal/agent/skills/candidates`

```json
{
  "description": "변동성 필터 강화",
  "skillParameters": {
    "entryRsiMax": 62,
    "minimumVolumeRatio": 1.5
  },
  "createdBy": "hermes-reflection-cron",
  "activate": false
}
```

`description`과 비어 있지 않은 `skillParameters`가 필수다. 호환 경로 `PUT /skills`도 Candidate만
생성하며 `activate=true`는 `400`이다.

`AgentSkillResponse` 필드: `id`, `createdAt`, `statusChangedAt`, `description`, `active`, `status`,
`skillParameters`, `version`, `parentVersion`, `shadowEvaluation`, `statusReason`, `statusChangedBy`.

### 9.2 버전 목록 조회

`GET /api/v1/internal/agent/skills/versions`

`status`를 생략하면 모든 버전을 최신 `version`부터 반환한다. 단일 상태, 쉼표 구분 상태 또는 같은
query parameter 반복을 지원한다.

```text
?status=CANDIDATE
?status=SHADOW
?status=CANDIDATE,SHADOW
?status=CANDIDATE&status=SHADOW
```

```json
[
  {
    "version": 4,
    "status": "SHADOW",
    "parentVersion": 3,
    "createdAt": "2026-07-19T08:00:00Z",
    "statusChangedAt": "2026-07-19T08:01:00Z",
    "shadowEvaluation": {}
  }
]
```

응답 객체에는 위 필드 외에도 전체 `AgentSkillResponse` 필드가 포함된다. Self-Improvement Cron은 새
Candidate를 생성하기 전에 `CANDIDATE,SHADOW`를 조회하고 기존 진행 버전이 있으면 이를 이어서 처리해야
한다. 이 조회는 데이터를 변경하지 않으므로 관리 변경 헤더를 요구하지 않는다.

### 9.3 Shadow, 승격, 거절

각 요청 본문은 동일하다.

```json
{
  "actor": "paper-strategy-operator",
  "reason": "Shadow 표본과 Risk 조건 검토 완료"
}
```

`start`, `evaluate`, `promote`, `reject` 모두 상태 전이 규칙을 서버가 강제한다. Shadow 평가는
요청자가 보낸 성과값이 아니라 위 quote 표본에서 Broker가 계산·저장한 성과만 사용한다. 전략 승격과 Rollback은
현재 PAPER 모드에서만 허용되며 LIVE에서는 차단된다.

### 9.4 성과와 Rollback

`AgentSkillPerformance` 필드: `skillVersion`, `tradeCount`, `evaluationDays`, `winRate`,
`totalReturnRate`, `averageReturnRate`, `averageProfit`, `averageLoss`, `profitLossRatio`, `profitFactor`,
`sharpeRatio`, `maxDrawdown`, `holdAccuracy`, `riskBlockEffect`, `evaluatedAt`.

데이터로 계산할 수 없는 지표는 합성하지 않고 `null`일 수 있다.

Rollback 요청:

```json
{
  "targetVersion": 2,
  "reason": "승인된 성능 저하 대응",
  "approvedBy": "paper-strategy-operator"
}
```

`targetVersion`은 양수이며 `reason`, `approvedBy`는 필수다. Rollback 평가 응답은
`currentVersion`, `previousVersion`, `requiresRollback`, `reason`을 반환한다.

### 9.5 Reset

`POST /api/v1/internal/agent/reset`

기본 설정 `ADMIN_RESET_ENABLED=false`에서는 Controller가 등록되지 않아 `404`다. 활성화하더라도
PAPER 모드에서만 실행 가능하다.

```json
{
  "actor": "paper-operator",
  "reason": "approved test reset",
  "correlationId": "reset-20260719-001",
  "confirmation": "RESET_PAPER_LEARNING_DATA"
}
```

요청 본문의 `actor`, `correlationId`와 감사 헤더도 모두 제공해야 한다. Hermes 자가개선 Cron에는
Reset confirmation과 실행 권한을 제공하지 않는다.

## 10. 운영 모니터링

### 10.1 Cron heartbeat

`POST /api/v1/internal/operations/cron-heartbeats`

```json
{
  "cronName": "domestic-market-context",
  "executionId": "domestic-market-context-20260719T010000Z",
  "phase": "STARTED",
  "expectedNextAt": "2026-07-20T00:00:00Z",
  "message": "analysis started"
}
```

| 필드 | 제약 |
|---|---|
| `cronName` | 필수, 최대 100자 |
| `executionId` | 필수, 최대 100자 |
| `phase` | `STARTED`, `SUCCEEDED`, `FAILED` |
| `expectedNextAt` | 권장. Broker 수신 시각보다 1초~7일 뒤인 실제 다음 Cron 슬롯의 UTC `Instant` |
| `expectedIntervalSeconds` | 호환용. `expectedNextAt`이 없을 때 필수, 60~604800초 |
| `message` | 선택, 최대 1000자 |

응답 `CronHeartbeat`: `cronName`, `executionId`, `phase`, `expectedIntervalSeconds`, `lastStartedAt`,
`lastCompletedAt`, `expectedNextAt`, `message`, `updatedAt`.

`expectedNextAt`을 보내면 Broker는 이를 그대로 누락 판정 기준으로 저장하고 응답의
`expectedIntervalSeconds`는 Broker 수신 시각에서 그 슬롯까지 남은 초로 계산한다. 두 필드를 함께 보내면
`expectedNextAt`이 우선한다. 둘 다 없으면 `400`이다. 같은 실행의 `STARTED`와 완료 heartbeat에는 같은
절대 `expectedNextAt`을 보내야 완료 시각 때문에 다음 슬롯이 밀리지 않는다.

호환용 `expectedIntervalSeconds`를 사용할 때도 명목상 고정 주기가 아니라 heartbeat 전송 시점부터 실제
다음 슬롯까지 남은 초를 보내야 한다. 장중 마지막 실행이면 다음 거래일 첫 슬롯, 금요일 마지막 실행이면
월요일 첫 슬롯까지의 초를 계산한다. `CRON_MISSED`는 저장된 `expectedNextAt + MONITOR_CRON_GRACE`를
현재 Broker UTC 시각이 지난 경우에만 발생한다.

### 10.2 운영 상태

`GET /api/v1/internal/operations/status`

`OperationalStatus`:

- `checkedAt`, `killSwitchEnabled`
- `activeAlerts[]`: `code`, `severity`, `message`, `detectedAt`, `details`
- `sources`: 소스별 `source`, `lastSuccessAt`, `dataFetchedAt`, `complete`, `freshness`,
  `lastFailureAt`, `lastFailureMessage`
- `cronHeartbeats[]`

경보 대상에는 KIS/Naver/OpenDART 장애, Market Context 만료, Kill Switch 변경, 일일 손실 한도,
주문 대사·DB 저장 실패, Cron 누락, 뉴스·Watchlist·Overview stale 상태가 포함된다.

## 11. Hermes 권장 호출 흐름

### 11.1 신규 매수 전

1. `GET /trading/environment`
2. `GET /trading/risk-policy`
3. `GET /market/status`
4. `GET /market/overview`
5. Hermes 분석 후 `POST /internal/trading/market-contexts`
6. `GET /account/portfolio`, 필요 시 뉴스·Fundamentals 조회
7. Feature와 Decision을 Broker DB에 남기는 Cycle 또는 연결된 결정 흐름 실행
8. 고정된 idempotency key로 `POST /market/order`
9. `success`, `status`, `replayed`, `brokerOrderId`, `orderId` 확인

사전 조회는 분석과 빠른 중단을 위한 것이다. 최종 안전 검증은 주문 API 내부에서 모두 다시 수행한다.

### 11.2 재시도 규칙

- 조회 API: 외부 API 특성과 rate limit을 고려한 제한적 backoff 재시도.
- 주문 API: 동일 `idempotencyKey`로만 재시도.
- 취소 API: 동일 `Idempotency-Key` 헤더로만 재시도.
- `UNKNOWN`: 새 주문을 만들지 말고 미체결·Broker 일지·주문 대사 결과 확인.
- `REJECTED`: 원인을 해소하기 전 다른 key로 우회 재주문 금지.
- `502`, `503`, `complete=false`, stale 데이터: 신규 매수 보류.

## 12. 구현상 주의점

- 인증이 없으므로 네트워크 격리가 보안 경계다.
- 요청과 응답 본문 및 헤더는 Broker 감사 로그에 저장되므로 비밀값을 API 본문에 넣지 않는다.
- `TradingLog`에는 account key가 포함될 수 있으므로 `/daily-logs` 접근 범위를 내부 네트워크로 제한한다.
- Watchlist와 뉴스는 하드코딩 Mock fallback을 사용하지 않는다. 공급자 장애 시 실패한다.
- KIS `MOCK` 프로필은 공식 모의투자 환경이며 실전 투자를 의미하지 않는다.
- `OVERSEAS_ORDER_ENABLED` 기본값은 `false`이며, `true`는 KIS Paper 해외 주문만 활성화한다. 실전 해외 주문은 항상 차단된다.
- OpenAPI 자동 문서는 코드 구조를 보여주는 보조 자료이며, 주문 재시도·fail-closed·운영 권한 규칙은
  이 문서를 우선 연동 계약으로 사용한다.
