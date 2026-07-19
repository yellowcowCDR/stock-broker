# Broker 운영 모니터링

## 엔드포인트

- `GET /actuator/health`: DB와 `brokerOperational` Health 상태
- `GET /actuator/metrics`: Micrometer 진단 지표
- `GET /actuator/prometheus`: Prometheus scrape
- `GET /actuator/scheduledtasks`: Broker 내부 Scheduler 목록
- `GET /api/v1/internal/operations/status`: 활성 경보, 공급자 상태, Kill Switch, Cron heartbeat 근거

모든 엔드포인트는 Broker의 `127.0.0.1:8080` 또는 전용 Docker 네트워크 안에서만 사용합니다.
외부 인터넷에 공개하지 않습니다.

## Hermes Cron heartbeat

Hermes Cron은 실행 시 `STARTED`, 정상 종료 시 `SUCCEEDED`, 실패 시 `FAILED`를 전송합니다.
`occurredAt`은 받지 않으며 Broker가 수신한 UTC 시각을 Source of Truth로 저장합니다.

```json
{
  "cronName": "domestic-market-analysis",
  "executionId": "20260719-DOMESTIC-MARKET-001",
  "phase": "STARTED",
  "expectedNextAt": "2026-07-20T00:00:00Z",
  "message": "cycle started"
}
```

```http
POST /api/v1/internal/operations/cron-heartbeats
Content-Type: application/json
```

`expectedNextAt`은 고정 주기가 아니라 실제 다음 Cron 슬롯의 UTC 시각입니다. 장중 마지막 실행은 다음
거래일 첫 슬롯, 금요일 마지막 실행은 월요일 첫 슬롯을 지정합니다. 같은 `executionId`로 완료 heartbeat를
보낼 때도 STARTED와 같은 `expectedNextAt`을 사용합니다. 한 번 이상 등록된 Cron이
`expectedNextAt + MONITOR_CRON_GRACE`까지 새 heartbeat를 보내지 않으면 `CRON_MISSED`가
발생합니다. 등록되지 않은 이름은 스케줄을 추측하지 않으므로 감시할 수 없습니다.

기존 클라이언트는 `expectedNextAt` 대신 `expectedIntervalSeconds`를 보낼 수 있지만, 이 값 역시 명목상
고정 주기가 아니라 heartbeat 시점부터 실제 다음 슬롯까지 남은 초여야 합니다. 신규 연동은 시간 계산
오차와 완료 시각 drift를 피하기 위해 `expectedNextAt`을 사용합니다.

## 감지 항목

- KIS, Naver, OpenDART, Alpha Vantage 호출 성공/실패
- News, Watchlist, Market Overview stale 또는 incomplete
- Market Context 없음, 만료, 만료 임박
- Kill Switch 상태 및 재시작 전후 변경
- 일일 손실 한도 차단
- KIS 주문 상태/비용 대사 실패
- Persistence save/delete 실패
- Broker 내부 Scheduler 실패
- Hermes Cron 실패 또는 heartbeat 누락

## 경보 전달

Broker는 상태와 지표를 생성하지만 이메일·Slack 같은 외부 채널로 직접 메시지를 보내지 않습니다.
Prometheus/Alertmanager가 [경보 규칙](prometheus-alert-rules.yml)을 읽어 운영 채널로 전달하도록
구성합니다. 외부 경보 시스템이 없어도 `/api/v1/internal/operations/status`와
`/actuator/health`에서 동일 상태를 확인할 수 있습니다.

## Reset 격리

`ADMIN_RESET_ENABLED=false`가 기본이며 이 상태에서는 `/api/v1/internal/agent/reset`
Controller가 생성되지 않습니다. PAPER 운영자가 유지보수 시간에만 일시적으로 활성화하고,
요청의 actor/reason/correlationId/confirmation을 남긴 뒤 다시 비활성화합니다. LIVE 모드에서는
서비스 계층이 추가로 차단합니다. Hermes Cron 설정에는 `ADMIN_RESET_ENABLED`나 Reset 확인문을
제공하지 않습니다.

`/api/v1/internal/agent/**`와 `/api/v1/internal/trading/**`의 POST/PUT/PATCH/DELETE 요청은
인증과 별개로 `X-Actor`와 `X-Correlation-ID` 헤더가 필수입니다. 기존 API 감사 필터가 헤더와 요청 본문, 응답 상태를 DB에
기록합니다. 이 헤더는 신원 인증 수단이 아니며 내부 호출의 추적성과 책임 주체 기록을 위한 값입니다.
