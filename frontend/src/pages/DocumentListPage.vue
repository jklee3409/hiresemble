<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  canonicalDocumentQuery,
  documentQuerySignature,
  parseDocumentFilters,
} from '@/features/documents/filters'
import {
  DOCUMENT_PARSE_STATUS_LABELS,
  DOCUMENT_TYPE_LABELS,
  EVIDENCE_EXTRACTION_STATUS_LABELS,
  formatFileSize,
} from '@/features/documents/presentation'
import { documentQueryKeys, useDocumentListQuery } from '@/features/documents/queries'
import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import { validateUpload } from '@/features/documents/validation'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import {
  DOCUMENT_PARSE_STATUSES,
  DOCUMENT_TYPES,
  EVIDENCE_EXTRACTION_STATUSES,
  type DocumentParseStatus,
  type DocumentType,
  type EvidenceExtractionStatus,
} from '@/shared/api/documentContracts'
import {
  createDocumentDownloadUrl,
  createDocumentIdempotencyKey,
  deleteDocument,
  reparseDocument,
  uploadDocument,
} from '@/shared/api/documentApi'
import { normalizeApiError } from '@/shared/api/errors'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseDocumentFilters(route.query))
const documents = useDocumentListQuery(userId, filters)

const filterDocumentType = ref('')
const filterParseStatus = ref('')
const filterEvidenceStatus = ref('')
const selectedFile = ref<File | null>(null)
const documentType = ref<DocumentType>('RESUME')
const displayName = ref('')
const isDragging = ref(false)
const fileInput = useTemplateRef<HTMLInputElement>('fileInput')
const uploadErrors = ref<Record<string, string>>({})
const actionError = ref('')
const message = ref('')
let uploadIdempotencyKey = ''
const reparseKeys = new Map<string, string>()
const DOCUMENT_TYPE_HINTS: Record<DocumentType, string> = {
  RESUME: '학력, 경력, 프로젝트처럼 지원 준비에 필요한 경험을 정리해요.',
  PORTFOLIO: '프로젝트와 결과물에서 역할과 성과를 찾아 정리해요.',
  CAREER_DESCRIPTION: '회사별 담당 업무와 성과를 자소서 소재 후보로 정리해요.',
  CERTIFICATE: '자격 취득 내용을 프로필에 연결할 때 활용해요.',
  TRANSCRIPT: '학업 이력과 성적 정보를 확인할 때 활용해요.',
  OTHER: '지원 준비에 참고할 수 있는 기타 자료로 등록해요.',
}

const uploadMutation = useMutation({
  mutationFn: (input: {
    file: File
    documentType: DocumentType
    displayName: string | null
    idempotencyKey: string
  }) => uploadDocument(input.file, input.documentType, input.displayName, input.idempotencyKey),
})
const reparseMutation = useMutation({
  mutationFn: (input: { id: string; version: number; idempotencyKey: string }) =>
    reparseDocument(input.id, { version: input.version }, input.idempotencyKey),
})
const downloadMutation = useMutation({ mutationFn: createDocumentDownloadUrl })
const deleteMutation = useMutation({
  mutationFn: (input: { id: string; version: number }) => deleteDocument(input.id, input.version),
})

watch(
  filters,
  (value) => {
    filterDocumentType.value = value.documentType ?? ''
    filterParseStatus.value = value.parseStatus ?? ''
    filterEvidenceStatus.value = value.evidenceExtractionStatus ?? ''
  },
  { immediate: true },
)

watch([selectedFile, documentType, displayName], () => {
  if (!uploadMutation.isPending.value) uploadIdempotencyKey = ''
})

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalDocumentQuery(parseDocumentFilters(query))
    if (documentQuerySignature(query) !== documentQuerySignature(canonical)) {
      void router.replace({ query: canonical })
    }
  },
  { immediate: true },
)

function selectFile(event: Event): void {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
  uploadErrors.value = {}
}

