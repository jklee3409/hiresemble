# GitHub Source Application

source 등록·선택·refresh·delete, 실패 source의 재실행, same-run resume, Session-bound setup/OAuth+PKCE connection lifecycle, disconnect cleanup, workflow command/query와 canonical candidate 적용을 조정한다. 공개 repository snapshot은 commit-addressed archive port를 우선하고, private Run은 credential을 복사하지 않고 현재 ACTIVE connection에서 repository-scoped token을 해석한다.
