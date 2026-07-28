<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import StringListInput from '@/features/profile/StringListInput.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import {
  DESIRED_INDUSTRY_PRESETS,
  DESIRED_INDUSTRY_SUGGESTIONS,
  DESIRED_LOCATION_PRESETS,
  DESIRED_LOCATION_SUGGESTIONS,
  DESIRED_ROLE_PRESETS,
  DESIRED_ROLE_SUGGESTIONS,
} from '@/features/profile/preferenceOptions'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import { type ProfileFormValues, validateProfileForm } from '@/features/profile/schemas'
import type { ProfileCompletionItem, ProfileDto, ProfileWrite } from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
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
  { key: 'expectedGraduationDate', label: '졸업(예정)일' },
] as const

const authStore = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const userId = computed(() => authStore.currentUser?.id ?? '')
const queryKey = computed(() => profileQueryKeys.profile(userId.value))
const form = reactive<ProfileFormValues>(emptyForm())
const baselineSignature = ref('')
const fieldErrors = ref<Record<string, string>>({})
const message = ref('')
const generalError = ref('')
const conflict = ref<{ draft: Record<string, unknown>; latest: ProfileDto } | null>(null)
const formSignature = computed(() => signature(form))
const isDirty = computed(
  () => baselineSignature.value !== '' && formSignature.value !== baselineSignature.value,
)

const profileQuery = useQuery({
  queryKey,
  queryFn: profileApi.getProfile,
  enabled: computed(() => userId.value !== ''),
})

watch(
  () => profileQuery.data.value,
  (profile) => {
    if (
      profile !== undefined &&
      conflict.value === null &&
      (baselineSignature.value === '' || !isDirty.value)
    ) {
      loadProfile(profile)
    }
  },
  { immediate: true },
)

watch(formSignature, () => {
  if (isDirty.value) message.value = ''
})

const saveMutation = useMutation({
  mutationFn: (request: ProfileWrite) => profileApi.updateProfile(request),
  onSuccess: (saved) => {
    queryClient.setQueryData(queryKey.value, saved)
    loadProfile(saved)
    fieldErrors.value = {}
    generalError.value = ''
    message.value = '저장 완료'
  },
})

const completionPercent = computed(() => {
  const missing = profileQuery.data.value?.missingCompletionItems.length ?? 5
  return (5 - missing) * 20
})
const saveStatus = computed(() => {
  if (saveMutation.isPending.value) return '저장 중'
  if (isDirty.value) return '저장되지 않은 변경 사항'
  if (message.value !== '') return message.value
  return '변경 사항 없음'
})

async function save(continueToEducation = false): Promise<void> {
  message.value = ''
  generalError.value = ''
  if (!isDirty.value) {
    if (continueToEducation) await router.push({ name: 'profile-education' })
    return
  }

  const validation = validateProfileForm(form)
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }

  try {
    await saveMutation.mutateAsync(validation.data)
    if (continueToEducation) await router.push({ name: 'profile-education' })
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
  baselineSignature.value = signature(form)
}

function cancelConflict(): void {
  const latest = conflict.value?.latest
  conflict.value = null
  if (latest !== undefined) loadProfile(latest)
}

function reapplyConflict(value: Record<string, unknown>): void {
  const latest = conflict.value?.latest
  if (latest === undefined) return
  loadProfile(latest)
  Object.assign(form, value, { version: latest.version })
  conflict.value = null
  message.value = ''
}

