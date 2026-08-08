<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref, watch } from 'vue'

import { profileQueryKeys } from '@/features/profile/queryKeys'
import type { RunAcceptedDto } from '@/shared/api/agentRunContracts'
import {
  CAREER_ARTIFACT_PROFILE_SECTIONS,
  CAREER_ARTIFACT_TEMPLATES,
  createCareerArtifactRequestSchema,
  generateCareerArtifactRequestSchema,
  normalizeCareerArtifactRenderProfile,
  type CareerArtifactType,
} from '@/shared/api/careerArtifactContracts'
import type { ExperienceItemDto } from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import { listExperiences } from '@/shared/api/profileApi'

import {
  clearCareerArtifactDraft,
  createCareerArtifactDraftKey,
  createEmptyCareerArtifactDraft,
  loadCareerArtifactDraft,
  pendingCareerArtifactIdempotencyKey,
  regenerateCareerArtifactDraftKey,
  saveCareerArtifactDraft,
  type CareerArtifactGenerationDraft,
} from './drafts'
import {
  ARTIFACT_FILE_LABELS,
  ARTIFACT_TYPE_LABELS,
  PROFILE_SECTION_LABELS,
  careerArtifactErrorMessage,
  hasCareerArtifactQualityWarning,
} from './presentation'
import {
  useCareerArtifactModelCatalogQuery,
  useCareerArtifactReadinessQuery,
  useCreateCareerArtifactMutation,
  useGenerateCareerArtifactMutation,
} from './queries'

const props = withDefaults(
  defineProps<{
    userId: string
    displayName: string
    email: string | null
    mode?: 'create' | 'regenerate'
    artifactType?: CareerArtifactType | null
    artifactId?: string
    artifactVersion?: number
    fixedTitle?: string
    initialModel?: string
    initialStep?: number
  }>(),
  {
    mode: 'create',
    artifactType: null,
    artifactId: '',
    artifactVersion: 0,
    fixedTitle: '',
    initialModel: '',
    initialStep: 1,
  },
)

const emit = defineEmits<{
  submitted: [accepted: RunAcceptedDto]
  cancelled: []
  'step-change': [step: number]
  'type-change': [type: CareerArtifactType | null]
  conflict: [code: string]
}>()

const form = ref<HTMLFormElement | null>(null)
const experiencePage = ref(0)
const experienceSize = 10
const selectedExperienceDetails = ref<Record<string, ExperienceItemDto>>({})
const errors = ref<Record<string, string>>({})
const submitError = ref('')

const draftKey = computed(() =>
  props.mode === 'create'
    ? createCareerArtifactDraftKey(props.userId)
    : regenerateCareerArtifactDraftKey(props.userId, props.artifactId, props.artifactVersion),
)
const initialDraft =
  loadCareerArtifactDraft(draftKey.value) ??
  createEmptyCareerArtifactDraft(props.displayName, props.email, props.artifactType)
if (props.artifactType !== null) initialDraft.artifactType = props.artifactType
if (props.fixedTitle !== '') initialDraft.title = props.fixedTitle
const draft = reactive<CareerArtifactGenerationDraft>(initialDraft)
const currentStep = ref(Math.min(4, Math.max(1, props.initialStep)))

