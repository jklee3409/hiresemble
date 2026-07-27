<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import {
  type EducationFormValues,
  type ProfileFormValues,
  validateEducationForm,
  validateProfileForm,
} from '@/features/profile/schemas'
import type { EducationCreateRequest, ProfileDto, ProfileWrite } from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const userId = computed(() => authStore.currentUser?.id ?? '')
const step = ref(1)
const profileForm = reactive<ProfileFormValues>({
  legalName: '',
  introduction: '',
  desiredRoles: [],
  desiredIndustries: [],
  desiredLocations: [],
  expectedGraduationDate: '',
  version: 0,
})
const educationForm = reactive<EducationFormValues>({
  schoolName: '',
  major: '',
  degree: '',
  educationStatus: 'ENROLLED',
  admissionDate: '',
  graduationDate: '',
  gpa: '',
  gpaScale: '',
  isPrimary: true,
  description: '',
})
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const message = ref('')
const profileConflict = ref<{ draft: Record<string, unknown>; latest: ProfileDto } | null>(null)
const steps = [
  { number: 1, label: '기본 정보', description: '이름과 소개' },
  { number: 2, label: '대표 학력', description: '주요 교육 이력' },
  { number: 3, label: '희망 조건', description: '직무·산업·지역' },
  { number: 4, label: '문서 업로드', description: '이력서·포트폴리오' },
] as const

const profileQuery = useQuery({
  queryKey: computed(() => profileQueryKeys.profile(userId.value)),
  queryFn: profileApi.getProfile,
  enabled: computed(() => userId.value !== ''),
})
const educationQuery = useQuery({
  queryKey: computed(() =>
    profileQueryKeys.educations(userId.value, { page: 0, size: 20, sort: 'createdAt,desc' }),
  ),
  queryFn: () => profileApi.listEducations({ page: 0, size: 20, sort: 'createdAt,desc' }),
  enabled: computed(() => userId.value !== ''),
})

watch(
  () => profileQuery.data.value,
  (profile) => {
    if (profile !== undefined && profileConflict.value === null) loadProfile(profile)
  },
  { immediate: true },
)

const profileMutation = useMutation({
  mutationFn: (request: ProfileWrite) => profileApi.updateProfile(request),
})
const educationMutation = useMutation({
  mutationFn: (request: EducationCreateRequest) => profileApi.createEducation(request),
})
const isLoading = computed(() => profileQuery.isPending.value || educationQuery.isPending.value)
const hasLoadError = computed(() => profileQuery.isError.value || educationQuery.isError.value)
const completionPercent = computed(() => {
  const missing = profileQuery.data.value?.missingCompletionItems.length ?? 5
  return (5 - missing) * 20
})

async function saveBasic(): Promise<void> {
  await saveProfile(2)
}

async function saveDesired(): Promise<void> {
  await saveProfile(4)
}

async function saveProfile(nextStep: number): Promise<void> {
  fieldErrors.value = {}
  generalError.value = ''
  message.value = ''
  const validation = validateProfileForm(profileForm)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) return
  try {
    const saved = await profileMutation.mutateAsync(validation.data)
    queryClient.setQueryData(profileQueryKeys.profile(userId.value), saved)
    loadProfile(saved)
    step.value = nextStep
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (isVersionConflict(apiError)) {
      const latest = await profileApi.getProfile()
      profileConflict.value = { draft: { ...validation.data }, latest }
      generalError.value = '최신 프로필과 내 입력을 비교해 다시 적용해 주세요.'
      return
    }
    generalError.value = apiError.message
  }
}

async function saveEducation(): Promise<void> {
  fieldErrors.value = {}
  generalError.value = ''
  const validation = validateEducationForm({ ...educationForm, isPrimary: true })
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) return
  try {
    await educationMutation.mutateAsync({ ...validation.data, isPrimary: true })
    await educationQuery.refetch()
    await profileQuery.refetch()
    step.value = 3
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    generalError.value = apiError.message
  }
}

function useExistingPrimary(): void {
  step.value = 3
}

function loadProfile(profile: ProfileDto): void {
  Object.assign(profileForm, {
    legalName: profile.legalName ?? '',
    introduction: profile.introduction ?? '',
    desiredRoles: [...profile.desiredRoles],
    desiredIndustries: [...profile.desiredIndustries],
    desiredLocations: [...profile.desiredLocations],
    expectedGraduationDate: profile.expectedGraduationDate ?? '',
    version: profile.version,
  })
}

function cancelConflict(): void {
  const latest = profileConflict.value?.latest
  profileConflict.value = null
  if (latest !== undefined) loadProfile(latest)
}

