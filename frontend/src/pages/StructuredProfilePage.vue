<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'

import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { useDocumentListQuery } from '@/features/documents/queries'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import {
  type AwardFormValues,
  type CareerFormValues,
  type CertificationFormValues,
  type EducationFormValues,
  type LanguageScoreFormValues,
  validateAwardForm,
  validateCareerForm,
  validateCertificationForm,
  validateEducationForm,
  validateLanguageScoreForm,
} from '@/features/profile/schemas'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import type {
  AwardCreateRequest,
  AwardDto,
  CareerCreateRequest,
  CareerDto,
  CertificationCreateRequest,
  CertificationDto,
  EducationCreateRequest,
  EducationDto,
  LanguageScoreCreateRequest,
  LanguageScoreDto,
  PageResponse,
  StructuredProfileDto,
} from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

export type ResourceKind = 'education' | 'certification' | 'language' | 'award' | 'career'

type StructuredCreateRequest =
  | EducationCreateRequest
  | CertificationCreateRequest
  | LanguageScoreCreateRequest
  | AwardCreateRequest
  | CareerCreateRequest

interface FormModel
  extends
    EducationFormValues,
    CertificationFormValues,
    LanguageScoreFormValues,
    AwardFormValues,
    CareerFormValues {
  version: number
}

const props = defineProps<{ kind: ResourceKind }>()
const authStore = useAuthStore()
const queryClient = useQueryClient()
const userId = computed(() => authStore.currentUser?.id ?? '')
const page = ref(0)
const size = ref(20)
const sort = ref(defaultSort(props.kind))
const filters = computed(() => ({ page: page.value, size: size.value, sort: sort.value }))
const documentLinkable = computed(() => ['certification', 'language', 'award'].includes(props.kind))
const selectableDocuments = useDocumentListQuery(
  userId,
  { page: 0, size: 100, sort: 'updatedAt,desc' },
  documentLinkable,
)
const queryKey = computed(() => resourceQueryKey(props.kind, userId.value, filters.value))
const form = reactive<FormModel>(emptyForm())
const editingId = ref<string | null>(null)
const isFormOpen = ref(false)
const fieldErrors = ref<Record<string, string>>({})
const message = ref('')
const generalError = ref('')
const conflict = ref<{
  draft: Record<string, unknown>
  latest: StructuredProfileDto
  id: string
} | null>(null)

const resourceQuery = useQuery({
  queryKey,
  queryFn: () => listResources(props.kind, filters.value),
  enabled: computed(() => userId.value !== ''),
})

const saveMutation = useMutation({
  mutationFn: (command: {
    id: string | null
    version: number
    request: StructuredCreateRequest
  }) =>
    command.id === null
      ? createResource(props.kind, command.request)
      : updateResource(props.kind, command.id, command.version, command.request),
})

const deleteMutation = useMutation({
  mutationFn: (item: StructuredProfileDto) => deleteResource(props.kind, item.id, item.version),
})

const title = computed(() => resourceLabels[props.kind].title)
const description = computed(() => resourceLabels[props.kind].description)
const addLabel = computed(() => resourceLabels[props.kind].add)
const sortOptions = computed(() => resourceLabels[props.kind].sorts)
const conflictFields = computed(() => fieldsForKind(props.kind))

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  fieldErrors.value = {}
  generalError.value = ''
  conflict.value = null
  isFormOpen.value = true
}

function openEdit(item: StructuredProfileDto): void {
  Object.assign(form, emptyForm(), resourceToForm(props.kind, item), { version: item.version })
  editingId.value = item.id
  fieldErrors.value = {}
  generalError.value = ''
  conflict.value = null
  isFormOpen.value = true
}

function closeForm(): void {
  isFormOpen.value = false
  editingId.value = null
  conflict.value = null
  fieldErrors.value = {}
  generalError.value = ''
}

