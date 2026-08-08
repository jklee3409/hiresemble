# Career Artifact Tests

Career Artifact Gate 3의 feature-gated API/application, strict content와 POI/Object adapter 계약을 외부 provider 없이 검증한다.

- `CareerArtifactApiIntegrationTest.java`: readiness, lifecycle, owner, idempotency, retry와 storage/outbox
- [domain](domain/index.md): deterministic grounding validator
- [infrastructure](infrastructure/index.md): Office renderer 안전성
