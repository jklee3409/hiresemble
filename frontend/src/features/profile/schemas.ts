import { z } from 'zod'

import type {
  AwardCreateRequest,
  CareerCreateRequest,
  CertificationCreateRequest,
  EducationCreateRequest,
  EducationStatus,
  LanguageScoreCreateRequest,
  ProfileWrite,
} from '@/shared/api/contracts'

export interface ValidationResult<T> {
  data: T | null
  fieldErrors: Record<string, string>
}

export interface ProfileFormValues {
  legalName: string
  introduction: string
  desiredRoles: string[]
  desiredIndustries: string[]
  desiredLocations: string[]
  expectedGraduationDate: string
  version: number
}

export interface EducationFormValues {
  schoolName: string
  major: string
  degree: string
  educationStatus: EducationStatus
  admissionDate: string
  graduationDate: string
  gpa: string
  gpaScale: string
  isPrimary: boolean
  description: string
}

export interface CertificationFormValues {
  name: string
  issuer: string
  credentialNumber: string
  acquiredDate: string
  expiresAt: string
  description: string
}

export interface LanguageScoreFormValues {
  testName: string
  score: string
  grade: string
  testedAt: string
  expiresAt: string
}

export interface AwardFormValues {
  name: string
  organizer: string
  awardedAt: string
  description: string
}

export interface CareerFormValues {
  organization: string
  position: string
  employmentType: string
  startedAt: string
  endedAt: string
  isCurrent: boolean
  responsibilities: string
  achievements: string
}

const optionalDate = z.union([z.literal(''), z.iso.date()])

const optionalText = (max: number) => z.string().trim().max(max, `${max}자 이하로 입력해 주세요.`)

const requiredText = (max: number) =>
  z.string().trim().min(1, '필수 입력입니다.').max(max, `${max}자 이하로 입력해 주세요.`)

const desiredList = z
  .array(requiredText(100))
  .max(10, '최대 10개까지 입력할 수 있습니다.')
  .transform((values) => values.map((value) => value.trim()))
  .superRefine((values, context) => {
    const canonical = values.map((value) => value.toLocaleLowerCase())
    if (new Set(canonical).size !== canonical.length) {
      context.addIssue({ code: 'custom', message: '중복 항목을 제거해 주세요.' })
    }
  })

const profileSchema = z.object({
  legalName: optionalText(100),
  introduction: optionalText(2000),
  desiredRoles: desiredList,
  desiredIndustries: desiredList,
  desiredLocations: desiredList,
  expectedGraduationDate: optionalDate,
  version: z.number().int().nonnegative(),
})

const educationSchema = z
  .object({
    schoolName: requiredText(200),
    major: optionalText(200),
    degree: optionalText(100),
    educationStatus: z.enum([
      'ENROLLED',
      'LEAVE_OF_ABSENCE',
      'EXPECTED_GRADUATION',
      'GRADUATED',
      'WITHDRAWN',
    ]),
    admissionDate: optionalDate,
    graduationDate: optionalDate,
    gpa: z.string().trim(),
    gpaScale: z.string().trim(),
    isPrimary: z.boolean(),
    description: optionalText(5000),
  })
  .superRefine((value, context) => {
    validateDateOrder(value.admissionDate, value.graduationDate, 'graduationDate', context)
    const hasGpa = value.gpa !== ''
    const hasScale = value.gpaScale !== ''
    if (hasGpa !== hasScale) {
      context.addIssue({
        code: 'custom',
        path: ['gpa'],
        message: '학점과 기준 학점을 함께 입력해 주세요.',
      })
      return
    }
    if (hasGpa) {
      const gpa = Number(value.gpa)
      const scale = Number(value.gpaScale)
      if (!Number.isFinite(gpa) || gpa < 0 || gpa > 10) {
        context.addIssue({ code: 'custom', path: ['gpa'], message: '학점은 0~10 범위여야 합니다.' })
      }
      if (!Number.isFinite(scale) || scale < 0.01 || scale > 10) {
        context.addIssue({
          code: 'custom',
          path: ['gpaScale'],
          message: '기준 학점은 0.01~10 범위여야 합니다.',
        })
      }
      if (Number.isFinite(gpa) && Number.isFinite(scale) && gpa > scale) {
        context.addIssue({
          code: 'custom',
          path: ['gpa'],
          message: '학점은 기준 학점보다 클 수 없습니다.',
        })
      }
    }
  })

const certificationSchema = z
  .object({
    name: requiredText(200),
    issuer: optionalText(200),
    credentialNumber: optionalText(200),
    acquiredDate: optionalDate,
    expiresAt: optionalDate,
    description: optionalText(5000),
  })
  .superRefine((value, context) =>
    validateDateOrder(value.acquiredDate, value.expiresAt, 'expiresAt', context),
  )

const languageSchema = z
  .object({
    testName: requiredText(100),
    score: requiredText(100),
    grade: optionalText(100),
    testedAt: optionalDate,
    expiresAt: optionalDate,
  })
  .superRefine((value, context) =>
    validateDateOrder(value.testedAt, value.expiresAt, 'expiresAt', context),
  )

const awardSchema = z.object({
  name: requiredText(200),
  organizer: optionalText(200),
  awardedAt: optionalDate,
  description: optionalText(5000),
})

