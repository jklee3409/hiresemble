<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import {
  type SignupFormValues,
  type SignupCredentialField,
  signupPasswordChecks,
  validateSignupCredentialField,
  validateSignupForm,
} from '@/features/auth/formValidation'
import { authErrorMessage, fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

type ConsentDetail = 'service' | 'ai'

const authStore = useAuthStore()
const router = useRouter()
const form = reactive<SignupFormValues>({
  email: '',
  password: '',
  passwordConfirm: '',
  displayName: '',
  termsAgreed: false,
  aiConsent: false,
})
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const isSubmitting = ref(false)
const passwordVisible = ref(false)
const passwordConfirmVisible = ref(false)
const consentDetail = ref<ConsentDetail | null>(null)
const consentDialog = ref<HTMLElement | null>(null)
const consentCloseButton = ref<HTMLButtonElement | null>(null)
const passwordChecks = computed(() => signupPasswordChecks(form.password))
let consentTrigger: HTMLElement | null = null
let bodyOverflowBeforeConsent = ''

watch(consentDetail, async (detail) => {
  if (detail === null) return
  bodyOverflowBeforeConsent = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', handleConsentKeydown)
  await nextTick()
  consentCloseButton.value?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleConsentKeydown)
  if (consentDetail.value !== null) document.body.style.overflow = bodyOverflowBeforeConsent
})

async function submit(): Promise<void> {
  generalError.value = ''
  const validation = validateSignupForm(form)
  fieldErrors.value = validation.fieldErrors

  if (validation.data === null) {
    await focusFirstError()
    return
  }

  isSubmitting.value = true
  try {
    await authStore.signup({
      email: validation.data.email,
      password: validation.data.password,
      displayName: validation.data.displayName,
      termsAgreed: validation.data.termsAgreed,
      aiConsent: validation.data.aiConsent,
    })
    await router.replace({ name: 'onboarding' })
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (apiError.code === 'EMAIL_ALREADY_REGISTERED' && fieldErrors.value.email === undefined) {
      fieldErrors.value.email = '이미 가입된 이메일이에요. 로그인해 주세요.'
    }
    generalError.value = authErrorMessage(apiError)
    isSubmitting.value = false
    await focusFirstError()
  } finally {
    isSubmitting.value = false
  }
}

async function focusFirstError(): Promise<void> {
  await nextTick()
  const firstField = Object.keys(fieldErrors.value)[0]
  if (firstField === undefined) {
    return
  }

  document.getElementById(`signup-${firstField}`)?.focus()
}

function validateCredentialField(field: SignupCredentialField): void {
  const error = validateSignupCredentialField(field, form)
  const nextErrors = { ...fieldErrors.value }
  if (error === undefined) delete nextErrors[field]
  else nextErrors[field] = error
  fieldErrors.value = nextErrors
}

function revalidateCredentialField(field: SignupCredentialField): void {
  if (fieldErrors.value[field] !== undefined) validateCredentialField(field)
}

function passwordRuleClass(valid: boolean): Record<string, boolean> {
  return {
    'password-guidance__rule--valid': form.password !== '' && valid,
    'password-guidance__rule--invalid': form.password !== '' && !valid,
  }
}

function passwordRuleMarker(valid: boolean): string {
  return form.password !== '' && valid ? '✓' : '•'
}

function openConsentDetail(detail: ConsentDetail, event: MouseEvent): void {
  consentTrigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  consentDetail.value = detail
}

function closeConsentDetail(): void {
  if (consentDetail.value === null) return
  consentDetail.value = null
  document.removeEventListener('keydown', handleConsentKeydown)
  document.body.style.overflow = bodyOverflowBeforeConsent
  const trigger = consentTrigger
  consentTrigger = null
  void nextTick(() => trigger?.focus())
}

