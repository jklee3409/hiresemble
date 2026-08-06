# Progress

## Overview

다섯 P0 승인 명세를 연결한 전체 시스템 설계와 단계별 구현 계획, 승인 결정 기록이 작성되어 있다. P0–P8은 완료됐고 P8.5의 Chat strict output부터 문서 finalize까지 live 증거가 있으나 terminal 보정은 live 재검증 전이다. P8.5-V–P8.9-A가 P9의 선행이며 P10은 사용자 설정, 운영 안정성, 출시 준비로 분리돼 있다. 이 디렉터리는 코드 진행 문서를 대신하지 않는다.

## [2026-08-06] Session Summary (자기소개서 OpenAI 모델 선택·품질 개선 보고서)

- What was done: 공식 모델·가격·폐기 문서와 현재 구조를 대조해 안정성 판단, 구현 구조, 초안 품질 개선 로드맵을 보고서로 작성했다.
- Key decisions: 4.5 preview는 폐기되어 제외하고 5.6~5 계열 10개 exact model ID를 허용한다.
- Issues encountered: 계정별 entitlement는 정적 문서만으로 보장할 수 없어 live smoke test 후속으로 남겼다.
- Validation: 공식 OpenAI 문서 링크와 코드·명세·테스트 결과를 교차 확인했다.
- Next steps: golden set·judge rubric·A/B와 모델별 비용/품질 지표를 운영에 도입한다.

## [2026-08-06] Session Summary (외부 디자인 도구 참조 제거)

- What was done:
  - 공고 분석 HTML 가이드의 외부 디자인 도구 전용 `design-system` 조건과 저장소 context 참조를 제거하고 제품 디자인 규칙 자체만 남겼다.
  - 과거 Session Summary는 수행 사실을 보존하면서 특정 도구명과 삭제된 경로를 일반 표현으로 정리했다.
- Key decisions:
  - 앞으로 이 저장소의 UI 계약과 구현 근거는 활성 명세·HTML 가이드·Vue 코드에만 둔다.
- Issues encountered:
  - `job-analysis-page-design-guide.html` 단독 Prettier 검사는 기존 문서 전체의 수기 formatting 때문에 실패했다. 관련 없는 대량 HTML 재포맷은 수행하지 않았다.
- Validation:
  - 저장소 전체 대소문자 무시 검색에서 제거 대상 도구명 0건, `git diff --check` 통과를 확인했다. HTML 단독 Prettier 실패 범위는 위에 기록했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 디자인 가이드 우선 계약 확정)

- What was done:
  - 사용자가 과거 외부 디자인 도구의 저장소 context 삭제 의도와 `job-analysis-page-design-guide.html` 우선 적용을 확인했다.
  - 모바일 104px 단일 gauge, 커버리지 meta, 요약 tile 제거와 primary CTA 우선순위를 실제 Vue·명세·E2E에 반영했다.
- Key decisions:
  - 활성 페이지 명세와 가이드가 충돌한 모바일 판단 영역은 사용자 승인에 따라 가이드 계약으로 명세를 갱신했다.
- Issues encountered:
  - 기존 E2E가 과거 제목 크기와 세 metric의 844px 배치를 고정하고 있어 새 responsive 계약과 충돌했다.
- Validation:
  - Frontend check 67 files/282 tests·production build와 Job Analysis·visual fixture Chromium 2/2가 통과했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 디자인 가이드 구현 반영)

- What was done:
  - 가이드를 `frontend/`에 적용했다. 변경 파일은 `src/styles/main.css`, `src/shared/ui/AppIcon.vue`, `src/pages/JobAnalysisPage.vue`, `src/features/jobs/JobPreparationJourney.vue`, `src/layouts/JobDetailLayout.vue`다.
  - 가이드 12장의 외부 디자인 도구 전용 문서 갱신 지시를 조건부 문구로 바꿨다. 해당 파일이 저장소에서 삭제 staged 상태이기 때문이며, 예외 근거는 이 문서와 가이드에 남긴다.
- Key decisions:
  - 가이드의 `.hs-*` class 이름은 예시로 취급하고 실제 구현에서는 기존 `analysis-*` 이름을 유지했다. 화면 계약과 테스트 단언을 보존하는 최소 변경 원칙에 따른 결정이다.
- Issues encountered:
  - 이번 세션 중 과거 외부 디자인 도구 context 7개 파일이 작업 트리에서 사라지고 삭제가 index에 staged됐다. 이 작업에서 수행한 변경이 아니며 되돌리지 않았다.
- Validation:
  - frontend lint·format·typecheck·build 통과. `vitest`는 Node 버전 제약으로 미실행이다. 상세는 `frontend/src/pages/progress.md`에 있다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 페이지 디자인 개편 가이드 작성)