async function save(): Promise<void> {
  message.value = ''
  generalError.value = ''
  const validation = validateCurrentForm()
  fieldErrors.value = validation.fieldErrors
  if (validation.data === null) return

  try {
    const saved = await saveMutation.mutateAsync({
      id: editingId.value,
      version: form.version,
      request: validation.data,
    })
    await refreshAfterMutation()
    isFormOpen.value = false
    editingId.value = null
    message.value = `${resourceTitle(props.kind, saved)}을(를) 저장했어요.`
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (isVersionConflict(apiError) && editingId.value !== null) {
      const refreshed = await resourceQuery.refetch()
      const latest = refreshed.data?.items.find((item) => item.id === editingId.value)
      if (latest !== undefined) {
        conflict.value = {
          draft: { ...validation.data, version: form.version },
          latest,
          id: editingId.value,
        }
        generalError.value = '최신값과 내 입력을 비교해 다시 적용해 주세요.'
        return
      }
    }
    generalError.value = apiError.message
  }
}

async function remove(item: StructuredProfileDto): Promise<void> {
  if (!window.confirm(`${resourceTitle(props.kind, item)}을(를) 삭제할까요?`)) return
  message.value = ''
  generalError.value = ''
  try {
    await deleteMutation.mutateAsync(item)
    await refreshAfterMutation()
    message.value = '삭제했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (isVersionConflict(apiError)) {
      await resourceQuery.refetch()
      generalError.value =
        '삭제하려던 항목이 다른 곳에서 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해 주세요.'
      return
    }
    generalError.value = apiError.message
  }
}

async function makePrimary(item: EducationDto): Promise<void> {
  const request: EducationCreateRequest = {
    schoolName: item.schoolName,
    major: item.major,
    degree: item.degree,
    educationStatus: item.educationStatus,
    admissionDate: item.admissionDate,
    graduationDate: item.graduationDate,
    gpa: item.gpa,
    gpaScale: item.gpaScale,
    isPrimary: true,
    description: item.description,
  }
  try {
    await saveMutation.mutateAsync({ id: item.id, version: item.version, request })
    await refreshAfterMutation()
    message.value = '먼저 보여 줄 학력을 변경했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    await resourceQuery.refetch()
    generalError.value =
      apiError.code === 'RESOURCE_STATE_CONFLICT'
        ? '먼저 보여 줄 학력이 다른 곳에서 변경됐어요. 최신 목록을 확인해 주세요.'
        : apiError.message
  }
}

function cancelConflict(): void {
  const latest = conflict.value?.latest
  conflict.value = null
  if (latest !== undefined) {
    Object.assign(form, emptyForm(), resourceToForm(props.kind, latest), {
      version: latest.version,
    })
  }
}

function reapplyConflict(value: Record<string, unknown>): void {
  const latest = conflict.value?.latest
  if (latest === undefined) return
  const merged = { ...latest, ...value, version: latest.version } as StructuredProfileDto
  Object.assign(form, emptyForm(), resourceToForm(props.kind, merged), {
    version: latest.version,
  })
  conflict.value = null
  message.value = '선택한 내 입력을 최신 내용에 다시 적용했어요. 확인한 뒤 저장해 주세요.'
}

async function refreshAfterMutation(): Promise<void> {
  await resourceQuery.refetch()
  if (props.kind === 'education') {
    await queryClient.invalidateQueries({ queryKey: profileQueryKeys.profile(userId.value) })
  }
}

function validateCurrentForm(): {
  data: StructuredCreateRequest | null
  fieldErrors: Record<string, string>
} {
  switch (props.kind) {
    case 'education':
      return validateEducationForm(form)
    case 'certification':
      return validateCertificationForm(form)
    case 'language':
      return validateLanguageScoreForm(form)
    case 'award':
      return validateAwardForm(form)
    case 'career':
      return validateCareerForm(form)
  }
}

function onSortChange(): void {
  page.value = 0
}

function emptyForm(): FormModel {
  return {
    schoolName: '',
    major: '',
    degree: '',
    educationStatus: 'ENROLLED',
    admissionDate: '',
    graduationDate: '',
    gpa: '',
    gpaScale: '',
    isPrimary: false,
    description: '',
    evidenceDocumentId: '',
    name: '',
    issuer: '',
    credentialNumber: '',
    acquiredDate: '',
    expiresAt: '',
    testName: '',
    score: '',
    grade: '',
    testedAt: '',
    organizer: '',
    awardedAt: '',
    organization: '',
    position: '',
    employmentType: '',
    startedAt: '',
    endedAt: '',
    isCurrent: false,
    responsibilities: '',
    achievements: '',
    version: 0,
  }
}

