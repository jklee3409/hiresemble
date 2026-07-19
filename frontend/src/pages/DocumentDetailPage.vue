<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  DOCUMENT_PARSE_STATUS_LABELS,
  DOCUMENT_TYPE_LABELS,
  EVIDENCE_EXTRACTION_STATUS_LABELS,
  documentStateMessage,
  formatFileSize,
} from '@/features/documents/presentation'
import {
  documentQueryKeys,
  useDocumentDetailQuery,
  useDocumentTextQuery,
} from '@/features/documents/queries'
import { validateManualText } from '@/features/documents/validation'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import DocumentRunMonitor from '@/features/documents/DocumentRunMonitor.vue'
import DocumentEvidencePanel from '@/features/documents/DocumentEvidencePanel.vue'
import {
  createDocumentDownloadUrl,
  createDocumentIdempotencyKey,
  deleteDocument,
  provideDocumentManualText,
  reparseDocument,
} from '@/shared/api/documentApi'
import { normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const documentId = computed(() => String(route.params.documentId ?? ''))
const document = useDocumentDetailQuery(userId, documentId)
const textEnabled = computed(() => document.data.value?.parseStatus === 'PARSED')
const documentText = useDocumentTextQuery(userId, documentId, textEnabled)
const manualText = ref('')
const manualError = ref('')
const actionError = ref('')
const message = ref('')
const activeRunId = ref(typeof route.query.run === 'string' ? route.query.run : '')
const monitoredRunId = computed(
  () => activeRunId.value || document.data.value?.latestAgentRunId || '',
)
let manualIdempotencyKey = ''
let reparseIdempotencyKey = ''

watch(manualText, () => {
  if (!manualMutation.isPending.value) manualIdempotencyKey = ''
})

const manualMutation = useMutation({
  mutationFn: (input: { text: string; version: number; key: string }) =>
    provideDocumentManualText(
      documentId.value,
      { text: input.text, version: input.version },
      input.key,
    ),
})
const reparseMutation = useMutation({
  mutationFn: (input: { version: number; key: string }) =>
    reparseDocument(documentId.value, { version: input.version }, input.key),
})
const downloadMutation = useMutation({
  mutationFn: () => createDocumentDownloadUrl(documentId.value),
})
const deleteMutation = useMutation({
  mutationFn: (version: number) => deleteDocument(documentId.value, version),
})

const loadError = computed(() =>
  document.error.value ? normalizeApiError(document.error.value).message : '',
)

async function submitManualText(): Promise<void> {
  if (document.data.value === undefined) return
  manualError.value = validateManualText(manualText.value) ?? ''
  if (manualError.value !== '') return
  actionError.value = ''
  try {
    const accepted = await manualMutation.mutateAsync({
      text: manualText.value,
      version: document.data.value.version,
      key:
        manualIdempotencyKey ||
        (manualIdempotencyKey = createDocumentIdempotencyKey('manual-text')),
    })
    activeRunId.value = accepted.agentRunId
    manualIdempotencyKey = ''
    message.value =
      accepted.status === 'WAITING_USER'
        ? '텍스트 입력을 접수했습니다.'
        : '같은 작업을 다시 시작했습니다.'
    await refreshDocument()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function reparse(): Promise<void> {
  if (document.data.value === undefined) return
  actionError.value = ''
  try {
    const accepted = await reparseMutation.mutateAsync({
      version: document.data.value.version,
      key:
        reparseIdempotencyKey || (reparseIdempotencyKey = createDocumentIdempotencyKey('reparse')),
    })
    activeRunId.value = accepted.agentRunId
    reparseIdempotencyKey = ''
    message.value = '새 재처리 작업을 접수했습니다.'
    await refreshDocument()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function download(): Promise<void> {
  actionError.value = ''
  try {
    const value = await downloadMutation.mutateAsync()
    const anchor = window.document.createElement('a')
    anchor.href = value.url
    anchor.rel = 'noopener noreferrer'
    anchor.target = '_blank'
    anchor.click()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function remove(): Promise<void> {
  if (document.data.value === undefined || !window.confirm('이 문서를 삭제할까요?')) return
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync(document.data.value.version)
    closeAgentRunStreamsForResource(userId.value, 'DOCUMENT', documentId.value)
    cache.removeQueries({ queryKey: documentQueryKeys.detail(userId.value, documentId.value) })
    cache.removeQueries({ queryKey: documentQueryKeys.text(userId.value, documentId.value) })
    cache.removeQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
    await cache.invalidateQueries({ queryKey: documentQueryKeys.root(userId.value) })
    await router.replace({ name: 'documents', query: { deleted: 'true' } })
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function refreshDocument(): Promise<void> {
  await cache.invalidateQueries({
    queryKey: documentQueryKeys.detail(userId.value, documentId.value),
  })
  await cache.invalidateQueries({
    queryKey: documentQueryKeys.text(userId.value, documentId.value),
  })
  await cache.invalidateQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
}
</script>

<template>
  <section aria-labelledby="document-heading">
    <RouterLink class="text-sm font-semibold text-indigo-700" to="/documents"
      >← 문서 목록</RouterLink
    >
    <p v-if="document.isPending.value" class="mt-6" aria-live="polite">문서 상세를 불러오는 중…</p>
    <div
      v-else-if="document.isError.value"
      class="mt-6 rounded-xl bg-red-50 p-5 text-red-800"
      role="alert"
    >
      <p>{{ loadError }}</p>
      <RouterLink class="mt-3 inline-block underline" to="/documents"
        >문서 목록으로 돌아가기</RouterLink
      >
    </div>
    <template v-else-if="document.data.value">
      <div class="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 id="document-heading" class="text-2xl font-bold">
            {{ document.data.value.displayName }}
          </h2>
          <p class="mt-2 text-slate-600">
            {{ DOCUMENT_TYPE_LABELS[document.data.value.documentType] }} ·
            {{ document.data.value.mimeType }} ·
            {{ formatFileSize(document.data.value.fileSizeBytes) }}
          </p>
        </div>
        <div class="flex gap-2">
          <button
            class="rounded-lg border border-slate-300 px-3 py-2"
            type="button"
            @click="reparse"
          >
            재처리</button
          ><button
            class="rounded-lg border border-slate-300 px-3 py-2"
            type="button"
            @click="download"
          >
            다운로드</button
          ><button
            class="rounded-lg border border-red-300 px-3 py-2 text-red-700"
            type="button"
            @click="remove"
          >
            삭제
          </button>
        </div>
      </div>

      <div class="mt-6 grid gap-4 md:grid-cols-2">
        <section class="rounded-xl bg-white p-5">
          <h3 class="font-semibold">파싱 상태</h3>
          <p class="mt-2">{{ DOCUMENT_PARSE_STATUS_LABELS[document.data.value.parseStatus] }}</p>
        </section>
        <section class="rounded-xl bg-white p-5">
          <h3 class="font-semibold">근거 추출 상태</h3>
          <p class="mt-2">
            {{ EVIDENCE_EXTRACTION_STATUS_LABELS[document.data.value.evidenceExtractionStatus] }}
          </p>
        </section>
      </div>
      <p class="mt-4 rounded-lg bg-sky-50 p-3 text-sm text-sky-900">
        {{
          documentStateMessage(
            document.data.value.parseStatus,
            document.data.value.evidenceExtractionStatus,
          )
        }}
      </p>
      <p
        v-if="document.data.value.safeError"
        class="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
        role="alert"
      >
        {{ document.data.value.safeError.message }}
      </p>
      <p v-if="message" class="mt-3 text-sm text-emerald-700" role="status">{{ message }}</p>
      <p v-if="actionError" class="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">
        {{ actionError }}
      </p>

      <dl class="mt-6 grid gap-3 rounded-xl bg-white p-5 text-sm sm:grid-cols-2">
        <div>
          <dt class="font-medium">페이지</dt>
          <dd>{{ document.data.value.pageCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt class="font-medium">문자 수</dt>
          <dd>{{ document.data.value.characterCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt class="font-medium">업로드</dt>
          <dd>{{ new Date(document.data.value.uploadedAt).toLocaleString('ko-KR') }}</dd>
        </div>
        <div>
          <dt class="font-medium">최근 수정</dt>
          <dd>{{ new Date(document.data.value.updatedAt).toLocaleString('ko-KR') }}</dd>
        </div>
      </dl>

      <section
        v-if="activeRunId || document.data.value.latestAgentRunId"
        class="mt-6 rounded-xl bg-white p-5"
      >
        <h3 class="font-semibold">Agent Run</h3>
        <RouterLink
          class="mt-2 inline-block text-indigo-700 underline"
          :to="{
            name: 'agent-run-detail',
            params: { agentRunId: activeRunId || document.data.value.latestAgentRunId },
          }"
          >작업 진행 상세 보기</RouterLink
        >
        <DocumentRunMonitor
          :user-id="userId"
          :document-id="documentId"
          :agent-run-id="monitoredRunId"
        />
      </section>

      <section
        v-if="document.data.value.parseStatus === 'NEEDS_MANUAL_TEXT'"
        class="mt-6 rounded-xl bg-white p-5"
      >
        <h3 class="font-semibold">텍스트 직접 입력</h3>
        <p class="mt-1 text-sm text-slate-600">
          공백을 제외해 100자 이상, 최대 500,000자를 입력하세요. 기존 WAITING_USER 작업을
          재개합니다.
        </p>
        <form class="mt-4" novalidate @submit.prevent="submitManualText">
          <label class="block text-sm font-medium"
            >문서 텍스트<textarea
              v-model="manualText"
              class="mt-1 min-h-56 w-full rounded-lg border border-slate-300 p-3"
              maxlength="500000"
            />
          </label>
          <p v-if="manualError" class="mt-2 text-sm text-red-700" role="alert">{{ manualError }}</p>
          <button
            class="mt-3 rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
            type="submit"
            :disabled="manualMutation.isPending.value"
          >
            {{ manualMutation.isPending.value ? '재개 중…' : '텍스트 저장 후 재개' }}
          </button>
        </form>
      </section>

      <section class="mt-6 rounded-xl bg-white p-5">
        <h3 class="font-semibold">추출 텍스트</h3>
        <p v-if="documentText.isPending.value" class="mt-3">텍스트를 불러오는 중…</p>
        <p v-else-if="documentText.isError.value" class="mt-3 text-sm text-amber-800" role="alert">
          추출 텍스트를 아직 불러올 수 없습니다.
        </p>
        <pre
          v-else-if="documentText.data.value"
          class="mt-3 max-h-96 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-4 text-sm"
          >{{ documentText.data.value.text }}</pre>
        <p v-else class="mt-3 text-sm text-slate-600">
          파싱이 완료되면 원문 미리보기가 표시됩니다.
        </p>
      </section>

      <DocumentEvidencePanel :user-id="userId" :document-id="documentId" />
    </template>
  </section>
</template>