- What was done:
  - `job-analysis-page-design-guide.html`을 신규 작성했다. 12개 장으로 현재 화면 감사, 토스 레퍼런스 실측·번역, 추가 토큰 8개와 차트 팔레트 6개, 자체 제작 SVG 아이콘 18종, `JobAnalysisDetailDto` 필드별 UI 매핑, 자동 분석 상태 전이 다이어그램과 렌더 분기 우선순위, 동작하는 데스크톱·모바일 시안, 차트 4종 규격, 모션·접근성 규칙, 구현 체크리스트를 담았다.
  - 결과 화면을 카드 8개 적층에서 단일 리포트 패널로 재구성하고, 적합도 2중 링 게이지·요건 100% 누적 막대·카테고리 가로 막대·이력 추이 라인 4개 차트를 규격화했다.
- Key decisions:
  - 기존 `main.css` 토큰은 수정·삭제하지 않고 additive 8개만 추가한다. 결과 화면 primary CTA는 `자기소개서 준비하기` 하나로 고정하고 재분석은 quiet 버튼으로 낮춘다.
  - 차트 마크 색은 기존 semantic 토큰을 재사용하지 않고 별도 `--chart-*` 4색을 정의했다. 텍스트·아이콘에는 기존 semantic 토큰을 계속 쓴다.
  - 당시 외부 디자인 도구의 gradient 금지 규칙에 대해 결정 히어로 배경 blob과 추이 차트 면 채움 두 곳만 예외로 명시했다. 사용자 지정 레퍼런스 반영이 근거다.
- Issues encountered:
  - 기존 semantic 조합 `#147253/#8a5a08/#b4232d/#667085`은 색각 검증에서 적–주황 쌍 deutan ΔE 1.7로 실패했다. 차트 마크로 사용할 수 없어 별도 팔레트를 산출했다.
  - 채택 팔레트 `#12855f/#a16207/#a01b3c/#8792a6`은 일반 시각 ΔE 16.2·surface 대비 3:1 통과이나 CVD 최악 쌍 ΔE 7.9로 WARN 구간이다. 아이콘·한글 라벨·2px 간격·텍스처 4중 2차 인코딩을 필수 조건으로 문서에 고정했다.
- Validation:
  - OKLab 6검사 팔레트 검증 스크립트를 `--pairs all`, light, surface `#ffffff`로 실행해 기존 조합의 FAIL과 채택 조합의 결과를 확인했다.
  - 브라우저에서 문서를 렌더해 12개 장, 아이콘 참조 104건 전부 해석됨(끊긴 참조 0), 좁은 폭에서 body 가로 스크롤 없음을 확인했다. 데스크톱 grid는 media query 강제 override로 렌더 확인했다.
  - 프론트엔드 코드 변경이 없으므로 `corepack pnpm check`는 실행하지 않았다.
- Next steps:
  - 구현 시 12장 순서대로 진행하고 `frontend/` 하위 `progress.md`와 이 가이드의 예외 기록을 같은 작업에서 갱신한다.

## [2026-08-04] Session Summary (외부 reference 기반 Landing·분석 결과 visual 방향)

- What was done:
  - UI/UX 재설계 메모에 Codle의 큰 value hero·long-form rhythm, Pure Global의 dark transition·bold CTA, FLEX Study의 immersive glow·path 분기에서 선택한 방향과 Hiresemble 적용 원칙을 기록했다.
- Key decisions:
  - blocking loader·검증되지 않은 통계·무거운 3D asset은 제외하고 blue token, 작은 motion, 실제 데이터 chart와 명확한 report hierarchy로 번역한다.
- Issues encountered:
  - 인앱 Browser가 0개여서 Playwright CLI 실제 Chromium fallback을 사용했고 확인한 scroll 장면과 3/5초 반복 animation을 설계 메모에 기록했다.
- Validation:
  - 세 reference의 실제 Chromium snapshot·full-page/scroll capture·animation 상태, page 명세, Vue 구현과 로컬 desktop/mobile 캡처를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 결과 화면 재감사)

- What was done:
  - UI/UX 재설계 메모에 공고 분석 화면의 사용자 목적, 우선 정보, primary action, 기존 카드·색면 문제, 통합 대상과 반응형 밀도 결정을 기록했다.
- Key decisions:
  - 분석 화면을 통계 dashboard가 아니라 지원 판단 report로 보고 결과 요약부터 조건 근거까지 한 흐름으로 연결한다.
- Issues encountered:
  - 변경 후 Browser visual 검증은 unavailable로 미실행이다.
- Validation:
  - 활성 page 명세와 Vue 구조를 대조하고 Frontend 전체 check를 통과했다.
- Next steps:
  - 후속 visual 회귀에서 report section 간격과 mobile 밀도를 확인한다.

## [2026-08-03] Session Summary (Job Analysis source normalization·retry 설계 동기화)

- What was done:
  - 시스템 아키텍처에 source-only extraction, server canonical normalization, 단계별 prompt identity와 semantic/transport retry·guidance 보존을 반영했다.
- Key decisions:
  - workflow version·공개 API·DB를 일괄 변경하지 않고 schema/prompt/canonical downstream hash로 무효화 범위를 결정한다.
- Issues encountered:
  - None.
