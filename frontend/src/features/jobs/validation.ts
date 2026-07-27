import { z } from 'zod'

import type { CreateJobRequest, UpdateJobRequest } from '@/shared/api/jobContracts'

const optionalText = (max: number) => z.string().trim().max(max)
const httpUrl = z
  .string()
  .trim()
  .min(1, '공고 URL을 입력해 주세요.')
  .max(2_000, '공고 URL은 2,000자 이하여야 합니다.')
  .refine((value) => {
    try {
      const url = new URL(value)
      return url.protocol === 'http:' || url.protocol === 'https:'
    } catch {
      return false
    }
  }, 'HTTP(S) 공고 URL을 입력해 주세요.')

const createFormSchema = z.object({
  sourceUrl: httpUrl,
  companyName: optionalText(200),
  positionName: optionalText(300),
  descriptionText: z.string().max(200_000, '공고 본문은 200,000자 이하여야 합니다.'),
  deadlineAt: z.string(),
})

const updateFormSchema = z.object({
  companyName: optionalText(200),
  title: optionalText(300),
  positionName: optionalText(300),
  descriptionText: z.string().max(200_000, '공고 본문은 200,000자 이하여야 합니다.'),
  deadlineAt: z.string(),
  version: z.number().int().nonnegative(),
})

export interface JobCreateForm {
  sourceUrl: string
  companyName: string
  positionName: string
  descriptionText: string
  deadlineAt: string
}

export interface JobUpdateForm {
  companyName: string
  title: string
  positionName: string
  descriptionText: string
  deadlineAt: string
  version: number
}

export function validateJobCreateForm(form: JobCreateForm): {
  data: CreateJobRequest | null
  fieldErrors: Record<string, string>
} {
  const parsed = createFormSchema.safeParse(form)
  if (!parsed.success) return { data: null, fieldErrors: flattenErrors(parsed.error) }
  const deadlineAt = localDateTimeToInstant(parsed.data.deadlineAt)
  if (parsed.data.deadlineAt !== '' && deadlineAt === null) {
    return { data: null, fieldErrors: { deadlineAt: '올바른 마감 일시를 입력해 주세요.' } }
  }
  return {
    data: {
      sourceUrl: parsed.data.sourceUrl,
      companyName: nullable(parsed.data.companyName),
      positionName: nullable(parsed.data.positionName),
      descriptionText: nullable(parsed.data.descriptionText),
      deadlineAt,
    },
    fieldErrors: {},
  }
}

export function validateJobUpdateForm(form: JobUpdateForm): {
  data: UpdateJobRequest | null
  fieldErrors: Record<string, string>
} {
  const parsed = updateFormSchema.safeParse(form)
  if (!parsed.success) return { data: null, fieldErrors: flattenErrors(parsed.error) }
  const deadlineAt = localDateTimeToInstant(parsed.data.deadlineAt)
  if (parsed.data.deadlineAt !== '' && deadlineAt === null) {
    return { data: null, fieldErrors: { deadlineAt: '올바른 마감 일시를 입력해 주세요.' } }
  }
  return {
    data: {
      companyName: nullable(parsed.data.companyName),
      title: nullable(parsed.data.title),
      positionName: nullable(parsed.data.positionName),
      descriptionText: nullable(parsed.data.descriptionText),
      deadlineAt,
      version: parsed.data.version,
    },
    fieldErrors: {},
  }
}

export function instantToLocalDateTime(value: string | null): string {
  if (value === null) return ''
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function localDateTimeToInstant(value: string): string | null {
  if (value === '') return null
  const time = Date.parse(value)
  return Number.isNaN(time) ? null : new Date(time).toISOString()
}

function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function flattenErrors(error: z.ZodError): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const issue of error.issues) {
    const field = String(issue.path[0] ?? 'form')
    errors[field] ??= issue.message
  }
  return errors
}
