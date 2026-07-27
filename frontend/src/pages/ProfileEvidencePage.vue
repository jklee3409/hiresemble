<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useDocumentListQuery } from '@/features/documents/queries'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import {
  metadataFieldsToRecord,
  metadataToFields,
  type EvidenceMetadataField,
} from '@/features/profile/evidenceMetadata'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import type {
  EvidenceDto,
  EvidenceMetadataValue,
  EvidenceUpdateRequest,
  EvidenceVerificationStatus,
} from '@/shared/api/contracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const userId = computed(() => authStore.currentUser?.id ?? '')
const status = ref<'' | EvidenceVerificationStatus>('')
const category = ref('')
const page = ref(0)
const size = ref(20)
const sort = ref('updatedAt,desc')
const documentId = ref(typeof route.query.documentId === 'string' ? route.query.documentId : '')
const documents = useDocumentListQuery(userId, { page: 0, size: 100, sort: 'updatedAt,desc' })
const filters = computed<profileApi.EvidenceListParams>(() => ({
  verificationStatus: status.value || undefined,
  evidenceCategory: category.value.trim() || undefined,
  documentId: documentId.value || undefined,
  page: page.value,
  size: size.value,
  sort: sort.value,
}))
const queryKey = computed(() => profileQueryKeys.evidence(userId.value, filters.value))

const evidenceQuery = useQuery({
  queryKey,
  queryFn: () => profileApi.listEvidence(filters.value),
  enabled: computed(() => userId.value !== ''),
})

interface MetadataEntry extends EvidenceMetadataField {
  id: number
}

let metadataEntryId = 0
const editForm = reactive({ title: '', content: '', version: 0 })
const metadataEntries = ref<MetadataEntry[]>([])
const editingId = ref<string | null>(null)
const fieldErrors = ref<Record<string, string>>({})
const generalError = ref('')
const message = ref('')
const conflict = ref<{ draft: Record<string, unknown>; latest: EvidenceDto } | null>(null)

const editMutation = useMutation({
  mutationFn: (input: { id: string; request: EvidenceUpdateRequest }) =>
    profileApi.updateEvidence(input.id, input.request),
})
const verificationMutation = useMutation({
  mutationFn: (input: {
    evidence: EvidenceDto
    nextStatus: Extract<EvidenceVerificationStatus, 'VERIFIED' | 'REJECTED'>
  }) =>
    profileApi.verifyEvidence(input.evidence.id, {
      status: input.nextStatus,
      version: input.evidence.version,
    }),
})

function applyFilters(): void {
  page.value = 0
  void router.replace({
    query: {
      ...(documentId.value ? { documentId: documentId.value } : {}),
    },
  })
}

function openEdit(evidence: EvidenceDto): void {
  if (evidence.verificationStatus === 'SOURCE_DELETED') return
  editingId.value = evidence.id
  Object.assign(editForm, {
    title: evidence.title,
    content: evidence.content,
    version: evidence.version,
  })
  metadataEntries.value = metadataToEntries(evidence.metadata)
  fieldErrors.value = {}
  generalError.value = ''
  conflict.value = null
}

function closeEdit(): void {
  editingId.value = null
  conflict.value = null
  fieldErrors.value = {}
  generalError.value = ''
}

async function saveEdit(): Promise<void> {
  if (editingId.value === null) return
  message.value = ''
  generalError.value = ''
  fieldErrors.value = {}
  const title = editForm.title.trim()
  const content = editForm.content.trim()
  if (title.length < 1 || title.length > 250)
    fieldErrors.value.title = '제목을 입력하고 250자 안으로 작성해 주세요.'
  if (content.length < 1 || content.length > 20000)
    fieldErrors.value.content = '내용을 입력하고 20,000자 안으로 작성해 주세요.'
  const metadata = buildMetadata()
  if (Object.keys(fieldErrors.value).length > 0 || metadata === null) return

  const request: EvidenceUpdateRequest = { title, content, metadata, version: editForm.version }
  try {
    await editMutation.mutateAsync({ id: editingId.value, request })
    await evidenceQuery.refetch()
    editingId.value = null
    message.value = '경험 정보를 저장했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (apiError.code === 'EVIDENCE_SOURCE_DELETED') {
      await evidenceQuery.refetch()
      editingId.value = null
      generalError.value = '원본이 삭제된 경험 정보는 읽기만 할 수 있어요.'
      return
    }
    if (isVersionConflict(apiError)) {
      const refreshed = await evidenceQuery.refetch()
      const latest = refreshed.data?.items.find((item) => item.id === editingId.value)
      if (latest !== undefined) {
        conflict.value = { draft: { ...request }, latest }
        generalError.value = '최근 저장된 내용과 내 입력을 비교해 다시 적용해 주세요.'
        return
      }
    }
    generalError.value = apiError.message
  }
}

