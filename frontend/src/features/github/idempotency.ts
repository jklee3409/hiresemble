import { createGitHubIdempotencyKey } from '@/shared/api/githubSourceApi'

export type GitHubMutationAction = 'create' | 'selection' | 'refresh'

interface PendingKey {
  identity: string
  key: string
}

export class PendingGitHubIdempotencyKeys {
  private readonly keys = new Map<string, PendingKey>()

  constructor(
    private readonly createKey: (
      action: GitHubMutationAction,
    ) => string = createGitHubIdempotencyKey,
  ) {}

  keyFor(scope: string, identity: string, action: GitHubMutationAction): string {
    const current = this.keys.get(scope)
    if (current?.identity === identity) return current.key
    const key = this.createKey(action)
    this.keys.set(scope, { identity, key })
    return key
  }

  complete(scope: string, identity: string): void {
    if (this.keys.get(scope)?.identity === identity) this.keys.delete(scope)
  }
}
