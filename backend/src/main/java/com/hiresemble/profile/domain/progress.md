# Progress

## Overview

P2 프로필 완료도와 구조화 source·direct evidence 불변식이 구현되어 있다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile/domain 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (P2 완료도·날짜·evidence 정책 구현)

- What was done:
  - 다섯 완료 항목, 배열 canonicalization, 학력·GPA·자격·어학·경력 날짜와 source mapping 정책을 구현했다.
  - source별 direct evidence title·content·metadata 생성 규칙을 구현했다.

- Key decisions:
  - profile completion은 저장 입력이 아닌 read 시 서버 계산 결과이며 기능 접근을 차단하지 않는다.
  - source 수정은 evidence 별도 편집을 덮고 `VERIFIED`·`verifiedAt`을 다시 설정한다.

- Issues encountered:
  - None

- Validation:
  - 도메인 단위 테스트에서 완료도, 날짜·GPA, mapping, 재동기화 우선과 `SOURCE_DELETED` 금지를 검증했다.

- Next steps:
  - P4 AI 추출 evidence는 direct source 규칙과 분리해 추가한다.