async function verify(
  evidence: EvidenceDto,
  nextStatus: Extract<EvidenceVerificationStatus, 'VERIFIED' | 'REJECTED'>,
): Promise<void> {
  if (evidence.verificationStatus === 'SOURCE_DELETED') return
  generalError.value = ''
  message.value = ''
  try {
    await verificationMutation.mutateAsync({ evidence, nextStatus })
    await evidenceQuery.refetch()
    message.value =
      nextStatus === 'VERIFIED' ? '경험 정보를 승인했어요.' : '경험 정보를 거절했어요.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    await evidenceQuery.refetch()
    generalError.value = isVersionConflict(apiError)
      ? '검토 상태가 바뀌어 최신 목록을 불러왔어요. 확인한 뒤 다시 시도해 주세요.'
      : apiError.message
  }
}

function cancelConflict(): void {
  const latest = conflict.value?.latest
  conflict.value = null
  if (latest !== undefined) openEdit(latest)
}

function reapplyConflict(value: Record<string, unknown>): void {
  const latest = conflict.value?.latest
  if (latest === undefined) return
  editForm.title = typeof value.title === 'string' ? value.title : latest.title
  editForm.content = typeof value.content === 'string' ? value.content : latest.content
  const metadata =
    typeof value.metadata === 'object' && value.metadata !== null && !Array.isArray(value.metadata)
      ? (value.metadata as Record<string, EvidenceMetadataValue>)
      : latest.metadata
  metadataEntries.value = metadataToEntries(metadata)
  editForm.version = latest.version
  conflict.value = null
  message.value = '선택한 내 입력을 최신 내용에 다시 적용했어요. 확인한 뒤 저장해 주세요.'
}

function metadataToEntries(metadata: Record<string, EvidenceMetadataValue>): MetadataEntry[] {
  return metadataToFields(metadata).map((field) => ({
    id: metadataEntryId++,
    ...field,
  }))
}

function addMetadataEntry(): void {
  metadataEntries.value.push({ id: metadataEntryId++, key: '', type: 'text', value: '' })
}

function removeMetadataEntry(id: number): void {
  metadataEntries.value = metadataEntries.value.filter((entry) => entry.id !== id)
}

function buildMetadata(): Record<string, EvidenceMetadataValue> | null {
  const result = metadataFieldsToRecord(metadataEntries.value)
  if (result.error) fieldErrors.value.metadata = result.error
  else delete fieldErrors.value.metadata
  return result.data
}

function statusLabel(value: EvidenceVerificationStatus): string {
  return {
    PENDING: '검토 대기',
    VERIFIED: '승인됨',
    REJECTED: '거절됨',
    SOURCE_DELETED: '원본 삭제됨',
  }[value]
}

function sourceLabel(value: EvidenceDto['sourceType']): string {
  return (
    {
      EDUCATION: '학력',
      CERTIFICATION: '자격증',
      LANGUAGE_SCORE: '어학',
      AWARD: '수상',
      CAREER: '경력',
      DOCUMENT_CHUNK: '문서',
      MANUAL: '수동',
    } as const
  )[value]
}

function statusTone(
  value: EvidenceVerificationStatus,
): 'neutral' | 'success' | 'danger' | 'warning' {
  return (
    {
      PENDING: 'neutral',
      VERIFIED: 'success',
      REJECTED: 'danger',
      SOURCE_DELETED: 'warning',
    } as const
  )[value]
}

