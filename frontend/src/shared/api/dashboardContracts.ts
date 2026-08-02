import { z } from 'zod'

import { JOB_STATUSES } from './jobContracts'

const uuidSchema = z.uuid()
const instantSchema = z.iso.datetime({ offset: true })
const monthSchema = z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/)
const localDateSchema = z.iso.date()
const countSchema = z.number().int().nonnegative()
const profileCompletionItemSchema = z.enum([
  'LEGAL_NAME',
  'DESIRED_ROLE',
  'DESIRED_INDUSTRY',
  'DESIRED_LOCATION',
  'PRIMARY_EDUCATION',
])
const educationLevelSchema = z.enum([
  'OTHER',
  'HIGH_SCHOOL',
  'ASSOCIATE',
  'BACHELOR',
  'MASTER',
  'DOCTORATE',
])
const educationStatusSchema = z.enum([
  'ENROLLED',
  'LEAVE_OF_ABSENCE',
  'EXPECTED_GRADUATION',
  'GRADUATED',
  'WITHDRAWN',
])

export const dashboardDeadlineJobSchema = z
  .object({
    id: uuidSchema,
    companyName: z.string().max(200).nullable(),
    title: z.string().max(300).nullable(),
    positionName: z.string().max(300).nullable(),
    status: z.enum(JOB_STATUSES),
    deadlineAt: instantSchema,
  })
  .strict()

export const dashboardSchema = z
  .object({
    generatedAt: instantSchema,
    month: monthSchema,
    profile: z
      .object({
        displayName: z.string().min(1).max(100),
        legalName: z.string().min(1).max(100).nullable(),
        desiredRoles: z.array(z.string().min(1).max(100)).max(10),
        desiredLocations: z.array(z.string().min(1).max(100)).max(10),
        completed: z.boolean(),
        completionPercent: z.number().int().min(0).max(100),
        missingItems: z.array(profileCompletionItemSchema).max(5),
        primaryEducation: z
          .object({
            schoolName: z.string().min(1).max(200),
            major: z.string().max(200).nullable(),
            degree: z.string().max(100).nullable(),
            educationLevel: educationLevelSchema,
            educationStatus: educationStatusSchema,
          })
          .strict()
          .nullable(),
      })
      .strict(),
    documents: z
      .object({
        registeredCount: countSchema,
        processingCount: countSchema,
        needsActionCount: countSchema,
      })
      .strict(),
    jobs: z
      .object({
        registeredCount: countSchema,
        preparingCount: countSchema,
        submittedCount: countSchema,
      })
      .strict(),
    agentRuns: z.object({ activeCount: countSchema }).strict(),
    deadlineDays: z
      .array(
        z
          .object({
            date: localDateSchema,
            count: z.number().int().positive(),
            items: z.array(dashboardDeadlineJobSchema),
          })
          .strict()
          .refine((value) => value.count === value.items.length, '마감 공고 수가 일치해야 합니다.'),
      )
      .max(31),
  })
  .strict()

export const careerGuidePostSchema = z
  .object({
    id: uuidSchema,
    status: z.literal('PUBLISHED'),
    displayOrder: countSchema,
    category: z.string().min(1).max(60),
    title: z.string().min(1).max(200),
    summary: z.string().min(1).max(500),
    body: z.string().min(1).max(10_000),
    publishedAt: instantSchema,
    version: countSchema,
  })
  .strict()

export const careerGuidePostsSchema = z.array(careerGuidePostSchema).max(100)

export type DashboardDto = z.infer<typeof dashboardSchema>
export type DashboardDeadlineJobDto = z.infer<typeof dashboardDeadlineJobSchema>
export type CareerGuidePostDto = z.infer<typeof careerGuidePostSchema>
