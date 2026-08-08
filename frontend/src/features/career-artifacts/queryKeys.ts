import type {
  CareerArtifactListParams,
  CareerArtifactVersionListParams,
} from '@/shared/api/careerArtifactApi'
import type { CareerArtifactType } from '@/shared/api/careerArtifactContracts'

export const careerArtifactQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'careerArtifacts'] as const
  },
  readiness(userId: string) {
    return [...this.root(userId), 'readiness'] as const
  },
  modelCatalog(userId: string, artifactType: CareerArtifactType) {
    return [...this.root(userId), 'aiModels', artifactType] as const
  },
  listRoot(userId: string) {
    return [...this.root(userId), 'list'] as const
  },
  list(userId: string, filters: CareerArtifactListParams) {
    return [...this.listRoot(userId), filters] as const
  },
  detail(userId: string, artifactId: string) {
    return [...this.root(userId), 'detail', artifactId] as const
  },
  versionRoot(userId: string, artifactId: string) {
    return [...this.detail(userId, artifactId), 'versions'] as const
  },
  versions(userId: string, artifactId: string, filters: CareerArtifactVersionListParams) {
    return [...this.versionRoot(userId, artifactId), filters] as const
  },
}