function confidenceLabel(value: number | null): string {
  return value === null ? '신뢰도 미산정' : `신뢰도 ${Math.round(value * 100)}%`
}
</script>

<template>
  <section class="evidence-page app-page" aria-labelledby="evidence-heading">
    <ProfileTabs />
    <PageHeader
      heading-id="evidence-heading"
      title="경험 정보"
      description="내가 입력했거나 자료에서 찾은 경험을 확인하고 다듬어 보세요."
      eyebrow="나의 경험"
    />
    <p class="alert alert--info evidence-page__guidance">
      직접 입력한 내용과 자료에서 찾은 경험을 함께 확인할 수 있어요. 원본이 삭제된 항목은 이전
      기록을 위해 읽기 전용으로 남겨요.
    </p>

    <form class="filter-toolbar evidence-filters" @submit.prevent="applyFilters">
      <label class="field">
        <span class="field__label">상태</span>
        <select v-model="status" class="control control--compact">
          <option value="">전체</option>
          <option value="PENDING">검토 대기</option>
          <option value="VERIFIED">승인됨</option>
          <option value="REJECTED">거절됨</option>
          <option value="SOURCE_DELETED">원본 삭제됨</option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">카테고리</span>
        <input v-model="category" class="control control--compact" maxlength="80" />
      </label>
      <label class="field">
        <span class="field__label">정렬</span>
        <select v-model="sort" class="control control--compact" @change="applyFilters">
          <option value="updatedAt,desc">최근 수정순</option>
          <option value="confidence,desc">신뢰도순</option>
        </select>
      </label>
      <label class="field evidence-filters__document">
        <span class="field__label">출처 문서</span>
        <select
          id="evidence-document-filter"
          v-model="documentId"
          class="control control--compact"
          :disabled="documents.isPending.value || documents.isError.value"
        >
          <option value="">전체</option>
          <option
            v-for="candidate in documents.data.value?.items"
            :key="candidate.id"
            :value="candidate.id"
          >
            {{ candidate.displayName }}
          </option>
        </select>
      </label>
      <button type="submit" class="button button--primary button--compact">필터 적용</button>
    </form>
    <p v-if="documents.isError.value" class="inline-error" role="alert">
      출처 자료를 불러오지 못했어요.
    </p>

    <p v-if="message" class="alert alert--success evidence-page__message" role="status">
      {{ message }}
    </p>
    <p v-if="generalError" class="alert alert--danger evidence-page__message" role="alert">
      {{ generalError }}
    </p>

    <section
      v-if="editingId"
      class="evidence-editor section-surface"
      role="region"
      aria-label="경험 정보 편집"
    >
      <div class="evidence-editor__header">
        <div>
          <p class="section-kicker">내용 다듬기</p>
          <h3 class="section-title">경험 정보 편집</h3>
        </div>
        <button type="button" class="button button--ghost button--compact" @click="closeEdit">
          닫기
        </button>
      </div>
      <VersionConflictPanel
        v-if="conflict"
        class="mt-4"
        :draft="conflict.draft"
        :latest="conflict.latest"
        :fields="[
          { key: 'title', label: '제목' },
          { key: 'content', label: '내용' },
          { key: 'metadata', label: '추가 정보' },
        ]"
        @cancel="cancelConflict"
        @reapply="reapplyConflict"
      />
      <form class="evidence-editor__form" novalidate @submit.prevent="saveEdit">
        <label class="field">
          <span class="field__label">제목</span>
          <input
            id="evidence-title"
            v-model="editForm.title"
            class="control"
            maxlength="250"
            :aria-invalid="Boolean(fieldErrors.title)"
            :aria-describedby="fieldErrors.title ? 'evidence-title-error' : undefined"
          />
          <span v-if="fieldErrors.title" id="evidence-title-error" class="inline-error">{{
            fieldErrors.title
          }}</span>
        </label>
        <label class="field">
          <span class="field__label">내용</span>
          <textarea
            id="evidence-content"
            v-model="editForm.content"
            class="control evidence-editor__content"
            maxlength="20000"
            :aria-invalid="Boolean(fieldErrors.content)"
            :aria-describedby="fieldErrors.content ? 'evidence-content-error' : undefined"
          />
          <span v-if="fieldErrors.content" id="evidence-content-error" class="inline-error">{{
            fieldErrors.content
          }}</span>
        </label>
        <fieldset
          class="field evidence-editor__metadata"
          :aria-invalid="Boolean(fieldErrors.metadata)"
          :aria-describedby="fieldErrors.metadata ? 'evidence-metadata-error' : 'metadata-help'"
        >
          <legend class="field__label">추가 정보</legend>
          <p id="metadata-help" class="field__help">
            역할이나 성과처럼 따로 남겨 둘 내용을 항목별로 추가할 수 있어요.
          </p>
          <div v-for="(entry, index) in metadataEntries" :key="entry.id" class="metadata-entry">
            <label class="field">
              <span class="sr-only">추가 정보 {{ index + 1 }} 이름</span>
              <input v-model="entry.key" class="control" placeholder="예: 담당 역할" />
            </label>
            <label class="field">
              <span class="sr-only">추가 정보 {{ index + 1 }} 형식</span>
              <select v-model="entry.type" class="control">
                <option value="text">글자</option>
                <option value="number">숫자</option>
                <option value="boolean">예·아니요</option>
                <option value="empty">내용 없음</option>
              </select>
            </label>
            <label v-if="entry.type !== 'empty'" class="field">
              <span class="sr-only">추가 정보 {{ index + 1 }} 내용</span>
              <select v-if="entry.type === 'boolean'" v-model="entry.value" class="control">
                <option value="true">예</option>
                <option value="false">아니요</option>
              </select>
              <input
                v-else
                v-model="entry.value"
                class="control"
                :inputmode="entry.type === 'number' ? 'decimal' : 'text'"
                placeholder="내용 입력"
              />
            </label>
            <span v-else class="metadata-entry__empty">내용 없음</span>
            <button
              type="button"
              class="button button--ghost button--compact"
              :aria-label="`추가 정보 ${index + 1} 삭제`"
              @click="removeMetadataEntry(entry.id)"
            >
              삭제
            </button>
          </div>
          <button
            type="button"
            class="button button--secondary button--compact metadata-entry__add"
            @click="addMetadataEntry"
          >
            추가 정보 넣기
          </button>
          <span v-if="fieldErrors.metadata" id="evidence-metadata-error" class="inline-error">{{
            fieldErrors.metadata
          }}</span>
        </fieldset>
        <div class="form-actions">
          <button
            type="submit"
            class="button button--primary"
            :disabled="editMutation.isPending.value"
          >
            {{ editMutation.isPending.value ? '저장 중…' : '경험 정보 저장' }}
          </button>
          <button type="button" class="button button--secondary" @click="closeEdit">취소</button>
        </div>
      </form>
    </section>

    <StatePanel
      v-if="evidenceQuery.isPending.value"
      class="evidence-page__state"
      kind="loading"
      title="경험 정보를 불러오는 중…"
      description="저장된 출처와 검토 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="evidenceQuery.isError.value"
      class="evidence-page__state"
      kind="error"
      title="경험 정보를 불러오지 못했어요."
      description="잠시 후 다시 시도해 주세요."
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="evidenceQuery.refetch()">
          다시 시도
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="evidenceQuery.data.value?.items.length === 0"
      class="evidence-page__state"
      kind="empty"
      title="조건에 맞는 경험 정보가 없어요."
      description="필터를 바꾸거나 프로필과 자료에 경험을 추가해 주세요."
    />
    <ul v-else class="evidence-list data-list">
      <li
        v-for="evidence in evidenceQuery.data.value?.items"
        :key="evidence.id"
        class="evidence-card data-card"
        :data-testid="`evidence-card-${evidence.id}`"
      >
        <div class="evidence-card__header">
          <div class="evidence-card__identity">
            <div class="evidence-card__title">
              <h3>{{ evidence.title }}</h3>
              <StatusBadge
                :label="statusLabel(evidence.verificationStatus)"
                :tone="statusTone(evidence.verificationStatus)"
              />
            </div>
            <dl class="evidence-card__meta">
              <div>
                <dt>출처</dt>
                <dd>{{ sourceLabel(evidence.sourceType) }}</dd>
              </div>
              <div>
                <dt>카테고리</dt>
                <dd>{{ evidence.evidenceCategory }}</dd>
              </div>
              <div>
                <dt>신뢰도</dt>
                <dd>{{ confidenceLabel(evidence.confidence) }}</dd>
              </div>
            </dl>
          </div>
          <div
            v-if="evidence.verificationStatus !== 'SOURCE_DELETED'"
            class="evidence-card__actions"
          >
            <button
              type="button"
              class="button button--ghost button--compact"
              @click="openEdit(evidence)"
            >
              수정
            </button>
            <button
              type="button"
              class="button button--secondary button--compact"
              :disabled="verificationMutation.isPending.value"
              @click="verify(evidence, 'VERIFIED')"
            >
              승인
            </button>
            <button
              type="button"
              class="button button--danger button--compact"
              :disabled="verificationMutation.isPending.value"
              @click="verify(evidence, 'REJECTED')"
            >
              거절
            </button>
          </div>
        </div>
        <p class="evidence-card__content">{{ evidence.content }}</p>
        <p
          v-if="evidence.verificationStatus === 'SOURCE_DELETED'"
          class="alert alert--warning evidence-card__readonly"
        >
          원본이 삭제되어 읽기 전용이에요. 수정·승인·거절할 수 없어요.
        </p>
      </li>
    </ul>

    <PaginationNav
      v-if="evidenceQuery.data.value && evidenceQuery.data.value.totalPages > 0"
      :page="page"
      :total-pages="evidenceQuery.data.value.totalPages"
      label="경험 정보 페이지"
      @change="page = $event"
    />
  </section>
