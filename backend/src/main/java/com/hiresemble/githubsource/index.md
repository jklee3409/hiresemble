# GitHub Source

공개 GitHub 계정·repository 등록, bounded snapshot 수집, provenance 검증과 canonical 경험 적용을 담당한다.

- [api](api/index.md): Session/CSRF 기반 HTTP 계약과 안전한 projection
- [application](application/index.md): source command/query, workflow 경계와 idempotency
- [domain](domain/index.md): URL, 상태, 선택과 snapshot 불변식
- [infrastructure](infrastructure/index.md): JDBC, GitHub REST, private snapshot storage와 삭제 outbox

Career Artifact와 `/profile/github` Frontend는 이 모듈 범위가 아니다.
