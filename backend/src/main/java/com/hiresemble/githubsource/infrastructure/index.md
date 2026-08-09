# GitHub Source Infrastructure

PostgreSQL/JdbcClient 저장, 고정 GitHub REST/OAuth endpoint, RS256 App JWT와 repository-scoped installation token, private gzip snapshot storage, revocation/snapshot 삭제 outbox와 lease worker를 관리한다. token과 upstream credential은 영속화하지 않는다.
