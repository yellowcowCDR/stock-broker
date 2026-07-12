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
4. **시장 운영 시간 검증:** KIS 휴장일 API 연동 및 로컬 캐싱을 통한 안전한 주문 시간(09:00 ~ 15:30) 검증
5. **장 마감 일괄 정산 배치:** 매일 오후 4시, 당일 거래 내역 및 수익률을 정산하여 데일리 리포트 데이터 생성

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

### 2. 로컬 서버 실행
```bash
# Spring Boot 프로젝트 빌드 및 실행
./gradlew bootRun
```

## 🐳 Deployment (Docker & CI/CD)

본 프로젝트는 Docker 및 GitHub Actions 기반의 자동화된 무중단 배포를 지원합니다.

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
