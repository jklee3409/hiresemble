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
import type { DocumentParseStatus, EvidenceExtractionStatus } from '@/shared/api/documentContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
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
        ? '입력한 내용을 저장했어요.'
        : '같은 작업을 다시 시작했어요.'
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
    message.value = '새로 읽는 작업을 접수했어요.'
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
  <section class="document-detail app-page" aria-labelledby="document-heading">
    <RouterLink class="back-link" to="/documents">
      <AppIcon name="arrow-left" />
      이력서·자료
    </RouterLink>
    <StatePanel
      v-if="document.isPending.value"
      class="document-detail__state"
      kind="loading"
      title="자료를 불러오는 중…"
      description="자료 정보와 정리 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="document.isError.value"
      class="document-detail__state"
      kind="error"
      title="자료를 불러오지 못했어요."
      :description="loadError"
    >
      <template #actions>
        <RouterLink class="button button--secondary" to="/documents">
          이력서·자료로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>
    <template v-else-if="document.data.value">
      <PageHeader
        heading-id="document-heading"
        :title="document.data.value.displayName"
        :description="`${DOCUMENT_TYPE_LABELS[document.data.value.documentType]} · ${formatFileSize(document.data.value.fileSizeBytes)}`"
        eyebrow="자료 확인"
      >
        <template #actions>
          <div class="document-detail__actions">
            <button class="button button--secondary button--compact" type="button" @click="reparse">
              재처리
            </button>
            <button class="button button--ghost button--compact" type="button" @click="download">
              다운로드
            </button>
            <button class="button button--danger button--compact" type="button" @click="remove">
              삭제
            </button>
          </div>
        </template>
      </PageHeader>

      <div class="document-status-grid">
        <section class="document-status">
          <span class="document-status__label">문서 읽기</span>
          <StatusBadge
            :label="DOCUMENT_PARSE_STATUS_LABELS[document.data.value.parseStatus]"
            :tone="parseTone(document.data.value.parseStatus)"
          />
          <p>등록한 자료의 내용을 읽는 과정이에요.</p>
        </section>
        <section class="document-status">
          <span class="document-status__label">경력 정보 정리</span>
          <StatusBadge
            :label="EVIDENCE_EXTRACTION_STATUS_LABELS[document.data.value.evidenceExtractionStatus]"
            :tone="evidenceTone(document.data.value.evidenceExtractionStatus)"
          />
          <p>읽은 내용에서 확인할 경력 정보를 정리하는 과정이에요.</p>
        </section>
      </div>
      <p class="alert alert--info document-detail__message">
        {{
          documentStateMessage(
            document.data.value.parseStatus,
            document.data.value.evidenceExtractionStatus,
          )
        }}
      </p>
      <p
        v-if="document.data.value.safeError"
        class="alert alert--warning document-detail__message"
        role="alert"
      >
        {{ document.data.value.safeError.message }}
      </p>
      <p v-if="message" class="alert alert--success document-detail__message" role="status">
        {{ message }}
      </p>
      <p v-if="actionError" class="alert alert--danger document-detail__message" role="alert">
        {{ actionError }}
      </p>

      <dl class="metadata-grid section-surface document-metadata">
        <div>
          <dt>페이지</dt>
          <dd>{{ document.data.value.pageCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt>문자 수</dt>
          <dd>{{ document.data.value.characterCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt>업로드</dt>
          <dd>{{ new Date(document.data.value.uploadedAt).toLocaleString('ko-KR') }}</dd>
        </div>
        <div>
          <dt>최근 수정</dt>
          <dd>{{ new Date(document.data.value.updatedAt).toLocaleString('ko-KR') }}</dd>
        </div>
      </dl>

      <section
        v-if="activeRunId || document.data.value.latestAgentRunId"
        class="document-section section-surface"
      >
        <div class="document-section__heading">
          <div>
            <p class="section-kicker">진행 상황</p>
            <h3 class="section-title">자료 분석 진행 상황</h3>
          </div>
          <RouterLink
            class="button button--secondary button--compact"
            :to="{
              name: 'agent-run-detail',
              params: { agentRunId: activeRunId || document.data.value.latestAgentRunId },
            }"
            >분석 기록 자세히 보기</RouterLink
          >
        </div>
        <DocumentRunMonitor
          :user-id="userId"
          :document-id="documentId"
          :agent-run-id="monitoredRunId"
        />
      </section>

      <section
        v-if="document.data.value.parseStatus === 'NEEDS_MANUAL_TEXT'"
        class="document-section section-surface manual-text-section"
      >
        <p class="section-kicker">직접 입력</p>
        <h3 class="section-title">내용 직접 입력</h3>
        <p class="document-section__description">
          자료에서 읽지 못한 내용을 직접 입력해 주세요. 공백을 제외하고 100자 이상 입력하면 기다리던
          자료 분석을 이어서 진행해요.
        </p>
        <form class="manual-text-form" novalidate @submit.prevent="submitManualText">
          <label class="field">
            <span class="field__label">자료 내용</span>
            <textarea
              v-model="manualText"
              class="control manual-text-form__editor"
              maxlength="500000"
            />
          </label>
          <p v-if="manualError" class="inline-error" role="alert">{{ manualError }}</p>
          <button
            class="button button--primary"
            type="submit"
            :disabled="manualMutation.isPending.value"
          >
            {{ manualMutation.isPending.value ? '다시 시작하는 중…' : '내용 저장하고 계속하기' }}
          </button>
        </form>
      </section>

      <section class="document-section section-surface">
        <p class="section-kicker">읽은 내용</p>
        <h3 class="section-title">자료 미리보기</h3>
        <p v-if="documentText.isPending.value" class="document-text__status" role="status">
          자료 내용을 불러오는 중…
        </p>
        <p
          v-else-if="documentText.isError.value"
          class="alert alert--warning document-text__status"
          role="alert"
        >
          자료 내용을 아직 불러올 수 없어요.
        </p>
        <pre v-else-if="documentText.data.value" class="document-text__preview">{{
          documentText.data.value.text
        }}</pre>
        <p v-else class="document-text__status">문서를 다 읽으면 내용 미리보기가 표시돼요.</p>
      </section>

      <DocumentEvidencePanel :user-id="userId" :document-id="documentId" />
    </template>
  </section>
</template>

<style scoped>
.document-detail__state,
.document-status-grid,
.document-detail__message,
.document-metadata,
.document-section {
  margin-top: var(--space-5);
}

.document-detail__actions,
.document-section__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.document-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.document-status {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-2) var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.document-status__label {
  font-weight: 700;
}

.document-status p {
  grid-column: 1 / -1;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.document-metadata {
  padding: var(--space-5);
}

.document-section {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.document-section__heading {
  justify-content: space-between;
}

.document-section__description,
.document-text__status {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.manual-text-form {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.manual-text-form .field {
  width: 100%;
}

.manual-text-form__editor {
  min-height: 15rem;
  line-height: 1.7;
}

.document-text__preview {
  max-height: 32rem;
  margin-top: var(--space-4);
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  line-height: 1.8;
  overflow-wrap: anywhere;
  padding: var(--space-5);
  white-space: pre-wrap;
}

@media (max-width: 40rem) {
  .document-status-grid {
    grid-template-columns: 1fr;
  }

  .document-section__heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