</template>

<style scoped>
.evidence-page__guidance,
.evidence-filters,
.evidence-page__message,
.evidence-editor,
.evidence-page__state,
.evidence-list {
  margin-top: var(--space-5);
}

.evidence-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(8rem, 0.7fr)) minmax(12rem, 1.4fr) auto;
  align-items: end;
}

.evidence-filters__document {
  min-width: 0;
}

.evidence-editor {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.evidence-editor__header,
.evidence-card__header,
.evidence-card__title,
.evidence-card__actions,
.evidence-card__meta {
  display: flex;
  align-items: center;
}

.evidence-editor__header,
.evidence-card__header {
  justify-content: space-between;
  gap: var(--space-4);
}

.evidence-editor__form {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.evidence-editor__content {
  min-height: 10rem;
}

.evidence-editor__metadata {
  min-width: 0;
  border: 0;
  padding: 0;
}

.metadata-entry {
  display: grid;
  grid-template-columns: minmax(8rem, 1fr) minmax(7rem, 0.55fr) minmax(9rem, 1.4fr) auto;
  gap: var(--space-2);
  margin-top: var(--space-3);
  align-items: end;
}

.metadata-entry__empty {
  align-self: center;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.metadata-entry__add {
  margin-top: var(--space-3);
}

@media (max-width: 767px) {
  .metadata-entry {
    grid-template-columns: minmax(0, 1fr);
  }
}

.evidence-card {
  padding: var(--space-5);
}

.evidence-card__identity {
  min-width: 0;
}

.evidence-card__title,
.evidence-card__actions,
.evidence-card__meta {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.evidence-card__title h3 {
  overflow-wrap: anywhere;
  font-weight: 700;
}

.evidence-card__meta {
  gap: var(--space-2) var(--space-5);
  margin-top: var(--space-3);
}

.evidence-card__meta div {
  display: flex;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
}

.evidence-card__meta dt {
  color: var(--color-text-muted);
}

.evidence-card__meta dd {
  color: var(--color-text-secondary);
  font-weight: 650;
}

.evidence-card__content {
  margin-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.evidence-card__readonly {
  margin-top: var(--space-4);
}

@media (max-width: 64rem) {
  .evidence-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .evidence-filters {
    grid-template-columns: 1fr;
  }

  .evidence-card__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