function reapplyConflict(value: Record<string, unknown>): void {
  const latest = profileConflict.value?.latest
  if (latest === undefined) return
  loadProfile({ ...latest, ...value, version: latest.version } as ProfileDto)
  profileConflict.value = null
  message.value = '선택한 내 입력을 최신값에 재적용했습니다. 다시 저장해 주세요.'
}

function later(): void {
  void router.push('/dashboard')
}

function complete(): void {
  void router.push('/documents')
}

async function retryLoad(): Promise<void> {
  await Promise.all([profileQuery.refetch(), educationQuery.refetch()])
}
</script>

<template>
  <section class="onboarding app-page" aria-labelledby="onboarding-heading">
    <PageHeader
      heading-id="onboarding-heading"
      title="내 정보 설정"
      description="지원 준비에 필요한 기본 정보를 네 단계로 정리합니다. 입력하지 않은 항목은 나중에 언제든 보완할 수 있습니다."
      eyebrow="Getting started"
    />

    <ol class="onboarding-steps" aria-label="온보딩 진행 단계">
      <li
        v-for="item in steps"
        :key="item.number"
        class="onboarding-step"
        :class="{
          'onboarding-step--current': item.number === step,
          'onboarding-step--complete': item.number < step,
        }"
        :aria-current="item.number === step ? 'step' : undefined"
      >
        <span class="onboarding-step__marker" aria-hidden="true">
          <AppIcon v-if="item.number < step" name="check" />
          <span v-else>{{ item.number }}</span>
        </span>
        <span class="onboarding-step__text">
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </span>
      </li>
    </ol>

    <StatePanel
      v-if="isLoading"
      class="onboarding-state"
      kind="loading"
      title="온보딩 정보를 불러오는 중…"
      description="저장된 프로필과 대표 학력을 확인하고 있습니다."
    />
    <StatePanel
      v-else-if="hasLoadError"
      class="onboarding-state"
      kind="error"
      title="온보딩 정보를 불러오지 못했습니다."
      description="연결 상태를 확인한 뒤 다시 시도해 주세요."
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="retryLoad">다시 시도</button>
      </template>
    </StatePanel>
    <p v-if="generalError" class="alert alert--danger onboarding-message" role="alert">
      {{ generalError }}
    </p>
    <p v-if="message" class="alert alert--success onboarding-message" role="status">
      {{ message }}
    </p>

    <VersionConflictPanel
      v-if="!isLoading && !hasLoadError && profileConflict"
      class="onboarding-message"
      :draft="profileConflict.draft"
      :latest="profileConflict.latest"
      :fields="[
        { key: 'legalName', label: '이름' },
        { key: 'introduction', label: '소개' },
        { key: 'desiredRoles', label: '희망 직무' },
        { key: 'desiredIndustries', label: '희망 산업' },
        { key: 'desiredLocations', label: '희망 지역' },
        { key: 'expectedGraduationDate', label: '졸업 예정일' },
      ]"
      @cancel="cancelConflict"
      @reapply="reapplyConflict"
    />

    <form
      v-if="!isLoading && !hasLoadError && step === 1"
      class="onboarding-card section-surface"
      novalidate
      @submit.prevent="saveBasic"
    >
      <header class="section-header">
        <div>
          <p class="page-eyebrow">Step 1 of 4</p>
          <h3 class="section-title">기본 정보</h3>
          <p class="section-description">지원 과정에서 사용할 이름과 간단한 소개를 입력합니다.</p>
        </div>
      </header>
      <div class="field">
        <label class="field-label" for="onboarding-legalName">이름</label>
        <input
          id="onboarding-legalName"
          v-model="profileForm.legalName"
          class="control"
          maxlength="100"
          :aria-invalid="Boolean(fieldErrors.legalName)"
          :aria-describedby="fieldErrors.legalName ? 'onboarding-legalName-error' : undefined"
        />
        <span v-if="fieldErrors.legalName" id="onboarding-legalName-error" class="field-error">{{
          fieldErrors.legalName
        }}</span>
      </div>
      <div class="field">
        <label class="field-label" for="onboarding-introduction">간단 소개</label>
        <textarea
          id="onboarding-introduction"
          v-model="profileForm.introduction"
          class="control min-h-28"
          maxlength="2000"
        />
        <p class="field-help">핵심 경험이나 관심 직무를 2~3문장으로 정리할 수 있습니다.</p>
      </div>
      <div class="field onboarding-date-field">
        <label class="field-label" for="onboarding-graduationDate">졸업 예정일</label>
        <input
          id="onboarding-graduationDate"
          v-model="profileForm.expectedGraduationDate"
          class="control"
          type="date"
        />
      </div>
      <div class="onboarding-actions">
        <button type="button" class="button button--ghost" @click="later">추후 입력</button>
        <button
          type="submit"
          class="button button--primary"
          :disabled="profileMutation.isPending.value"
        >
          {{ profileMutation.isPending.value ? '저장 중…' : '저장하고 다음' }}
          <AppIcon name="arrow-right" />
        </button>
      </div>
    </form>

    <form
      v-else-if="!isLoading && !hasLoadError && step === 2"
      class="onboarding-card section-surface"
      novalidate
      @submit.prevent="saveEducation"
    >
      <header class="section-header">
        <div>
          <p class="page-eyebrow">Step 2 of 4</p>
          <h3 class="section-title">대표 학력</h3>
          <p class="section-description">프로필에서 가장 먼저 보여 줄 교육 이력을 설정합니다.</p>
        </div>
      </header>
      <div
        v-if="educationQuery.data.value?.items.some((item) => item.isPrimary)"
        class="alert alert--success"
      >
        <AppIcon name="check" />
        <span>이미 대표 학력이 있습니다.</span>
        <button type="button" class="text-link" @click="useExistingPrimary">
          기존 대표 학력 사용하기
        </button>
      </div>
      <div class="field">
        <label class="field-label" for="onboarding-schoolName">학교명</label>
        <input
          id="onboarding-schoolName"
          v-model="educationForm.schoolName"
          class="control"
          maxlength="200"
          :aria-invalid="Boolean(fieldErrors.schoolName)"
          :aria-describedby="fieldErrors.schoolName ? 'onboarding-schoolName-error' : undefined"
        />
        <span v-if="fieldErrors.schoolName" id="onboarding-schoolName-error" class="field-error">{{
          fieldErrors.schoolName
        }}</span>
      </div>
      <div class="onboarding-form-grid">
        <div class="field">
          <label class="field-label" for="onboarding-major">전공</label>
          <input
            id="onboarding-major"
            v-model="educationForm.major"
            class="control"
            maxlength="200"
          />
        </div>
        <div class="field">
          <label class="field-label" for="onboarding-educationStatus">재학 상태</label>
          <select
            id="onboarding-educationStatus"
            v-model="educationForm.educationStatus"
            class="control"
          >
            <option value="ENROLLED">재학</option>
            <option value="LEAVE_OF_ABSENCE">휴학</option>
            <option value="EXPECTED_GRADUATION">졸업 예정</option>
            <option value="GRADUATED">졸업</option>
            <option value="WITHDRAWN">중퇴</option>
          </select>
        </div>
      </div>
      <div class="onboarding-actions">
        <button type="button" class="button button--ghost" @click="step = 1">
          <AppIcon name="arrow-left" />
          이전
        </button>
        <span class="onboarding-actions__spacer" />
        <button type="button" class="button button--secondary" @click="step = 3">건너뛰기</button>
        <button
          type="submit"
          class="button button--primary"
          :disabled="educationMutation.isPending.value"
        >
          {{ educationMutation.isPending.value ? '저장 중…' : '대표 학력 저장' }}
          <AppIcon name="arrow-right" />
        </button>
      </div>
    </form>

    <form
      v-else-if="!isLoading && !hasLoadError && step === 3"
      class="onboarding-card section-surface"
      novalidate
      @submit.prevent="saveDesired"
    >
      <header class="section-header">
        <div>
          <p class="page-eyebrow">Step 3 of 4</p>
          <h3 class="section-title">희망 직무·산업·지역</h3>
          <p class="section-description">
            지원 방향을 정리합니다. 각 항목은 키보드로 추가하고 개별 삭제할 수 있습니다.
          </p>
        </div>
      </header>
      <StringListInput
        id="onboarding-desiredRoles"
        v-model="profileForm.desiredRoles"
        label="희망 직무"
        :error="fieldErrors.desiredRoles"
      />
      <StringListInput
        id="onboarding-desiredIndustries"
        v-model="profileForm.desiredIndustries"
        label="희망 산업"
        :error="fieldErrors.desiredIndustries"
      />
      <StringListInput
        id="onboarding-desiredLocations"
        v-model="profileForm.desiredLocations"
        label="희망 지역"
        :error="fieldErrors.desiredLocations"
      />
      <div class="onboarding-actions">
        <button type="button" class="button button--ghost" @click="step = 2">
          <AppIcon name="arrow-left" />
          이전
        </button>
        <span class="onboarding-actions__spacer" />
        <button type="button" class="button button--secondary" @click="step = 4">추후 입력</button>
        <button
          type="submit"
          class="button button--primary"
          :disabled="profileMutation.isPending.value"
        >
          {{ profileMutation.isPending.value ? '저장 중…' : '저장하고 다음' }}
          <AppIcon name="arrow-right" />
        </button>
      </div>
    </form>

    <section
      v-else-if="!isLoading && !hasLoadError"
      class="onboarding-card section-surface"
      aria-labelledby="onboarding-complete-heading"
    >
      <header class="section-header">
        <div>
          <p class="page-eyebrow">Step 4 of 4</p>
          <h3 id="onboarding-complete-heading" class="section-title">이력서 또는 포트폴리오</h3>
          <p class="section-description">
            문서를 등록하면 텍스트 처리와 근거 추출 상태를 별도로 확인할 수 있습니다.
          </p>
        </div>
      </header>

      <div class="onboarding-summary">
        <div class="onboarding-summary__top">
          <div>
            <span>현재 프로필</span>
            <strong>{{
              profileQuery.data.value?.profileCompleted ? '필수 항목 완료' : '보완 권장'
            }}</strong>
          </div>
          <strong>{{ completionPercent }}%</strong>
        </div>
        <progress class="progress-track" :value="completionPercent" max="100">
          {{ completionPercent }}%
        </progress>
        <p>프로필 미완료는 다른 기능 이용을 차단하지 않습니다.</p>
      </div>

      <div class="alert alert--info">
        <AppIcon name="upload" />
        <span>
          업로드 화면에서 PDF, DOCX, TXT 파일을 등록할 수 있습니다. 지금 문서가 없다면 대시보드로
          이동해 나중에 계속하세요.
        </span>
      </div>
      <div class="onboarding-actions onboarding-actions--final">
        <button type="button" class="button button--secondary" @click="later">나중에 계속</button>
        <button type="button" class="button button--primary" @click="complete">
          <AppIcon name="upload" />
          문서 업로드로 이동
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.onboarding {
  max-width: 56rem;
  margin-inline: auto;
}

