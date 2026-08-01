# Progress

## Overview

P5 URL fetch 보안·제한·페이지 분류 테스트가 구현됐다.

## [2026-08-01] Session Summary (WebP fetch 회귀)

- What was done:
  - project-generated WebP 정상 decode, MIME/magic mismatch, malformed와 pixel limit fixture를 추가했다.
- Key decisions:
  - network 없이 in-memory transport와 synthetic image를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - WebP focused, JPEG·PNG 전체 회귀와 Backend check 통과.
- Next steps:
  - None.

## [2026-07-27] Session Summary (SSRF·deadline 회귀 검증)

- What was done:
  - 정상·redirect·private/loopback·timeout·size·content type·HTTP status·로그인/봇/JS/빈 페이지를 검증했다.
- Key decisions:
  - connector가 resolver의 검증된 주소를 그대로 받는지와 body read deadline을 별도 assertion한다.
- Issues encountered:
  - 초기 fake transport timeout만으로는 slow body와 DNS rebinding을 검출하지 못해 socket 경계 테스트를 추가했다.
- Validation:
  - 관련 unit 12개와 전체 Backend check가 통과했다.
- Next steps:
  - 없음.
