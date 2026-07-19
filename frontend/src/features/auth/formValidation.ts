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

export interface ValidationResult<T> {
  data: T | null
  fieldErrors: Record<string, string>
}

const emailSchema = z
  .string()
  .trim()
  .min(3, '이메일은 3자 이상 입력해 주세요.')
  .max(320, '이메일은 320자 이하로 입력해 주세요.')
  .email('올바른 이메일 형식을 입력해 주세요.')

const signupPasswordSchema = z.string().superRefine((password, context) => {
  const bytes = utf8ByteLength(password)
  if (bytes < 10 || bytes > 72) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호는 UTF-8 기준 10~72바이트여야 합니다.',
    })
  }
})

const loginPasswordSchema = z.string().superRefine((password, context) => {
  const bytes = utf8ByteLength(password)
  if (bytes < 1 || bytes > 72) {
    context.addIssue({
      code: 'custom',
      message: '비밀번호는 UTF-8 기준 1~72바이트여야 합니다.',
    })
  }
})

const signupSchema = z
  .object({
    email: emailSchema,
    password: signupPasswordSchema,
    passwordConfirm: z.string(),
    displayName: z
      .string()
      .trim()
      .min(1, '표시 이름을 입력해 주세요.')
      .max(100, '표시 이름은 100자 이하로 입력해 주세요.')
      .refine(
        (displayName) => !/[\p{Cc}/\\]/u.test(displayName),
        '표시 이름에 제어 문자나 경로 구분자를 사용할 수 없습니다.',
      ),
    termsAgreed: z.boolean().refine((agreed) => agreed, '이용약관 동의가 필요합니다.'),
    aiConsent: z.boolean().refine((agreed) => agreed, 'AI 처리 동의가 필요합니다.'),
  })
  .refine((form) => form.password === form.passwordConfirm, {
    message: '비밀번호가 일치하지 않습니다.',
    path: ['passwordConfirm'],
  })

const loginSchema = z.object({
  email: emailSchema,
  password: loginPasswordSchema,
})

export function validateSignupForm(values: SignupFormValues): ValidationResult<SignupFormValues> {
  return validate(signupSchema, values)
}

export function validateLoginForm(values: LoginFormValues): ValidationResult<LoginFormValues> {
  return validate(loginSchema, values)
}

export function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).byteLength
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