const artifactType = computed<CareerArtifactType | null>({
  get: () => draft.artifactType,
  set: (value) => {
    if (props.mode === 'regenerate') return
    if (draft.artifactType !== value) draft.model = ''
    draft.artifactType = value
    emit('type-change', value)
  },
})
const templateKey = computed(() =>
  artifactType.value === null ? '' : CAREER_ARTIFACT_TEMPLATES[artifactType.value],
)
const experienceFilters = computed(() => ({
  verificationStatus: 'VERIFIED' as const,
  page: experiencePage.value,
  size: experienceSize,
  sort: 'updatedAt,desc',
}))
const experiences = useQuery({
  queryKey: computed(() => profileQueryKeys.experiences(props.userId, experienceFilters.value)),
  queryFn: () => listExperiences(experienceFilters.value),
  enabled: computed(() => props.userId !== ''),
})
const readiness = useCareerArtifactReadinessQuery(computed(() => props.userId))
const models = useCareerArtifactModelCatalogQuery(
  computed(() => props.userId),
  artifactType,
)
const createMutation = useCreateCareerArtifactMutation(computed(() => props.userId))
const generateMutation = useGenerateCareerArtifactMutation(
  computed(() => props.userId),
  computed(() => props.artifactId),
)
const isSubmitting = computed(
  () => createMutation.isPending.value || generateMutation.isPending.value,
)
const selectedModel = computed(
  () => models.data.value?.find((model) => model.id === draft.model) ?? null,
)
const modelSelectionValid = computed(
  () =>
    !models.isError.value && (models.data.value?.length ?? 0) > 0 && selectedModel.value !== null,
)
const selectedCategories = computed(() =>
  draft.experienceItemIds
    .map((id) => selectedExperienceDetails.value[id]?.evidenceCategory)
    .filter((value): value is string => value !== undefined),
)
const qualityWarning = computed(() => hasCareerArtifactQualityWarning(selectedCategories.value))
const canGoPrevious = computed(() => currentStep.value > 1)
const stepLabels = ['만들 문서', '경험 선택', 'AI 모델', '표시 정보·확인'] as const

watch(
  draft,
  () => {
    draft.step = currentStep.value
    saveCareerArtifactDraft(draftKey.value, draft)
  },
  { deep: true },
)

watch(
  () => props.initialStep,
  (step) => setStep(Math.min(step, earliestIncompleteStep())),
)

watch(
  () => models.data.value,
  (catalog) => {
    if (
      draft.model === '' &&
      props.initialModel !== '' &&
      catalog?.some((model) => model.id === props.initialModel)
    ) {
      draft.model = props.initialModel
    }
  },
  { immediate: true },
)

watch(
  () => experiences.data.value?.items,
  (items) => {
    for (const item of items ?? []) {
      if (draft.experienceItemIds.includes(item.id)) {
        selectedExperienceDetails.value = { ...selectedExperienceDetails.value, [item.id]: item }
      }
    }
  },
  { immediate: true },
)

setStep(Math.min(currentStep.value, earliestIncompleteStep()))
saveCareerArtifactDraft(draftKey.value, draft)

function setStep(step: number): void {
  const next = Math.min(4, Math.max(1, step))
  currentStep.value = next
  draft.step = next
  emit('step-change', next)
}

function earliestIncompleteStep(): number {
  if (draft.artifactType === null) return 1
  if (draft.experienceItemIds.length < 1 || draft.experienceItemIds.length > 20) return 2
  if (draft.model === '') return 3
  return 4
}

async function nextStep(): Promise<void> {
  if (!validateStep(currentStep.value)) {
    await focusFirstInvalid()
    return
  }
  setStep(Math.min(4, currentStep.value + 1))
}

function previousStep(): void {
  if (canGoPrevious.value) setStep(currentStep.value - 1)
}

function validateStep(step: number): boolean {
  const nextErrors: Record<string, string> = {}
  if (step === 1 && draft.artifactType === null) {
    nextErrors.artifactType = '만들 문서를 선택해 주세요.'
  }
  if (step === 2 && (draft.experienceItemIds.length < 1 || draft.experienceItemIds.length > 20)) {
    nextErrors.experienceItemIds = '확인된 경험을 1개 이상 20개 이하로 선택해 주세요.'
  }
  if (step === 3 && !modelSelectionValid.value) {
    nextErrors.model = models.isError.value
      ? 'AI 모델 목록을 불러오지 못했어요. 다시 불러온 뒤 선택해 주세요.'
      : '현재 사용할 수 있는 AI 모델을 선택해 주세요.'
  }
  if (step === 4) {
    if (draft.title.trim().length < 1 || draft.title.trim().length > 120) {
      nextErrors.title = '제목을 1자 이상 120자 이하로 입력해 주세요.'
    }
    const normalized = normalizeCareerArtifactRenderProfile(draft.renderProfile)
    const parsed = createCareerArtifactRequestSchema.shape.renderProfile.safeParse(normalized)
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const field = issue.path[0]
        if (typeof field === 'string' && nextErrors[field] === undefined) {
          nextErrors[field] = renderProfileError(field)
        }
      }
    }
  }
  errors.value = nextErrors
  return Object.keys(nextErrors).length === 0
}