function defaultSort(kind: ResourceKind): string {
  switch (kind) {
    case 'education':
      return 'createdAt,desc'
    case 'certification':
      return 'acquiredDate,desc'
    case 'language':
      return 'testedAt,desc'
    case 'award':
      return 'awardedAt,desc'
    case 'career':
      return 'startedAt,desc'
  }
}

function resourceQueryKey(kind: ResourceKind, ownerId: string, params: profileApi.PageParams) {
  switch (kind) {
    case 'education':
      return profileQueryKeys.educations(ownerId, params)
    case 'certification':
      return profileQueryKeys.certifications(ownerId, params)
    case 'language':
      return profileQueryKeys.languageScores(ownerId, params)
    case 'award':
      return profileQueryKeys.awards(ownerId, params)
    case 'career':
      return profileQueryKeys.careers(ownerId, params)
  }
}

function listResources(
  kind: ResourceKind,
  params: profileApi.PageParams,
): Promise<PageResponse<StructuredProfileDto>> {
  switch (kind) {
    case 'education':
      return profileApi.listEducations(params)
    case 'certification':
      return profileApi.listCertifications(params)
    case 'language':
      return profileApi.listLanguageScores(params)
    case 'award':
      return profileApi.listAwards(params)
    case 'career':
      return profileApi.listCareers(params)
  }
}

function createResource(
  kind: ResourceKind,
  request: StructuredCreateRequest,
): Promise<StructuredProfileDto> {
  switch (kind) {
    case 'education':
      return profileApi.createEducation(request as EducationCreateRequest)
    case 'certification':
      return profileApi.createCertification(request as CertificationCreateRequest)
    case 'language':
      return profileApi.createLanguageScore(request as LanguageScoreCreateRequest)
    case 'award':
      return profileApi.createAward(request as AwardCreateRequest)
    case 'career':
      return profileApi.createCareer(request as CareerCreateRequest)
  }
}

function updateResource(
  kind: ResourceKind,
  id: string,
  version: number,
  request: StructuredCreateRequest,
): Promise<StructuredProfileDto> {
  switch (kind) {
    case 'education':
      return profileApi.updateEducation(id, { ...(request as EducationCreateRequest), version })
    case 'certification':
      return profileApi.updateCertification(id, {
        ...(request as CertificationCreateRequest),
        version,
      })
    case 'language':
      return profileApi.updateLanguageScore(id, {
        ...(request as LanguageScoreCreateRequest),
        version,
      })
    case 'award':
      return profileApi.updateAward(id, { ...(request as AwardCreateRequest), version })
    case 'career':
      return profileApi.updateCareer(id, { ...(request as CareerCreateRequest), version })
  }
}

function deleteResource(kind: ResourceKind, id: string, version: number): Promise<void> {
  switch (kind) {
    case 'education':
      return profileApi.deleteEducation(id, version)
    case 'certification':
      return profileApi.deleteCertification(id, version)
    case 'language':
      return profileApi.deleteLanguageScore(id, version)
    case 'award':
      return profileApi.deleteAward(id, version)
    case 'career':
      return profileApi.deleteCareer(id, version)
  }
}

function resourceTitle(kind: ResourceKind, item: StructuredProfileDto): string {
  switch (kind) {
    case 'education':
      return (item as EducationDto).schoolName
    case 'certification':
      return (item as CertificationDto).name
    case 'language':
      return `${(item as LanguageScoreDto).testName} ${(item as LanguageScoreDto).score}`
    case 'award':
      return (item as AwardDto).name
    case 'career':
      return (item as CareerDto).organization
  }
}

function resourceSubtitle(kind: ResourceKind, item: StructuredProfileDto): string {
  switch (kind) {
    case 'education': {
      const education = item as EducationDto
      return [education.major, education.degree, education.educationStatus]
        .filter(Boolean)
        .join(' · ')
    }
    case 'certification': {
      const certification = item as CertificationDto
      return [certification.issuer, certification.acquiredDate].filter(Boolean).join(' · ')
    }
    case 'language': {
      const language = item as LanguageScoreDto
      return [language.grade, language.testedAt].filter(Boolean).join(' · ')
    }
    case 'award': {
      const award = item as AwardDto
      return [award.organizer, award.awardedAt].filter(Boolean).join(' · ')
    }
    case 'career': {
      const career = item as CareerDto
      return [career.position, career.startedAt, career.isCurrent ? '재직 중' : career.endedAt]
        .filter(Boolean)
        .join(' · ')
    }
  }
}

