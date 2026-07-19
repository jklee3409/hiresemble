import type { EvidenceListParams, PageParams } from '@/shared/api/profileApi'

export const profileQueryKeys = {
  profile(userId: string) {
    return ['user', userId, 'profile'] as const
  },
  educations(userId: string, filters: PageParams) {
    return ['user', userId, 'profile', 'educations', filters] as const
  },
  certifications(userId: string, filters: PageParams) {
    return ['user', userId, 'profile', 'certifications', filters] as const
  },
  languageScores(userId: string, filters: PageParams) {
    return ['user', userId, 'profile', 'languageScores', filters] as const
  },
  awards(userId: string, filters: PageParams) {
    return ['user', userId, 'profile', 'awards', filters] as const
  },
  careers(userId: string, filters: PageParams) {
    return ['user', userId, 'profile', 'careers', filters] as const
  },
  evidence(userId: string, filters: EvidenceListParams) {
    return ['user', userId, 'profile', 'evidence', filters] as const
  },
}
