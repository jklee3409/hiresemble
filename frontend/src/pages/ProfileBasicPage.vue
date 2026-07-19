<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'

import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import { type ProfileFormValues, validateProfileForm } from '@/features/profile/schemas'
import type { ProfileCompletionItem, ProfileDto, ProfileWrite } from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

const completionLabels: Record<ProfileCompletionItem, string> = {
  LEGAL_NAME: '이름',
  DESIRED_ROLE: '희망 직무',
  DESIRED_INDUSTRY: '희망 산업',
  DESIRED_LOCATION: '희망 지역',
  PRIMARY_EDUCATION: '대표 학력',
}

const conflictFields = [
  { key: 'legalName', label: '이름' },
  { key: 'introduction', label: '간단 소개' },
  { key: 'desiredRoles', label: '희망 직무' },
  { key: 'desiredIndustries', label: '희망 산업' },
  { key: 'desiredLocations', label: '희망 지역' },
  { key: 'expectedGraduationDate', label: '졸업 예정일' },
] as const

const authStore = useAuthStore()
const queryClient = useQueryClient()
const userId = computed(() => authStore.currentUser?.id ?? '')
const queryKey = computed(() => profileQueryKeys.profile(userId.value))
const form = reactive<ProfileFormValues>(emptyForm())
const fieldErrors = ref<Record<string, string>>({})
const message = ref('')
const generalError = ref('')
const conflict = ref<{ draft: Record<string, unknown>; latest: ProfileDto } | null>(null)

const profileQuery = useQuery({
  queryKey,
  queryFn: profileApi.getProfile,
  enabled: computed(() => userId.value !== ''),
})

watch(
  () => profileQuery.data.value,
  (profile) => {
    if (profile !== undefined && conflict.value === null) loadProfile(profile)
  },
  { immediate: true },
)

const saveMutation = useMutation({
  mutationFn: (request: ProfileWrite) => profileApi.updateProfile(request),
  onSuccess: (saved) => {
    queryClient.setQueryData(queryKey.value, saved)
    loadProfile(saved)
    fieldErrors.value = {}
    generalError.value = ''
    message.value = '프로필을 저장했습니다.'
  },
})

const completionPercent = computed(() => {
  const missing = profileQuery.data.value?.missingCompletionItems.length ?? 5
  return (5 - missing) * 20
})

async function save(): Promise<void> {
  message.value = ''
  generalError.value = ''
  const validation = validateProfileForm(form)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) return

  try {
    await saveMutation.mutateAsync(validation.data)
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (isVersionConflict(apiError)) {
      const latest = await profileApi.getProfile()
      queryClient.setQueryData(queryKey.value, latest)
      conflict.value = { draft: { ...validation.data }, latest }
      generalError.value = '최신 프로필과 내 입력을 비교해 다시 적용해 주세요.'
      return
    }
    generalError.value = apiError.message
  }
}

