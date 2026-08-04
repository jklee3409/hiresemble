import { z } from 'zod'

export interface SignupFormValues {
  email: string
  password: string
  passwordConfirm: string
  displayName: string
  termsAgreed: boolean
  aiConsent: boolean
}

export interface LoginFormValues {
  email: string
  password: string
}

export interface DisplayNameFormValues {
  displayName: string
}

export interface ValidationResult<T> {
  data: T | null
  fieldErrors: Record<string, string>
}

export type SignupCredentialField = 'email' | 'password' | 'passwordConfirm'

export interface SignupPasswordChecks {
  characterCount: number
  byteLength: number
  letter: boolean
  number: boolean
  specialCharacter: boolean
}

const emailSchema = z
  .string()
  .trim()
  .min(1, '이메일을 입력해 주세요.')
  .min(3, '이메일 형식을 확인해 주세요.')
  .max(320, '이메일 형식을 확인해 주세요.')
  .email('이메일 형식을 확인해 주세요.')
  .refine((email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(email), '이메일 형식을 확인해 주세요.')

const signupPasswordSchema = z.string().superRefine((password, context) => {
  const checks = signupPasswordChecks(password)
  if (checks.characterCount < 10) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호는 10자 이상 입력해주세요.',
    })
  } else if (checks.byteLength > 72) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호가 너무 길어요. 조금 짧게 입력해 주세요.',
    })
  } else if (!checks.letter || !checks.number || !checks.specialCharacter) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호에 문자, 숫자, 특수문자를 각각 1개 이상 포함해 주세요.',
    })
  }
})

const loginPasswordSchema = z.string().superRefine((password, context) => {
  const bytes = utf8ByteLength(password)
  if (bytes < 1) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호를 입력해 주세요.',
    })
  } else if (bytes > 72) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호가 너무 깁니다. 조금 짧게 입력해 주세요.',
    })
  }
})

const displayNameSchema = z
  .string()
  .trim()
  .min(1, '닉네임을 입력해 주세요.')
  .max(100, '닉네임은 100자 이하로 입력해 주세요.')
  .refine(
    (displayName) => !/[\p{Cc}/\\]/u.test(displayName),
    '닉네임에 줄바꿈이나 /, \\를 사용할 수 없어요.',
  )

const signupSchema = z
  .object({
    email: emailSchema,
    password: signupPasswordSchema,
    passwordConfirm: z.string(),
    displayName: displayNameSchema,
    termsAgreed: z.boolean().refine((agreed) => agreed, '필수 이용약관에 동의해 주세요.'),
    aiConsent: z.boolean().refine((agreed) => agreed, '필수 AI 처리 안내에 동의해 주세요.'),
  })
  .refine((form) => form.password === form.passwordConfirm, {
    message: '입력한 비밀번호가 서로 달라요.',
    path: ['passwordConfirm'],
  })

const loginSchema = z.object({
  email: emailSchema,
  password: loginPasswordSchema,
})

const displayNameFormSchema = z.object({
  displayName: displayNameSchema,
})

export function validateSignupForm(values: SignupFormValues): ValidationResult<SignupFormValues> {
  return validate(signupSchema, values)
}

export function validateSignupCredentialField(
  field: SignupCredentialField,
  values: SignupFormValues,
): string | undefined {
  if (field === 'passwordConfirm') {
    return values.password === values.passwordConfirm ? undefined : '입력한 비밀번호가 서로 달라요.'
  }

  const schema = field === 'email' ? emailSchema : signupPasswordSchema
  const result = schema.safeParse(values[field])
  return result.success ? undefined : result.error.issues[0]?.message
}

export function validateLoginForm(values: LoginFormValues): ValidationResult<LoginFormValues> {
  return validate(loginSchema, values)
}

export function validateDisplayNameForm(
  values: DisplayNameFormValues,
): ValidationResult<DisplayNameFormValues> {
  return validate(displayNameFormSchema, values)
}

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).byteLength
}

export function signupPasswordChecks(value: string): SignupPasswordChecks {
  const characterCount = Array.from(value).length
  const byteLength = utf8ByteLength(value)
  return {
    characterCount,
    byteLength,
    letter: /\p{L}/u.test(value),
    number: /\p{N}/u.test(value),
    specialCharacter: /[\p{P}\p{S}]/u.test(value),
  }
}

function validate<T>(schema: z.ZodType<T>, value: unknown): ValidationResult<T> {
  const result = schema.safeParse(value)
  if (result.success) {
    return { data: result.data, fieldErrors: {} }
  }

  const fieldErrors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0]
    if (typeof field === 'string' && fieldErrors[field] === undefined) {
      fieldErrors[field] = issue.message
    }
  }

  return { data: null, fieldErrors }
}
