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
AI_PROVIDER_BASE_URL=https://api.openai.com/v1
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

## P8.5-V 사용자 local 검증

2026-08-01 초기 bounded smoke 결과는 Chat 2회 시도(첫 호출은 `/v1` 누락 404, 보정 후 `429 insufficient_quota`), Embedding 1회 시도(`429 insufficient_quota`), Tavily BASIC 1회 성공이었다. 이후 실제 문서 실행에서는 parse·mask·chunk·Embedding이 성공하고 Chat endpoint에 도달했지만 `EXTRACT_EVIDENCE_CANDIDATES` strict schema 요청이 거절됐다. 당시 raw Provider error code·param·request ID가 영구 보존되지 않아 직접 확정할 수는 없지만, 수정 전 runtime schema의 bare `metadata` object는 OpenAI strict subset 검사에서 재현 가능하게 실패했다. Provider output을 제한된 scalar entry 배열로 바꾸고 전체 schema·실제 SDK 요청 payload를 offline 검증했으며 수정 뒤 실제 Provider 호출은 0회다.

현재 판정은 Embedding 연결 증거만 있고 Chat capability와 문서 수직 흐름은 재검증되지 않은 `IMPLEMENTED_NOT_LIVE_VERIFIED`다. 일반 `local` profile에서 다음 순서로 검증한다.

1. capability smoke: Chat 1회, Embedding 1회, Tavily BASIC 1회.
2. 문서 업로드→실제 embedding→근거 추출.
3. 공고 등록→실제 공고 추출→공고 분석.
4. 자기소개서 생성→검증.
5. 면접 준비→Tavily 조사→질문 생성→답변 feedback.

검증 기록에는 기능 성공 여부, safe error code, request ID, Agent Run ID, usage/cost 합계만 남긴다. key·prompt·response·문서/자소서/면접 답변 원문을 복사하지 않는다. 연결 성공과 결과 품질을 별도로 판정한다.

- capability smoke 성공: `LOCAL_CAPABILITY_VERIFIED`
- P4~P8 수직 흐름 성공: `LOCAL_VERTICAL_VERIFIED`
- 두 범위와 민감정보 없는 기록 완료: P8.5 `DONE`

strict schema 호환성 수정 작업에서는 실제 Provider를 호출하지 않았다. P8.6 기능 한도가 구현된 뒤에는 실제 제품 UAT도 해당 feature usage event를 정상 생성하며, 같은 idempotency replay는 추가 소비하지 않아야 한다.

## Codex bounded adapter smoke

이 검증은 제품 사용량을 제한하지 않고 구현 검증 호출만 제한한다.

```powershell
$env:CODEX_REAL_PROVIDER_TEST_ENABLED='true'
$env:CODEX_REAL_PROVIDER_TEST_MAX_COST_USD='0.050000'
Set-Location backend
.\gradlew.bat codexRealProviderTest --no-daemon --console=plain
```

synthetic non-PII만 전송하며 정상 실행은 Chat 1, Embedding 1, Search 1회다. adapter/framework/test retry는 0이고 성공 capability는 재실행하지 않는다. 구체적 결함을 수정한 뒤에만 capability별 한 번 더 허용하며 절대 상한은 capability별 2·총 6회다. persistent safe counter는 `backend/.codex-real-provider-call-summary.json`, 복사 report는 `backend/build/reports/codex-real-provider/call-summary.json`이다. 두 파일에는 count·estimated cost·완료 capability만 기록한다.

gate 또는 key가 없으면 task는 호출 없이 skip하며 상태는 `IMPLEMENTED_NOT_LIVE_VERIFIED`다. 이 adapter smoke만으로 P4~P8 수직 검증 완료를 주장하지 않는다.

strict Chat 보정만 재검증할 때는 전체 task 대신 기존 capability별 task를 딱 한 번 실행한다.

```powershell
Set-Location backend
.\gradlew.bat codexRealOpenAiChatTest --no-daemon --console=plain
```

성공 뒤 같은 task를 반복하지 않고 일반 UI에서 문서 ingestion을 한 번 수행한다. 기록은 safe error code·request ID·Agent Run ID·usage/cost 합계로 제한하며, Chat capability 성공과 `EXTRACT_EVIDENCE_CANDIDATES`를 포함한 문서 vertical 성공을 별도로 판정한다.

## Key rotation과 rollback

key rotation은 Secret을 교체하고 애플리케이션을 재시작한다. 장애 시 자동 Fake fallback 대신 provider를 `none`으로 두거나 offline profile로 재시작한다. test·CI·E2E 경로에는 영향이 없다. 가격 변경은 새 immutable catalog version과 migration을 먼저 배포하고 그 version을 workflow 설정에 함께 적용한다.
