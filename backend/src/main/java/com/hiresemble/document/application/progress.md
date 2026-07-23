# Progress

## Overview

P4 Document command/query와 workflow·storage·evidence·outbox port 경계를 구현했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/document/application 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Document application 수명주기 연결)

- What was done:
  - upload idempotency, budget reserve, typed Run link, manual same-run resume, reparse 새 Run과 삭제 transaction을 연결했다.
  - Object 준비 뒤 Document·Run·budget·idempotency 완료를 한 transaction에서 commit하고 실패 시 Object를 보상한다.
  - active Document owner resolver를 Agent Run resource filter application port에 제공한다.
- Key decisions:
  - normalized 비공백 100자 미만은 `WAITING_USER`, manual text는 source revision 증가와 비용 재예약을 수행한다.
- Issues encountered:
  - 양수 Fake 예약을 terminal fixture가 직접 종료하던 테스트를 unused reservation release 후 종료하도록 고쳤다.
- Validation:
  - 동시 같은 key, replay/hash mismatch, 완료 trigger rollback, owner resource filter, version 409, retry lineage, compensation, partial success가 통과했다.
- Next steps:
  - P5 이후 provenance 구현은 `EvidenceReferenceQueryPort` contributor로 확장한다.