const careerSchema = z
  .object({
    organization: requiredText(200),
    position: optionalText(200),
    employmentType: optionalText(50),
    startedAt: optionalDate,
    endedAt: optionalDate,
    isCurrent: z.boolean(),
    responsibilities: optionalText(20000),
    achievements: optionalText(20000),
  })
  .superRefine((value, context) => {
    if (value.isCurrent && value.endedAt !== '') {
      context.addIssue({
        code: 'custom',
        path: ['endedAt'],
        message: '재직 중인 경력은 종료일을 입력할 수 없습니다.',
      })
    }
    validateDateOrder(value.startedAt, value.endedAt, 'endedAt', context)
  })

export function validateProfileForm(values: ProfileFormValues): ValidationResult<ProfileWrite> {
  const parsed = validate(profileSchema, values)
  if (parsed.data === null) return parsed
  return {
    fieldErrors: {},
    data: {
      legalName: nullable(parsed.data.legalName),
      introduction: nullable(parsed.data.introduction),
      desiredRoles: parsed.data.desiredRoles,
      desiredIndustries: parsed.data.desiredIndustries,
      desiredLocations: parsed.data.desiredLocations,
      expectedGraduationDate: nullable(parsed.data.expectedGraduationDate),
      version: parsed.data.version,
    },
  }
}

export function validateEducationForm(
  values: EducationFormValues,
): ValidationResult<EducationCreateRequest> {
  const parsed = validate(educationSchema, values)
  if (parsed.data === null) return { data: null, fieldErrors: parsed.fieldErrors }
  return {
    fieldErrors: {},
    data: {
      schoolName: parsed.data.schoolName,
      major: nullable(parsed.data.major),
      degree: nullable(parsed.data.degree),
      educationStatus: parsed.data.educationStatus,
      admissionDate: nullable(parsed.data.admissionDate),
      graduationDate: nullable(parsed.data.graduationDate),
      gpa: parsed.data.gpa === '' ? null : Number(parsed.data.gpa),
      gpaScale: parsed.data.gpaScale === '' ? null : Number(parsed.data.gpaScale),
      isPrimary: parsed.data.isPrimary,
      description: nullable(parsed.data.description),
    },
  }
}

export function validateCertificationForm(
  values: CertificationFormValues,
): ValidationResult<CertificationCreateRequest> {
  const parsed = validate(certificationSchema, values)
  if (parsed.data === null) return { data: null, fieldErrors: parsed.fieldErrors }
  return {
    fieldErrors: {},
    data: {
      name: parsed.data.name,
      issuer: nullable(parsed.data.issuer),
      credentialNumber: nullable(parsed.data.credentialNumber),
      acquiredDate: nullable(parsed.data.acquiredDate),
      expiresAt: nullable(parsed.data.expiresAt),
      description: nullable(parsed.data.description),
      evidenceDocumentId: null,
    },
  }
}

export function validateLanguageScoreForm(
  values: LanguageScoreFormValues,
): ValidationResult<LanguageScoreCreateRequest> {
  const parsed = validate(languageSchema, values)
  if (parsed.data === null) return { data: null, fieldErrors: parsed.fieldErrors }
  return {
    fieldErrors: {},
    data: {
      testName: parsed.data.testName,
      score: parsed.data.score,
      grade: nullable(parsed.data.grade),
      testedAt: nullable(parsed.data.testedAt),
      expiresAt: nullable(parsed.data.expiresAt),
      evidenceDocumentId: null,
    },
  }
}

export function validateAwardForm(values: AwardFormValues): ValidationResult<AwardCreateRequest> {
  const parsed = validate(awardSchema, values)
  if (parsed.data === null) return { data: null, fieldErrors: parsed.fieldErrors }
  return {
    fieldErrors: {},
    data: {
      name: parsed.data.name,
      organizer: nullable(parsed.data.organizer),
      awardedAt: nullable(parsed.data.awardedAt),
      description: nullable(parsed.data.description),
      evidenceDocumentId: null,
    },
  }
}

export function validateCareerForm(
  values: CareerFormValues,
): ValidationResult<CareerCreateRequest> {
  const parsed = validate(careerSchema, values)
  if (parsed.data === null) return parsed
  return {
    fieldErrors: {},
    data: {
      organization: parsed.data.organization,
      position: nullable(parsed.data.position),
      employmentType: nullable(parsed.data.employmentType),
      startedAt: nullable(parsed.data.startedAt),
      endedAt: parsed.data.isCurrent ? null : nullable(parsed.data.endedAt),
      isCurrent: parsed.data.isCurrent,
      responsibilities: nullable(parsed.data.responsibilities),
      achievements: nullable(parsed.data.achievements),
    },
  }
}

export function parseDesiredItems(value: string): string[] {
  return value
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function validate<T>(schema: z.ZodType<T>, value: unknown): ValidationResult<T> {
  const result = schema.safeParse(value)
  if (result.success) return { data: result.data, fieldErrors: {} }
  const fieldErrors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (typeof field === 'string' && fieldErrors[field] === undefined) {
      fieldErrors[field] = issue.message
    }
  }
  return { data: null, fieldErrors }
}

function validateDateOrder(
  start: string,
  end: string,
  path: string,
  context: z.RefinementCtx,
): void {
  if (start !== '' && end !== '' && start > end) {
    context.addIssue({
      code: 'custom',
      path: [path],
      message: '종료일은 시작일보다 빠를 수 없습니다.',
    })
  }
}
