# Progress

## Overview

GitHub Source domain 구현 상태를 추적한다.

## [2026-08-07] Session Summary (GitHub URL·상태·선택 불변식)

- What was done: strict URL canonicalization, account/repository shape, source transition과 unique 1~10 repository selection을 구현했다.
- Key decisions: HTTPS github.com shape 외 user-info·port·query·fragment·encoding·extra path를 fail closed한다.
- Issues encountered: `.git` suffix 제거와 additional repository path 거부 순서를 명확히 했다.
- Validation: URL/status/selection domain fixture가 통과했다.
- Next steps: None.
