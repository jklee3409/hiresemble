<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import {
  DESIRED_LOCATION_PRESETS,
  DESIRED_LOCATION_SUGGESTIONS,
  DESIRED_ROLE_PRESETS,
  DESIRED_ROLE_SUGGESTIONS,
} from '@/features/profile/preferenceOptions'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
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
  educationLevel: 'BACHELOR',
  educationStatus: 'ENROLLED',
  admissionDate: '',
  graduationDate: '',
  gpa: '',
  gpaScale: '',
  description: '',
})
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const message = ref('')
const profileConflict = ref<{ draft: Record<string, unknown>; latest: ProfileDto } | null>(null)
const steps = [
  { number: 1, label: '기본 정보', description: '이름과 소개' },
  { number: 2, label: '최종 학력', description: '가장 높은 교육 단계' },
  { number: 3, label: '희망 조건', description: '관심 있는 분야' },
  { number: 4, label: '자료 등록', description: '이력서·포트폴리오' },
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
  if (validation.data === null) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }
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
  const validation = validateEducationForm(educationForm)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }
  try {
    await educationMutation.mutateAsync(validation.data)
    await educationQuery.refetch()
    await profileQuery.refetch()
    step.value = 3
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    generalError.value = apiError.message
  }
}

function useExistingFinalEducation(): void {
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
  message.value = '선택한 내 입력을 최신 내용에 다시 적용했어요. 확인한 뒤 저장해 주세요.'
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
      title="나에게 맞게 시작해 볼까요?"
      description="지금 아는 만큼만 입력해도 괜찮아요. 나중에 언제든 이어서 채울 수 있어요."
      eyebrow="첫 준비"
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
      title="입력한 정보를 불러오는 중…"
      description="저장한 프로필과 최종 학력을 확인하고 있어요."
    />
    <StatePanel
      v-else-if="hasLoadError"
      class="onboarding-state"
      kind="error"
      title="입력한 정보를 불러오지 못했어요."
      description="잠시 후 다시 시도해 주세요."
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
        { key: 'expectedGraduationDate', label: '졸업(예정)일' },
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
          <p class="page-eyebrow">1 / 4</p>
          <h3 class="section-title">기본 정보</h3>
          <p class="section-description">지원할 때 사용할 이름과 간단한 소개를 입력해 주세요.</p>
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
        <p class="field-help">핵심 경험이나 관심 직무를 2~3문장으로 적어 보세요.</p>
      </div>
      <div class="field onboarding-date-field">
        <label class="field-label" for="onboarding-graduationDate">졸업(예정)일</label>
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
          <p class="page-eyebrow">2 / 4</p>
          <h3 class="section-title">최종 학력</h3>
          <p class="section-description">
            학력 단계를 선택하면 등록된 항목 중 가장 높은 단계를 최종 학력으로 표시해요.
          </p>
        </div>
      </header>
      <div
        v-if="educationQuery.data.value?.items.some((item) => item.isPrimary)"
        class="alert alert--success"
      >
        <AppIcon name="check" />
        <span>이미 최종 학력으로 표시된 항목이 있어요.</span>
        <button type="button" class="text-link" @click="useExistingFinalEducation">
          저장된 학력 사용하기
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
          <label class="field-label" for="onboarding-educationLevel">학력 단계</label>
          <select
            id="onboarding-educationLevel"
            v-model="educationForm.educationLevel"
            class="control"
          >
            <option value="HIGH_SCHOOL">고등학교</option>
            <option value="ASSOCIATE">대학교(전문학사)</option>
            <option value="BACHELOR">대학교(학사)</option>
            <option value="MASTER">대학원(석사)</option>
            <option value="DOCTORATE">대학원(박사)</option>
            <option value="OTHER">기타 교육</option>
          </select>
        </div>
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
          {{ educationMutation.isPending.value ? '저장 중…' : '이 학력 저장' }}
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
          <p class="page-eyebrow">3 / 4</p>
          <h3 class="section-title">희망 직무·산업·지역</h3>
          <p class="section-description">
            관심 있는 직무, 산업과 지역을 입력해 주세요. 입력한 항목은 언제든 바꿀 수 있어요.
          </p>
        </div>
      </header>
      <StringListInput
        id="onboarding-desiredRoles"
        v-model="profileForm.desiredRoles"
        label="희망 직무"
        :error="fieldErrors.desiredRoles"
        placeholder="예: 프론트엔드 개발자"
        help="직무명을 입력하면 관련 선택지를 추천해 드려요"
        :presets="DESIRED_ROLE_PRESETS"
        :suggestions="DESIRED_ROLE_SUGGESTIONS"
      />
      <StringListInput
        id="onboarding-desiredIndustries"
        v-model="profileForm.desiredIndustries"
        label="희망 산업"
        :error="fieldErrors.desiredIndustries"
        placeholder="예: IT·소프트웨어"
      />
      <StringListInput
        id="onboarding-desiredLocations"
        v-model="profileForm.desiredLocations"
        label="희망 지역"
        :error="fieldErrors.desiredLocations"
        placeholder="예: 서울 또는 원격근무"
        help="시·도나 원하는 근무 방식을 직접 입력할 수 있어요"
        :presets="DESIRED_LOCATION_PRESETS"
        :suggestions="DESIRED_LOCATION_SUGGESTIONS"
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
          <p class="page-eyebrow">4 / 4</p>
          <h3 id="onboarding-complete-heading" class="section-title">이력서 또는 포트폴리오</h3>
          <p class="section-description">
            자료를 등록하면 내용을 읽고 경력 정보를 정리하는 과정을 확인할 수 있어요.
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
        <p>지금 모두 채우지 않아도 다른 준비를 계속할 수 있어요.</p>
      </div>

      <div class="alert alert--info">
        <AppIcon name="upload" />
        <span>
          PDF, DOCX, TXT 파일을 등록할 수 있어요. 지금 자료가 없다면 나중에 이어서 등록해도
          괜찮아요.
        </span>
      </div>
      <div class="onboarding-actions onboarding-actions--final">
        <button type="button" class="button button--secondary" @click="later">나중에 계속</button>
        <button type="button" class="button button--primary" @click="complete">
          <AppIcon name="upload" />
          자료 등록으로 이동
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