.onboarding-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin: 2rem 0 0;
  padding: 0;
  list-style: none;
}

.onboarding-step {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
  color: var(--color-muted);
  padding-right: 1rem;
}

.onboarding-step:not(:last-child)::after {
  position: absolute;
  top: 1rem;
  right: 0.5rem;
  left: 2.5rem;
  z-index: 0;
  height: 1px;
  background: var(--color-border);
  content: '';
}

.onboarding-step__marker {
  position: relative;
  z-index: 1;
  display: inline-grid;
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-muted);
  font-size: 0.75rem;
  font-weight: 750;
}

.onboarding-step__marker .icon {
  width: 0.875rem;
  height: 0.875rem;
}

.onboarding-step__text {
  position: relative;
  z-index: 1;
  min-width: 0;
  background: var(--color-canvas);
  padding-right: 0.5rem;
}

.onboarding-step__text strong,
.onboarding-step__text small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.onboarding-step__text strong {
  color: var(--color-muted-strong);
  font-size: 0.75rem;
}

.onboarding-step__text small {
  margin-top: 0.1rem;
  font-size: 0.6875rem;
}

.onboarding-step--complete .onboarding-step__marker {
  border-color: var(--color-success);
  background: var(--color-success-soft);
  color: var(--color-success);
}

.onboarding-step--current .onboarding-step__marker {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: white;
  box-shadow: var(--focus-ring);
}

