# Progress

## Overview

P5 Job JDBC·Scheduler/fetch, P6 immutable Analysis JDBC·비용 설정과 자동 분석 claim store·reconciliation 설정이 구현됐다.

## [2026-08-04] Session Summary (Job Analysis coverage 저장)

- What was done:
  - analysis insert/select가 nullable fit score와 `analysis_coverage`를 저장·조회하도록 확장했다.
- Key decisions:
  - immutable 과거 analysis를 backfill하지 않고 null coverage로 rubric 세대를 구분한다.
- Issues encountered:
  - store summary와 application outdated projection 양쪽에 새 필드 전달이 필요했다.
- Validation:
  - JDBC compile과 관련 workflow persistence 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (자동 분석 claim store·설정)

- What was done:
  - owner/job/version unique insert, `SKIP LOCKED` lease claim, launched·blocked·retry projection query와 typed properties를 추가했다.
- Key decisions:
  - SQL은 사용자 원문·prompt·provider 응답을 저장하지 않고 safe error만 보존한다.
- Issues encountered:
  - None.
- Validation:
  - concurrent replay, restart reconciliation과 quota 실패 DB assertion 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (정적 WebP 안전 fetch)

- What was done:
  - WebP MIME/Accept, RIFF size·WEBP magic, ImageIO decode·dimensions·pixel 검증을 추가했다.
- Key decisions:
  - Apache-2.0 pure-Java `webp-imageio:0.3.3` read path를 사용하고 animation은 fail closed한다.
- Issues encountered:
  - Java 기본 ImageIO만으로는 WebP reader가 없어 별도 plugin이 필요했다.
- Validation:
  - synthetic WebP 정상/mismatch/malformed/pixel, JPEG·PNG와 SSRF/redirect/timeout 회귀 및 dependencyInsight 통과.
- Next steps:
  - animated WebP와 writer 기능은 지원 범위가 아니다.

## [2026-08-01] Session Summary (HTML strict charset·이미지 fetch)

- What was done:
  - header/BOM/meta/default/fallback charset decoder와 Korean alias 정규화, JPEG/PNG magic·dimension·size 검증을 구현했다.
- Key decisions:
  - `euc-kr` 계열은 현실의 CP949 확장 호환을 위해 MS949 decoder로 정규화하고 malformed/unmappable은 REPORT로 거부한다.
- Issues encountered:
  - Jsoup는 decoded DOM inspection에만 사용하고 raw byte charset 우선순위는 명시적 decoder로 고정했다.
- Validation:
  - charset 12경계와 SSRF/redirect/decompression/image MIME focused test 및 Backend 전체 check 통과.
- Next steps:
  - WebP는 Java 표준 ImageIO 지원이 없어 현재 allowlist에서 제외한다.

## [2026-07-29] Session Summary (P6 분석 persistence)

- What was done:
  - immutable analysis·criteria·evidence·Run link insert/query와 reuse/latest/history SQL을 추가했다.
- Key decisions:
  - 모든 사용자 query는 owner scope를 사용하고 V7 sealing·FK·trigger와 같은 transaction에서 apply한다.
- Issues encountered:
  - 없음.
- Validation:
  - repository/application·migration negative와 전체 check가 통과했다.
- Next steps:
  - None.

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
