import type { EvidenceListParams, ExperienceListParams, PageParams } from '@/shared/api/profileApi'

export const profileQueryKeys = {
  profile(userId: string) {
    return ['user', userId, 'profile'] as const
  },
  eligibility(userId: string) {
    return ['user', userId, 'profile', 'eligibility'] as const
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
    return ['user', userId, 'evidence', filters] as const
  },
  evidenceRoot(userId: string) {
    return ['user', userId, 'evidence'] as const
  },
  experiences(userId: string, filters: ExperienceListParams) {
    return ['user', userId, 'experiences', filters] as const
  },
  experience(userId: string, experienceId: string) {
    return ['user', userId, 'experiences', experienceId] as const
  },
  experiencesRoot(userId: string) {
    return ['user', userId, 'experiences'] as const
  },
}
