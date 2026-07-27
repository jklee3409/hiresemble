<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'

import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import { type ProfileFormValues, validateProfileForm } from '@/features/profile/schemas'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import type { ProfileCompletionItem, ProfileDto, ProfileWrite } from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

const completionLabels: Record<ProfileCompletionItem, string> = {
  LEGAL_NAME: '이름',
  DESIRED_ROLE: '희망 직무',
  DESIRED_INDUSTRY: '희망 산업',
  DESIRED_LOCATION: '희망 지역',
  PRIMARY_EDUCATION: '먼저 보여 줄 학력',
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
    message.value = '프로필을 저장했어요.'
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
  message.value = '선택한 내 입력을 최신 내용에 다시 적용했어요. 확인한 뒤 저장해 주세요.'
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
  <section class="profile-basic app-page" aria-labelledby="profile-basic-heading">
    <ProfileTabs />
    <PageHeader
      heading-id="profile-basic-heading"
      title="내 프로필"
      description="한 번 정리한 기본 정보와 희망 조건을 여러 지원에 활용할 수 있어요. 부족한 항목은 나중에 채워도 괜찮아요."
      eyebrow="나의 경험"
    >
      <template #actions>
        <div v-if="profileQuery.data.value" class="completion-summary" aria-label="프로필 완료율">
          <div class="completion-summary__label">
            <span>프로필 완료율</span>
            <strong>{{ completionPercent }}% 완료</strong>
          </div>
          <progress class="progress-track" :value="completionPercent" max="100">
            {{ completionPercent }}%
          </progress>
          <span class="completion-summary__state">
            {{ profileQuery.data.value.profileCompleted ? '필수 항목 완료' : '보완 권장' }}
          </span>
        </div>
      </template>
    </PageHeader>

    <StatePanel
      v-if="profileQuery.isPending.value"
      class="profile-basic__state"
      kind="loading"
      title="프로필을 불러오는 중…"
      description="저장한 기본 정보와 완료 항목을 확인하고 있어요."
    />
    <StatePanel
      v-else-if="profileQuery.isError.value"
      class="profile-basic__state"
      kind="error"
      title="프로필을 불러오지 못했어요."
      description="잠시 후 다시 시도해 주세요."
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="profileQuery.refetch()">
          다시 시도
        </button>
      </template>
    </StatePanel>

    <template v-else>
      <aside
        v-if="profileQuery.data.value && !profileQuery.data.value.profileCompleted"
        class="profile-completion-note"
        aria-label="프로필 보완 권장"
      >
        <div class="profile-completion-note__intro">
          <AppIcon name="alert" />
          <div>
            <strong>조금 더 채우면 좋아요.</strong>
            <p>아래 항목을 입력하면 지원할 때 활용할 정보가 더 분명해져요.</p>
          </div>
        </div>
        <ul class="profile-completion-note__items">
          <li v-for="item in profileQuery.data.value.missingCompletionItems" :key="item">
            <span aria-hidden="true" />
            {{ completionLabels[item] }}
          </li>
        </ul>
      </aside>

      <VersionConflictPanel
        v-if="conflict"
        class="profile-basic__message"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="[...conflictFields]"
        @cancel="cancelConflict"
        @reapply="reapplyConflict"
      />

      <form class="profile-form section-surface" novalidate @submit.prevent="save">
        <section class="profile-form__section" aria-labelledby="profile-identity-heading">
          <header>
            <h3 id="profile-identity-heading" class="section-title">기본 정보</h3>
            <p class="section-description">
              지원할 때 사용할 이름, 소개와 졸업 예정일을 입력해 주세요.
            </p>
          </header>
          <div class="profile-form__grid">
            <div class="field">
              <label class="field-label" for="profile-legalName">이름</label>
              <input
                id="profile-legalName"
                v-model="form.legalName"
                class="control"
                maxlength="100"
                :aria-invalid="Boolean(fieldErrors.legalName)"
                :aria-describedby="fieldErrors.legalName ? 'profile-legalName-error' : undefined"
              />
              <p v-if="fieldErrors.legalName" id="profile-legalName-error" class="field-error">
                {{ fieldErrors.legalName }}
              </p>
            </div>
            <div class="field">
              <label class="field-label" for="profile-expectedGraduationDate">졸업 예정일</label>
              <input
                id="profile-expectedGraduationDate"
                v-model="form.expectedGraduationDate"
                class="control"
                type="date"
                :aria-invalid="Boolean(fieldErrors.expectedGraduationDate)"
                :aria-describedby="
                  fieldErrors.expectedGraduationDate
                    ? 'profile-expectedGraduationDate-error'
                    : undefined
                "
              />
              <p
                v-if="fieldErrors.expectedGraduationDate"
                id="profile-expectedGraduationDate-error"
                class="field-error"
              >
                {{ fieldErrors.expectedGraduationDate }}
              </p>
            </div>
            <div class="field profile-form__wide">
              <label class="field-label" for="profile-introduction">간단 소개</label>
              <textarea
                id="profile-introduction"
                v-model="form.introduction"
                class="control min-h-32"
                maxlength="2000"
                :aria-invalid="Boolean(fieldErrors.introduction)"
                :aria-describedby="
                  fieldErrors.introduction ? 'profile-introduction-error' : undefined
                "
              />
              <p
                v-if="fieldErrors.introduction"
                id="profile-introduction-error"
                class="field-error"
              >
                {{ fieldErrors.introduction }}
              </p>
            </div>
          </div>
        </section>

        <section class="profile-form__section" aria-labelledby="profile-preference-heading">
          <header>
            <h3 id="profile-preference-heading" class="section-title">희망 조건</h3>
            <p class="section-description">
              관심 있는 직무, 산업과 지역을 입력해 두면 지원 방향을 빠르게 확인할 수 있어요.
            </p>
          </header>
          <div class="profile-form__preferences">
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
          </div>
        </section>

        <footer class="profile-form__footer">
          <div class="profile-form__feedback">
            <p v-if="generalError" class="alert alert--danger" role="alert">
              {{ generalError }}
            </p>
            <p v-if="message" class="alert alert--success" role="status">{{ message }}</p>
          </div>
          <button
            type="submit"
            class="button button--primary"
            :disabled="saveMutation.isPending.value"
          >
            <span v-if="saveMutation.isPending.value" class="button-spinner" aria-hidden="true" />
            {{ saveMutation.isPending.value ? '저장 중…' : '프로필 저장' }}
          </button>
        </footer>
      </form>
    </template>
  </section>
</template>

<style scoped>
.completion-summary {
  display: grid;
  width: min(16rem, 100%);
  gap: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: 0.75rem 0.875rem;
}

.completion-summary__label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.completion-summary__label span,
.completion-summary__state {
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.completion-summary__label strong {
  color: var(--color-brand);
  font-size: 0.9375rem;
}

.profile-basic__state,
.profile-basic__message,
.profile-completion-note,
.profile-form {
  margin-top: 1.5rem;
}

.profile-completion-note {
  display: grid;
  grid-template-columns: minmax(16rem, 0.8fr) minmax(0, 1.2fr);
  gap: 1rem;
  border: 1px solid #ead08a;
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: #704905;
  padding: 1rem;
}

.profile-completion-note__intro {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.profile-completion-note__intro > .icon {
  margin-top: 0.1rem;
}

.profile-completion-note__intro strong {
  font-size: 0.875rem;
}

.profile-completion-note__intro p {
  margin: 0.2rem 0 0;
  font-size: 0.75rem;
  line-height: 1.55;
}

.profile-completion-note__items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.375rem 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.profile-completion-note__items li {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  color: #704905;
  font-size: 0.75rem;
  font-weight: 650;
}

.profile-completion-note__items li > span {
  width: 0.375rem;
  height: 0.375rem;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #ad750e;
}

.profile-form {
  overflow: hidden;
}

.profile-form__section {
  display: grid;
  gap: 1.25rem;
  padding: clamp(1.25rem, 3vw, 2rem);
}

.profile-form__section + .profile-form__section {
  border-top: 1px solid var(--color-border);
}

.profile-form__grid,
.profile-form__preferences {
  display: grid;
  gap: 1rem;
}

.profile-form__footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-subtle);
  padding: 1rem clamp(1.25rem, 3vw, 2rem);
}

.profile-form__feedback {
  min-width: 0;
  flex: 1 1 auto;
}

.profile-form__feedback .alert + .alert {
  margin-top: 0.5rem;
}

@media (min-width: 720px) {
  .profile-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .profile-form__wide {
    grid-column: 1 / -1;
  }

  .profile-form__preferences {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 719px) {
  .profile-completion-note {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 479px) {
  .profile-form__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-form__footer .button {
    width: 100%;
  }
}
</style>