function handleConsentKeydown(event: KeyboardEvent): void {
  if (consentDetail.value === null) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeConsentDetail()
    return
  }
  if (event.key !== 'Tab' || consentDialog.value === null) return

  const focusable = Array.from(
    consentDialog.value.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex="-1"])',
    ),
  )
  const first = focusable[0]
  const last = focusable.at(-1)
  if (first === undefined || last === undefined) {
    event.preventDefault()
    consentDialog.value.focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <div class="auth-page">
    <p class="page-eyebrow">나만의 지원 준비 시작</p>
    <h1 class="page-title">회원가입</h1>
    <p class="page-description">취업 준비를 한곳에서 시작할 계정을 만들어 보세요.</p>

    <form class="auth-page__form" novalidate :aria-busy="isSubmitting" @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="signup-email">이메일</label>
        <input
          id="signup-email"
          v-model="form.email"
          class="control"
          type="email"
          inputmode="email"
          autocomplete="email"
          placeholder="name@example.com"
          autofocus
          :aria-invalid="Boolean(fieldErrors.email)"
          :aria-describedby="fieldErrors.email ? 'signup-email-error' : undefined"
          :disabled="isSubmitting"
          @blur="validateCredentialField('email')"
          @input="revalidateCredentialField('email')"
        />
        <p v-if="fieldErrors.email" id="signup-email-error" class="field-error">
          {{ fieldErrors.email }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-displayName">닉네임</label>
        <input
          id="signup-displayName"
          v-model="form.displayName"
          class="control"
          type="text"
          autocomplete="nickname"
          placeholder="서비스에서 사용할 이름"
          :aria-invalid="Boolean(fieldErrors.displayName)"
          :aria-describedby="fieldErrors.displayName ? 'signup-displayName-error' : undefined"
          :disabled="isSubmitting"
        />
        <p v-if="fieldErrors.displayName" id="signup-displayName-error" class="field-error">
          {{ fieldErrors.displayName }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-password">비밀번호</label>
        <div class="password-control">
          <input
            id="signup-password"
            v-model="form.password"
            class="control"
            :type="passwordVisible ? 'text' : 'password'"
            autocomplete="new-password"
            :aria-invalid="Boolean(fieldErrors.password)"
            :aria-describedby="
              fieldErrors.password
                ? 'signup-password-help signup-password-error'
                : 'signup-password-help'
            "
            :disabled="isSubmitting"
            @blur="validateCredentialField('password')"
            @input="revalidateCredentialField('password')"
          />
          <button
            type="button"
            class="password-control__toggle"
            :aria-label="passwordVisible ? '비밀번호 숨기기' : '비밀번호 보기'"
            :aria-pressed="passwordVisible"
            :disabled="isSubmitting"
            @click="passwordVisible = !passwordVisible"
          >
            {{ passwordVisible ? '숨기기' : '보기' }}
          </button>
        </div>
        <div id="signup-password-help" class="password-guidance">
          <p class="password-guidance__title">안전한 비밀번호를 이렇게 만들어 주세요.</p>
          <ul>
            <li :class="passwordRuleClass(passwordChecks.characterCount >= 10)">
              <span class="password-guidance__marker" aria-hidden="true">
                {{ passwordRuleMarker(passwordChecks.characterCount >= 10) }}
              </span>
              <span>비밀번호는 10자 이상 입력해주세요.</span>
            </li>
            <li
              :class="
                passwordRuleClass(
                  passwordChecks.letter && passwordChecks.number && passwordChecks.specialCharacter,
                )
              "
            >
              <span class="password-guidance__marker" aria-hidden="true">{{
                passwordRuleMarker(
                  passwordChecks.letter && passwordChecks.number && passwordChecks.specialCharacter,
                )
              }}</span>
              <span>숫자/문자/특수 문자를 최소 한 글자 이상 포함해주세요.</span>
            </li>
            <li>
              <span class="password-guidance__marker" aria-hidden="true">•</span>
              <span>다른 서비스에서 쓰지 않는 비밀번호를 권장해요.</span>
            </li>
          </ul>
        </div>
        <p v-if="fieldErrors.password" id="signup-password-error" class="field-error">
          {{ fieldErrors.password }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="signup-passwordConfirm"> 비밀번호 확인 </label>
        <div class="password-control">
          <input
            id="signup-passwordConfirm"
            v-model="form.passwordConfirm"
            class="control"
            :type="passwordConfirmVisible ? 'text' : 'password'"
            autocomplete="new-password"
            :aria-invalid="Boolean(fieldErrors.passwordConfirm)"
            :aria-describedby="
              fieldErrors.passwordConfirm ? 'signup-passwordConfirm-error' : undefined
            "
            :disabled="isSubmitting"
            @blur="validateCredentialField('passwordConfirm')"
            @input="revalidateCredentialField('passwordConfirm')"
          />
          <button
            type="button"
            class="password-control__toggle"
            :aria-label="passwordConfirmVisible ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'"
            :aria-pressed="passwordConfirmVisible"
            :disabled="isSubmitting"
            @click="passwordConfirmVisible = !passwordConfirmVisible"
          >
            {{ passwordConfirmVisible ? '숨기기' : '보기' }}
          </button>
        </div>
        <p v-if="fieldErrors.passwordConfirm" id="signup-passwordConfirm-error" class="field-error">
          {{ fieldErrors.passwordConfirm }}
        </p>
      </div>

      <div class="consent-option">
        <div class="consent-option__row">
          <label class="consent-option__label" for="signup-termsAgreed">
            <input
              id="signup-termsAgreed"
              v-model="form.termsAgreed"
              class="checkbox-control"
              type="checkbox"
              :aria-invalid="Boolean(fieldErrors.termsAgreed)"
              :aria-describedby="
                fieldErrors.termsAgreed
                  ? 'signup-termsAgreed-summary signup-termsAgreed-error'
                  : 'signup-termsAgreed-summary'
              "
              :disabled="isSubmitting"
            />
            <span>
              <strong>이용약관·개인정보 수집·이용 동의 <em>필수</em></strong>
              <small id="signup-termsAgreed-summary">
                계정을 만들고 서비스를 제공하는 데 필요한 내용이에요.
              </small>
            </span>
          </label>
          <button
            type="button"
            class="consent-option__detail"
            aria-haspopup="dialog"
            aria-label="이용약관·개인정보 상세 보기"
            :disabled="isSubmitting"
            @click="openConsentDetail('service', $event)"
          >
            상세 보기
          </button>
        </div>
        <p
          v-if="fieldErrors.termsAgreed"
          id="signup-termsAgreed-error"
          class="field-error consent-option__error"
        >
          {{ fieldErrors.termsAgreed }}
        </p>
      </div>

      <div class="consent-option">
        <div class="consent-option__row">
          <label class="consent-option__label" for="signup-aiConsent">
            <input
              id="signup-aiConsent"
              v-model="form.aiConsent"
              class="checkbox-control"
              type="checkbox"
              :aria-invalid="Boolean(fieldErrors.aiConsent)"
              :aria-describedby="
                fieldErrors.aiConsent
                  ? 'signup-aiConsent-summary signup-aiConsent-error'
                  : 'signup-aiConsent-summary'
              "
              :disabled="isSubmitting"
            />
            <span>
              <strong>취업 준비 지원을 위한 AI 처리 동의 <em>필수</em></strong>
              <small id="signup-aiConsent-summary">
                이력서와 공고를 정리하고 맞춤 결과를 만들 때 필요해요.
              </small>
            </span>
          </label>
          <button
            type="button"
            class="consent-option__detail"
            aria-haspopup="dialog"
            aria-label="AI 처리 상세 보기"
            :disabled="isSubmitting"
            @click="openConsentDetail('ai', $event)"
          >
            상세 보기
          </button>
        </div>
        <p
          v-if="fieldErrors.aiConsent"
          id="signup-aiConsent-error"
          class="field-error consent-option__error"
        >
          {{ fieldErrors.aiConsent }}
        </p>
      </div>

      <p v-if="generalError" class="alert alert--danger" role="alert">
        {{ generalError }}
      </p>

      <button
        class="button button--primary auth-page__submit"
        type="submit"
        :disabled="isSubmitting"
      >
        <span v-if="isSubmitting" class="button-spinner" aria-hidden="true" />
        {{ isSubmitting ? '가입 중…' : '가입하기' }}
      </button>
    </form>

    <p class="auth-page__alternate">
      이미 계정이 있나요?
      <RouterLink class="text-link" to="/login">로그인</RouterLink>
    </p>

    <Teleport to="body">
      <div v-if="consentDetail" class="consent-modal-backdrop" @click.self="closeConsentDetail">
        <section
          ref="consentDialog"
          class="consent-modal"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="`consent-modal-title-${consentDetail}`"
          tabindex="-1"
        >
          <header class="consent-modal__header">
            <div class="consent-modal__heading">
              <span class="consent-modal__badge">필수 · 자세히 보기</span>
              <h2 :id="`consent-modal-title-${consentDetail}`">
                {{
                  consentDetail === 'service' ? '이용약관·개인정보 수집 및 이용' : 'AI 처리 안내'
                }}
              </h2>
              <p>
                {{
                  consentDetail === 'service'
                    ? '가입 전 꼭 알아야 할 약속을 쉽게 정리했어요.'
                    : '내 정보를 어디에, 어떻게 사용하는지 알려드려요.'
                }}
              </p>
            </div>
            <button
              ref="consentCloseButton"
              type="button"
              class="consent-modal__close"
              :aria-label="
                consentDetail === 'service' ? '이용약관·개인정보 상세 닫기' : 'AI 처리 상세 닫기'
              "
              @click="closeConsentDetail"
            >
              <span aria-hidden="true">×</span>
            </button>
          </header>

          <div v-if="consentDetail === 'service'" class="consent-modal__body">
            <section class="consent-summary" aria-labelledby="service-summary-title">
              <p id="service-summary-title" class="consent-summary__eyebrow">한눈에 보기</p>
              <ul>
                <li>
                  <span aria-hidden="true">✓</span>
                  <div>
                    <strong>안전하게 저장해요</strong>
                    <p>비밀번호는 그대로 알아볼 수 없도록 바꾸어 보관해요.</p>
                  </div>
                </li>
                <li>
                  <span aria-hidden="true">✓</span>
                  <div>
                    <strong>필요한 정보만 받아요</strong>
                    <p>계정과 맞춤형 취업 준비 기능에 필요한 내용만 사용해요.</p>
                  </div>
                </li>
                <li>
                  <span aria-hidden="true">✓</span>
                  <div>
                    <strong>탈퇴하면 정리해요</strong>
                    <p>접근을 바로 막고, 사용자 자료를 24시간 안에 삭제해요.</p>
                  </div>
                </li>
              </ul>
            </section>

            <section class="consent-detail-section">
              <div class="consent-detail-section__heading">
                <span class="consent-detail-section__number">1</span>
                <h3>서비스 이용 약속</h3>
              </div>
              <div class="consent-detail-section__content">
                <ul>
                  <li>
                    Hiresemble은 사용자가 입력하고 승인한 경력 정보를 바탕으로 공고 분석,
                    자기소개서, 면접 준비를 돕는 서비스예요.
                  </li>
                  <li>
                    계정은 본인이 안전하게 관리하고, 다른 사람의 권리를 침해하거나 불법적인 자료를
                    등록하지 않아야 해요.
                  </li>
                  <li>
                    AI 결과는 취업 준비를 돕는 참고 자료예요. 제출 전 사실과 표현을 직접 확인해야
                    하며, 채용 결과를 보장하지 않아요.
                  </li>
                  <li>
                    서비스 안정성이나 다른 사용자의 권리를 해치는 이용은 제한될 수 있고, 중요한 약관
                    변경은 시행 전에 알릴게요.
                  </li>
                </ul>
              </div>
            </section>

            <section class="consent-detail-section">
              <div class="consent-detail-section__heading">
                <span class="consent-detail-section__number">2</span>
                <h3>개인정보를 이렇게 다뤄요</h3>
              </div>
              <div class="consent-detail-section__content">
                <dl class="consent-detail-grid">
                  <div>
                    <dt>수집하는 정보</dt>
                    <dd>
                      이메일, 안전하게 바꾸어 저장한 비밀번호, 닉네임, 동의 시각과 이후 직접
                      입력하거나 올린 프로필·경력·자료·공고·자기소개서·면접 정보예요.
                    </dd>
                  </div>
                  <div>
                    <dt>이용 목적</dt>
                    <dd>
                      계정·로그인 유지, 맞춤 취업 준비 기능 제공, 사용자 자료 저장과 복구, 보안 및
                      오류 대응
                    </dd>
                  </div>
                  <div>
                    <dt>보유 기간</dt>
                    <dd>
                      회원인 동안 보관해요. 탈퇴를 접수하면 바로 접근을 막고 사용자 자료는 24시간
                      안에 삭제해요. 개인을 알아볼 수 없는 삭제 확인 내용만 30일 동안 남겨요.
                    </dd>
                  </div>
                  <div>
                    <dt>동의를 거부하면</dt>
                    <dd>
                      동의하지 않을 권리가 있어요. 다만 계정 생성과 사용자 자료를 사용하는 핵심
                      서비스 제공이 어려워 회원가입을 진행할 수 없어요.
                    </dd>
                  </div>
                </dl>
              </div>
            </section>

            <aside class="consent-modal__notice">
              <span class="consent-modal__notice-icon" aria-hidden="true">✓</span>
              <div>
                <strong>비밀번호는 우리도 알아볼 수 없어요.</strong>
                <p>
                  입력한 비밀번호는 다시 알아볼 수 없는 형태로 바꾸어 안전하게 저장해요. 자료,
                  자기소개서, 면접 답변 내용도 기능 제공에 필요한 곳에서만 다뤄요.
                </p>
              </div>
            </aside>
          </div>

          <div v-else class="consent-modal__body">
            <section class="consent-summary" aria-labelledby="ai-summary-title">
              <p id="ai-summary-title" class="consent-summary__eyebrow">한눈에 보기</p>
              <ul>
                <li>
                  <span aria-hidden="true">✦</span>
                  <div>
                    <strong>OpenAI 기반으로 처리해요</strong>
                    <p>취업 준비 기능을 제공하기 위해 필요한 내용만 사용해요.</p>
                  </div>
                </li>
                <li>
                  <span aria-hidden="true">✦</span>
                  <div>
                    <strong>민감한 정보는 먼저 가려요</strong>
                    <p>전화번호나 상세 주소 등은 가능한 범위에서 보이지 않게 처리해요.</p>
                  </div>
                </li>
                <li>
                  <span aria-hidden="true">✦</span>
                  <div>
                    <strong>결과는 직접 확인해요</strong>
                    <p>저장하거나 제출하기 전에 사실과 표현을 확인할 수 있어요.</p>
                  </div>
                </li>
              </ul>
            </section>

            <section class="consent-detail-section">
              <div class="consent-detail-section__heading">
                <span class="consent-detail-section__number">1</span>
                <h3>어떤 정보를 사용하나요?</h3>
              </div>
              <div class="consent-detail-section__content">
                <ul>
                  <li>
                    사용자가 등록하거나 활용을 승인한 프로필·경력 근거, 이력서·포트폴리오 내용, 채용
                    공고, 자기소개서와 면접 답변을 기능 수행에 필요한 범위에서 처리해요.
                  </li>
                  <li>
                    문서 정리, 공고 적합도 분석, 자기소개서 초안·검증, 면접 질문과 답변 피드백에
                    OpenAI 기반 기능을 사용해요.
                  </li>
                  <li>
                    처리를 위해 전달한 내용은 OpenAI 서비스를 개선하는 데 사용되지 않아요. 다만
                    안전한 운영을 위해 OpenAI가 최대 30일 동안 보관할 수 있어요.
                  </li>
                  <li>
                    기업·직무 조사에는 공개된 웹 정보만 검색하며, 사용자 개인정보를 검색어로 보내지
                    않아요.
                  </li>
                </ul>
              </div>
            </section>

            <section class="consent-detail-section">
              <div class="consent-detail-section__heading">
                <span class="consent-detail-section__number">2</span>
                <h3>어떻게 보호하나요?</h3>
              </div>
              <div class="consent-detail-section__content">
                <ul>
                  <li>
                    OpenAI로 보내기 전에 전화번호, 이메일, 상세 주소, 주민등록번호, 계정
                    비밀정보처럼 안전을 위해 필요한 내용은 가능한 범위에서 먼저 가려요.
                  </li>
                  <li>
                    사용자가 승인한 경력 근거를 우선 사용하고, 전달한 전체 내용이나 문서 내용을
                    일반적인 서비스 기록에 남기지 않아요.
                  </li>
                  <li>
                    AI는 채용 결정을 내리지 않아요. 결과에는 오류나 누락이 있을 수 있어 저장하거나
                    제출하기 전에 사용자가 확인할 수 있게 해요.
                  </li>
                </ul>
              </div>
            </section>

            <aside class="consent-modal__notice">
              <span class="consent-modal__notice-icon" aria-hidden="true">i</span>
              <div>
                <strong>동의 여부는 직접 선택해요.</strong>
                <p>
                  동의하지 않을 권리가 있어요. 다만 AI가 핵심 기능에 필요해 동의하지 않으면 현재는
                  회원가입을 진행할 수 없어요.
                </p>
              </div>
            </aside>
          </div>

          <footer class="consent-modal__footer">
            <div>
              <strong>시행일 2026년 8월 4일</strong>
              <small>상세 내용만 확인해도 동의가 자동 선택되지 않아요.</small>
            </div>
            <button type="button" class="button button--primary" @click="closeConsentDetail">
              내용을 확인했어요
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.auth-page__form {
  display: grid;
  gap: 1rem;
  margin-top: 1.75rem;
}

.auth-page__submit {
  width: 100%;
  margin-top: 0.25rem;
}

.auth-page__alternate {
  margin: 1.5rem 0 0;
  color: var(--color-muted);
  font-size: 0.875rem;
  text-align: center;
}

.consent-option {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: 0.75rem;
}

.consent-option__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.consent-option__label {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 0.625rem;
}

.consent-option__label strong,
.consent-option__label small {
  display: block;
}

.consent-option__label strong {
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
}

.consent-option__label strong em {
  margin-left: 0.25rem;
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-style: normal;
}

.consent-option__label small {
  margin-top: 0.125rem;
  color: var(--color-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}

.consent-option__error {
  margin: 0.5rem 0 0 1.75rem;
}

.consent-option__detail {
  flex: 0 0 auto;
  min-height: 2.25rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-brand);
  padding: 0.35rem 0.25rem;
  font-size: 0.75rem;
  font-weight: 760;
  text-decoration: underline;
  text-underline-offset: 0.2rem;
}

.consent-option__detail:hover:not(:disabled) {
  background: var(--color-brand-soft);
  text-decoration: none;
}

.password-control {
  position: relative;
}

.password-control .control {
  padding-right: 4rem;
}

.password-control__toggle {
  position: absolute;
  top: 50%;
  right: 0.5rem;
  min-width: 2.75rem;
  min-height: 2.75rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-brand);
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 720;
  transform: translateY(-50%);
}

.password-control__toggle:hover:not(:disabled) {
  background: var(--color-brand-soft);
}

.password-guidance {
  margin-top: 0.5rem;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--color-brand-soft) 68%, white);
  padding: 0.75rem;
}

.password-guidance__title {
  margin: 0 0 0.4rem;
  color: var(--color-ink-soft);
  font-size: 0.75rem;
  font-weight: 740;
}

.password-guidance ul {
  display: grid;
  gap: 0.25rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.password-guidance li {
  display: flex;
  gap: 0.4rem;
  color: var(--color-muted);
  font-size: 0.75rem;
  line-height: 1.5;
}

.password-guidance li small {
  color: inherit;
  font-size: inherit;
}

.password-guidance__marker {
  width: 0.75rem;
  color: var(--color-brand);
  text-align: center;
}

.password-guidance__rule--valid {
  color: var(--color-success, #15803d) !important;
}

.password-guidance__rule--invalid {
  color: var(--color-danger, #c2413b) !important;
}

.consent-modal-backdrop {
  position: fixed;
  z-index: 90;
  inset: 0;
  display: grid;
  place-items: center;
  overflow-y: auto;
  background: rgb(9 23 46 / 68%);
  padding: 1.5rem;
  backdrop-filter: blur(6px);
}

.consent-modal {
  display: grid;
  width: min(100%, 46rem);
  max-height: min(50rem, calc(100dvh - 3rem));
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 82%);
  border-radius: 1.75rem;
  background: var(--color-surface, #fff);
  box-shadow:
    0 2rem 6rem rgb(5 20 48 / 34%),
    0 0.25rem 1rem rgb(5 20 48 / 10%);
}

.consent-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface, #fff);
  padding: 1.65rem 1.75rem 1.4rem;
}

.consent-modal__heading {
  min-width: 0;
}

.consent-modal__badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  padding: 0.3rem 0.7rem;
  font-size: 0.6875rem;
  font-weight: 780;
  letter-spacing: 0.01em;
}

.consent-modal__header h2 {
  margin: 0.65rem 0 0;
  color: var(--color-ink);
  font-size: clamp(1.3rem, 3vw, 1.7rem);
  line-height: 1.28;
  letter-spacing: -0.025em;
}

.consent-modal__heading > p {
  margin: 0.45rem 0 0;
  color: var(--color-muted);
  font-size: 0.8125rem;
  line-height: 1.55;
}

.consent-modal__close {
  display: grid;
  flex: 0 0 auto;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: var(--color-surface-subtle);
  color: var(--color-ink-soft);
  font-size: 1.5rem;
  line-height: 1;
  transition:
    background-color 140ms ease,
    color 140ms ease;
}

.consent-modal__close:hover {
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.consent-modal__body {
  display: grid;
  gap: 1rem;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 1.5rem 1.75rem 1.75rem;
  scrollbar-gutter: stable;
}

.consent-summary {
  border-radius: 1.25rem;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--color-brand-soft) 82%, white),
    color-mix(in srgb, var(--color-surface-subtle) 80%, white)
  );
  padding: 1.15rem;
}

