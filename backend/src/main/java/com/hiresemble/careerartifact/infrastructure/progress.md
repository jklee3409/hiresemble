# Progress

## Overview

V28 JDBC persistence, POI Office renderer와 private Object lifecycle adapter가 구현됐다.

## [2026-08-08] Session Summary (Office renderer와 Object lifecycle)

- What was done:
  - A4·one-column DOCX, 16:9 PPTX, reopen/content-type/macro·embedded media·external relationship/size 검증과 upload metadata 확인·삭제 outbox를 구현했다.
- Key decisions:
  - 사용자 link는 plain text로만 렌더링하고 title은 Object key가 아니라 attachment filename 정규화에만 사용한다.
- Issues encountered:
  - POI가 만드는 빈 run은 글꼴 크기 assertion 대상에서 제외했다. 표준 `/docProps/thumbnail.jpeg`만 문서 속성으로 허용하고 `/ppt/media`와 다른 image는 계속 거부한다.
- Validation:
  - Office reopen·MIME·font·slide·contact·external relationship·S3 attachment 테스트를 통과했다.
- Next steps:
  - LibreOffice 시각 fixture는 설치된 개발 환경에서만 선택적으로 추가할 수 있다.
