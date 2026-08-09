import { z } from 'zod'

const utf8Length = (value: string) => new TextEncoder().encode(value).byteLength

const currentPasswordSchema = z
  .string()
  .refine((value) => utf8Length(value) >= 1, '현재 비밀번호를 입력해 주세요.')
  .refine((value) => utf8Length(value) <= 72, '비밀번호가 너무 깁니다.')

const newPasswordSchema = z.string().superRefine((value, context) => {
  if (Array.from(value).length < 10) {
    context.addIssue({ code: 'custom', message: '새 비밀번호는 10자 이상 입력해 주세요.' })
  } else if (utf8Length(value) > 72) {
    context.addIssue({ code: 'custom', message: '새 비밀번호가 너무 깁니다.' })
  } else if (!/\p{L}/u.test(value) || !/\p{N}/u.test(value) || !/[\p{P}\p{S}]/u.test(value)) {
    context.addIssue({
      code: 'custom',
      message: '문자, 숫자, 특수문자를 각각 1개 이상 포함해 주세요.',
    })
  }
})

export const passwordChangeRequestSchema = z
  .object({
    currentPassword: currentPasswordSchema,
    newPassword: newPasswordSchema,
  })
  .strict()
  .refine((value) => value.currentPassword !== value.newPassword, {
    path: ['newPassword'],
    message: '현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.',
  })

export const accountDeletionRequestSchema = z
  .object({
    currentPassword: currentPasswordSchema,
    permanentDeletionConfirmed: z.literal(true),
  })
  .strict()

export const accountDeletionAcceptedSchema = z
  .object({
    deletionRequestId: z.uuid(),
    purgeBy: z.iso.datetime({ offset: true }),
  })
  .strict()

export type PasswordChangeRequest = z.infer<typeof passwordChangeRequestSchema>
export type AccountDeletionRequest = z.infer<typeof accountDeletionRequestSchema>
export type AccountDeletionAcceptedDto = z.infer<typeof accountDeletionAcceptedSchema>
