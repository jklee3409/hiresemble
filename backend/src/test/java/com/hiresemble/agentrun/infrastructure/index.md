# Agent Run Infrastructure Tests

Agent Run infrastructure의 Spring configuration·worker 통합 경계를 검증하는 하위 테스트를 관리한다.

- [config](config/index.md): scheduler conditional configuration 검증
- [progress.md](progress.md): 이 영역의 검증 이력

Production cadence나 retry 정책을 테스트 편의를 위해 변경하지 않고, 외부 서비스는 Fake/Testcontainers 경계만 사용한다.
