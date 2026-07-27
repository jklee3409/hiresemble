# Progress

## Overview

P5 Job JDBC·Scheduler 설정과 SSRF-safe URL fetch adapter가 구현됐다.

## [2026-07-27] Session Summary (Job persistence와 안전한 fetch 구현)

- What was done:
  - owner-scoped SQL, batch conditional close와 pinned socket 기반 HTTP(S) fetch를 추가했다.
- Key decisions:
  - redirect마다 URL·DNS를 재검사하고 전체 fetch에 하나의 절대 response deadline을 적용한다.
- Issues encountered:
  - 초기 HttpClient가 검증 후 DNS를 다시 해석하고 stream body timeout을 보장하지 않아 raw JDK socket transport로 보정했다.
- Validation:
  - DNS rebinding, redirect private IP, loopback, slow body, size, content type, status와 페이지 분류 테스트가 통과했다.
- Next steps:
  - 실제 외부 사이트 호환성은 provider 활성화 전 별도 통합 환경에서 검증한다.