function dropFile(event: DragEvent): void {
  selectedFile.value = event.dataTransfer?.files[0] ?? null
  uploadErrors.value = {}
  isDragging.value = false
}

function clearSelectedFile(): void {
  selectedFile.value = null
  uploadErrors.value = {}
  if (fileInput.value) fileInput.value.value = ''
}

async function upload(): Promise<void> {
  actionError.value = ''
  message.value = ''
  uploadErrors.value = validateUpload({
    file: selectedFile.value,
    documentType: documentType.value,
    displayName: displayName.value,
  })
  if (selectedFile.value === null || Object.keys(uploadErrors.value).length > 0) {
    await nextTick()
    focusFirstInvalidControl()
    return
  }

  try {
    const accepted = await uploadMutation.mutateAsync({
      file: selectedFile.value,
      documentType: documentType.value,
      displayName: displayName.value.trim() || null,
      idempotencyKey:
        uploadIdempotencyKey || (uploadIdempotencyKey = createDocumentIdempotencyKey('upload')),
    })
    notifications.toast('자료를 등록하고 분석을 시작했어요.', 'success')
    await router.push({
      name: 'document-detail',
      params: { documentId: accepted.documentId },
      query: { run: accepted.agentRunId },
    })
    uploadIdempotencyKey = ''
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

function applyFilters(): void {
  void router.push({
    query: canonicalDocumentQuery({
      documentType: DOCUMENT_TYPES.find((value) => value === filterDocumentType.value),
      parseStatus: DOCUMENT_PARSE_STATUSES.find((value) => value === filterParseStatus.value),
      evidenceExtractionStatus: EVIDENCE_EXTRACTION_STATUSES.find(
        (value) => value === filterEvidenceStatus.value,
      ),
      page: 0,
      size: filters.value.size,
      sort: filters.value.sort,
    }),
  })
}

function updatePage(page: number): void {
  void router.push({ query: canonicalDocumentQuery({ ...filters.value, page }) })
}

function updateSort(event: Event): void {
  const sort = (event.target as HTMLSelectElement).value as 'uploadedAt,desc' | 'updatedAt,desc'
  void router.push({ query: canonicalDocumentQuery({ ...filters.value, page: 0, sort }) })
}

async function reparse(id: string, version: number): Promise<void> {
  const confirmed = await notifications.confirm({
    title: '자료를 다시 분석할까요?',
    message:
      'AI 사용량이 새로 집계될 수 있어요. 기존 원본과 검토 결과는 새 분석이 끝날 때까지 유지됩니다.',
    confirmLabel: '다시 분석',
  })
  if (!confirmed) return
  actionError.value = ''
  try {
    const accepted = await reparseMutation.mutateAsync({
      id,
      version,
      idempotencyKey:
        reparseKeys.get(id) ??
        (() => {
          const key = createDocumentIdempotencyKey('reparse')
          reparseKeys.set(id, key)
          return key
        })(),
    })
    notifications.toast('자료를 다시 분석하기 시작했어요.', 'success')
    await router.push({
      name: 'document-detail',
      params: { documentId: id },
      query: { run: accepted.agentRunId },
    })
    reparseKeys.delete(id)
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function download(id: string): Promise<void> {
  actionError.value = ''
  try {
    const value = await downloadMutation.mutateAsync(id)
    const anchor = window.document.createElement('a')
    anchor.href = value.url
    anchor.rel = 'noopener noreferrer'
    anchor.target = '_blank'
    anchor.click()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function remove(id: string, version: number, name: string): Promise<void> {
  const confirmed = await notifications.confirm({
    title: '등록 자료를 삭제할까요?',
    message: `${name} 원본과 아직 참조되지 않은 분석 소재가 삭제됩니다. 다른 기능에서 이미 사용한 기록은 안전한 이력으로 남아요.`,
    confirmLabel: '자료 삭제',
  })
  if (!confirmed) return
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync({ id, version })
    closeAgentRunStreamsForResource(userId.value, 'DOCUMENT', id)
    cache.removeQueries({ queryKey: documentQueryKeys.detail(userId.value, id) })
    cache.removeQueries({ queryKey: documentQueryKeys.text(userId.value, id) })
    cache.removeQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
    await cache.invalidateQueries({ queryKey: documentQueryKeys.root(userId.value) })
    message.value = '자료를 삭제했어요.'
    notifications.toast('자료를 삭제했어요.', 'success')
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

function parseTone(
  value: DocumentParseStatus,
): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return (
    {
      UPLOADED: 'neutral',
      PARSING: 'info',
      PARSED: 'success',
      NEEDS_MANUAL_TEXT: 'warning',
      FAILED: 'danger',
    } as const
  )[value]
}

function evidenceTone(value: EvidenceExtractionStatus): 'neutral' | 'info' | 'success' | 'danger' {
  return (
    {
      NOT_STARTED: 'neutral',
      QUEUED: 'neutral',
      EXTRACTING: 'info',
      SUCCEEDED: 'success',
      FAILED: 'danger',
    } as const
  )[value]
}
</script>

<template>
  <section class="documents-page app-page" aria-labelledby="documents-heading">
    <PageHeader
      heading-id="documents-heading"
      title="이력서·자료"
      description="이력서와 포트폴리오를 등록하면 AI가 경험을 정리하고, 활용할 소재를 직접 선택할 수 있어요."
      eyebrow="나의 자료"
    />

    <form class="upload-panel section-surface" novalidate @submit.prevent="upload">
      <header class="upload-panel__heading">
        <div>
          <p class="section-kicker">자료 등록</p>
          <h2 class="section-title">경력을 설명해 줄 자료를 등록하세요.</h2>
          <p>
            원본 파일은 그대로 보관하고, 읽어 낸 내용과 정리된 경력 정보는 등록 후 직접 확인할 수
            있어요.
          </p>
        </div>
        <ol class="upload-panel__steps" aria-label="자료 등록 순서">
          <li class="upload-panel__step upload-panel__step--current">
            <span>01</span>
            <strong>파일 선택</strong>
          </li>
          <li>
            <span>02</span>
            <strong>자료 분류</strong>
          </li>
          <li>
            <span>03</span>
            <strong>내용 분석</strong>
          </li>
        </ol>
      </header>
      <div class="upload-panel__file">
        <p class="upload-panel__label"><span>01</span> 파일 선택</p>
        <label
          for="document-file"
          class="dropzone"
          :class="{ 'dropzone--active': isDragging }"
          @dragenter.prevent="isDragging = true"
          @dragover.prevent
          @dragleave.prevent="isDragging = false"
          @drop.prevent="dropFile"
        >
          <span class="dropzone__icon" aria-hidden="true"><AppIcon name="upload" /></span>
          <strong>{{
            isDragging ? '여기에 놓아 주세요' : '파일을 끌어오거나 눌러 선택하세요'
          }}</strong>
          <span>PDF · DOCX · TXT, 파일당 최대 20MB</span>
          <input
            id="document-file"
            ref="fileInput"
            class="dropzone__input"
            type="file"
            accept=".pdf,.docx,.txt"
            :aria-describedby="
              uploadErrors.file ? 'document-file-help document-file-error' : 'document-file-help'
            "
            :aria-invalid="Boolean(uploadErrors.file)"
            @change="selectFile"
          />
          <span id="document-file-help" class="dropzone__privacy">
            등록한 파일은 본인의 취업 준비에만 사용해요.
          </span>
        </label>
        <div v-if="selectedFile" class="selected-file" role="status">
          <span class="selected-file__icon" aria-hidden="true"><AppIcon name="documents" /></span>
          <div>
            <span>선택한 파일</span>
            <strong>{{ selectedFile.name }}</strong>
            <small>{{ formatFileSize(selectedFile.size) }}</small>
          </div>
          <button
            type="button"
            class="button button--ghost button--compact"
            @click="clearSelectedFile"
          >
            선택 해제
          </button>
        </div>
        <p v-if="uploadErrors.file" id="document-file-error" class="inline-error" role="alert">
          {{ uploadErrors.file }}
        </p>
      </div>
      <div class="upload-panel__details">
        <p class="upload-panel__label"><span>02</span> 자료 분류</p>
        <label class="field">
          <span class="field__label">자료 유형</span>
          <select id="document-upload-type" v-model="documentType" class="control">
            <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
              {{ DOCUMENT_TYPE_LABELS[type] }}
            </option>
          </select>
          <span class="field-help">{{ DOCUMENT_TYPE_HINTS[documentType] }}</span>
        </label>
        <label class="field">
          <span class="field__label">자료 이름 <span class="field__optional">(선택)</span></span>
          <input
            id="document-displayName"
            v-model="displayName"
            class="control"
            maxlength="255"
            placeholder="예: 2026 상반기 이력서"
            :aria-invalid="Boolean(uploadErrors.displayName)"
            :aria-describedby="uploadErrors.displayName ? 'document-displayName-error' : undefined"
          />
          <span
            v-if="uploadErrors.displayName"
            id="document-displayName-error"
            class="inline-error"
            >{{ uploadErrors.displayName }}</span
          >
        </label>
        <div class="upload-panel__action">
          <p>
            <AppIcon name="runs" />
            등록하면 자료를 읽고 자소서에 활용할 경험 후보를 정리해요.
          </p>
          <button
            id="document-upload-submit"
            type="submit"
            class="button button--primary upload-panel__submit"
            :disabled="uploadMutation.isPending.value"
          >
            <span v-if="uploadMutation.isPending.value" class="button-spinner" aria-hidden="true" />
            {{ uploadMutation.isPending.value ? '등록을 시작하는 중…' : '등록하고 분석 시작' }}
          </button>
        </div>
      </div>
    </form>

    <details class="filter-disclosure documents-page__filters" open>
      <summary>자료 검색·필터</summary>
      <form class="filter-toolbar document-filters" @submit.prevent="applyFilters">
        <label class="field">
          <span class="field__label">자료 유형</span>
          <select v-model="filterDocumentType" class="control control--compact">
            <option value="">전체</option>
            <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
              {{ DOCUMENT_TYPE_LABELS[type] }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">자료 확인</span>
          <select v-model="filterParseStatus" class="control control--compact">
            <option value="">전체</option>
            <option v-for="value in DOCUMENT_PARSE_STATUSES" :key="value" :value="value">
              {{ DOCUMENT_PARSE_STATUS_LABELS[value] }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">경험·소재 정리</span>
          <select v-model="filterEvidenceStatus" class="control control--compact">
            <option value="">전체</option>
            <option v-for="value in EVIDENCE_EXTRACTION_STATUSES" :key="value" :value="value">
              {{ EVIDENCE_EXTRACTION_STATUS_LABELS[value] }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">정렬</span>
          <select :value="filters.sort" class="control control--compact" @change="updateSort">
            <option value="uploadedAt,desc">최근 업로드순</option>
            <option value="updatedAt,desc">최근 수정순</option>
          </select>
        </label>
        <button class="button button--primary button--compact" type="submit">필터 적용</button>
      </form>
    </details>

    <p v-if="message" class="alert alert--success documents-page__message" role="status">
      {{ message }}
    </p>
    <p v-if="actionError" class="alert alert--danger documents-page__message" role="alert">
      {{ actionError }}
    </p>
    <StatePanel
      v-if="documents.isPending.value"
      class="documents-page__state"
      kind="loading"
      title="등록한 자료를 불러오는 중…"
      description="자료를 읽고 정리하는 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="documents.isError.value"
      class="documents-page__state"
      kind="error"
      title="자료를 불러오지 못했어요."
      description="잠시 후 다시 시도해 주세요."
    >
      <template #actions>
        <button class="button button--secondary" type="button" @click="documents.refetch()">
          다시 시도
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="documents.data.value?.items.length === 0"
      class="documents-page__state"
      kind="empty"
      title="조건에 맞는 자료가 없어요."
      description="필터를 바꾸거나 위에서 첫 자료를 등록해 주세요."
    />
    <ul v-else class="document-list data-list">
      <li
        v-for="document in documents.data.value?.items"
        :key="document.id"
        class="document-row data-card"
      >
        <div class="document-row__identity">
          <RouterLink
            class="document-row__title"
            :to="{ name: 'document-detail', params: { documentId: document.id } }"
            >{{ document.displayName }}</RouterLink
          >
          <p class="document-row__file">
            {{ document.originalFilename }} · {{ DOCUMENT_TYPE_LABELS[document.documentType] }} ·
            {{ formatFileSize(document.fileSizeBytes) }}
          </p>
          <p class="document-row__time">
            업로드 {{ new Date(document.uploadedAt).toLocaleString('ko-KR') }}
          </p>
        </div>
        <div class="document-row__statuses" aria-label="자료 상태">
          <StatusBadge
            prefix="자료"
            :label="DOCUMENT_PARSE_STATUS_LABELS[document.parseStatus]"
            :tone="parseTone(document.parseStatus)"
          />
          <StatusBadge
            prefix="소재 정리"
            :label="EVIDENCE_EXTRACTION_STATUS_LABELS[document.evidenceExtractionStatus]"
            :tone="evidenceTone(document.evidenceExtractionStatus)"
          />
        </div>
        <div class="document-row__actions">
          <button
            type="button"
            class="button button--secondary button--compact"
            @click="reparse(document.id, document.version)"
          >
            다시 분석
          </button>
          <button
            type="button"
            class="button button--ghost button--compact"
            @click="download(document.id)"
          >
            원본 열기
          </button>
          <button
            type="button"
            class="button button--danger button--compact"
            @click="remove(document.id, document.version, document.displayName)"
          >
            삭제
          </button>
        </div>
      </li>
    </ul>

    <PaginationNav
      v-if="documents.data.value && documents.data.value.totalPages > 0"
      :page="filters.page"
      :total-pages="documents.data.value.totalPages"
      label="자료 페이지"
      @change="updatePage"
    />
  </section>
</template>

<style scoped>
.upload-panel,
.documents-page__filters,
.documents-page__message,
.documents-page__state,
.document-list {
  margin-top: var(--space-6);
}

.upload-panel {
  display: grid;
  grid-template-columns: minmax(18rem, 1.1fr) minmax(16rem, 0.9fr);
  gap: var(--space-5) var(--space-7);
  overflow: hidden;
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.upload-panel__heading {
  display: flex;
  grid-column: 1 / -1;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-6);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--space-6);
}

.upload-panel__heading h2 {
  max-width: 34rem;
  font-size: clamp(1.35rem, 2.4vw, 2rem);
  line-height: 1.2;
  letter-spacing: -0.04em;
}

.upload-panel__heading p:last-child {
  max-width: 31rem;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.upload-panel__steps {
  display: flex;
  flex: 0 0 auto;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.upload-panel__steps li {
  min-width: 5.5rem;
  border-top: 1px solid var(--color-border-strong);
  color: var(--color-muted);
  padding: 0.5rem 1rem 0 0;
}

.upload-panel__steps span,
.upload-panel__steps strong {
  display: block;
}

.upload-panel__steps span {
  font-size: 0.625rem;
  font-variant-numeric: tabular-nums;
}

.upload-panel__steps strong {
  margin-top: 0.15rem;
  font-size: 0.6875rem;
}

.upload-panel__steps .upload-panel__step--current {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.upload-panel__file,
.upload-panel__details {
  min-width: 0;
}

.upload-panel__details {
  display: grid;
  align-content: start;
  gap: var(--space-4);
  border-left: 1px solid var(--color-border);
  padding-left: var(--space-7);
}

.upload-panel__label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 var(--space-3);
  color: var(--color-ink);
  font-size: 0.8125rem;
  font-weight: 750;
}

.upload-panel__label span {
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
}

.dropzone {
  position: relative;
  display: flex;
  min-height: 15rem;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  text-align: center;
  transition:
    border-color var(--motion-fast),
    background var(--motion-fast);
}

.dropzone:hover,
.dropzone:focus-within,
.dropzone--active {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
}

.dropzone__icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand);
  color: white;
  font-size: 1.35rem;
}

.dropzone strong {
  color: var(--color-text);
}

.dropzone span:last-of-type {
  font-size: var(--font-size-sm);
}

.dropzone__input {
  position: absolute;
  inset: 0;
  width: 100%;
  cursor: pointer;
  opacity: 0;
}

.dropzone__privacy {
  margin-top: var(--space-2);
  color: var(--color-muted);
  font-size: var(--font-size-xs) !important;
}

.selected-file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
  border: 1px solid var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-text-secondary);
  padding: var(--space-3);
  font-size: var(--font-size-sm);
}

.selected-file__icon {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand);
  color: white;
}

.selected-file > div {
  min-width: 0;
  flex: 1 1 auto;
}

.selected-file span,
.selected-file strong,
.selected-file small {
  display: block;
}

.selected-file span,
.selected-file small {
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.selected-file strong {
  color: var(--color-text);
  overflow-wrap: anywhere;
  white-space: normal;
}

.upload-panel__action {
  display: grid;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.upload-panel__action p {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.upload-panel__action .icon {
  flex: 0 0 auto;
  color: var(--color-brand);
  margin-top: 0.1rem;
}

.upload-panel__submit {
  width: 100%;
}

.document-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(8rem, 1fr)) auto;
  align-items: end;
  gap: var(--space-3);
}

.documents-page__filters > .document-filters {
  margin-top: var(--space-6);
}

.document-row {
  display: grid;
  grid-template-columns: minmax(14rem, 1.4fr) minmax(15rem, 1fr) auto;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
}

.document-row__identity {
  min-width: 0;
}

.document-row__title {
  color: var(--color-brand-strong);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.document-row__title:hover {
  text-decoration: underline;
  text-underline-offset: 0.18em;
}

.document-row__file,
.document-row__time {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  overflow-wrap: anywhere;
}

.document-row__statuses,
.document-row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.document-row__actions {
  justify-content: flex-end;
}

@media (max-width: 64rem) {
  .document-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .document-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .document-row__statuses {
    grid-column: 1;
    grid-row: 2;
  }

  .document-row__actions {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
}

@media (max-width: 48rem) {
  .upload-panel {
    grid-template-columns: 1fr;
  }

  .upload-panel__heading {
    align-items: stretch;
    flex-direction: column;
  }

  .upload-panel__steps {
    width: 100%;
  }

  .upload-panel__steps li {
    flex: 1 1 0;
  }

  .upload-panel__details {
    border-top: 1px solid var(--color-border);
    border-left: 0;
    padding-top: var(--space-5);
    padding-left: 0;
  }
}

@media (max-width: 40rem) {
  .document-filters,
  .upload-panel__fields {
    grid-template-columns: 1fr;
  }

  .document-row {
    grid-template-columns: 1fr;
  }

  .document-row__statuses,
  .document-row__actions {
    grid-column: 1;
    grid-row: auto;
  }

  .document-row__actions {
    justify-content: flex-start;
  }

  .selected-file {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .selected-file > div {
    min-width: calc(100% - 3rem);
  }
}
</style>