function resourceToForm(kind: ResourceKind, item: StructuredProfileDto): Partial<FormModel> {
  switch (kind) {
    case 'education': {
      const value = item as EducationDto
      return {
        schoolName: value.schoolName,
        major: value.major ?? '',
        degree: value.degree ?? '',
        educationStatus: value.educationStatus,
        admissionDate: value.admissionDate ?? '',
        graduationDate: value.graduationDate ?? '',
        gpa: value.gpa?.toString() ?? '',
        gpaScale: value.gpaScale?.toString() ?? '',
        isPrimary: value.isPrimary,
        description: value.description ?? '',
      }
    }
    case 'certification': {
      const value = item as CertificationDto
      return {
        name: value.name,
        issuer: value.issuer ?? '',
        credentialNumber: value.credentialNumber ?? '',
        acquiredDate: value.acquiredDate ?? '',
        expiresAt: value.expiresAt ?? '',
        description: value.description ?? '',
        evidenceDocumentId: value.evidenceDocumentId ?? '',
      }
    }
    case 'language': {
      const value = item as LanguageScoreDto
      return {
        testName: value.testName,
        score: value.score,
        grade: value.grade ?? '',
        testedAt: value.testedAt ?? '',
        expiresAt: value.expiresAt ?? '',
        evidenceDocumentId: value.evidenceDocumentId ?? '',
      }
    }
    case 'award': {
      const value = item as AwardDto
      return {
        name: value.name,
        organizer: value.organizer ?? '',
        awardedAt: value.awardedAt ?? '',
        description: value.description ?? '',
        evidenceDocumentId: value.evidenceDocumentId ?? '',
      }
    }
    case 'career': {
      const value = item as CareerDto
      return {
        organization: value.organization,
        position: value.position ?? '',
        employmentType: value.employmentType ?? '',
        startedAt: value.startedAt ?? '',
        endedAt: value.endedAt ?? '',
        isCurrent: value.isCurrent,
        responsibilities: value.responsibilities ?? '',
        achievements: value.achievements ?? '',
      }
    }
  }
}

function fieldsForKind(kind: ResourceKind): Array<{ key: string; label: string }> {
  const common: Record<ResourceKind, Array<{ key: string; label: string }>> = {
    education: [
      { key: 'schoolName', label: '학교명' },
      { key: 'major', label: '전공' },
      { key: 'degree', label: '학위' },
      { key: 'educationStatus', label: '상태' },
      { key: 'admissionDate', label: '입학일' },
      { key: 'graduationDate', label: '졸업일' },
      { key: 'gpa', label: '학점' },
      { key: 'gpaScale', label: '기준 학점' },
      { key: 'isPrimary', label: '먼저 보여 줄 학력' },
      { key: 'description', label: '설명' },
    ],
    certification: [
      { key: 'name', label: '자격증명' },
      { key: 'issuer', label: '발급 기관' },
      { key: 'credentialNumber', label: '자격 번호' },
      { key: 'acquiredDate', label: '취득일' },
      { key: 'expiresAt', label: '만료일' },
      { key: 'description', label: '설명' },
      { key: 'evidenceDocumentId', label: '증빙 문서' },
    ],
    language: [
      { key: 'testName', label: '시험명' },
      { key: 'score', label: '점수' },
      { key: 'grade', label: '등급' },
      { key: 'testedAt', label: '응시일' },
      { key: 'expiresAt', label: '만료일' },
      { key: 'evidenceDocumentId', label: '증빙 문서' },
    ],
    award: [
      { key: 'name', label: '수상명' },
      { key: 'organizer', label: '주최 기관' },
      { key: 'awardedAt', label: '수상일' },
      { key: 'description', label: '설명' },
      { key: 'evidenceDocumentId', label: '증빙 문서' },
    ],
    career: [
      { key: 'organization', label: '회사·기관' },
      { key: 'position', label: '직무' },
      { key: 'employmentType', label: '고용 형태' },
      { key: 'startedAt', label: '시작일' },
      { key: 'endedAt', label: '종료일' },
      { key: 'isCurrent', label: '재직 중' },
      { key: 'responsibilities', label: '역할' },
      { key: 'achievements', label: '성과' },
    ],
  }
  return common[kind]
}

