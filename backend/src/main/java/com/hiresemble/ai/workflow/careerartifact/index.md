# Career Artifact AI workflow

`resume-generation-v1`과 `portfolio-generation-v1`의 고정 8단계 실행, exact model·bounded context·strict fact-check와 실패·중단 Object 보상을 담당한다.

- `CareerArtifactGenerationWorkflow.java`: request/context, 세 Chat step, local render/validate/persist
- `CareerArtifactGenerationFailureHandler.java`: current version을 보존하는 실패·취소·중단 정리
