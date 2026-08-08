import type {
  GitHubRepositoryListParams,
  GitHubSourceListParams,
} from '@/shared/api/githubSourceApi'

export const gitHubSourceQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'githubSources'] as const
  },
  listRoot(userId: string) {
    return [...this.root(userId), 'list'] as const
  },
  list(userId: string, filters: GitHubSourceListParams) {
    return [...this.listRoot(userId), filters] as const
  },
  detail(userId: string, sourceId: string) {
    return [...this.root(userId), 'detail', sourceId] as const
  },
  repositoryRoot(userId: string, sourceId: string) {
    return [...this.detail(userId, sourceId), 'repositories'] as const
  },
  repositories(userId: string, sourceId: string, filters: GitHubRepositoryListParams) {
    return [...this.repositoryRoot(userId, sourceId), filters] as const
  },
}
