# Progress

## Overview

public/private GitHub Source와 App connection domain 구현 상태를 추적한다.

## [2026-08-09] Session Summary (Private access domain enum·record)

- What was done: access mode, repository visibility, connection status/selection과 안전한 record를 추가했다.
- Key decisions: PUBLIC은 connection 없음, GITHUB_APP은 owner connection 필수 shape를 유지한다.
- Issues encountered: 없음.
- Validation: domain/DB 음수 경로와 전체 check가 통과했다.
- Next steps: 상태 enum을 webhook용으로 임의 확장하지 않는다.

## [2026-08-07] Session Summary (GitHub URL·상태·선택 불변식)

- What was done: strict URL canonicalization, account/repository shape, source transition과 unique 1~10 repository selection을 구현했다.
- Key decisions: HTTPS github.com shape 외 user-info·port·query·fragment·encoding·extra path를 fail closed한다.
- Issues encountered: `.git` suffix 제거와 additional repository path 거부 순서를 명확히 했다.
- Validation: URL/status/selection domain fixture가 통과했다.
- Next steps: None.