function renderProfileError(field: string): string {
  if (field === 'displayName') return '표시 이름을 1자 이상 100자 이하로 입력해 주세요.'
  if (field === 'email') return '올바른 이메일 주소를 입력해 주세요.'
  if (field === 'phone') return '연락처는 30자 이하로 입력해 주세요.'
  if (field === 'links') return '링크는 HTTPS 주소로 최대 5개까지 입력해 주세요.'
  return '표시 정보를 확인해 주세요.'
}

async function focusFirstInvalid(): Promise<void> {
  await nextTick()
  form.value?.querySelector<HTMLElement>('[aria-invalid="true"], .field-error + input')?.focus()
}

function toggleExperience(item: ExperienceItemDto): void {
  const selected = draft.experienceItemIds.includes(item.id)
  if (selected) {
    draft.experienceItemIds = draft.experienceItemIds.filter((id) => id !== item.id)
    const next = { ...selectedExperienceDetails.value }
    delete next[item.id]
    selectedExperienceDetails.value = next
    return
  }
  if (draft.experienceItemIds.length >= 20) {
    errors.value = { ...errors.value, experienceItemIds: '경험은 최대 20개까지 선택할 수 있어요.' }
    return
  }
  draft.experienceItemIds = [...draft.experienceItemIds, item.id]
  selectedExperienceDetails.value = { ...selectedExperienceDetails.value, [item.id]: item }
  const nextErrors = { ...errors.value }
  delete nextErrors.experienceItemIds
  errors.value = nextErrors
}

function addLink(): void {
  if (draft.renderProfile.links.length >= 5) return
  draft.renderProfile.links.push({ label: '', url: '' })
}

function removeLink(index: number): void {
  draft.renderProfile.links.splice(index, 1)
}

async function submit(): Promise<void> {
  submitError.value = ''
  if (!validateStep(4) || !modelSelectionValid.value || draft.artifactType === null) {
    await focusFirstInvalid()
    return
  }

  const renderProfile = normalizeCareerArtifactRenderProfile(draft.renderProfile)
  const common = {
    experienceItemIds: [...draft.experienceItemIds],
    model: draft.model,
    templateKey: templateKey.value,
    includeProfileSections: [...draft.includeProfileSections],
    renderProfile,
  }
  try {
    let accepted: RunAcceptedDto
    if (props.mode === 'create') {
      const request = createCareerArtifactRequestSchema.parse({
        ...common,
        artifactType: draft.artifactType,
        title: draft.title.trim(),
      })
      const idempotencyKey = pendingCareerArtifactIdempotencyKey(draft, request, 'create')
      saveCareerArtifactDraft(draftKey.value, draft)
      accepted = await createMutation.mutateAsync({ request, idempotencyKey })
    } else {
      const request = generateCareerArtifactRequestSchema.parse({
        ...common,
        version: props.artifactVersion,
      })
      const idempotencyKey = pendingCareerArtifactIdempotencyKey(draft, request, 'regenerate')
      saveCareerArtifactDraft(draftKey.value, draft)
      accepted = await generateMutation.mutateAsync({ request, idempotencyKey })
    }
    clearCareerArtifactDraft(draftKey.value)
    emit('submitted', accepted)
  } catch (error) {
    const apiError = normalizeApiError(error)
    submitError.value = careerArtifactErrorMessage(apiError)
    if (apiError.status === 409) {
      await Promise.allSettled([readiness.refetch(), models.refetch(), experiences.refetch()])
      emit('conflict', apiError.code)
    }
  }
}

function cancel(): void {
  clearCareerArtifactDraft(draftKey.value)
  emit('cancelled')
}
</script>

