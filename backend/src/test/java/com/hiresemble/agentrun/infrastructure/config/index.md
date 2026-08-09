# Agent Run Configuration Tests

`SchedulingConfiguration`의 property on/off Spring context 등록을 검증한다.

- [SchedulingConfigurationTest.java](SchedulingConfigurationTest.java): scheduler 활성·비활성 bean 경계
- [progress.md](progress.md): 검증 이력

통합 테스트의 수동 outbox processing과 scheduled processing이 경쟁하지 않도록 test profile 계약을 보호한다.
