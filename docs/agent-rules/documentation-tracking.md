# 디렉터리 문서 추적 규칙

## 목적

Codex가 파일을 변경하기 전에 책임과 기존 상태를 파악하고, 작업 후 다음 작업자가 검증 결과와 남은 문제를 이어받을 수 있도록 계층형 문서를 유지한다.

## 관리 대상

프로젝트가 직접 소유하고 사람이 변경하는 디렉터리는 `index.md`와 `progress.md`를 가진다. 중간 namespace 디렉터리도 현재 디렉터리 구조와 부모-자식 경계를 명확히 하기 위해 관리 대상에 포함한다.

이번 초기 기준 대상은 다음과 같다.

```text
./
├─ .codex/
├─ .github/
│  └─ workflows/
├─ backend/
│  └─ src/main/
│     ├─ java/com/hiresemble/
│     └─ resources/db/migration/
├─ docs/
│  ├─ agent-rules/
│  └─ spec/
└─ frontend/
   ├─ e2e/
   └─ src/
      ├─ router/
      └─ styles/
```

표현을 압축한 경로의 모든 실제 중간 디렉터리(`backend/src`, `backend/src/main`, `backend/src/main/java`, `backend/src/main/java/com`, `backend/src/main/resources`, `backend/src/main/resources/db`)에도 두 문서를 둔다.

## 제외 대상

다음은 문서를 생성하지 않는다.

- VCS/IDE: `.git`, `.idea`, `.vscode`
- 의존성/외부 도구: `node_modules`, `.pnpm-store`, `vendor`, `backend/gradle`, Gradle Wrapper 내부
- 빌드/테스트 결과: `build`, `dist`, `target`, `out`, `coverage`, `playwright-report`, `test-results`, `blob-report`
- cache/temp: `.gradle`, `.vite`, `.cache`, `tmp`, `temp`, logs
- 자동 생성 코드/문서: `generated`, OpenAPI generator 출력 등
- 로컬 인프라 데이터: `infra/data`, `docker-data`

이름만으로 불명확하면 파일의 소유권과 생성 주체를 확인한다. 사람이 직접 관리하는 소스면 포함하고 도구가 재생성하면 제외한다.

## `index.md` 작성 규칙

현재 구조와 안정적인 책임을 설명한다. 최소 항목은 다음과 같다.

1. 디렉터리 목적
2. 주요 파일 및 하위 디렉터리
3. 각 구성 요소의 역할
4. 다른 디렉터리와의 의존 관계
5. 변경 시 주의사항
6. 관련 규칙 및 문서의 상대 경로 링크

새 파일을 모두 나열하지 말고 책임을 이해하는 데 필요한 주요 항목만 기록한다. 구조, 소유권, 공개 경계가 바뀔 때 갱신한다.

## `progress.md` 작성 규칙

최소한 다음 제목을 유지한다.

```markdown
## 현재 구현 상태

## 완료된 작업

## 진행 중인 작업

## 남은 작업

## 확인된 문제

## 기술적 결정 사항

## 테스트 및 검증 결과

## 마지막 수정 일자
```

완료된 작업에는 단순한 “완료” 대신 작업 목적, 생성·수정·삭제한 주요 파일, 핵심 내용이 드러나게 쓴다. 결정 사항에는 선택 이유와 거절한 대안의 핵심을, 검증 결과에는 실제 명령과 성공/실패/미실행을 쓴다. 마지막 수정 일자는 `YYYY-MM-DD` 형식으로 실제 문서를 갱신한 날짜를 사용한다.

오래된 상세 이력이 문서를 압도하면 현재 상태를 요약하고 Git 이력으로 넘기되, 미해결 문제와 중요한 결정은 삭제하지 않는다.

## 계층 원칙

- 루트 `index.md`: 전체 프로젝트 구조와 모듈 관계
- 루트 `progress.md`: 여러 모듈에 걸친 전체 상태와 결정
- 모듈 문서: 모듈의 공개 책임, 내부 영역, 모듈 단위 검증
- 하위 문서: 해당 package/기능/설정 영역의 세부 상태

상위 문서에는 하위 구현 목록을 반복하지 않고 링크와 영향만 기록한다. 하위 문서에는 프로젝트 전체 소개를 반복하지 않는다.

## 변경 전후 절차

변경 전에는 대상과 모든 기존 상위 `index.md`/`progress.md`를 읽는다. 변경 후에는 직접 영향받은 디렉터리 문서를 모두 갱신한다. 파일을 다른 디렉터리로 이동하면 출발지와 도착지 문서를 모두 갱신한다. 프로젝트 전체 규칙, 공통 계약, root 설정 변경은 루트 `progress.md`에도 요약한다.

빌드나 테스트 실패는 원인과 미검증 범위를 남긴다. 문서 갱신만으로 상태를 “구현 완료”로 올리지 않는다.

## 링크 규칙

- 저장소 내부 문서는 상대 경로 Markdown 링크를 사용한다.
- 링크는 실제 파일 또는 디렉터리를 가리켜야 한다.
- 같은 파일을 상·하위 문서에서 장문으로 복제하지 않는다.
- 이동 시 inbound link를 `rg`로 찾아 함께 고친다.

## 관련 문서

- [최상위 작업 지침](../../AGENTS.md)
- [공통 작업 절차](workflow.md)
- [루트 구조 안내](../../index.md)