- Validation:
  - 구현·contract와 문서 표현을 대조했고 Backend 전체 check 통과.
- Next steps:
  - 실제 Provider 검증 결과는 live gate 기록으로 분리한다.

## [2026-08-02] Session Summary (구조화 profile fact 분석 경계)

- What was done:
  - 시스템 아키텍처에 profile structured fact allowlist, 별도 provenance, support compatibility와 hash 무효화 흐름을 기록했다.
- Key decisions:
  - 8단계 workflow와 기존 evidence provenance는 유지하고 서버 검증 경계만 확장한다.
- Issues encountered:
  - None.
- Validation:
  - 활성 spec 및 실제 구현과 설계 설명을 대조했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Chat·embedding routing 경계 분리)

- What was done:
  - 시스템 아키텍처에 ModelRouter의 Chat·image text 책임과 immutable embedding policy의 vector retrieval route 책임을 분리해 기록했다.
- Key decisions:
  - retrieval checkpoint는 embedding provider·product·dimension·version·generation identity를 포함한다.
- Issues encountered:
  - None.
- Validation:
  - Job Analysis·Cover Letter executor와 active policy adapter의 실제 구현을 기준으로 문서를 검토했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (image reference 직렬화 경계 보정)

- What was done:
  - system architecture에 Provider-visible reference text와 단일 image content part 결합 경계를 반영했다.
- Key decisions:
  - Media 내부 metadata 대신 실제 wire request를 식별 계약의 검증 기준으로 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Spring AI 2.0 serializer 동작과 native OpenAI SDK request capture test를 대조했다.
- Next steps:
  - Spring AI 의존성 갱신 시 같은 wire-level 회귀를 실행한다.

## [2026-08-02] Session Summary (V18 반영 후 tentative migration 재배치)

- What was done:
  - Dashboard Career Guide V18 사용에 맞춰 P8.6·P8.7·P8.9-A·P9 예상 migration을 V19~V22로 재배치했다.
- Key decisions:
  - 미래 번호는 tentative이며 각 phase 착수 시 latest migration을 다시 확인한다.
- Issues encountered:
  - None.
- Validation:
  - DB 명세, migration index와 implementation plan의 번호를 교차 확인했다.
- Next steps:
  - P8.6 착수 전 V19 사용 가능 여부를 재검증한다.

## [2026-08-02] Session Summary (전반 UI/UX·공고 자동 분석 설계)

- What was done:
  - 현재 UI 감사, 내비게이션 대안, 토큰·page variant·wire 구성, V16 durable orchestration과 guide 방식을 설계 메모·architecture·implementation plan에 반영했다.
- Key decisions:
  - desktop 상단 navigation·mobile bottom navigation, 실제 component 기반 guide와 backend-owned BALANCED auto chain을 채택했다.
- Issues encountered:
  - 과거 tentative V16 문서 번호를 V17 이후로 이동해 실제 migration과 충돌하지 않게 했다.
- Validation:
  - 구현 diff, 명세, 생성 OpenAPI와 Browser fixture 결과를 대조했다.
- Next steps:
  - 별도 운영 기능의 tentative migration은 V17부터 사용한다.

## [2026-08-01] Session Summary (Job extraction v3 시스템 설계)

- What was done:
  - system architecture와 implementation plan에 trusted mapping, shared failure, retry contributor, WebP, aggregate를 기록했다.
- Key decisions:
  - v1·v2는 legacy, v3만 executable이며 DB/API baseline은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Backend/Frontend/P5 actual 검증 결과와 실제 Provider 호출 0회를 동기화했다.
- Next steps:
  - animated WebP와 image PDF OCR은 제외 상태를 유지한다.

## [2026-08-01] Session Summary (Job extraction v2 시스템 설계)

- What was done:
  - fetch/decode→inspect→image extraction→source compose→structured fields→validation 흐름과 구현 완료 항목을 추가했다.
- Key decisions:
  - V15 latest와 P8.6 tentative V16 이후 번호를 유지하고 raw HTML/image bytes는 영속화하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend/Frontend 전체 검증과 P5 actual 결과를 구현 계획에 동기화했다.
- Next steps:
  - WebP와 live Provider 검증은 후속 검토다.

## [2026-08-01] Session Summary (V15 추가 작업과 후속 migration 재배치)

- What was done:
  - 구현 계획과 운영 계약의 tentative migration 번호를 V16~V19로 재배치하고 V15 사용자 대외활동을 별도 추가 개선으로 기록했다.
- Key decisions:
  - 직접 등록 활동은 문서 workflow 결과가 아니라 profile-owned aggregate와 direct evidence projection으로 설계했다.
- Issues encountered:
  - None.
- Validation:
  - DB 명세와 migration index, 후속 단계 문서의 번호를 교차 확인했다.
- Next steps:
  - 후속 단계 시작 전에 최신 migration 번호를 다시 확인한다.

## [2026-08-01] Session Summary (workflow-owned terminal partial 설계)

