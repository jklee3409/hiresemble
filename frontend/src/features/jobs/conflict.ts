import type { JobDetailDto, JobStatus, UpdateJobRequest } from '@/shared/api/jobContracts'

export const JOB_EDITABLE_CONFLICT_FIELDS = [
  { key: 'companyName', label: '회사명' },
  { key: 'title', label: '공고 제목' },
  { key: 'positionName', label: '직무명' },
  { key: 'descriptionText', label: '공고 본문' },
  { key: 'deadlineAt', label: '마감 일시' },
] as const

export interface JobConflictDraft extends UpdateJobRequest {
  status?: JobStatus
}

export interface JobVersionConflict {
  latest: JobDetailDto
  draft: JobConflictDraft
  fields: ReadonlyArray<{ key: keyof JobConflictDraft; label: string }>
}

export function reapplyJobDraft(
  latest: JobDetailDto,
  draft: JobConflictDraft,
  selectedFields: readonly (keyof JobConflictDraft)[],
): JobConflictDraft {
  const result: JobConflictDraft = {
    companyName: latest.companyName,
    title: latest.title,
    positionName: latest.positionName,
    descriptionText: latest.descriptionText,
    deadlineAt: latest.deadlineAt,
    version: latest.version,
  }
  for (const field of selectedFields) {
    if (field !== 'version' && Object.prototype.hasOwnProperty.call(draft, field)) {
      Object.assign(result, { [field]: draft[field] })
    }
  }
  return result
}

export function isJobVersionConflict(error: { status: number; code: string }): boolean {
  return error.status === 409 && error.code === 'RESOURCE_VERSION_CONFLICT'
}
