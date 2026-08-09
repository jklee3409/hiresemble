# GitHub Source Infrastructure

PostgreSQL/JdbcClient 저장, 고정 GitHub REST/OAuth endpoint, 검증된 `codeload.github.com` 공개 archive 수집, RS256 App JWT와 repository-scoped installation token, private gzip snapshot storage, revocation/snapshot 삭제 outbox와 lease worker를 관리한다. 공개 archive는 redirect origin·commit SHA·압축/해제 크기·entry 경로를 검증하며, token과 upstream credential은 영속화하지 않는다.