<template>
  <form ref="form" class="artifact-generation" novalidate @submit.prevent="submit">
    <ol class="artifact-generation__steps" aria-label="생성 단계">
      <li
        v-for="(label, index) in stepLabels"
        :key="label"
        :class="{ 'artifact-generation__step--current': currentStep === index + 1 }"
        :aria-current="currentStep === index + 1 ? 'step' : undefined"
      >
        <span>{{ index + 1 }}</span>
        <strong>{{ label }}</strong>
      </li>
    </ol>

    <section
      v-if="currentStep === 1"
      class="artifact-generation__panel"
      aria-labelledby="artifact-step-1"
    >
      <header>
        <p class="section-kicker">1단계</p>
        <h2 id="artifact-step-1">어떤 초안을 만들까요?</h2>
        <p>AI가 만든 초안이므로 다운로드한 파일을 지원 전에 직접 검토해 주세요.</p>
      </header>
      <fieldset :aria-describedby="errors.artifactType ? 'artifact-type-error' : undefined">
        <legend class="sr-only">만들 문서</legend>
        <label
          v-for="type in ['RESUME', 'PORTFOLIO'] as const"
          :key="type"
          class="artifact-choice-card"
          :class="{ 'artifact-choice-card--selected': artifactType === type }"
        >
          <input
            v-model="artifactType"
            type="radio"
            name="artifactType"
            :value="type"
            :disabled="mode === 'regenerate'"
            :aria-invalid="Boolean(errors.artifactType)"
          />
          <span>
            <strong>{{ ARTIFACT_TYPE_LABELS[type] }}</strong>
            <small>{{ ARTIFACT_FILE_LABELS[type] }} 파일</small>
          </span>
        </label>
      </fieldset>
      <p v-if="errors.artifactType" id="artifact-type-error" class="inline-error" role="alert">
        {{ errors.artifactType }}
      </p>
    </section>

    <section
      v-else-if="currentStep === 2"
      class="artifact-generation__panel"
      aria-labelledby="artifact-step-2"
    >
      <header>
        <p class="section-kicker">2단계</p>
        <h2 id="artifact-step-2">확인된 경험을 선택하세요</h2>
        <p>
          현재 페이지에서 선택해도 다른 페이지의 선택은 유지됩니다. 최대 20개까지 사용할 수 있어요.
        </p>
      </header>
      <p class="artifact-generation__selection" aria-live="polite">
        선택 {{ draft.experienceItemIds.length }}개
      </p>
      <p v-if="experiences.isPending.value" role="status">확인된 경험을 불러오는 중…</p>
      <div v-else-if="experiences.isError.value" class="alert alert--warning">
        경험을 불러오지 못했어요.
        <button type="button" class="text-link" @click="experiences.refetch()">
          다시 불러오기
        </button>
      </div>
      <div v-else-if="experiences.data.value?.items.length === 0" class="alert alert--info">
        사용할 수 있는 확인된 경험이 없어요. 경험 보관함에서 먼저 확인해 주세요.
      </div>
      <ul v-else class="artifact-experience-list">
        <li v-for="item in experiences.data.value?.items" :key="item.id">
          <label>
            <input
              type="checkbox"
              :checked="draft.experienceItemIds.includes(item.id)"
              :aria-invalid="Boolean(errors.experienceItemIds)"
              :aria-describedby="errors.experienceItemIds ? 'artifact-experience-error' : undefined"
              @change="toggleExperience(item)"
            />
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.content }}</small>
              <span class="artifact-experience-list__badges" aria-label="경험 출처">
                <em v-if="item.documentSourceCount > 0">업로드 자료</em>
                <em v-if="item.githubRepositorySourceCount > 0">GitHub</em>
              </span>
            </span>
          </label>
        </li>
      </ul>
      <div
        v-if="experiences.data.value && experiences.data.value.totalPages > 1"
        class="pagination-controls"
      >
        <button
          type="button"
          class="button button--secondary"
          :disabled="experiencePage === 0"
          @click="experiencePage -= 1"
        >
          이전
        </button>
        <span>{{ experiencePage + 1 }} / {{ experiences.data.value.totalPages }}</span>
        <button
          type="button"
          class="button button--secondary"
          :disabled="experiencePage + 1 >= experiences.data.value.totalPages"
          @click="experiencePage += 1"
        >
          다음
        </button>
      </div>
      <p
        v-if="errors.experienceItemIds"
        id="artifact-experience-error"
        class="inline-error"
        role="alert"
      >
        {{ errors.experienceItemIds }}
      </p>
      <p v-if="draft.experienceItemIds.length > 0 && qualityWarning" class="alert alert--warning">
        프로젝트·경력 경험을 2개 이상, 강점 경험을 함께 선택하면 초안을 검토하기 더 좋아요. 지금
        선택으로도 계속할 수 있습니다.
      </p>
    </section>

    <section
      v-else-if="currentStep === 3"
      class="artifact-generation__panel"
      aria-labelledby="artifact-step-3"
    >
      <header>
        <p class="section-kicker">3단계</p>
        <h2 id="artifact-step-3">서버가 제공한 AI 모델을 선택하세요</h2>
        <p>표시된 정확한 모델 중 하나를 선택해야 생성할 수 있어요.</p>
      </header>
      <p v-if="models.isPending.value" role="status">사용할 수 있는 모델을 불러오는 중…</p>
      <div v-else-if="models.isError.value" class="alert alert--warning">
        AI 모델 목록을 불러오지 못했어요.
        <button type="button" class="text-link" @click="models.refetch()">다시 불러오기</button>
      </div>
      <p v-else-if="models.data.value?.length === 0" class="alert alert--warning">
        지금 선택할 수 있는 AI 모델이 없어요. 잠시 후 다시 확인해 주세요.
      </p>
      <fieldset v-else class="artifact-model-list">
        <legend class="sr-only">AI 모델</legend>
        <label
          v-for="model in models.data.value"
          :key="model.id"
          :class="{ 'artifact-choice-card--selected': draft.model === model.id }"
          class="artifact-choice-card"
        >
          <input
            v-model="draft.model"
            type="radio"
            name="model"
            :value="model.id"
            :aria-invalid="Boolean(errors.model)"
            :aria-describedby="errors.model ? 'artifact-model-error' : undefined"
          />
          <span>
            <strong>{{ model.displayName }} <em v-if="model.recommended">추천</em></strong>
            <small>{{ model.description }}</small>
          </span>
        </label>
      </fieldset>
      <p v-if="errors.model" id="artifact-model-error" class="inline-error" role="alert">
        {{ errors.model }}
      </p>
    </section>

    <section v-else class="artifact-generation__panel" aria-labelledby="artifact-step-4">
      <header>
        <p class="section-kicker">4단계</p>
        <h2 id="artifact-step-4">표시 정보와 요청 내용을 확인하세요</h2>
        <p>이 단계에서 확인 버튼을 눌러야만 AI 작업과 파일 생성이 시작됩니다.</p>
      </header>

      <div class="form-field">
        <label for="artifact-title">제목</label>
        <input
          id="artifact-title"
          v-model="draft.title"
          class="control"
          maxlength="120"
          :readonly="mode === 'regenerate'"
          :aria-invalid="Boolean(errors.title)"
          :aria-describedby="errors.title ? 'artifact-title-error' : undefined"
        />
        <span v-if="errors.title" id="artifact-title-error" class="inline-error">{{
          errors.title
        }}</span>
      </div>

      <dl class="artifact-generation__review">
        <div>
          <dt>문서</dt>
          <dd>{{ artifactType ? ARTIFACT_TYPE_LABELS[artifactType] : '' }}</dd>
        </div>
        <div>
          <dt>파일</dt>
          <dd>{{ artifactType ? ARTIFACT_FILE_LABELS[artifactType] : '' }}</dd>
        </div>
        <div>
          <dt>고정 템플릿</dt>
          <dd>{{ templateKey }}</dd>
        </div>
        <div>
          <dt>경험</dt>
          <dd>{{ draft.experienceItemIds.length }}개</dd>
        </div>
        <div>
          <dt>AI 모델</dt>
          <dd>{{ selectedModel?.displayName ?? '다시 선택 필요' }}</dd>
        </div>
      </dl>

      <fieldset class="artifact-profile-sections">
        <legend>표시할 프로필 정보</legend>
        <label v-for="section in CAREER_ARTIFACT_PROFILE_SECTIONS" :key="section">
          <input v-model="draft.includeProfileSections" type="checkbox" :value="section" />
          {{ PROFILE_SECTION_LABELS[section] }}
        </label>
      </fieldset>

      <section class="artifact-render-profile" aria-labelledby="renderer-profile-title">
        <header>
          <h3 id="renderer-profile-title">파일에 표시할 이름과 연락처</h3>
          <p class="alert alert--info">
            이 정보는 파일 생성에만 사용되며 AI 문맥으로 전송되지 않습니다.
          </p>
        </header>
        <div class="form-field">
          <label for="artifact-display-name">표시 이름</label>
          <input
            id="artifact-display-name"
            v-model="draft.renderProfile.displayName"
            class="control"
            maxlength="100"
            :aria-invalid="Boolean(errors.displayName)"
            :aria-describedby="errors.displayName ? 'artifact-display-name-error' : undefined"
          />
          <span v-if="errors.displayName" id="artifact-display-name-error" class="inline-error">{{
            errors.displayName
          }}</span>
        </div>
        <label class="artifact-contact-toggle">
          <input v-model="draft.renderProfile.includeContact" type="checkbox" />
          파일에 연락처와 링크 표시
        </label>
        <template v-if="draft.renderProfile.includeContact">
          <div class="artifact-contact-grid">
            <div class="form-field">
              <label for="artifact-email">이메일</label>
              <input
                id="artifact-email"
                v-model="draft.renderProfile.email"
                class="control"
                type="email"
                maxlength="320"
                :aria-invalid="Boolean(errors.email)"
                :aria-describedby="errors.email ? 'artifact-render-contact-error' : undefined"
              />
            </div>
            <div class="form-field">
              <label for="artifact-phone">연락처</label>
              <input
                id="artifact-phone"
                v-model="draft.renderProfile.phone"
                class="control"
                maxlength="30"
                :aria-invalid="Boolean(errors.phone)"
                :aria-describedby="errors.phone ? 'artifact-render-contact-error' : undefined"
              />
            </div>
          </div>
          <div class="artifact-links">
            <div class="artifact-links__heading">
              <strong>HTTPS 링크</strong>
              <button
                type="button"
                class="button button--secondary"
                :disabled="draft.renderProfile.links.length >= 5"
                @click="addLink"
              >
                링크 추가
              </button>
            </div>
            <div
              v-for="(link, index) in draft.renderProfile.links"
              :key="index"
              class="artifact-link-row"
            >
              <div class="form-field">
                <label :for="`artifact-link-label-${index}`">링크 이름</label>
                <input
                  :id="`artifact-link-label-${index}`"
                  v-model="link.label"
                  class="control"
                  maxlength="50"
                  :aria-invalid="Boolean(errors.links)"
                  :aria-describedby="errors.links ? 'artifact-render-contact-error' : undefined"
                />
              </div>
              <div class="form-field">
                <label :for="`artifact-link-url-${index}`">HTTPS 주소</label>
                <input
                  :id="`artifact-link-url-${index}`"
                  v-model="link.url"
                  class="control"
                  type="url"
                  maxlength="500"
                  :aria-invalid="Boolean(errors.links)"
                  :aria-describedby="errors.links ? 'artifact-render-contact-error' : undefined"
                />
              </div>
              <button type="button" class="button button--secondary" @click="removeLink(index)">
                제거
              </button>
            </div>
          </div>
          <p
            v-if="errors.email || errors.phone || errors.links"
            id="artifact-render-contact-error"
            class="inline-error"
            role="alert"
          >
            {{ errors.email ?? errors.phone ?? errors.links }}
          </p>
        </template>
      </section>

      <p
        v-if="readiness.data.value && !readiness.data.value.verifiedStrengthCount"
        class="alert alert--warning"
      >
        확인된 강점 경험이 아직 없어요. 현재 선택으로도 만들 수 있지만 결과를 더 꼼꼼히 검토해
        주세요.
      </p>
      <p v-if="submitError" class="alert alert--danger" role="alert">{{ submitError }}</p>
    </section>

    <footer class="artifact-generation__actions">
      <button type="button" class="button button--secondary" @click="cancel">취소</button>
      <div>
        <button
          v-if="canGoPrevious"
          type="button"
          class="button button--secondary"
          @click="previousStep"
        >
          이전
        </button>
        <button
          v-if="currentStep < 4"
          type="button"
          class="button button--primary"
          @click="nextStep"
        >
          다음
        </button>
        <button
          v-else
          type="submit"
          class="button button--primary"
          :disabled="isSubmitting || !modelSelectionValid"
        >
          {{
            isSubmitting
              ? '생성 요청 중…'
              : mode === 'create'
                ? '파일 생성 요청'
                : '새 버전 생성 요청'
          }}
        </button>
      </div>
    </footer>
  </form>
