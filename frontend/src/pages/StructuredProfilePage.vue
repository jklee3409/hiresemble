<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref } from 'vue'

import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import ProfileSectionActions from '@/features/profile/ProfileSectionActions.vue'
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
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
import { useNotifications } from '@/shared/ui/notifications'
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

const educationStatusLabels: Record<EducationDto['educationStatus'], string> = {
  ENROLLED: '재학',
  LEAVE_OF_ABSENCE: '휴학',
  EXPECTED_GRADUATION: '졸업 예정',
  GRADUATED: '졸업',
  WITHDRAWN: '중퇴',
}

const educationLevelLabels: Record<EducationDto['educationLevel'], string> = {
  OTHER: '기타 교육',
  HIGH_SCHOOL: '고등학교',
  ASSOCIATE: '대학교(전문학사)',
  BACHELOR: '대학교(학사)',
  MASTER: '대학원(석사)',
  DOCTORATE: '대학원(박사)',
}

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
const notifications = useNotifications()
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
  if (validation.data === null) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }

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
    notifications.toast(message.value, 'success')
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
  const confirmed = await notifications.confirm({
    title: `${resourceTitle(props.kind, item)}을(를) 삭제할까요?`,
    message: '내 지원 정보와 이후 AI 소재 후보에서 함께 제외됩니다.',
    confirmLabel: '정보 삭제',
  })
  if (!confirmed) return
  message.value = ''
  generalError.value = ''
  try {
    await deleteMutation.mutateAsync(item)
    await refreshAfterMutation()
    message.value = '삭제했어요.'
    notifications.toast(message.value, 'success')
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
    educationLevel: 'BACHELOR',
    educationStatus: 'ENROLLED',
    admissionDate: '',
    graduationDate: '',
    gpa: '',
    gpaScale: '',
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
      return [
        educationLevelLabels[education.educationLevel],
        education.major,
        education.degree,
        educationStatusLabel(education.educationStatus),
      ]
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
        educationLevel: value.educationLevel,
        educationStatus: value.educationStatus,
        admissionDate: value.admissionDate ?? '',
        graduationDate: value.graduationDate ?? '',
        gpa: value.gpa?.toString() ?? '',
        gpaScale: value.gpaScale?.toString() ?? '',
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

function educationStatusLabel(value: unknown): string {
  return typeof value === 'string' &&
    Object.prototype.hasOwnProperty.call(educationStatusLabels, value)
    ? educationStatusLabels[value as EducationDto['educationStatus']]
    : '알 수 없는 상태'
}

function fieldsForKind(
  kind: ResourceKind,
): Array<{ key: string; label: string; format?: (value: unknown) => string }> {
  const common: Record<
    ResourceKind,
    Array<{ key: string; label: string; format?: (value: unknown) => string }>
  > = {
    education: [
      { key: 'schoolName', label: '학교명' },
      { key: 'major', label: '전공' },
      {
        key: 'educationLevel',
        label: '학력 단계',
        format: (value) =>
          typeof value === 'string' &&
          Object.prototype.hasOwnProperty.call(educationLevelLabels, value)
            ? educationLevelLabels[value as EducationDto['educationLevel']]
            : '기타 교육',
      },
      { key: 'degree', label: '학위·과정명' },
      { key: 'educationStatus', label: '상태', format: educationStatusLabel },
      { key: 'admissionDate', label: '입학일' },
      { key: 'graduationDate', label: '졸업(예정)일' },
      { key: 'gpa', label: '학점' },
      { key: 'gpaScale', label: '기준 학점' },
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
    description: '학력 단계를 기준으로 서버가 최종 학력을 자동으로 표시해요.',
    add: '학력 추가',
    sorts: [
      { value: 'createdAt,desc', label: '최근 등록순' },
      { value: 'graduationDate,desc', label: '졸업(예정)일순' },
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
  <section
    class="structured-profile app-page profile-workspace-shell"
    :aria-labelledby="`${kind}-heading`"
  >
    <ProfileTabs />
    <div class="profile-workspace-shell__content">
      <PageHeader
        :heading-id="`${kind}-heading`"
        :title="title"
        :description="description"
        eyebrow="내 지원 정보"
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
            <label class="field"
              >학교명<input
                id="education-schoolName"
                v-model="form.schoolName"
                class="control"
                maxlength="200"
              /><span v-if="fieldErrors.schoolName" class="field-error">{{
                fieldErrors.schoolName
              }}</span></label
            >
            <label class="field"
              >전공<input v-model="form.major" class="control" maxlength="200"
            /></label>
            <label class="field"
              >학력 단계<select v-model="form.educationLevel" class="control">
                <option value="HIGH_SCHOOL">고등학교</option>
                <option value="ASSOCIATE">대학교(전문학사)</option>
                <option value="BACHELOR">대학교(학사)</option>
                <option value="MASTER">대학원(석사)</option>
                <option value="DOCTORATE">대학원(박사)</option>
                <option value="OTHER">기타 교육</option>
              </select></label
            >
            <label class="field"
              >학위·과정명<input
                v-model="form.degree"
                class="control"
                maxlength="100"
                placeholder="예: 컴퓨터공학 학사"
            /></label>
            <label class="field"
              >재학 상태<select v-model="form.educationStatus" class="control">
                <option value="ENROLLED">재학</option>
                <option value="LEAVE_OF_ABSENCE">휴학</option>
                <option value="EXPECTED_GRADUATION">졸업 예정</option>
                <option value="GRADUATED">졸업</option>
                <option value="WITHDRAWN">중퇴</option>
              </select></label
            >
            <label class="field"
              >입학일<input v-model="form.admissionDate" class="control" type="date"
            /></label>
            <label class="field"
              >졸업(예정)일<input v-model="form.graduationDate" class="control" type="date" /><span
                v-if="fieldErrors.graduationDate"
                class="field-error"
                >{{ fieldErrors.graduationDate }}</span
              ></label
            >
            <label class="field"
              >학점<input v-model="form.gpa" class="control" inputmode="decimal" /><span
                v-if="fieldErrors.gpa"
                class="field-error"
                >{{ fieldErrors.gpa }}</span
              ></label
            >
            <label class="field"
              >기준 학점<input v-model="form.gpaScale" class="control" inputmode="decimal" /><span
                v-if="fieldErrors.gpaScale"
                class="field-error"
                >{{ fieldErrors.gpaScale }}</span
              ></label
            >
            <label class="field form-span"
              >설명<textarea v-model="form.description" class="control min-h-24" maxlength="5000" />
            </label>
          </template>

          <template v-else-if="kind === 'certification'">
            <label class="field"
              >자격증명<input
                id="certification-name"
                v-model="form.name"
                class="control"
                maxlength="200"
              /><span v-if="fieldErrors.name" class="field-error">{{
                fieldErrors.name
              }}</span></label
            >
            <label class="field"
              >발급 기관<input v-model="form.issuer" class="control" maxlength="200"
            /></label>
            <label class="field"
              >자격 번호<input v-model="form.credentialNumber" class="control" maxlength="200"
            /></label>
            <label class="field"
              >취득일<input v-model="form.acquiredDate" class="control" type="date"
            /></label>
            <label class="field"
              >만료일<input v-model="form.expiresAt" class="control" type="date" /><span
                v-if="fieldErrors.expiresAt"
                class="field-error"
                >{{ fieldErrors.expiresAt }}</span
              ></label
            >
            <label class="field form-span"
              >설명<textarea v-model="form.description" class="control min-h-24" maxlength="5000" />
            </label>
          </template>

          <template v-else-if="kind === 'language'">
            <label class="field"
              >시험명<input
                id="language-testName"
                v-model="form.testName"
                class="control"
                maxlength="100"
              /><span v-if="fieldErrors.testName" class="field-error">{{
                fieldErrors.testName
              }}</span></label
            >
            <label class="field"
              >점수<input v-model="form.score" class="control" maxlength="100" /><span
                v-if="fieldErrors.score"
                class="field-error"
                >{{ fieldErrors.score }}</span
              ></label
            >
            <label class="field"
              >등급<input v-model="form.grade" class="control" maxlength="100"
            /></label>
            <label class="field"
              >응시일<input v-model="form.testedAt" class="control" type="date"
            /></label>
            <label class="field"
              >만료일<input v-model="form.expiresAt" class="control" type="date" /><span
                v-if="fieldErrors.expiresAt"
                class="field-error"
                >{{ fieldErrors.expiresAt }}</span
              ></label
            >
          </template>

          <template v-else-if="kind === 'award'">
            <label class="field"
              >수상명<input
                id="award-name"
                v-model="form.name"
                class="control"
                maxlength="200"
              /><span v-if="fieldErrors.name" class="field-error">{{
                fieldErrors.name
              }}</span></label
            >
            <label class="field"
              >주최 기관<input v-model="form.organizer" class="control" maxlength="200"
            /></label>
            <label class="field"
              >수상일<input v-model="form.awardedAt" class="control" type="date"
            /></label>
            <label class="field form-span"
              >설명<textarea v-model="form.description" class="control min-h-24" maxlength="5000" />
            </label>
          </template>

          <template v-else>
            <label class="field"
              >회사·기관<input
                id="career-organization"
                v-model="form.organization"
                class="control"
                maxlength="200"
              /><span v-if="fieldErrors.organization" class="field-error">{{
                fieldErrors.organization
              }}</span></label
            >
            <label class="field"
              >직무<input v-model="form.position" class="control" maxlength="200"
            /></label>
            <label class="field"
              >고용 형태<input v-model="form.employmentType" class="control" maxlength="50"
            /></label>
            <label class="field"
              >시작일<input v-model="form.startedAt" class="control" type="date"
            /></label>
            <label class="field"
              >종료일<input
                v-model="form.endedAt"
                class="control"
                type="date"
                :disabled="form.isCurrent"
              /><span v-if="fieldErrors.endedAt" class="field-error">{{
                fieldErrors.endedAt
              }}</span></label
            >
            <label class="check-row form-span"
              ><input
                v-model="form.isCurrent"
                class="checkbox-control"
                type="checkbox"
                @change="form.isCurrent && (form.endedAt = '')"
              />현재 재직 중</label
            >
            <label class="field form-span"
              >역할<textarea
                v-model="form.responsibilities"
                class="control min-h-28"
                maxlength="20000"
              />
            </label>
            <label class="field form-span"
              >성과<textarea
                v-model="form.achievements"
                class="control min-h-28"
                maxlength="20000"
              />
            </label>
          </template>

          <label v-if="documentLinkable" class="field form-span">
            증빙 문서
            <select
              :id="`${kind}-evidenceDocumentId`"
              v-model="form.evidenceDocumentId"
              class="control"
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
            <span v-if="selectableDocuments.isError.value" class="field-error">
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
                  >최종 학력</span
                >
              </div>
              <p v-if="resourceSubtitle(kind, item)" class="structured-item__meta">
                {{ resourceSubtitle(kind, item) }}
              </p>
            </div>
            <div class="structured-item__actions">
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
      <ProfileSectionActions v-if="!isFormOpen" />
    </div>
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