- What was done:
  - 공용 Orchestrator에서 workflow별 partial terminal 결정을 분리하고 문서 filtering과 자기소개서 scope failure의 상태 전이를 설계에 고정했다.
- Key decisions:
  - Interview `LIMITED|NONE`과 문서 rejection은 정상 성공 결과이며 실제 failed scope만 partial terminal policy의 대상이다.
- Issues encountered:
  - terminal 분류 수정 뒤 live run은 수행하지 않았다.
- Validation:
  - 68 suites/466 tests와 시스템 아키텍처·구현 계획을 대조했다.
- Next steps:
  - P8.5-V에서 문서 terminal만 bounded 재검증한다.

## [2026-08-01] Session Summary (trusted output 경계와 최신 live 실패)

- What was done:
  - strict boundary를 parse→shape→binding→record→workflow→trusted mapper→domain으로 구체화하고 latest semantic failure 증거를 반영했다.
- Key decisions:
  - server-owned identifier·사용처 없는 Provider metadata를 제거하고 repair guidance가 있는 의미 오류만 2 attempt를 허용한다.
- Issues encountered:
  - run `26f9b3d0-3bf7-4587-b2f7-938e8d8e045d`의 exact invalid field와 finish reason은 미확정이다.
- Validation:
  - 설계와 68 suites/459 tests, API/migration 불변을 대조했다.
- Next steps:
  - P8.5-V bounded live gate를 유지한다.

## [2026-08-01] Session Summary (strict Provider output 경계와 live 상태 반영)

- What was done:
  - schema 생성→중앙 검증→Gateway→workflow/domain mapping 구조와 문서 실행의 Embedding 성공·Chat strict 실패 사실을 반영했다.
- Key decisions:
  - offline 수정 완료와 live capability/vertical 성공을 분리하고 P8.5-V gate를 유지한다.
- Issues encountered:
  - 과거 raw error metadata 부재로 원인 확실성은 `HIGH_CONFIDENCE`다.
- Validation:
  - 구현 계획 기준선 68 suites/452 tests와 실제 Provider 수정 후 0회를 코드 기록과 대조했다.
- Next steps:
  - Chat capability와 document vertical 각 1회 후 상태를 갱신한다.

## [2026-08-01] Session Summary (V14와 Provider smoke 상태 반영)

- What was done:
  - 구현 계획·아키텍처·결정 기록에 V14와 Chat/Embedding quota 실패, Tavily 성공을 반영했다.
- Key decisions:
  - P8.6 이후 tentative migration을 V15부터로 이동했다.
- Issues encountered:
  - OpenAI capability 성공 전이므로 P8.5 상태는 유지한다.
- Validation:
  - phase dependency와 V14~V18 번호 참조를 대조했다.
- Next steps:
  - quota 복구 후 capability와 vertical 검증 상태를 갱신한다.

## [2026-08-01] Session Summary (P9 이전 운영 기반 설계 재구성)

- What was done:
  - 구현 계획과 시스템 아키텍처를 P8.5-V, P8.6, P8.7, P8.8, P8.9-A/B, P9, P10-A/B/C 단계로 재구성했다.
  - budget·quota·usage·payment 분리, 실패 UX taxonomy, Backoffice 범위와 채택·기각 대안을 별도 결정 문서로 고정했다.
- Key decisions:
  - billing usage는 중복 ledger 대신 feature usage event에 immutable policy snapshot을 두고 SQL read model부터 시작한다.
  - P8.9-A만 P9의 선행으로 두며 P8.9-B mutation은 별도 후속 단계다.
- Issues encountered:
  - P8.5 실제 capability와 P4~P8 vertical flow는 호출 기록이 0회라 검증 완료로 판정할 수 없었다.
- Validation:
  - phase별 책임·상태·migration tentative 표기와 문서 링크를 자체 검토하고 Markdown·diff 검사를 통과했다.
- Next steps:
  - P8.5-V 사용자 검증과 P8.6 구현을 각각 명시된 handoff에 따라 진행한다.

## [2026-08-01] Session Summary (P8.5 구현 계획 게이트)

- What was done:
  - P8과 P9 사이에 외부 Provider 연결·로컬 활성화 게이트를 공식 추가했다.
- Key decisions:
  - Fake/WireMock와 full regression 완료 후에도 key 기반 bounded live verification 전에는 `IMPLEMENTED_NOT_LIVE_VERIFIED`로 판정한다.
- Issues encountered:
  - None.
- Validation:
  - 구현·profile·test matrix와 계획 완료 조건을 대조했다.
- Next steps:
  - P8.5 DONE 판정 전 bounded live verification이 필요하다.

## [2026-07-31] Session Summary (P8 구현·final-source 검증 상태 반영)

- What was done:
  - 구현 계획에 V12·11 API·두 실행 workflow·Frontend 세 route와 P8/P7/P6 actual 증거를 반영했다.
  - 1차 self-audit의 `FOLLOW_UP` output·foreign owner 404 finding과 한 번의 제한 보정 및 전체 재검증을 기록했다.
  - 두 번째 self-audit의 finding 없는 `PASS`와 fingerprint 불변을 반영해 P8 완료 상태를 확정했다.