const resourceLabels: Record<
  ResourceKind,
  {
    title: string
    description: string
    add: string
    sorts: Array<{ value: string; label: string }>
  }
> = {
  education: {
    title: '학력',
    description: '지원할 때 먼저 보여 줄 학력을 정리해 두세요.',
    add: '학력 추가',
    sorts: [
      { value: 'createdAt,desc', label: '최근 등록순' },
      { value: 'graduationDate,desc', label: '졸업일순' },
    ],
  },
  certification: {
    title: '자격증',
    description: '직무와 연결되는 자격증을 모아 두세요.',
    add: '자격증 추가',
    sorts: [
      { value: 'acquiredDate,desc', label: '취득일순' },
      { value: 'createdAt,desc', label: '최근 등록순' },
    ],
  },
  language: {
    title: '어학 성적',
    description: '어학 성적과 유효기간을 한눈에 확인하세요.',
    add: '어학 성적 추가',
    sorts: [
      { value: 'testedAt,desc', label: '응시일순' },
      { value: 'createdAt,desc', label: '최근 등록순' },
    ],
  },
  award: {
    title: '수상',
    description: '나의 성과를 보여 주는 수상 경험을 정리하세요.',
    add: '수상 추가',
    sorts: [
      { value: 'awardedAt,desc', label: '수상일순' },
      { value: 'createdAt,desc', label: '최근 등록순' },
    ],
  },
  career: {
    title: '경력',
    description: '해 온 일과 만든 변화를 시간의 흐름대로 남겨 보세요.',
    add: '경력 추가',
    sorts: [
      { value: 'startedAt,desc', label: '시작일순' },
      { value: 'createdAt,desc', label: '최근 등록순' },
    ],
  },
}
</script>

