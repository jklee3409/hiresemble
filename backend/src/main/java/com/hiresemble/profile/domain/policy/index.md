# 프로필 Policy package 안내

## 디렉터리 목적

com.hiresemble.profile.domain.policy package는 도메인 불변식과 계산 정책을 소유한다.

## 주요 파일 및 하위 디렉터리

| 파일                                     | 역할                                                  |
| ---------------------------------------- | ----------------------------------------------------- |
| [ProfilePolicy.java](ProfilePolicy.java) | 프로필 입력 불변식과 교육/학력 evidence category 판별 |
| [ExperienceSimilarityPolicy.java](ExperienceSimilarityPolicy.java) | 경험 fingerprint, anchor·수치 충돌과 cosine 임계치 판정 |
| [progress.md](progress.md)               | 이 package의 이동·검증 이력                           |

## 구성 요소 역할

- 도메인 불변식과 계산 정책을 소유한다. 경험 자동 동일 판정은 cosine 0.94 이상·공통 anchor 2개 이상·수치 충돌 없음 조건을 모두 요구하고 0.82 이상은 사용자 검토 대상으로만 분류한다.
- 상위 계층의 책임을 더 구체적인 탐색 단위로 드러내며 새 동작이나 계약을 정의하지 않는다.

## 다른 디렉터리와의 의존 관계

- [상위 package](../index.md)의 계층 경계와 인접 계층의 공개 타입을 사용한다.

## 변경 시 주의사항

- 실제 책임과 파일이 없는 빈 package를 선행 생성하지 않는다.
- package 이동 시 접근 제한자를 넓히지 않고 경로, package 선언, import와 필요한 FQCN을 함께 검증한다.
- API·DB·workflow 동작 변경은 별도 계약 작업으로 분리한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../../docs/agent-rules/backend-development.md)
- [문서 추적 규칙](../../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [상위 package 안내](../index.md)
- [진행 상황](progress.md)