.consent-summary__eyebrow {
  margin: 0 0 0.8rem;
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.consent-summary ul {
  display: grid;
  gap: 0.85rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.consent-summary li {
  display: grid;
  grid-template-columns: 1.7rem minmax(0, 1fr);
  gap: 0.65rem;
}

.consent-summary li > span {
  display: grid;
  width: 1.7rem;
  height: 1.7rem;
  place-items: center;
  border-radius: 999px;
  background: var(--color-surface, #fff);
  color: var(--color-brand);
  font-size: 0.75rem;
  font-weight: 850;
  box-shadow: 0 0.15rem 0.5rem rgb(31 103 225 / 10%);
}

.consent-summary strong {
  display: block;
  color: var(--color-ink);
  font-size: 0.875rem;
  line-height: 1.45;
}

.consent-summary li p {
  margin: 0.15rem 0 0;
  color: var(--color-muted);
  font-size: 0.78125rem;
  line-height: 1.55;
}

.consent-detail-section {
  display: grid;
  gap: 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-surface, #fff);
  padding: 1.15rem;
}

.consent-detail-section__heading {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.consent-detail-section__number {
  display: grid;
  flex: 0 0 auto;
  width: 1.6rem;
  height: 1.6rem;
  place-items: center;
  border-radius: 0.5rem;
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-weight: 800;
}

.consent-detail-section h3 {
  margin: 0;
  color: var(--color-ink);
  font-size: 0.9375rem;
  letter-spacing: -0.012em;
}

.consent-detail-section ul {
  display: grid;
  gap: 0.7rem;
  margin: 0;
  padding-left: 1.15rem;
}

.consent-detail-section li,
.consent-detail-grid dd {
  color: var(--color-muted);
  font-size: 0.875rem;
  line-height: 1.7;
}

.consent-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0;
}

.consent-detail-grid > div {
  border: 1px solid color-mix(in srgb, var(--color-border) 78%, transparent);
  border-radius: 0.875rem;
  background: var(--color-surface-subtle);
  padding: 0.9rem;
}

.consent-detail-grid dt {
  margin-bottom: 0.35rem;
  color: var(--color-ink-soft);
  font-size: 0.75rem;
  font-weight: 780;
}

.consent-detail-grid dd {
  margin: 0;
}

.consent-modal__notice {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr);
  gap: 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-brand) 24%, var(--color-border));
  border-radius: 1.1rem;
  background: var(--color-brand-soft);
  padding: 1rem;
}