</template>

<style scoped>
.artifact-generation {
  display: grid;
  min-width: 0;
  gap: var(--space-6);
}

.artifact-generation__steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.artifact-generation__steps li {
  display: flex;
  min-width: 0;
  min-height: 3.5rem;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  color: var(--color-muted);
  background: var(--color-fill);
}

.artifact-generation__steps span {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface);
}

.artifact-generation__steps strong {
  overflow: hidden;
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-generation__steps .artifact-generation__step--current {
  color: var(--color-primary);
  background: var(--hs-blue-50);
}

.artifact-generation__panel {
  display: grid;
  gap: var(--space-5);
  padding: clamp(1.25rem, 4vw, 2rem);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.artifact-generation__panel header h2,
.artifact-generation__panel header p,
.artifact-render-profile h3 {
  margin: 0;
}

.artifact-generation__panel header > p:last-child {
  margin-top: var(--space-2);
  color: var(--color-muted);
}

.artifact-generation fieldset {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  border: 0;
}

.artifact-choice-card {
  display: flex;
  min-height: 5rem;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
}

.artifact-choice-card--selected {
  border-color: var(--color-primary);
  background: var(--hs-blue-50);
}

.artifact-choice-card input {
  margin-top: 0.2rem;
}

.artifact-choice-card span,
.artifact-choice-card small {
  display: block;
}

.artifact-choice-card small {
  margin-top: var(--space-1);
  color: var(--color-muted);
}

.artifact-choice-card em {
  padding: 0.15rem 0.4rem;
  border-radius: var(--radius-pill);
  color: var(--color-primary);
  background: var(--color-surface);
  font-size: var(--font-size-xs);
  font-style: normal;
}

.artifact-generation__selection {
  margin: 0;
  font-weight: 800;
}

.artifact-experience-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.artifact-experience-list label {
  display: flex;
  min-height: 4.5rem;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.artifact-experience-list label > span,
.artifact-experience-list small {
  display: block;
  min-width: 0;
}

.artifact-experience-list small {
  display: -webkit-box;
  margin-top: var(--space-1);
  overflow: hidden;
  color: var(--color-muted);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.artifact-experience-list__badges {
  display: flex !important;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: var(--space-2);
}

.artifact-experience-list__badges em {
  padding: 0.15rem 0.4rem;
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  background: var(--color-surface);
  font-size: var(--font-size-xs);
  font-style: normal;
}

.pagination-controls,
.artifact-generation__actions,
.artifact-generation__actions > div,
.artifact-links__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.artifact-generation__review {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.artifact-generation__review div {
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.artifact-generation__review dt {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.artifact-generation__review dd {
  margin: var(--space-1) 0 0;
  overflow-wrap: anywhere;
  font-weight: 750;
}

.artifact-profile-sections {
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
}

.artifact-profile-sections legend {
  grid-column: 1 / -1;
  font-weight: 800;
}

.artifact-profile-sections label,
.artifact-contact-toggle {
  display: flex;
  min-height: 2.75rem;
  align-items: center;
  gap: var(--space-2);
}

.artifact-render-profile {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.form-field {
  display: grid;
  gap: var(--space-2);
}

.form-field label {
  font-weight: 750;
}

.artifact-contact-grid,
.artifact-link-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.artifact-links {
  display: grid;
  gap: var(--space-3);
}

.artifact-link-row .button {
  grid-column: 1 / -1;
  justify-self: end;
}

@media (max-width: 44rem) {
  .artifact-generation__steps {
    grid-template-columns: 1fr;
  }

  .artifact-generation__steps li:not(.artifact-generation__step--current) {
    display: none;
  }

  .artifact-generation__review,
  .artifact-contact-grid,
  .artifact-link-row {
    grid-template-columns: 1fr;
  }

  .artifact-generation__actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .artifact-generation__actions > div,
  .artifact-generation__actions .button {
    width: 100%;
  }

  .artifact-generation__actions > div .button {
    flex: 1;
  }
}
</style>