- Key decisions:
  - 독립 validator를 사용한 것으로 기록하지 않고 이번 단계의 최종 판정은 single-agent read-only self-audit로 명시한다.
- Issues encountered:
  - 1차의 두 finding은 허용된 한 번의 제한 보정으로 해소됐고 두 번째 감사에서는 새 finding이 없었다.
- Validation:
  - 제한 보정 후 Backend 61 suites/407 tests, Frontend 60 files/238 tests, OpenAPI 63/84와 P8/P7/P6 actual 결과를 실제 로그·XML과 대조했다.
  - 두 번째 감사 전후 178개 변경 파일 fingerprint `6cc19fff43393713a8a1276297144f1bd916ca3bfe0155cc7140ef909d5eff08`이 동일했다.
- Next steps:
  - P9 모의 면접은 별도 단계로 남긴다.

## [2026-07-30] Session Summary (P7 완료 상태 반영)

- What was done:
  - 두 번째 read-only validator의 finding 없는 `PASS`와 전후 fingerprint 불변을 구현 계획의 P7 완료 상태에 반영했다.
- Key decisions:
  - P7을 완료하고 P8을 다음 단계로 표시하되 P8 구현은 이번 범위에서 시작하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Validator `PASS`; Backend 380 tests, Frontend 211 tests, OpenAPI 70/51, P7 actual 1/1·P6 회귀 2/2와 DB assertions 통과.
- Next steps:
  - 별도 요청에서 P8 면접 준비 계약을 고정한다.

## [2026-07-30] Session Summary (P7 validator 보정·재검증 상태 반영)

- What was done:
  - 구현 계획에 1차 validator의 suggestion 제약·409 UX 두 MAJOR, 단일 보정 라운드와 final-source 재검증 증거를 반영했다.
- Key decisions:
  - 보정 뒤 실제 검증이 모두 통과했어도 두 번째 read-only validator PASS 전에는 P7 완료 checkbox를 선택하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Backend 54 suites/380 tests, Frontend 53 files/211 tests, OpenAPI 70/51, P7 actual Chromium 1/1·P6 회귀 2/2와 DB assertions를 최신 로그와 대조했다.
- Next steps:
  - 최종 validator 재실행 결과로 P7 완료 또는 미검증 상태를 고정한다.

## [2026-07-30] Session Summary (P7 구현·actual 검증 상태 반영)

- What was done:
  - 구현 계획에 P7 V8·17 API·고정 generation/verification workflow·세 route와 actual E2E 증거를 반영했다.
- Key decisions:
  - actual 검증이 통과했어도 독립 validator 전에는 P7 완료 checkbox와 P8 선행 완료 상태를 올리지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Backend 377 tests, Frontend 204 tests, OpenAPI 70/51, P7 actual Chromium 1/1·P6 회귀 2/2와 DB assertions를 실제 로그와 대조했다.
- Next steps:
  - validator PASS일 때만 P7을 완료로 기록하고 다음 단계가 P8임을 표시한다.

## [2026-07-30] Session Summary (P6 actual gate 완료 상태 반영)

- What was done:
  - 구현 계획의 P6 체크리스트와 현재 단계를 final-source actual Chromium·DB assertion 결과에 맞췄다.
- Key decisions:
  - 기존 validator가 두 구현 MAJOR 해소를 확인했고 유일한 잔존 completion gap이던 actual wrapper가 통과해 P6를 완료로 전환했다.
- Issues encountered:
  - 첫 gate는 wrapper DB 컬럼명 오타로 실패해 `TEST_HARNESS_DEFECT` 1줄만 보정했다.
- Validation:
  - P6 Playwright 2/2, JUnit wrapper 1/1과 후속 DB assertions, process cleanup, `git diff --check`를 대조했다.
- Next steps:
  - P7은 P6 공개 분석·provenance 계약을 변경하지 않고 새 cover letter migration·API·workflow·화면으로 진행한다.

## [2026-07-27] Session Summary (P5 완료 상태와 P6 경계 반영)

- What was done:
  - 구현 계획의 P5 체크리스트·완료 조건과 P0~~P5 완료, P6~~P10 미착수 상태를 실제 코드·검증 수치에 맞췄다.
- Key decisions:
  - P5는 AC-04~06만 완료하며 analysis·RAG·fit score·eligibility는 P6에 유지한다.
- Issues encountered:
  - 최초 validator 보안·범위 지적을 한 차례 보정했고 최종 read-only validator가 신규 finding 없이 `PASS`를 반환했다.
- Validation:
  - Backend 322, Frontend 122, migration 6, Browser E2E 5와 OpenAPI 50/34 결과를 대조했다.
  - Validator 전후 173개 변경 파일 fingerprint가 `deacb3d70790bddf8baa27db3ec44eca10a7f6499a85f9477f1e8d3d96ed4212`로 일치했다.