<template>
  <section class="structured-profile app-page" :aria-labelledby="`${kind}-heading`">
    <ProfileTabs />
    <PageHeader
      :heading-id="`${kind}-heading`"
      :title="title"
      :description="description"
      eyebrow="나의 경험"
    >
      <template #actions>
        <button type="button" class="button button--primary" @click="openCreate">
          {{ addLabel }}
        </button>
      </template>
    </PageHeader>

    <p v-if="documentLinkable" class="alert alert--info structured-profile__guidance">
      지금 이용 중인 계정에 남아 있는 자료만 연결할 수 있어요.
    </p>

    <div class="filter-toolbar structured-profile__toolbar">
      <label class="field field--inline" :for="`${kind}-sort`">
        <span class="field__label">정렬</span>
        <select
          :id="`${kind}-sort`"
          v-model="sort"
          class="control control--compact"
          @change="onSortChange"
        >
          <option v-for="option in sortOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="message" class="alert alert--success structured-profile__message" role="status">
      {{ message }}
    </p>
    <p
      v-if="generalError && !isFormOpen"
      class="alert alert--danger structured-profile__message"
      role="alert"
    >
      {{ generalError }}
    </p>

    <section
      v-if="isFormOpen"
      class="structured-editor section-surface"
      role="region"
      :aria-label="editingId ? `${title} 수정` : `${title} 추가`"
    >
      <div class="structured-editor__header">
        <div>
          <p class="section-kicker">{{ editingId ? '등록 정보 수정' : '새 항목 등록' }}</p>
          <h3 class="section-title">{{ editingId ? `${title} 수정` : addLabel }}</h3>
        </div>
        <button type="button" class="button button--ghost button--compact" @click="closeForm">
          닫기
        </button>
      </div>

      <VersionConflictPanel
        v-if="conflict"
        class="mt-4"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="conflictFields"
        @cancel="cancelConflict"
        @reapply="reapplyConflict"
      />

      <form class="structured-form" novalidate @submit.prevent="save">
        <template v-if="kind === 'education'">
          <label class="text-sm font-medium"
            >학교명<input
              id="education-schoolName"
              v-model="form.schoolName"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
            /><span v-if="fieldErrors.schoolName" class="mt-1 block text-red-700">{{
              fieldErrors.schoolName
            }}</span></label
          >
          <label class="text-sm font-medium"
            >전공<input
              v-model="form.major"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
          /></label>
          <label class="text-sm font-medium"
            >학위<input
              v-model="form.degree"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="100"
          /></label>
          <label class="text-sm font-medium"
            >재학 상태<select
              v-model="form.educationStatus"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            >
              <option value="ENROLLED">재학</option>
              <option value="LEAVE_OF_ABSENCE">휴학</option>
              <option value="EXPECTED_GRADUATION">졸업 예정</option>
              <option value="GRADUATED">졸업</option>
              <option value="WITHDRAWN">중퇴</option>
            </select></label
          >
          <label class="text-sm font-medium"
            >입학일<input
              v-model="form.admissionDate"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
          /></label>
          <label class="text-sm font-medium"
            >졸업일<input
              v-model="form.graduationDate"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
            /><span v-if="fieldErrors.graduationDate" class="mt-1 block text-red-700">{{
              fieldErrors.graduationDate
            }}</span></label
          >
          <label class="text-sm font-medium"
            >학점<input
              v-model="form.gpa"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              inputmode="decimal"
            /><span v-if="fieldErrors.gpa" class="mt-1 block text-red-700">{{
              fieldErrors.gpa
            }}</span></label
          >
          <label class="text-sm font-medium"
            >기준 학점<input
              v-model="form.gpaScale"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              inputmode="decimal"
            /><span v-if="fieldErrors.gpaScale" class="mt-1 block text-red-700">{{
              fieldErrors.gpaScale
            }}</span></label
          >
          <label class="flex items-center gap-2 text-sm font-medium"
            ><input v-model="form.isPrimary" type="checkbox" />먼저 보여 줄 학력으로 설정</label
          >
          <label class="md:col-span-2 text-sm font-medium"
            >설명<textarea
              v-model="form.description"
              class="mt-1 min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="5000"
            />
          </label>
        </template>

        <template v-else-if="kind === 'certification'">
          <label class="text-sm font-medium"
            >자격증명<input
              id="certification-name"
              v-model="form.name"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
            /><span v-if="fieldErrors.name" class="mt-1 block text-red-700">{{
              fieldErrors.name
            }}</span></label
          >
          <label class="text-sm font-medium"
            >발급 기관<input
              v-model="form.issuer"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
          /></label>
          <label class="text-sm font-medium"
            >자격 번호<input
              v-model="form.credentialNumber"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
          /></label>
          <label class="text-sm font-medium"
            >취득일<input
              v-model="form.acquiredDate"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
          /></label>
          <label class="text-sm font-medium"
            >만료일<input
              v-model="form.expiresAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
            /><span v-if="fieldErrors.expiresAt" class="mt-1 block text-red-700">{{
              fieldErrors.expiresAt
            }}</span></label
          >
          <label class="md:col-span-2 text-sm font-medium"
            >설명<textarea
              v-model="form.description"
              class="mt-1 min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="5000"
            />
          </label>
        </template>

        <template v-else-if="kind === 'language'">
          <label class="text-sm font-medium"
            >시험명<input
              id="language-testName"
              v-model="form.testName"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="100"
            /><span v-if="fieldErrors.testName" class="mt-1 block text-red-700">{{
              fieldErrors.testName
            }}</span></label
          >
          <label class="text-sm font-medium"
            >점수<input
              v-model="form.score"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="100"
            /><span v-if="fieldErrors.score" class="mt-1 block text-red-700">{{
              fieldErrors.score
            }}</span></label
          >
          <label class="text-sm font-medium"
            >등급<input
              v-model="form.grade"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="100"
          /></label>
          <label class="text-sm font-medium"
            >응시일<input
              v-model="form.testedAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
          /></label>
          <label class="text-sm font-medium"
            >만료일<input
              v-model="form.expiresAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
            /><span v-if="fieldErrors.expiresAt" class="mt-1 block text-red-700">{{
              fieldErrors.expiresAt
            }}</span></label
          >
        </template>

        <template v-else-if="kind === 'award'">
          <label class="text-sm font-medium"
            >수상명<input
              id="award-name"
              v-model="form.name"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
            /><span v-if="fieldErrors.name" class="mt-1 block text-red-700">{{
              fieldErrors.name
            }}</span></label
          >
          <label class="text-sm font-medium"
            >주최 기관<input
              v-model="form.organizer"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
          /></label>
          <label class="text-sm font-medium"
            >수상일<input
              v-model="form.awardedAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
          /></label>
          <label class="md:col-span-2 text-sm font-medium"
            >설명<textarea
              v-model="form.description"
              class="mt-1 min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="5000"
            />
          </label>
        </template>

        <template v-else>
          <label class="text-sm font-medium"
            >회사·기관<input
              id="career-organization"
              v-model="form.organization"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
            /><span v-if="fieldErrors.organization" class="mt-1 block text-red-700">{{
              fieldErrors.organization
            }}</span></label
          >
          <label class="text-sm font-medium"
            >직무<input
              v-model="form.position"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="200"
          /></label>
          <label class="text-sm font-medium"
            >고용 형태<input
              v-model="form.employmentType"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="50"
          /></label>
          <label class="text-sm font-medium"
            >시작일<input
              v-model="form.startedAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              type="date"
          /></label>
          <label class="text-sm font-medium"
            >종료일<input
              v-model="form.endedAt"
              class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 disabled:bg-slate-100"
              type="date"
              :disabled="form.isCurrent"
            /><span v-if="fieldErrors.endedAt" class="mt-1 block text-red-700">{{
              fieldErrors.endedAt
            }}</span></label
          >
          <label class="flex items-center gap-2 text-sm font-medium"
            ><input
              v-model="form.isCurrent"
              type="checkbox"
              @change="form.isCurrent && (form.endedAt = '')"
            />현재 재직 중</label
          >
          <label class="md:col-span-2 text-sm font-medium"
            >역할<textarea
              v-model="form.responsibilities"
              class="mt-1 min-h-28 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="20000"
            />
          </label>
          <label class="md:col-span-2 text-sm font-medium"
            >성과<textarea
              v-model="form.achievements"
              class="mt-1 min-h-28 w-full rounded-lg border border-slate-300 px-3 py-2"
              maxlength="20000"
            />
          </label>
        </template>

        <label v-if="documentLinkable" class="md:col-span-2 text-sm font-medium">
          증빙 문서
          <select
            :id="`${kind}-evidenceDocumentId`"
            v-model="form.evidenceDocumentId"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            :disabled="selectableDocuments.isPending.value || selectableDocuments.isError.value"
          >
            <option value="">연결하지 않음</option>
            <option
              v-for="candidate in selectableDocuments.data.value?.items"
              :key="candidate.id"
              :value="candidate.id"
            >
              {{ candidate.displayName }}
            </option>
          </select>
          <span v-if="selectableDocuments.isError.value" class="mt-1 block text-red-700">
            자료 목록을 불러오지 못했어요.
          </span>
        </label>

        <p v-if="generalError" class="alert alert--danger form-span" role="alert">
          {{ generalError }}
        </p>
        <div class="form-actions form-span">
          <button
            type="submit"
            class="button button--primary"
            :disabled="saveMutation.isPending.value"
          >
            {{ saveMutation.isPending.value ? '저장 중…' : '저장' }}
          </button>
          <button type="button" class="button button--secondary" @click="closeForm">취소</button>
        </div>
      </form>
    </section>

    <StatePanel
      v-if="resourceQuery.isPending.value"
      class="structured-profile__state"
      kind="loading"
      :title="`${title} 목록을 불러오는 중…`"
      description="저장된 항목을 확인하고 있어요."
    />
    <StatePanel
      v-else-if="resourceQuery.isError.value"
      class="structured-profile__state"
      kind="error"
      :title="`${title} 목록을 불러오지 못했어요.`"
      description="연결 상태를 확인한 뒤 다시 시도해 주세요."
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="resourceQuery.refetch()">
          다시 시도
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="resourceQuery.data.value?.items.length === 0"
      class="structured-profile__state"
      kind="empty"
      :title="`등록된 ${title} 항목이 없어요.`"
      :description="`${title} 정보를 추가해 여러 지원에 활용해 보세요.`"
    >
      <template #actions>
        <button type="button" class="button button--primary" @click="openCreate">
          {{ addLabel }}
        </button>
      </template>
    </StatePanel>
    <ol
      v-else
      class="structured-list data-list"
      :class="{ 'structured-list--timeline': kind === 'career' }"
    >
      <li
        v-for="item in resourceQuery.data.value?.items"
        :key="item.id"
        class="structured-item data-card"
      >
        <div class="structured-item__body">
          <div class="structured-item__content">
            <div class="structured-item__title">
              <h3>{{ resourceTitle(kind, item) }}</h3>
              <span
                v-if="kind === 'education' && (item as EducationDto).isPrimary"
                class="status-badge status-badge--brand"
                >먼저 보여 줄 학력</span
              >
            </div>
            <p v-if="resourceSubtitle(kind, item)" class="structured-item__meta">
              {{ resourceSubtitle(kind, item) }}
            </p>
          </div>
          <div class="structured-item__actions">
            <button
              v-if="kind === 'education' && !(item as EducationDto).isPrimary"
              type="button"
              class="button button--secondary button--compact"
              @click="makePrimary(item as EducationDto)"
            >
              대표로 설정
            </button>
            <button
              type="button"
              class="button button--ghost button--compact"
              @click="openEdit(item)"
            >
              수정
            </button>
            <button
              type="button"
              class="button button--danger button--compact"
              :disabled="deleteMutation.isPending.value"
              @click="remove(item)"
            >
              삭제
            </button>
          </div>
        </div>
      </li>
    </ol>

    <PaginationNav
      v-if="resourceQuery.data.value && resourceQuery.data.value.totalPages > 0"
      :page="page"
      :total-pages="resourceQuery.data.value.totalPages"
      label="목록 페이지"
      @change="page = $event"
    />
  </section>