.onboarding-step--current .onboarding-step__text strong {
  color: var(--color-brand);
}

.onboarding-state,
.onboarding-message,
.onboarding-card {
  margin-top: 1.5rem;
}

.onboarding-card {
  display: grid;
  gap: 1.25rem;
  padding: clamp(1.25rem, 4vw, 2rem);
}

.onboarding-form-grid {
  display: grid;
  gap: 1rem;
}

.onboarding-date-field {
  max-width: 18rem;
}

.onboarding-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  border-top: 1px solid var(--color-border);
  padding-top: 1rem;
}

.onboarding-actions__spacer {
  flex: 1 1 auto;
}

.onboarding-summary {
  display: grid;
  gap: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  padding: 1rem;
}

.onboarding-summary__top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.onboarding-summary__top div > span,
.onboarding-summary__top div > strong {
  display: block;
}

.onboarding-summary__top span {
  color: var(--color-muted);
  font-size: 0.75rem;
}

.onboarding-summary__top strong {
  color: var(--color-ink);
  font-size: 0.9375rem;
}

.onboarding-summary__top > strong {
  color: var(--color-brand);
  font-size: 1.25rem;
}

.onboarding-summary p {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.75rem;
}

@media (min-width: 640px) {
  .onboarding-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .onboarding-steps {
    grid-template-columns: repeat(4, 2rem);
    justify-content: space-between;
  }

  .onboarding-step {
    display: block;
    padding: 0;
  }

  .onboarding-step:not(:last-child)::after {
    right: auto;
    left: 2rem;
    width: calc((100vw - 8rem) / 3);
  }

  .onboarding-step__text {
    display: none;
  }
}

@media (max-width: 479px) {
  .onboarding-actions > .button {
    flex: 1 1 auto;
  }

  .onboarding-actions__spacer {
    display: none;
  }

  .onboarding-actions--final > .button {
    flex: 1 1 100%;
  }
}
</style>