function loadProfile(profile: ProfileDto): void {
  Object.assign(form, {
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
  const latest = conflict.value?.latest
  conflict.value = null
  if (latest !== undefined) loadProfile(latest)
}

function reapplyConflict(value: Record<string, unknown>): void {
  const latest = conflict.value?.latest
  if (latest === undefined) return
  loadProfile({ ...latest, ...value, version: latest.version } as ProfileDto)
  conflict.value = null
  message.value = '선택한 내 입력을 최신값에 재적용했습니다. 내용을 확인하고 다시 저장해 주세요.'
}

function emptyForm(): ProfileFormValues {
  return {
    legalName: '',
    introduction: '',
    desiredRoles: [],
    desiredIndustries: [],
    desiredLocations: [],
    expectedGraduationDate: '',
    version: 0,
  }
}
</script>

<template>
  <section aria-labelledby="profile-basic-heading">
    <ProfileTabs />
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 id="profile-basic-heading" class="text-2xl font-bold">기본 프로필</h2>
        <p class="mt-2 text-slate-600">
          완료 여부는 안내용이며 다른 보호 화면 접근을 차단하지 않습니다.
        </p>
      </div>
      <div
        v-if="profileQuery.data.value"
        class="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm"
      >
        <strong>{{ completionPercent }}% 완료</strong>
        <span class="ml-2"
          >({{ profileQuery.data.value.profileCompleted ? '완료' : '보완 권장' }})</span
        >
      </div>
    </div>

    <p v-if="profileQuery.isPending.value" class="mt-6" aria-live="polite">프로필을 불러오는 중…</p>
    <div
      v-else-if="profileQuery.isError.value"
      class="mt-6 rounded-xl bg-red-50 p-4 text-red-800"
      role="alert"
    >
      프로필을 불러오지 못했습니다.
      <button type="button" class="ml-2 underline" @click="profileQuery.refetch()">
        다시 시도
      </button>
    </div>

    <template v-else>
      <aside
        v-if="profileQuery.data.value && !profileQuery.data.value.profileCompleted"
        class="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"
        aria-label="프로필 보완 권장"
      >
        <strong>프로필 보완을 권장합니다.</strong>
        <span class="ml-1">부족 항목:</span>
        <ul class="mt-2 flex flex-wrap gap-2">
          <li
            v-for="item in profileQuery.data.value.missingCompletionItems"
            :key="item"
            class="rounded-full bg-white px-2 py-1"
          >
            {{ completionLabels[item] }}
          </li>
        </ul>
      </aside>

      <VersionConflictPanel
        v-if="conflict"
        class="mt-6"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="[...conflictFields]"
        @cancel="cancelConflict"
        @reapply="reapplyConflict"
      />

      <form
        class="mt-6 space-y-5 rounded-2xl bg-white p-6 shadow-sm"
        novalidate
        @submit.prevent="save"
      >
        <div>
          <label class="text-sm font-medium" for="profile-legalName">이름</label>
          <input
            id="profile-legalName"
            v-model="form.legalName"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            maxlength="100"
            :aria-invalid="Boolean(fieldErrors.legalName)"
          />
          <p v-if="fieldErrors.legalName" class="mt-1 text-sm text-red-700">
            {{ fieldErrors.legalName }}
          </p>
        </div>
        <div>
          <label class="text-sm font-medium" for="profile-introduction">간단 소개</label>
          <textarea
            id="profile-introduction"
            v-model="form.introduction"
            class="mt-1 min-h-28 w-full rounded-lg border border-slate-300 px-3 py-2"
            maxlength="2000"
            :aria-invalid="Boolean(fieldErrors.introduction)"
          />
          <p v-if="fieldErrors.introduction" class="mt-1 text-sm text-red-700">
            {{ fieldErrors.introduction }}
          </p>
        </div>
        <div>
          <label class="text-sm font-medium" for="profile-expectedGraduationDate"
            >졸업 예정일</label
          >
          <input
            id="profile-expectedGraduationDate"
            v-model="form.expectedGraduationDate"
            class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
            type="date"
            :aria-invalid="Boolean(fieldErrors.expectedGraduationDate)"
          />
          <p v-if="fieldErrors.expectedGraduationDate" class="mt-1 text-sm text-red-700">
            {{ fieldErrors.expectedGraduationDate }}
          </p>
        </div>

        <StringListInput
          id="profile-desiredRoles"
          v-model="form.desiredRoles"
          label="희망 직무"
          :error="fieldErrors.desiredRoles"
        />
        <StringListInput
          id="profile-desiredIndustries"
          v-model="form.desiredIndustries"
          label="희망 산업"
          :error="fieldErrors.desiredIndustries"
        />
        <StringListInput
          id="profile-desiredLocations"
          v-model="form.desiredLocations"
          label="희망 지역"
          :error="fieldErrors.desiredLocations"
        />

        <p v-if="generalError" class="text-sm text-red-700" role="alert">{{ generalError }}</p>
        <p v-if="message" class="text-sm text-emerald-700" role="status">{{ message }}</p>
        <button
          type="submit"
          class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
          :disabled="saveMutation.isPending.value"
        >
          {{ saveMutation.isPending.value ? '저장 중…' : '프로필 저장' }}
        </button>
      </form>
    </template>
  </section>
</template>