- Next steps:
  - P6는 현재 Job content hash·typed resource 경계를 선행 조건으로 착수한다.

## [2026-07-23] Session Summary (책임별 backend 목표 구조 동기화)

- What was done:
  - `system-architecture.md`와 `implementation-plan.md`의 기능별 계층을 실제 책임 하위 package 구조와 P1~P4 구현 상태에 맞췄다.

- Key decisions:
  - 하위 package 목록은 허용 책임 기준이며 모든 기능에 빈 구조를 생성하는 목표가 아니다.
  - 구조 세분화는 탐색성과 책임 가시성만 개선하고 제품 계약을 변경하지 않는다.

- Issues encountered:
  - 구현 전 기준선으로 남아 있던 설계 상태를 코드·progress 기준의 현재 상태로 수정했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 완료 상태와 P5·P6 경계 반영)

- What was done:
  - 구현 계획의 P4 체크리스트·상태·완료 조건을 실제 Document·Fake AI·Browser E2E 결과에 맞췄다.
- Key decisions:
  - P4는 AC-03만 완료하며 P6 전체 RAG와 실제 provider 연결은 후속 범위로 유지한다.
- Issues encountered:
  - 최초 P4 Validator가 Agent Run Document resource filter의 application 예약 404를 `NEEDS_CHANGES`로 판정해 owner resolver 경계만 한 차례 보정했다.
- Validation:
  - 계획의 43 operations/30 paths, 단일 V5와 P5–P10 미착수 상태를 코드·테스트와 대조했다.
  - 보정 후 read-only 재검증이 `PASS`했고 status·content snapshot 207개가 각각 기준 SHA-256과 일치했다.
- Next steps:
  - P4는 AC-03 완료 상태이며 P5–P10은 각 선행 조건에 따라 착수한다.

## [2026-07-19] Session Summary (P3 구현 상태와 후속 migration 경계 반영)

- What was done:
  - 구현 계획의 P3를 실제 Agent Run·AI runtime·Frontend 복구 기반 구현과 최종 validator `PASS` 상태로 동기화했다.
  - P4·P5 착수 전에 남은 typed resource FK, 실제 provider·장시간 heartbeat, resource-linked retry/apply와 AC-13 잔여 범위를 명시했다.

- Key decisions:
  - P3는 AC-13 전체가 아니라 PostgreSQL Agent Run, 고정 workflow, 예산과 SSE 복구의 공통 기반만 담당한다.
  - resource가 없는 P3 Fake Run을 위해 generic UUID FK나 미래 domain table을 만들지 않고 typed owner 복합 FK는 실제 aggregate가 생기는 forward migration에서 추가한다.
  - Dashboard·공개 AI/개인정보 설정과 전체 운영 hardening은 계획대로 P10에 남긴다.

- Issues encountered:
  - 구현 계획에는 P3 미착수 문구가 남아 있어 코드·모듈 진행 문서와 맞지 않았고 최종 validator 전 실제 상태로 보정했다.
  - 실제 provider가 없는 P3와 P4 이후 provider 연동 위험을 혼동하지 않도록 disabled production gateway와 향후 heartbeat·price policy 검증 경계를 분리했다.
  - 최초 read-only Validator는 SSE owner 404 공통 오류 본문과 gateway 호출 중 주기 heartbeat 부재를 `NEEDS_CHANGES`로 판정했고, 허용된 한 차례 보정 범위를 두 항목과 직접 회귀 테스트로 제한했다.

- Validation:
  - P3의 35 operation·24 path, 단일 V4, Fake workflow·비용·SSE·Frontend 복구 구현과 P4 이후 endpoint·table 부재를 코드 및 하위 진행 문서와 대조했다.
  - 보정 후 Backend 243개·Frontend 78개 test, production build, P3 Chromium 2개, Compose와 diff 검증이 재실행에서도 통과했다.
  - 최초 `NEEDS_CHANGES`의 두 MAJOR를 제한 보정한 뒤 한 차례 read-only 재검증이 BLOCKER·MAJOR·MINOR 없이 `PASS`했다.
  - Validator 전후 status·content snapshot이 각각 81 line·258 file로 일치했다.

- Next steps:
  - P3는 완료됐으며 P4–P10 미착수 상태에서 다음 phase의 typed resource·provider 경계를 유지한다.

## [2026-07-18] Session Summary (P0 승인 결정 기록과 설계 기준선 전환)

- What was done:
  - 다섯 활성 명세의 독립 검증 `PASS` 뒤 proposal 상태를 `APPROVED_DECISION_RECORD`로 전환하고 승인 과정·대안은 보존했다.
  - 전체 시스템 설계의 D-01–D-18과 Gate A–C를 승인 완료 상태로 활성 명세에 연결하고 구현 계획의 P0 체크를 완료했다.

- Key decisions:
  - 현재 제품 계약의 단일 원천은 `docs/spec/**`이고 결정 기록과 파생 설계는 이를 대체하지 않는다.
  - P0 완료는 계약 확정만 뜻한다. P1–P10의 Java·TypeScript·Vue·Flyway·설정 작업은 모두 미착수다.