function signature(values: ProfileFormValues): string {
  return JSON.stringify({
    legalName: values.legalName,
    introduction: values.introduction,
    desiredRoles: values.desiredRoles,
    desiredIndustries: values.desiredIndustries,
    desiredLocations: values.desiredLocations,
    expectedGraduationDate: values.expectedGraduationDate,
    version: values.version,
  })
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
  <section
    class="profile-basic app-page profile-workspace-shell"
    aria-labelledby="profile-basic-heading"
  >
    <ProfileTabs />
    <div class="profile-workspace-shell__content">
      <PageHeader
        heading-id="profile-basic-heading"
        title="프로필 기본 정보"
        description="지원서에 공통으로 사용할 정보와 희망 조건을 관리하세요."
        eyebrow="내 지원 정보"
      >
        <template #actions>
          <div v-if="profileQuery.data.value" class="completion-inline" aria-label="프로필 완료율">
            <span>프로필 완성도</span>
            <strong>{{ completionPercent }}%</strong>
            <progress class="progress-track" :value="completionPercent" max="100">
              {{ completionPercent }}%
            </progress>
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
        <div class="profile-savebar">
          <div class="profile-savebar__status" role="status" aria-live="polite">
            <span :class="{ 'profile-savebar__dot--dirty': isDirty }" aria-hidden="true" />
            <div>
              <strong>{{ saveStatus }}</strong>
              <small>자동 저장되지 않아요. 변경 후 직접 저장해 주세요.</small>
            </div>
          </div>
          <button
            type="submit"
            form="profile-basic-form"
            class="button button--primary"
            :disabled="!isDirty || saveMutation.isPending.value"
          >
            <span v-if="saveMutation.isPending.value" class="button-spinner" aria-hidden="true" />
            {{ saveMutation.isPending.value ? '저장 중…' : '변경 내용 저장' }}
          </button>
        </div>

        <aside
          v-if="profileQuery.data.value && !profileQuery.data.value.profileCompleted"
          class="profile-completion-note"
          aria-label="프로필 보완 권장"
        >
          <div>
            <AppIcon name="alert" />
            <span>
              <strong
                >필수 항목 {{ profileQuery.data.value.missingCompletionItems.length }}개가 남아
                있어요.</strong
              >
              <small>모두 채우지 않아도 다른 기능을 사용할 수 있어요.</small>
            </span>
          </div>
          <ul>
            <li v-for="item in profileQuery.data.value.missingCompletionItems" :key="item">
              {{ completionLabels[item] }}
            </li>
          </ul>
        </aside>

        <p v-if="generalError" class="alert alert--danger profile-basic__message" role="alert">
          {{ generalError }}
        </p>

        <VersionConflictPanel
          v-if="conflict"
          class="profile-basic__message"
          :draft="conflict.draft"
          :latest="conflict.latest"
          :fields="[...conflictFields]"
          @cancel="cancelConflict"
          @reapply="reapplyConflict"
        />

        <form
          id="profile-basic-form"
          class="profile-editor"
          novalidate
          @submit.prevent="save(false)"
        >
          <section class="profile-editor__section" aria-labelledby="profile-identity-heading">
            <header class="profile-editor__section-heading">
              <h2 id="profile-identity-heading">기본 정보</h2>
              <p>지원서에 공통으로 사용할 이름과 졸업 일정을 입력하세요.</p>
            </header>
            <div class="profile-editor__fields profile-editor__fields--two">
              <div class="field">
                <label class="field-label" for="profile-legalName">이름</label>
                <input
                  id="profile-legalName"
                  v-model="form.legalName"
                  class="control"
                  maxlength="100"
                  autocomplete="name"
                  :aria-invalid="Boolean(fieldErrors.legalName)"
                  :aria-describedby="fieldErrors.legalName ? 'profile-legalName-error' : undefined"
                />
                <p v-if="fieldErrors.legalName" id="profile-legalName-error" class="field-error">
                  {{ fieldErrors.legalName }}
                </p>
              </div>
              <div class="field">
                <label class="field-label" for="profile-expectedGraduationDate">
                  졸업(예정)일
                </label>
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
            </div>
          </section>

          <section class="profile-editor__section" aria-labelledby="profile-introduction-heading">
            <header class="profile-editor__section-heading">
              <h2 id="profile-introduction-heading">자기소개</h2>
              <p>핵심 경험과 강점을 짧게 정리하면 공고 분석과 작성에 활용하기 좋아요.</p>
            </header>
            <div class="profile-editor__fields">
              <div class="field">
                <label class="field-label" for="profile-introduction">간단 소개</label>
                <textarea
                  id="profile-introduction"
                  v-model="form.introduction"
                  class="control profile-introduction"
                  maxlength="2000"
                  rows="7"
                  :aria-invalid="Boolean(fieldErrors.introduction)"
                  :aria-describedby="
                    fieldErrors.introduction
                      ? 'profile-introduction-help profile-introduction-error'
                      : 'profile-introduction-help'
                  "
                />
                <div id="profile-introduction-help" class="profile-editor__field-meta">
                  <p class="field-help">
                    담당했던 일, 강점과 앞으로 하고 싶은 일을 2~3문장으로 적어 보세요.
                  </p>
                  <p aria-live="polite">
                    {{ form.introduction.length.toLocaleString('ko-KR') }} / 2,000자
                  </p>
                </div>
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

          <section class="profile-editor__section" aria-labelledby="profile-preference-heading">
            <header class="profile-editor__section-heading">
              <h2 id="profile-preference-heading">희망 조건</h2>
              <p>직무, 산업과 지역을 최대 10개까지 자유롭게 입력할 수 있어요.</p>
            </header>
            <div class="profile-editor__fields profile-editor__preferences">
              <StringListInput
                id="profile-desiredRoles"
                v-model="form.desiredRoles"
                label="희망 직무"
                :error="fieldErrors.desiredRoles"
                placeholder="예: 프론트엔드 개발자"
                help="직무명을 입력하면 관련 항목을 추천해 드려요"
                :presets="DESIRED_ROLE_PRESETS"
                :suggestions="DESIRED_ROLE_SUGGESTIONS"
              />
              <StringListInput
                id="profile-desiredIndustries"
                v-model="form.desiredIndustries"
                label="희망 산업"
                :error="fieldErrors.desiredIndustries"
                placeholder="예: IT·소프트웨어"
                help="산업명을 입력하면 관련 항목을 추천해 드려요"
                :presets="DESIRED_INDUSTRY_PRESETS"
                :suggestions="DESIRED_INDUSTRY_SUGGESTIONS"
              />
              <StringListInput
                id="profile-desiredLocations"
                v-model="form.desiredLocations"
                label="희망 지역"
                :error="fieldErrors.desiredLocations"
                placeholder="예: 서울 또는 원격근무"
                help="시·도나 원하는 근무 방식을 직접 입력할 수 있어요"
                :presets="DESIRED_LOCATION_PRESETS"
                :suggestions="DESIRED_LOCATION_SUGGESTIONS"
              />
            </div>
          </section>

          <footer class="profile-editor__footer">
            <p>다음 항목에서 학력과 대표 학력을 이어서 관리할 수 있어요.</p>
            <button
              type="button"
              class="button button--secondary"
              :disabled="saveMutation.isPending.value"
              @click="save(true)"
            >
              {{ isDirty ? '저장 후 다음: 학력' : '다음: 학력' }}
              <AppIcon name="arrow-right" />
            </button>
          </footer>
        </form>
      </template>
    </div>
  </section>
</template>

<style scoped>
.profile-basic__state,
.profile-basic__message {
  margin-top: var(--space-6);
}

.completion-inline {
  display: grid;
  width: 12rem;
  grid-template-columns: 1fr auto;
  gap: var(--space-1) var(--space-3);
  align-items: center;
}

.completion-inline span {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.completion-inline strong {
  color: var(--color-ink);
  font-size: var(--font-size-sm);
}

.completion-inline .progress-track {
  grid-column: 1 / -1;
}

.profile-savebar {
  position: sticky;
  top: 4.5rem;
  z-index: 12;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-5);
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: var(--space-3) 0;
}

.profile-savebar__status {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.profile-savebar__status > span {
  width: 0.5rem;
  height: 0.5rem;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--color-success);
}

.profile-savebar__status > .profile-savebar__dot--dirty {
  background: var(--color-warning);
}

.profile-savebar__status strong,
.profile-savebar__status small {
  display: block;
}

.profile-savebar__status strong {
  color: var(--color-ink-soft);
  font-size: var(--font-size-sm);
}

.profile-savebar__status small {
  margin-top: 0.1rem;
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.profile-completion-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-5);
  color: var(--color-muted-strong);
  padding: var(--space-2) 0;
}