.consent-modal__notice-icon {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: 0.7rem;
  background: var(--color-surface, #fff);
  font-size: 0.95rem;
  font-weight: 800;
}

.consent-modal__notice strong {
  color: var(--color-brand-strong, var(--color-brand));
  font-size: 0.8125rem;
}

.consent-modal__notice p {
  margin: 0.35rem 0 0;
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
  line-height: 1.65;
}

.consent-modal__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface, #fff);
  padding: 1rem 1.75rem 1.25rem;
}

.consent-modal__footer > div {
  display: grid;
  gap: 0.2rem;
}

.consent-modal__footer strong {
  color: var(--color-ink-soft);
  font-size: 0.75rem;
}

.consent-modal__footer small {
  color: var(--color-muted);
  font-size: 0.6875rem;
  line-height: 1.4;
}

.consent-modal__footer .button {
  flex: 0 0 auto;
  min-width: 10.5rem;
}

@media (max-width: 40rem) {
  .consent-modal-backdrop {
    align-items: end;
    padding: 0;
  }

  .consent-modal {
    max-height: calc(100dvh - 1rem);
    border-top-left-radius: 1.5rem;
    border-top-right-radius: 1.5rem;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-bottom-right-radius: 0;
    border-bottom-left-radius: 0;
  }

  .consent-modal__header,
  .consent-modal__body {
    padding-right: 1rem;
    padding-left: 1rem;
  }

  .consent-modal__header {
    padding-top: 1.25rem;
    padding-bottom: 1rem;
  }

  .consent-modal__body {
    padding-top: 1rem;
    padding-bottom: 1rem;
  }

  .consent-detail-grid {
    grid-template-columns: 1fr;
  }

  .consent-modal__footer {
    align-items: stretch;
    flex-direction: column;
    gap: 0.75rem;
    padding: 0.85rem 1rem max(1rem, env(safe-area-inset-bottom));
  }

  .consent-modal__footer .button {
    width: 100%;
    min-width: 0;
  }
}
</style>
