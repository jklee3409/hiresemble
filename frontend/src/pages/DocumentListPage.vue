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
  type DocumentType,
} from '@/shared/api/documentContracts'
import {
  createDocumentDownloadUrl,
  createDocumentIdempotencyKey,
  deleteDocument,
  reparseDocument,
  uploadDocument,
} from '@/shared/api/documentApi'
import { normalizeApiError } from '@/shared/api/errors'
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
</script>

<template>
  <section aria-labelledby="documents-heading">
    <h2 id="documents-heading" class="text-2xl font-bold">문서·근거</h2>
    <p class="mt-2 text-slate-600">PDF, DOCX, TXT 파일을 업로드하고 두 처리 상태를 확인합니다.</p>

    <form class="mt-6 rounded-2xl bg-white p-6 shadow-sm" novalidate @submit.prevent="upload">
      <h3 class="text-lg font-semibold">문서 업로드</h3>
      <label
        for="document-file"
        class="mt-4 block cursor-pointer rounded-xl border-2 border-dashed border-slate-300 p-6 text-center focus-within:border-indigo-600"
        @dragover.prevent
        @drop.prevent="dropFile"
      >
        <span class="font-medium">파일을 놓거나 눌러 선택하세요</span>
        <span class="mt-1 block text-sm text-slate-600">PDF · DOCX · TXT, 파일당 최대 20MB</span>
        <input
          id="document-file"
          class="mt-3 block w-full text-sm"
          type="file"
          accept=".pdf,.docx,.txt"
          @change="selectFile"
        />
      </label>
      <p v-if="selectedFile" class="mt-2 text-sm" role="status">
        선택: {{ selectedFile.name }} ({{ formatFileSize(selectedFile.size) }})
      </p>
      <p v-if="uploadErrors.file" class="mt-2 text-sm text-red-700" role="alert">
        {{ uploadErrors.file }}
      </p>
      <div class="mt-4 grid gap-4 md:grid-cols-2">
        <label class="text-sm font-medium"
          >문서 유형<select
            id="document-upload-type"
            v-model="documentType"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          >
            <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
              {{ DOCUMENT_TYPE_LABELS[type] }}
            </option>
          </select></label
        >
        <label class="text-sm font-medium"
          >표시 이름 (선택)<input
            id="document-displayName"
            v-model="displayName"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            maxlength="255"
          />
          <span v-if="uploadErrors.displayName" class="mt-1 block text-red-700">{{
            uploadErrors.displayName
          }}</span>
        </label>
      </div>
      <button
        id="document-upload-submit"
        type="submit"
        class="mt-4 rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
        :disabled="uploadMutation.isPending.value"
      >
        {{ uploadMutation.isPending.value ? '업로드 접수 중…' : '업로드' }}
      </button>
    </form>

    <form
      class="mt-6 flex flex-wrap items-end gap-3 rounded-xl bg-white p-4"
      @submit.prevent="applyFilters"
    >
      <label class="text-sm font-medium"
        >문서 유형<select
          v-model="filterDocumentType"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="">전체</option>
          <option v-for="type in DOCUMENT_TYPES" :key="type" :value="type">
            {{ DOCUMENT_TYPE_LABELS[type] }}
          </option>
        </select></label
      >
      <label class="text-sm font-medium"
        >파싱 상태<select
          v-model="filterParseStatus"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="">전체</option>
          <option v-for="value in DOCUMENT_PARSE_STATUSES" :key="value" :value="value">
            {{ DOCUMENT_PARSE_STATUS_LABELS[value] }}
          </option>
        </select></label
      >
      <label class="text-sm font-medium"
        >근거 추출 상태<select
          v-model="filterEvidenceStatus"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="">전체</option>
          <option v-for="value in EVIDENCE_EXTRACTION_STATUSES" :key="value" :value="value">
            {{ EVIDENCE_EXTRACTION_STATUS_LABELS[value] }}
          </option>
        </select></label
      >
      <label class="text-sm font-medium"
        >정렬<select
          :value="filters.sort"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
          @change="updateSort"
        >
          <option value="uploadedAt,desc">최근 업로드순</option>
          <option value="updatedAt,desc">최근 수정순</option>
        </select></label
      >
      <button class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white" type="submit">
        필터 적용
      </button>
    </form>

    <p v-if="message" class="mt-4 text-sm text-emerald-700" role="status">{{ message }}</p>
    <p v-if="actionError" class="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">
      {{ actionError }}
    </p>
    <p v-if="documents.isPending.value" class="mt-8" aria-live="polite">문서 목록을 불러오는 중…</p>
    <div
      v-else-if="documents.isError.value"
      class="mt-8 rounded-xl bg-red-50 p-4 text-red-800"
      role="alert"
    >
      문서 목록을 불러오지 못했습니다.
      <button class="underline" type="button" @click="documents.refetch()">다시 시도</button>
    </div>
    <div
      v-else-if="documents.data.value?.items.length === 0"
      class="mt-8 rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600"
    >
      조건에 맞는 문서가 없습니다.
    </div>
    <ul v-else class="mt-6 space-y-4">
      <li
        v-for="document in documents.data.value?.items"
        :key="document.id"
        class="rounded-2xl bg-white p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <RouterLink
              class="font-semibold text-indigo-700"
              :to="{ name: 'document-detail', params: { documentId: document.id } }"
              >{{ document.displayName }}</RouterLink
            >
            <p class="mt-1 text-sm text-slate-600">
              {{ DOCUMENT_TYPE_LABELS[document.documentType] }} · {{ document.mimeType }} ·
              {{ formatFileSize(document.fileSizeBytes) }}
            </p>
            <p class="mt-2 text-sm">
              <span class="font-medium">파싱:</span>
              {{ DOCUMENT_PARSE_STATUS_LABELS[document.parseStatus] }}
              <span class="ml-3 font-medium">근거:</span>
              {{ EVIDENCE_EXTRACTION_STATUS_LABELS[document.evidenceExtractionStatus] }}
            </p>
            <p class="mt-1 text-xs text-slate-500">
              업로드 {{ new Date(document.uploadedAt).toLocaleString('ko-KR') }}
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              class="rounded-lg border border-slate-300 px-3 py-2 text-sm"
              @click="reparse(document.id, document.version)"
            >
              재처리
            </button>
            <button
              type="button"
              class="rounded-lg border border-slate-300 px-3 py-2 text-sm"
              @click="download(document.id)"
            >
              다운로드
            </button>
            <button
              type="button"
              class="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-700"
              @click="remove(document.id, document.version, document.displayName)"
            >
              삭제
            </button>
          </div>
        </div>
      </li>
    </ul>

    <nav
      v-if="documents.data.value && documents.data.value.totalPages > 0"
      class="mt-6 flex items-center justify-between"
      aria-label="문서 페이지"
    >
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="filters.page === 0"
        @click="updatePage(filters.page - 1)"
      >
        이전
      </button>
      <span class="text-sm"
        >{{ filters.page + 1 }} / {{ documents.data.value.totalPages }} 페이지</span
      >
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="filters.page + 1 >= documents.data.value.totalPages"
        @click="updatePage(filters.page + 1)"
      >
        다음
      </button>
    </nav>
  </section>
</template>
