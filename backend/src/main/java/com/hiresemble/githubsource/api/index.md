# GitHub Source API

`/api/v1/github-sources`의 5개 path·7개 operation과 private flag에서만 등록되는 `/api/v1/github-app-connections` 7개 operation, additive public/private DTO projection을 관리한다. raw snapshot, token, OAuth code/state, external installation ID field, 전체 SHA, storage key와 source-unit ID는 노출하지 않는다.