- Issues encountered:
  - 결정 기록의 승인 전 권장값 중 최종 제품 결정과 다른 항목은 삭제하지 않고 상단 precedence 안내와 8장 승인 결과 설명으로 역사적 맥락을 분리했다.
  - 전체 설계에 남아 있던 미결정·보류 표현을 승인된 상태·수명주기·비용·SSE·embedding 계약 또는 활성 명세 링크로 교체했다.

- Validation:
  - 명세 단계 read-only validator가 8개 정책, D 18개, Gate A 6개·B 5개·C 5개와 계층 간 enum·DTO·DB 제약을 `PASS`로 판정한 뒤 설계 상태를 전환했다.
  - proposal의 기존 결정 본문을 보존했고 활성 계약 우선 문구, P0 완료·P1 미착수 경계와 상대 링크를 확인했다.

- Next steps:
  - P1 시작 전에 활성 API·DB 명세에서 공통 HTTP·인증·idempotency 구현 범위를 잘라 작업하고 실제 migration·코드 검증 결과는 해당 모듈 진행 문서에 기록한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안서 최종 정합성 감사 및 보정)

- What was done:
  - 기존 `PROPOSAL`을 기준 명세·설계와 다시 대조하고 수정 전·후 별도 read-only validator로 의미 기반 계약 감사를 수행했다.
  - URL·memo 상한, 면접 답변 source, mock feedback 품질, 탈퇴 idempotency, embedding 선택, feedback 취소, 공개 DTO 경계와 프로필 완료 정책을 최소 보정했다.
  - 최종 validator `PASS` 뒤 문서 상태를 `READY_FOR_OWNER_REVIEW`로 변경했다.

- Key decisions:
  - D-01~D-18은 `RECOMMENDED` 10개와 `OWNER_DECISION_REQUIRED` 8개이며, embedding과 프로필 완료를 포함한 제품 질문 8개를 검토 대상으로 둔다.
  - 회원 탈퇴에는 replay 불가능한 idempotency를 적용하지 않고, mock feedback은 `BALANCED` 고정, 면접 답변 feedback은 성공 row만 생성한다.
  - 승인 후 기준 명세를 동기화하고 이 제안서는 결정 기록 또는 archived proposal로 전환한다.

- Issues encountered:
  - 최초 validator는 embedding 차원 고정, mock HIGH_QUALITY, 탈퇴 replay, profile hard gate 4개 BLOCKER와 URL·memo·source·취소·DTO 경계 등의 MAJOR를 확인해 `NEEDS_CHANGES`로 판정했다.
  - 보정 중 기준 명세나 코드로 범위를 확장하지 않았고, 공개 DTO에서 내부 checksum·hash·prompt/model·step reuse 정보를 제거했다.

- Validation:
  - 최종 validator는 D 18개, Gate A 6개·B 5개·C 5개, enum·DTO·quality·idempotency·cancel/retry·제품 분류·변경 경로를 의미 기반으로 검사해 `PASS`했다.
  - Markdown 표 열 수, 상대 링크, 중복 endpoint·enum/field, 상한·nullability, quality allowlist, 상태 전이를 정적으로 검사하고 Prettier·`git diff --check`를 실행했다.
  - 문서 전용 작업이므로 backend/frontend build는 실행하지 않았다.

- Next steps:
  - 제품 소유자가 8개 질문과 나머지 권장안을 승인·수정한 뒤 `docs/spec/**`을 한 작업에서 동기화하고 P0 완료 여부를 다시 판정한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안과 구현 차단 기준선 작성)

- What was done:
  - `system-architecture.md`의 D-01–D-18과 Gate A–C를 기능·API·DB·페이지·기술 명세 및 현재 bootstrap 코드와 대조해 `p0-contract-decision-proposal.md`를 작성했다.
  - canonical enum·상태 전이, 95개 기존 endpoint와 2개 제안 endpoint의 DTO·validation·오류·동기/비동기 계약, 데이터 수명주기·migration 책임, 고정 AI workflow, route·projection과 제품 승인 질문 6개를 구체화했다.
  - `index.md`와 설계·계획 문서에 제안서 링크를 추가하고 `implementation-plan.md`의 파일 소유권 표, `system-architecture.md`의 Gate·AC 범위 표기를 수정했다.

- Key decisions:
  - D 항목은 `RECOMMENDED` 11개와 `OWNER_DECISION_REQUIRED` 7개로 분류했으며, 사용자 승인 전에는 P0를 완료하거나 기준 명세·코드·migration에 적용하지 않는다.
  - PostgreSQL과 REST snapshot을 상태 원천으로 유지하고 SSE는 전달 수단으로만 사용하며, tenant 복합 FK·provenance·idempotency·outbox·lease/cancel·비용 reserve/settle을 구현 기준선으로 제안했다.
  - 회원 탈퇴는 Agent Run이 아닌 user FK 없는 durable deletion task와 receipt로 추적하고, AI는 8개 `WorkflowType`의 유한 step registry로만 실행한다.

