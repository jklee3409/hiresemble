<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
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
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseDocumentFilters(route.query))
const documents = useDocumentListQuery(userId, filters)

const filterDocumentType = ref('')
const filterParseStatus = ref('')
const filterEvidenceStatus = ref('')
const selectedFile = ref<File | null>(null)
const documentType = ref<DocumentType>('RESUME')
const displayName = ref('')
const uploadErrors = ref<Record<string, string>>({})
const actionError = ref('')
const message = ref('')
let uploadIdempotencyKey = ''
const reparseKeys = new Map<string, string>()

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
}

async function upload(): Promise<void> {
  actionError.value = ''
  message.value = ''
  uploadErrors.value = validateUpload({
    file: selectedFile.value,
    documentType: documentType.value,
    displayName: displayName.value,
  })
  if (selectedFile.value === null || Object.keys(uploadErrors.value).length > 0) return

  try {
    const accepted = await uploadMutation.mutateAsync({
      file: selectedFile.value,
      documentType: documentType.value,
      displayName: displayName.value.trim() || null,
      idempotencyKey:
        uploadIdempotencyKey || (uploadIdempotencyKey = createDocumentIdempotencyKey('upload')),
    })
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
  if (!window.confirm(`${name} 문서를 삭제할까요?`)) return
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync({ id, version })
    closeAgentRunStreamsForResource(userId.value, 'DOCUMENT', id)
    cache.removeQueries({ queryKey: documentQueryKeys.detail(userId.value, id) })
    cache.removeQueries({ queryKey: documentQueryKeys.text(userId.value, id) })
    cache.removeQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
    await cache.invalidateQueries({ queryKey: documentQueryKeys.root(userId.value) })
    message.value = '문서를 삭제했습니다.'
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
      title="문서·근거"
      description="경력 문서를 업로드하고 텍스트 처리와 근거 추출 상태를 각각 확인합니다."
      eyebrow="Documents"
    />

    <form class="upload-panel section-surface" novalidate @submit.prevent="upload">
      <div class="upload-panel__heading">
        <p class="section-kicker">Upload</p>
        <h3 class="section-title">문서 업로드</h3>
        <p>업로드가 접수되면 문서 상세에서 처리 과정을 이어서 확인할 수 있습니다.</p>
      </div>
      <label for="document-file" class="dropzone" @dragover.prevent @drop.prevent="dropFile">
        <span class="dropzone__icon" aria-hidden="true"><AppIcon name="upload" /></span>
        <strong>파일을 놓거나 눌러 선택하세요</strong>
        <span>PDF · DOCX · TXT, 파일당 최대 20MB</span>
        <input
          id="document-file"
          class="dropzone__input"
          type="file"
          accept=".pdf,.docx,.txt"
          @change="selectFile"
        />
      </label>
      <p v-if="selectedFile" class="selected-file" role="status">
        <span>선택한 파일</span>
        <strong>{{ selectedFile.name }}</strong>
        <span>{{ formatFileSize(selectedFile.size) }}</span>
      </p>
      <p v-if="uploadErrors.file" class="inline-error" role="alert">
        {{ uploadErrors.file }}
      </p>
      <div class="upload-panel__fields">
        <label class="field">
          <span class="field__label">문서 유형</span>
          <select id="document-upload-type" v-model="documentType" class="control">
            <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
              {{ DOCUMENT_TYPE_LABELS[type] }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">표시 이름 <span class="field__optional">(선택)</span></span>
          <input
            id="document-displayName"
            v-model="displayName"
            class="control"
            maxlength="255"
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
      </div>
      <button
        id="document-upload-submit"
        type="submit"
        class="button button--primary upload-panel__submit"
        :disabled="uploadMutation.isPending.value"
      >
        {{ uploadMutation.isPending.value ? '업로드 접수 중…' : '업로드' }}
      </button>
    </form>

    <form class="filter-toolbar document-filters" @submit.prevent="applyFilters">
      <label class="field">
        <span class="field__label">문서 유형</span>
        <select v-model="filterDocumentType" class="control control--compact">
          <option value="">전체</option>
          <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
            {{ DOCUMENT_TYPE_LABELS[type] }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">텍스트 처리</span>
        <select v-model="filterParseStatus" class="control control--compact">
          <option value="">전체</option>
          <option v-for="value in DOCUMENT_PARSE_STATUSES" :key="value" :value="value">
            {{ DOCUMENT_PARSE_STATUS_LABELS[value] }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">근거 추출</span>
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
      title="문서 목록을 불러오는 중…"
      description="업로드한 문서와 처리 상태를 확인하고 있습니다."
    />
    <StatePanel
      v-else-if="documents.isError.value"
      class="documents-page__state"
      kind="error"
      title="문서 목록을 불러오지 못했습니다."
      description="연결 상태를 확인한 뒤 다시 시도해 주세요."
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
      title="조건에 맞는 문서가 없습니다."
      description="필터를 조정하거나 위 업로드 영역에서 첫 문서를 등록해 주세요."
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
            {{ DOCUMENT_TYPE_LABELS[document.documentType] }} · {{ document.mimeType }} ·
            {{ formatFileSize(document.fileSizeBytes) }}
          </p>
          <p class="document-row__time">
            업로드 {{ new Date(document.uploadedAt).toLocaleString('ko-KR') }}
          </p>
        </div>
        <div class="document-row__statuses" aria-label="문서 처리 상태">
          <StatusBadge
            prefix="텍스트"
            :label="DOCUMENT_PARSE_STATUS_LABELS[document.parseStatus]"
            :tone="parseTone(document.parseStatus)"
          />
          <StatusBadge
            prefix="근거"
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
            재처리
          </button>
          <button
            type="button"
            class="button button--ghost button--compact"
            @click="download(document.id)"
          >
            다운로드
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
      label="문서 페이지"
      @change="updatePage"
    />
  </section>
</template>

<style scoped>
.upload-panel,
.document-filters,
.documents-page__message,
.documents-page__state,
.document-list {
  margin-top: var(--space-6);
}

.upload-panel {
  display: grid;
  grid-template-columns: minmax(13rem, 0.75fr) minmax(18rem, 1.25fr);
  gap: var(--space-5) var(--space-7);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.upload-panel__heading p:last-child {
  max-width: 31rem;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.dropzone {
  display: flex;
  min-height: 11rem;
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
.dropzone:focus-within {
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
  width: min(100%, 19rem);
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
}

.selected-file {
  display: flex;
  min-width: 0;
  grid-column: 2;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.selected-file strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-panel__fields {
  display: grid;
  grid-column: 2;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.upload-panel__submit {
  grid-column: 2;
  justify-self: start;
}

.document-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(8rem, 1fr)) auto;
  align-items: end;
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

  .selected-file,
  .upload-panel__fields,
  .upload-panel__submit {
    grid-column: 1;
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
}
</style>
