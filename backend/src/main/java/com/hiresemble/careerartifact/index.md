# Career Artifact

승인된 canonical 경험을 근거로 이력서 DOCX와 면접관용 포트폴리오 PPTX를 생성하는 Gate 3 Backend 수명주기를 담당한다.

- [api](api/index.md): feature-gated 9개 path·11개 operation과 공개 DTO
- [application](application/index.md): owner-scoped lifecycle, idempotency, retry와 workflow port
- [domain](domain/index.md): artifact/content/render-profile 타입과 결정론적 grounding 검증
- [infrastructure](infrastructure/index.md): JDBC, POI renderer, 설정과 Object 삭제 outbox
- [AI workflow](../ai/workflow/careerartifact/index.md): 두 고정 8단계 workflow와 실패 보상

Gate 4 Vue page·wizard·preview는 이 모듈 범위가 아니다.
