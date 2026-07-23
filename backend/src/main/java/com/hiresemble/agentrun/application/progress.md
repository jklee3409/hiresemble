# Progress

## Overview

P3 workflow launch, state, checkpoint, budget, retry·cancel·resume와 domain apply port가 구현됐다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/agentrun/application 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private 결합은 접근 제한자를 넓히지 않고 같은 package 이동 또는 명시적 이동 제외로 처리했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 typed Document resource 연결)

- What was done:
  - Document owner resolution, display label, deletion check와 cancel compensation 경계를 연결했다.
  - Agent Run 목록의 `DOCUMENT` resource filter가 active owner resolver를 통과한 뒤 typed resource criteria를 repository에 전달하도록 연결했다.
- Key decisions:
  - typed link가 공개 `resourceType/resourceId`의 원천이며 resource-linked retry도 owner를 다시 검증한다.
- Issues encountered:
  - 최초 P4 Validator가 application의 P3 예약 404 때문에 Document resource filter repository가 도달 불가능한 점을 MAJOR로 발견했다.
- Validation:
  - active owner Document 성공, 타 사용자·없는·삭제 Document 404, upload typed link, generic retry lineage와 delete cancel이 통과했다.
  - 허용된 한 차례 보정 뒤 최종 read-only Validator 판정은 `PASS`다.
- Next steps:
  - P5 Job typed link는 실제 aggregate와 함께 추가한다.

## [2026-07-19] Session Summary (Agent Run application port와 transaction 구현)

- What was done:
  - 내부 `WorkflowLauncher`와 query/state/checkpoint/budget/usage/retry/cancel/resume port를 구현했다.
  - blocking gateway 호출 중 DB lease를 주기적으로 갱신하는 `AgentRunLeaseHeartbeatPort`를 추가했다.
  - predecessor당 successor 하나, replay 가능한 idempotency metadata와 cancellation completion을 연결했다.
  - signup transaction에 기본 AI preference 생성을 추가했다.

- Key decisions:
  - workflow는 repository 대신 application port만 사용한다.
  - WAITING_USER는 같은 Run을 QUEUED로 resume하고 terminal retry는 새 lineage Run을 만든다.

- Issues encountered:
  - retry body가 없으므로 retry-scope canonical hash는 `{}`이며 일반 hash mismatch는 공통 idempotency 테스트가 검증한다.

- Validation:
  - lineage·동시 retry·signup 실패 rollback·cancel compensation·resume와 장시간 호출 heartbeat PostgreSQL 테스트가 통과했다.

- Next steps:
  - 실제 resource owner resolution과 apply adapter는 각 P4 이후 domain에서 제공한다.
