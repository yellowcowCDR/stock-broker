# KIS 오픈 API 모의투자 및 실전투자 연동 가이드

본 문서는 `hermes-trading-broker` 시스템 내에서 한국투자증권(KIS) 오픈 API를 안전하고 안정적으로 연동하기 위해 구현된 프로파일 분리, Rate Limit, 캐싱 전략 및 안전 장치(Kill-Switch)에 대한 설계와 사용 방법을 설명합니다.

---

## 1. 개요
KIS 오픈 API는 모의투자와 실전투자 환경에 따라 API 호출 엔드포인트(Base URL), 앱 키(App Key), 초당 요청 제한(Rate Limit) 등 운영 정책이 다릅니다. 이를 하나의 소스 코드로 유연하게 대응하고, 장애나 운영 실수로 인한 대규모 주문 사고를 방지하기 위해 다음과 같은 구조를 채택하였습니다.

*   **설정 격리**: Spring Profile (`mock`, `prod`)을 사용하여 환경 간 설정 충돌 방지
*   **Rate Limit 중앙 통제**: `Resilience4j` 라이브러리를 통한 API 호출 빈도 제어
*   **스마트 재시도(Retry)**: 네트워크 지연 등 일시적 장애 대응을 위한 조회(GET) 요청 지수 백오프 재시도
*   **캐시 분리**: `@Cacheable` 키에 환경(`KisEnvironment`) 변수를 추가하여 프로파일 간 데이터 오염 방지
*   **실전 투자 안전장치(Kill-Switch)**: 오동작 시 즉시 주문을 차단할 수 있는 설정 기반의 안전망 구현

---

## 2. 프로파일 설정 구조

### 2.1 Configuration Properties
모든 KIS API 관련 속성은 타입-세이프를 보장하기 위해 `KisProperties`와 `TradingProperties` 레코드 클래스에 매핑됩니다. 단순 `@Value` 어노테이션 사용을 지양하여 구조적이고 안전한 설정 로드를 지원합니다.

*   `kis.*`: API 인증 정보, Base URL, Rate Limit, Retry 임계값 등
*   `trading.*`: 주문 허용 여부(real-order), 킬스위치(kill-switch) 등 운영 제어 속성

### 2.2 application.yml
공통 환경 설정과 함께 실전 투자를 보수적으로 제한하기 위해, 기본값으로 실제 주문을 차단하고 킬 스위치를 켜 둡니다.
```yaml
kis:
  rate-limit:
    enabled: true
trading:
  real-order:
    enabled: false
  kill-switch:
    enabled: true
```

### 2.3 application-mock.yml (모의투자)
```yaml
kis:
  environment: MOCK
  api:
    base-url: "https://openapivts.koreainvestment.com:29443"
    # 모의투자 AppKey 및 AppSecret 설정
  rate-limit:
    requests-per-second: 1
    minimum-interval: 1100ms # 초당 1건 수준
```

### 2.4 application-prod.yml (실전투자)
```yaml
kis:
  environment: PRODUCTION
  production-rate-limit-type: NEW_APPLICANT # 또는 GENERAL
  api:
    base-url: "https://openapi.koreainvestment.com:9443"
    # 실전투자 계좌 정보 및 키 설정
  rate-limit:
    # 계좌 등급에 따라 조정 (NEW_APPLICANT는 초당 3건, 400ms interval 등)
```

---

## 3. 핵심 아키텍처

### 3.1 어플리케이션 구동 검증 (`KisConfigurationValidator`)
어플리케이션이 시작될 때(`@PostConstruct`) 현재 활성화된 Spring Profile과 `KisProperties`의 값이 일치하는지, 필수 값(Base URL, Account No 등)이 정상적으로 입력되었는지 검사합니다. 설정이 잘못된 경우 서버 기동을 즉시 중지(`IllegalStateException`)시킵니다.

### 3.2 Rate Limit 및 Retry 적용 (`KisRestClientInterceptor`)
*   모든 API 요청은 `RestClient`의 인터셉터를 통과하게 됩니다.
*   **Rate Limit**: `Resilience4jKisRateLimitCoordinator`를 호출하여 현재 환경에 맞는 허용량(Permit)을 얻을 때까지 대기합니다. (예: 모의투자의 경우 1.1초 간격 보장)
*   **Retry**: `GET` 요청(시세 조회, 포트폴리오 조회 등)에 한해 실패 시 `Resilience4j`의 지수 백오프(Exponential Backoff) 및 지터(Jitter)를 활용하여 재시도합니다.
*   **주문(POST) 요청 보호**: `POST` 등 상태를 변경하는 주문 요청은 중복 주문 방지를 위해 재시도(Retry) 대상에서 제외합니다.

### 3.3 캐시 격리
*   `MarketTimeValidator`와 같은 컴포넌트에서 사용하는 `@Cacheable` 키는 `mock` 환경과 `prod` 환경에서 동일하게 사용될 경우 문제가 발생할 수 있습니다.
*   이를 방지하기 위해 캐시 키 생성 시 현재 환경의 이름을 접두어로 사용합니다.
    (예: `@Cacheable(value = "marketStatusCache", key = "#root.target.getEnvironmentName() + ':domestic'")`)

### 3.4 토큰 격리 (`KisTokenManager`)
*   KIS 접근 토큰(Access Token) 발급 결과 역시 내부적으로 `ConcurrentHashMap`을 통해 캐싱됩니다.
*   동일한 어플리케이션 내에서도 `AppKey`와 `Environment` 해시를 조합한 고유한 `CacheKey`를 생성하여 환경 간 토큰이 꼬이는 문제를 방지합니다.

---

## 4. 실전 투자 안전 장치 (Kill-Switch)

의도치 않은 대량 주문, 알고리즘 오류 등 장애 상황을 방지하기 위해 `KisDomesticTradingAdapter`와 `KisOverseasTradingAdapter` 내부에 2중 안전 장치가 구현되어 있습니다.

1.  **`trading.real-order.enabled`**: 이 값이 `true`일 때만 실전 투자가 실행됩니다. (`mock` 환경에서는 무조건 주문 불가)
2.  **`trading.kill-switch.enabled`**: 이 값이 `true`이면 `real-order`가 켜져 있어도 즉각 주문을 차단합니다. 긴급 상황 발생 시 재배포 없이 설정(Config Server 등)만 변경하여 시장 진입을 막을 수 있습니다.

```java
private void validateOrderSafety() {
    if (kisProperties.environment() == KisEnvironment.PRODUCTION) {
        boolean realOrderEnabled = tradingProperties.realOrder() != null && tradingProperties.realOrder().enabled();
        boolean killSwitchEnabled = tradingProperties.killSwitch() == null || tradingProperties.killSwitch().enabled();

        if (!realOrderEnabled) {
            throw new IllegalStateException("Real orders are disabled in configuration.");
        }
        if (killSwitchEnabled) {
            throw new IllegalStateException("Kill switch is active. Real orders are blocked.");
        }
    }
}
```

---

## 5. 관련 파일

*   `com.hermes.broker.common.property.KisProperties`
*   `com.hermes.broker.common.property.TradingProperties`
*   `com.hermes.broker.common.config.KisConfigurationValidator`
*   `com.hermes.broker.market.adapter.out.external.interceptor.KisRestClientInterceptor`
*   `com.hermes.broker.market.adapter.out.external.ratelimit.Resilience4jKisRateLimitCoordinator`
*   `com.hermes.broker.market.adapter.out.external.KisDomesticTradingAdapter`
*   `com.hermes.broker.market.adapter.out.external.KisOverseasTradingAdapter`
