<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
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
  void router.push('/profile/basic')
}

async function retryLoad(): Promise<void> {
  await Promise.all([profileQuery.refetch(), educationQuery.refetch()])
}
</script>

<template>
  <section aria-labelledby="onboarding-heading" class="mx-auto max-w-3xl">
    <h2 id="onboarding-heading" class="text-2xl font-bold">온보딩</h2>
    <p class="mt-2 text-slate-600">
      필요한 정보를 단계별로 입력하세요. 언제든 추후 입력할 수 있습니다.
    </p>

    <ol class="mt-5 grid grid-cols-4 gap-2 text-center text-xs" aria-label="온보딩 진행 단계">
      <li
        v-for="number in 4"
        :key="number"
        class="rounded-full px-2 py-2"
        :class="number === step ? 'bg-indigo-700 text-white' : 'bg-slate-200 text-slate-700'"
        :aria-current="number === step ? 'step' : undefined"
      >
        {{ number }}
      </li>
    </ol>

    <p v-if="isLoading" class="mt-6">정보를 불러오는 중…</p>
    <p v-else-if="hasLoadError" class="mt-6 rounded-lg bg-red-50 p-4 text-red-800" role="alert">
      온보딩 정보를 불러오지 못했습니다.
      <button type="button" class="ml-2 underline" @click="retryLoad">다시 시도</button>
    </p>
    <p v-if="generalError" class="mt-4 text-sm text-red-700" role="alert">{{ generalError }}</p>
    <p v-if="message" class="mt-4 text-sm text-emerald-700" role="status">{{ message }}</p>

    <VersionConflictPanel
      v-if="!isLoading && !hasLoadError && profileConflict"
      class="mt-5"
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
      class="mt-6 space-y-4 rounded-2xl bg-white p-6 shadow-sm"
      novalidate
      @submit.prevent="saveBasic"
    >
      <h3 class="text-lg font-semibold">1. 기본 프로필</h3>
      <label class="block text-sm font-medium"
        >이름<input
          id="onboarding-legalName"
          v-model="profileForm.legalName"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          maxlength="100"
        /><span v-if="fieldErrors.legalName" class="mt-1 block text-red-700">{{
          fieldErrors.legalName
        }}</span></label
      >
      <label class="block text-sm font-medium"
        >간단 소개<textarea
          v-model="profileForm.introduction"
          class="mt-1 min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2"
          maxlength="2000"
        />
      </label>
      <label class="block text-sm font-medium"
        >졸업 예정일<input
          v-model="profileForm.expectedGraduationDate"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
          type="date"
      /></label>
      <div class="flex gap-2">
        <button type="submit" class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white">
          다음</button
        ><button type="button" class="rounded-lg border border-slate-300 px-4 py-2" @click="later">
          추후 입력
        </button>
      </div>
    </form>

    <form
      v-else-if="!isLoading && !hasLoadError && step === 2"
      class="mt-6 space-y-4 rounded-2xl bg-white p-6 shadow-sm"
      novalidate
      @submit.prevent="saveEducation"
    >
      <h3 class="text-lg font-semibold">2. 대표 학력</h3>
      <div
        v-if="educationQuery.data.value?.items.some((item) => item.isPrimary)"
        class="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900"
      >
        이미 대표 학력이 있습니다.
        <button type="button" class="font-semibold underline" @click="useExistingPrimary">
          기존 대표 학력 사용
        </button>
      </div>
      <label class="block text-sm font-medium"
        >학교명<input
          id="onboarding-schoolName"
          v-model="educationForm.schoolName"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          maxlength="200"
        /><span v-if="fieldErrors.schoolName" class="mt-1 block text-red-700">{{
          fieldErrors.schoolName
        }}</span></label
      >
      <label class="block text-sm font-medium"
        >전공<input
          v-model="educationForm.major"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          maxlength="200"
      /></label>
      <label class="block text-sm font-medium"
        >재학 상태<select
          v-model="educationForm.educationStatus"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="ENROLLED">재학</option>
          <option value="LEAVE_OF_ABSENCE">휴학</option>
          <option value="EXPECTED_GRADUATION">졸업 예정</option>
          <option value="GRADUATED">졸업</option>
          <option value="WITHDRAWN">중퇴</option>
        </select></label
      >
      <div class="flex gap-2">
        <button type="submit" class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white">
          대표 학력 저장</button
        ><button
          type="button"
          class="rounded-lg border border-slate-300 px-4 py-2"
          @click="step = 3"
        >
          건너뛰기
        </button>
      </div>
    </form>

    <form
      v-else-if="!isLoading && !hasLoadError && step === 3"
      class="mt-6 space-y-5 rounded-2xl bg-white p-6 shadow-sm"
      novalidate
      @submit.prevent="saveDesired"
    >
      <h3 class="text-lg font-semibold">3. 희망 직무·산업·지역</h3>
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
      <div class="flex gap-2">
        <button type="submit" class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white">
          저장하고 확인</button
        ><button
          type="button"
          class="rounded-lg border border-slate-300 px-4 py-2"
          @click="step = 4"
        >
          추후 입력
        </button>
      </div>
    </form>

    <section
      v-else-if="!isLoading && !hasLoadError"
      class="mt-6 rounded-2xl bg-white p-6 shadow-sm"
      aria-labelledby="onboarding-complete-heading"
    >
      <h3 id="onboarding-complete-heading" class="text-lg font-semibold">4. 완료 또는 추후 입력</h3>
      <p class="mt-3">
        현재 프로필:
        <strong>{{ profileQuery.data.value?.profileCompleted ? '완료' : '보완 권장' }}</strong>
      </p>
      <p v-if="profileQuery.data.value" class="mt-1 text-sm text-slate-600">
        충족한 항목당 20% · {{ (5 - profileQuery.data.value.missingCompletionItems.length) * 20 }}%
      </p>
      <p class="mt-4 rounded-lg bg-sky-50 p-3 text-sm text-sky-900">
        이력서·포트폴리오 문서 업로드는 P4에서 제공됩니다. 현재 가짜 업로드를 만들지 않으며 이
        단계는 안전하게 건너뜁니다.
      </p>
      <div class="mt-5 flex flex-wrap gap-2">
        <button
          type="button"
          class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white"
          @click="complete"
        >
          프로필 확인</button
        ><button type="button" class="rounded-lg border border-slate-300 px-4 py-2" @click="later">
          나중에 계속
        </button>
      </div>
    </section>
  </section>
</template>