- Issues encountered:
  - 1차 validator가 중첩 DTO, 면접 준비 품질 allowlist, 회원 삭제 run 소유권, 취소 후 resource 상태 4건을 `NEEDS_CHANGES`로 판정해 허용된 1회 보정과 동일 validator 재검증을 수행했다.
  - 2차 validator는 앞선 4건 해소를 확인했지만 request/response 문자열 상한, `JobSummaryDto` 필드 타입, `ResearchSourceType`, reparse placeholder를 새 `NEEDS_CHANGES`로 지적했다.
  - 루트 관리자가 마지막 지적을 명세와 같은 상한·enum·path로 정합화했으며, 오케스트레이션 상한에 따라 세 번째 validator는 실행하지 않았다. 따라서 최종 루트 보정분은 독립 validator 미검증 상태다.

- Validation:
  - backend·ai_workflow·frontend 분석 에이전트는 모두 `DONE`, 파일 변경 없음으로 종료했고 루트가 결과를 원문·diff와 대조했다.
  - validator는 두 번 모두 read-only·파일 변경 없음으로 전체 matrix를 검사했으며 두 번째 판정은 `DONE/NEEDS_CHANGES`였다.
  - 최종 루트 검사에서 D 18행(11/7), 기준 endpoint 95개 누락 0, 필수 타입 18개, 제품 질문 6개, Markdown 표 열 수, 로컬 링크, Prettier와 `git diff --check`가 통과했다.
  - 비즈니스 코드·테스트·dependency·migration·설정과 `docs/spec/**`는 변경하지 않았다.

- Next steps:
  - 제품 소유자가 6개 질문과 나머지 권장안을 승인·수정한 뒤 다섯 기준 명세를 한 번에 동기화하고, P1 착수 전에 최종 제안서의 독립 계약 검증을 다시 수행한다.

## [2026-07-18] Session Summary (전체 시스템 설계와 구현 계획 기준선 작성)

- What was done:
  - `docs/spec/`의 모든 명세와 현재 저장소 골격을 분석해 프로젝트 목적, MVP, 모듈·도메인 관계와 기능·DB·API·페이지 추적 설계를 작성했다.
  - 문서·공고·자기소개서·면접·Agent Run workflow, 인증·격리·개인정보, 비동기·복구·SSE 구조를 통합했다.
  - 계약 결정부터 AC-01~13 통합 검증까지의 단계별 구현 계획, 완료 조건과 backend·AI workflow·frontend·validator 파일 소유권을 작성했다.
  - 충돌·누락은 권장안과 구현 보류 범위를 분리해 기록했으며 기준 명세와 비즈니스 코드는 변경하지 않았다.

- Key decisions:
  - `docs/spec/`을 기준 계약으로 유지하고 파생 설계는 별도 `docs/design/`에서 관리한다.
  - 미결 상태·DTO·수명주기·AI 정책은 P0 결정 게이트가 닫히기 전 migration이나 공개 계약으로 구현하지 않는다.
  - 백엔드는 도메인·HTTP·persistence, AI workflow는 context·model·prompt·workflow, frontend는 UI·API consumer를 소유하고 루트 관리자가 계약·추적 문서를 통합한다.

- Issues encountered:
  - 공고 상태 축, 품질·version·질문 enum, tenant DB 제약, 삭제·provenance, 멱등성, Agent Run 복구·SSE, 자기소개서 최종화, 면접·모의 면접 lifecycle 등 구현 전 결정 항목이 확인됐다.
  - 상세 이슈와 권장안은 `system-architecture.md`의 명세 불일치 절에 기록했다.
  - 독립 validator가 자기소개서 목록·보관, 조사 재시도, 면접 준비 목록의 직접 추적과 상위 진행 문서·Markdown format 보완을 요구했다.

- Validation:
  - backend·AI workflow·frontend 읽기 전용 분석이 모두 `DONE`으로 종료됐고 파일을 수정하지 않았다.
  - 독립 validator가 사용자 요구 1~~15, AC-01~~13, 격리·비동기·책임 경계와 상대 링크를 통과시키고 보조 MVP 추적 3건을 `NEEDS_CHANGES`로 판정했다.
  - 지적된 자기소개서 `ARCHIVED`·목록, `research-runs` retry, `/interviews` 목록을 한 차례 보완했다.
  - 정적 재검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 변경 문서 상대 링크와 `git diff --check`가 통과했다.
  - `corepack pnpm --dir frontend exec prettier --check "../docs/design/*.md" "../docs/index.md" "../index.md"`가 통과했다.
  - 비즈니스 코드·dependency·migration·API·UI를 변경하지 않아 backend/frontend build test는 이번 문서 작성 범위에서 실행하지 않는다.

- Next steps:
  - 실제 구현 전에 P0 결정 게이트의 공개 계약·데이터 수명주기·AI 운영 정책을 사용자 승인으로 확정한다.