</template>

<style scoped>
.structured-profile__guidance,
.structured-profile__toolbar,
.structured-profile__message,
.structured-profile__state,
.structured-editor,
.structured-list {
  margin-top: var(--space-5);
}

.structured-profile__toolbar {
  justify-content: flex-end;
}

.structured-editor {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.structured-editor__header,
.structured-item__body,
.structured-item__title,
.structured-item__actions {
  display: flex;
  align-items: center;
}

.structured-editor__header,
.structured-item__body {
  justify-content: space-between;
  gap: var(--space-4);
}

.structured-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.structured-form :deep(label) {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 650;
}

.structured-form :deep(input:not([type='checkbox'])),
.structured-form :deep(select),
.structured-form :deep(textarea) {
  width: 100%;
  margin-top: var(--space-2);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
}

.structured-form :deep(input:not([type='checkbox'])),
.structured-form :deep(select) {
  min-height: 2.75rem;
}

.structured-form :deep(input:not([type='checkbox']):focus),
.structured-form :deep(select:focus),
.structured-form :deep(textarea:focus) {
  border-color: var(--color-brand);
  outline: 3px solid var(--color-focus);
  outline-offset: 1px;
}

.structured-form :deep(.md\:col-span-2),
.form-span {
  grid-column: 1 / -1;
}

.structured-item {
  position: relative;
  padding: var(--space-5);
}

.structured-list--timeline {
  padding-left: var(--space-5);
  border-left: 2px solid var(--color-border);
}

.structured-list--timeline .structured-item::before {
  position: absolute;
  top: 1.6rem;
  left: calc(-1 * var(--space-5) - 0.42rem);
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid var(--color-brand);
  border-radius: 50%;
  background: var(--color-canvas);
  content: '';
}

.structured-item__content {
  min-width: 0;
}

.structured-item__title,
.structured-item__actions {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.structured-item__title h3 {
  overflow-wrap: anywhere;
  font-weight: 700;
}

.structured-item__meta {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 48rem) {
  .structured-profile__toolbar {
    justify-content: stretch;
  }

  .structured-profile__toolbar .field {
    width: 100%;
  }

  .structured-form {
    grid-template-columns: 1fr;
  }

  .structured-form :deep(.md\:col-span-2) {
    grid-column: auto;
  }

  .form-span {
    grid-column: 1;
  }

  .structured-item__body {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