.profile-completion-note > div {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

.profile-completion-note > div > .icon {
  width: 1rem;
  flex: 0 0 auto;
  margin-top: 0.2rem;
  color: var(--color-warning);
}

.profile-completion-note strong,
.profile-completion-note small {
  display: block;
}

.profile-completion-note strong {
  color: var(--color-ink-soft);
  font-size: var(--font-size-sm);
}

.profile-completion-note small {
  margin-top: 0.1rem;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.profile-completion-note ul {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.profile-completion-note li {
  border-radius: 999px;
  background: var(--color-neutral-soft);
  color: var(--color-muted-strong);
  padding: 0.3rem 0.625rem;
  font-size: 0.6875rem;
  font-weight: 650;
}

.profile-editor {
  overflow: hidden;
  margin-top: var(--space-6);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.profile-editor__section {
  display: grid;
  grid-template-columns: minmax(10rem, 13rem) minmax(0, 1fr);
  gap: clamp(2rem, 4vw, 4rem);
  padding: clamp(1.5rem, 3vw, 2.5rem);
}

.profile-editor__section + .profile-editor__section {
  border-top: 1px solid var(--color-border);
}

.profile-editor__section-heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 1.0625rem;
  letter-spacing: -0.02em;
}

.profile-editor__section-heading p {
  margin: var(--space-2) 0 0;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  line-height: 1.65;
}

.profile-editor__fields {
  display: grid;
  min-width: 0;
  gap: var(--space-6);
}

.profile-editor__fields--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.profile-introduction {
  min-height: 10rem;
  resize: vertical;
}

.profile-editor__field-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-2);
}

.profile-editor__field-meta p {
  margin: 0;
}

.profile-editor__field-meta > p:last-child {
  flex: 0 0 auto;
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.profile-editor__preferences {
  gap: var(--space-8);
}

.profile-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  border-top: 1px solid var(--color-border);
  padding: var(--space-4) clamp(1.5rem, 3vw, 2.5rem);
}

.profile-editor__footer p {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

@media (max-width: 767px) {
  .profile-editor__section {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-5);
  }

  .profile-editor__fields--two {
    grid-template-columns: minmax(0, 1fr);
  }

  .profile-completion-note {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-completion-note ul {
    justify-content: flex-start;
  }
}

@media (max-width: 639px) {
  .profile-savebar {
    top: 4rem;
  }

  .profile-savebar__status small {
    display: none;
  }

  .profile-editor__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-editor__footer .button {
    width: 100%;
  }
}

@media (max-width: 479px) {
  .completion-inline {
    width: 100%;
  }

  .profile-savebar {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-savebar .button {
    width: 100%;
  }

  .profile-editor__field-meta {
    flex-direction: column;
    gap: var(--space-1);
  }
}
</style>
