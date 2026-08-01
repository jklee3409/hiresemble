# AI Provider 활성화

## 실행 모드

일반 개발은 `SPRING_PROFILES_ACTIVE=local`을 사용한다. 이 모드는 OpenAI Chat·Embedding과 Tavily Search를 실제 제품 workflow에 연결한다. key, provider/model 일치, immutable 가격 item, HTTPS endpoint, retry/store/vector-store 정책 중 하나라도 잘못되면 startup이 실패하며 disabled/Fake로 자동 fallback하지 않는다.

네트워크 없이 Backend만 실행하려면 `SPRING_PROFILES_ACTIVE=local-offline`을 명시한다. `test`, `ci`, `e2e`와 P4~P8 actual task는 개발 PC에 key가 있어도 network-disabled system property를 강제한다.

## Secret과 설정

Secret은 환경변수 또는 배포 Secret으로만 주입한다.

```text
SPRING_PROFILES_ACTIVE=local
AI_PROVIDER=openai
AI_CHAT_MODEL_PROVIDER=openai
AI_EMBEDDING_MODEL_PROVIDER=openai
AI_PROVIDER_API_KEY=<secret>
AI_PROVIDER_BASE_URL=https://api.openai.com
AI_PROVIDER_TIMEOUT=60s
AI_PROVIDER_MAX_RETRIES=0
AI_PROVIDER_STORE=false
AI_EMBEDDING_MODEL=text-embedding-3-small
AI_EMBEDDING_DIMENSION=1536
AI_VECTOR_STORE_PROVIDER=none
WEB_SEARCH_PROVIDER=tavily
TAVILY_API_KEY=<secret>
TAVILY_ENDPOINT=https://api.tavily.com/search
ALLOW_INSECURE_PROVIDER_ENDPOINT=false
```

key 값·suffix, prompt/input/raw response, 사용자 문서·답변, embedding vector는 로그·health·test report에 남기지 않는다. Spring AI Chat model 로그는 prompt를 출력할 수 있는 warning 경계를 차단하고 response storage를 끈다.

## 가격과 예산

활성 catalog version은 `2026073101`이다. 2026-07-31 공개 PAYG 기준을 사용한다.

- OpenAI [`gpt-5-mini`](https://developers.openai.com/api/docs/models/gpt-5-mini): input/cached input/output을 1M token 단위의 별도 item으로 둔다.
- OpenAI [`text-embedding-3-small`](https://developers.openai.com/api/docs/models/text-embedding-3-small): input을 1M token 단위로 둔다.
- Tavily [API Credits](https://docs.tavily.com/documentation/api-credits): PAYG 1 credit USD 0.008, BASIC 1 credit, ADVANCED 2 credits를 각각 request item으로 둔다.

가격 변경은 기존 row 수정이 아니라 새 Flyway migration과 price version으로 추가한다. runtime에서 외부 가격을 자동 조회·upsert하지 않는다. local workflow는 기존 async run absolute cap USD 0.30 전액을 접수 전에 예약하고 실제 usage를 item별로 settle한다.

## 일반 제품 실행

PostgreSQL/MinIO를 시작하고 두 key를 주입한 뒤 Backend와 Frontend를 평소처럼 실행한다. 문서 처리, 공고 추출·분석, 자기소개서 생성·검증, 면접 준비·답변 feedback은 기존 API/UI에서 실제 adapter로 연결된다. Provider 장애 시 자동 offline 전환은 없으며 운영자가 원인을 수정하거나 명시적으로 `local-offline`/provider `none`으로 재시작한다.

## Codex bounded live 검증

이 검증은 제품 사용량을 제한하지 않고 구현 검증 호출만 제한한다.

```powershell
$env:CODEX_REAL_PROVIDER_TEST_ENABLED='true'
$env:CODEX_REAL_PROVIDER_TEST_MAX_COST_USD='0.050000'
Set-Location backend
.\gradlew.bat codexRealProviderTest --no-daemon --console=plain
```

synthetic non-PII만 전송하며 정상 실행은 Chat 1, Embedding 1, Search 1회다. adapter/framework/test retry는 0이고 성공 capability는 재실행하지 않는다. 구체적 결함을 수정한 뒤에만 capability별 한 번 더 허용하며 절대 상한은 capability별 2·총 6회다. persistent safe counter는 `backend/.codex-real-provider-call-summary.json`, 복사 report는 `backend/build/reports/codex-real-provider/call-summary.json`이다. 두 파일에는 count·estimated cost·완료 capability만 기록한다.

gate 또는 key가 없으면 task는 호출 없이 skip하며 상태는 `IMPLEMENTED_NOT_LIVE_VERIFIED`다.

## Key rotation과 rollback

key rotation은 Secret을 교체하고 애플리케이션을 재시작한다. 장애 시 자동 Fake fallback 대신 provider를 `none`으로 두거나 offline profile로 재시작한다. test·CI·E2E 경로에는 영향이 없다. 가격 변경은 새 immutable catalog version과 migration을 먼저 배포하고 그 version을 workflow 설정에 함께 적용한다.
