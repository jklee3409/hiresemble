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
import { useAgentRunDetailQuery } from '@/features/agent-runs/queries'
import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import DocumentRunMonitor from '@/features/documents/DocumentRunMonitor.vue'
import DocumentEvidencePanel from '@/features/documents/DocumentEvidencePanel.vue'
import DocumentPagePreview from '@/features/documents/DocumentPagePreview.vue'
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
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const cache = useQueryClient()
const authStore = useAuthStore()
const notifications = useNotifications()
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
/* PDF는 원본을 페이지 단위로 그려 보여 주고, 렌더링할 수 없는 형식과 실패는 추출한 텍스트로 되돌린다. */
const pageViewerUnavailable = ref(false)
const pageViewerSupported = computed(
  () => document.data.value?.mimeType === 'application/pdf' && !pageViewerUnavailable.value,
)
const monitoredRunId = computed(
  () => activeRunId.value || document.data.value?.latestAgentRunId || '',
)
const monitoredRun = useAgentRunDetailQuery(userId, monitoredRunId)
const documentRunActive = computed(() =>
  ['QUEUED', 'RUNNING', 'WAITING_USER'].includes(monitoredRun.data.value?.status ?? ''),
)
let manualIdempotencyKey = ''
let reparseIdempotencyKey = ''

watch(manualText, () => {
  if (!manualMutation.isPending.value) manualIdempotencyKey = ''
})

watch(documentId, () => {
  pageViewerUnavailable.value = false
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
const reparseUnavailable = computed(
  () =>
    reparseMutation.isPending.value ||
    documentRunActive.value ||
    (monitoredRunId.value !== '' && (monitoredRun.isLoading.value || monitoredRun.isError.value)),
)
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
    notifications.toast(message.value, 'success')
    await refreshDocument()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
  }
}

async function reparse(): Promise<void> {
  if (document.data.value === undefined || reparseUnavailable.value) return
  const confirmed = await notifications.confirm({
    title: '자료를 다시 분석할까요?',
    message:
      '기존에 추출한 경험은 즉시 삭제되어 공고 분석과 자기소개서 작성에 더 이상 사용되지 않아요. 새 분석에서 경험을 다시 추출합니다.',
    confirmLabel: '다시 분석',
  })
  if (!confirmed) return
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
    notifications.toast('자료를 다시 분석하기 시작했어요.', 'success')
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
  if (document.data.value === undefined) return
  const confirmed = await notifications.confirm({
    title: '등록 자료를 삭제할까요?',
    message:
      '원본과 아직 참조되지 않은 분석 소재가 삭제됩니다. 직접 등록한 대외활동은 삭제되지 않아요.',
    confirmLabel: '자료 삭제',
  })
  if (!confirmed) return
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync(document.data.value.version)
    closeAgentRunStreamsForResource(userId.value, 'DOCUMENT', documentId.value)
    cache.removeQueries({ queryKey: documentQueryKeys.detail(userId.value, documentId.value) })
    cache.removeQueries({ queryKey: documentQueryKeys.text(userId.value, documentId.value) })
    cache.removeQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
    await cache.invalidateQueries({ queryKey: documentQueryKeys.root(userId.value) })
    notifications.toast('자료를 삭제했어요.', 'success')
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

function fileTypeLabel(mimeType: string): string {
  if (mimeType === 'application/pdf') return 'PDF 문서'
  if (mimeType.includes('wordprocessingml')) return 'Word 문서'
  if (mimeType === 'text/plain') return '텍스트 문서'
  return mimeType
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
        variant="detail"
      >
        <template #actions>
          <div class="document-detail__actions">
            <button
              class="button button--secondary button--compact"
              type="button"
              :disabled="reparseUnavailable"
              @click="reparse"
            >
              {{ documentRunActive ? '분석 진행 중…' : '다시 분석' }}
            </button>
            <button class="button button--ghost button--compact" type="button" @click="download">
              원본 열기
            </button>
            <button class="button button--danger button--compact" type="button" @click="remove">
              삭제
            </button>
          </div>
        </template>
      </PageHeader>

      <section class="document-overview section-surface" aria-label="업로드 자료 정보">
        <div class="document-overview__icon" aria-hidden="true"><AppIcon name="documents" /></div>
        <dl class="document-overview__details">
          <div>
            <dt>업로드한 파일</dt>
            <dd>{{ document.data.value.originalFilename }}</dd>
          </div>
          <div>
            <dt>파일 형식</dt>
            <dd>{{ fileTypeLabel(document.data.value.mimeType) }}</dd>
          </div>
          <div>
            <dt>파일 크기</dt>
            <dd>{{ formatFileSize(document.data.value.fileSizeBytes) }}</dd>
          </div>
          <div>
            <dt>등록 시점</dt>
            <dd>{{ new Date(document.data.value.uploadedAt).toLocaleString('ko-KR') }}</dd>
          </div>
          <div>
            <dt>최근 분석</dt>
            <dd>
              {{
                document.data.value.parsedAt
                  ? new Date(document.data.value.parsedAt).toLocaleString('ko-KR')
                  : '아직 분석 전'
              }}
            </dd>
          </div>
        </dl>
      </section>

      <div class="document-status-grid">
        <section class="document-status">
          <span class="document-status__label">자료 확인</span>
          <StatusBadge
            :label="DOCUMENT_PARSE_STATUS_LABELS[document.data.value.parseStatus]"
            :tone="parseTone(document.data.value.parseStatus)"
          />
          <p>업로드한 파일에서 내용을 안전하게 확인하고 있어요.</p>
        </section>
        <section class="document-status">
          <span class="document-status__label">경험·소재 정리</span>
          <StatusBadge
            :label="EVIDENCE_EXTRACTION_STATUS_LABELS[document.data.value.evidenceExtractionStatus]"
            :tone="evidenceTone(document.data.value.evidenceExtractionStatus)"
          />
          <p>자소서와 면접에 참고할 경험 후보를 찾아 정리해요.</p>
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

      <dl class="metadata-grid section-surface document-metadata" aria-label="분석 결과 요약">
        <div>
          <dt>페이지</dt>
          <dd>{{ document.data.value.pageCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt>문자 수</dt>
          <dd>{{ document.data.value.characterCount ?? '확인 전' }}</dd>
        </div>
        <div>
          <dt>추출한 소재</dt>
          <dd>
            {{ EVIDENCE_EXTRACTION_STATUS_LABELS[document.data.value.evidenceExtractionStatus] }}
          </dd>
        </div>
        <div>
          <dt>마지막 상태 변경</dt>
          <dd>{{ new Date(document.data.value.updatedAt).toLocaleString('ko-KR') }}</dd>
        </div>
      </dl>

      <section
        v-if="activeRunId || document.data.value.latestAgentRunId"
        class="document-section section-surface"
      >
        <div class="document-section__heading">
          <div>
            <p class="section-kicker">2. AI 분석</p>
            <h3 class="section-title">자료 분석 진행 상황</h3>
          </div>
          <RouterLink
            class="button button--secondary button--compact"
            :to="{
              name: 'agent-run-detail',
              params: { agentRunId: activeRunId || document.data.value.latestAgentRunId },
            }"
            >분석 과정 자세히 보기</RouterLink
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
        <div class="document-section__heading">
          <div>
            <p class="section-kicker">1. 원본 내용 확인</p>
            <h3 class="section-title">자료 미리보기</h3>
          </div>
          <button class="button button--ghost button--compact" type="button" @click="download">
            원본 파일 열기
          </button>
        </div>
        <DocumentPagePreview
          v-if="pageViewerSupported"
          :document-id="documentId"
          @unavailable="pageViewerUnavailable = true"
        />
        <template v-else>
          <p class="document-text__status">
            {{
              document.data.value.mimeType === 'application/pdf'
                ? '원본을 페이지로 보여 주지 못해 읽어 낸 내용을 대신 보여 드려요.'
                : 'PDF가 아닌 자료는 원본 대신 읽어 낸 내용을 보여 드려요. 원본은 위에서 열 수 있어요.'
            }}
          </p>
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
        </template>
      </section>

      <DocumentEvidencePanel
        :user-id="userId"
        :document-id="documentId"
        :document-name="document.data.value.displayName"
      />
    </template>
  </section>
</template>

<style scoped>
.document-detail__state,
.document-status-grid,
.document-overview,
.document-detail__message,
.document-metadata,
.document-section {
  margin-top: var(--space-5);
}

.document-overview {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-5);
}

.document-overview__icon {
  display: grid;
  width: 4rem;
  height: 4rem;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-brand-soft);
  color: var(--color-brand);
  font-size: 1.75rem;
}

.document-overview__details {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3) var(--space-5);
}

.document-overview__details dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.document-overview__details dd {
  margin-top: var(--space-1);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
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
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
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
  border: 0;
  border-radius: var(--radius-lg);
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

  .document-overview {
    grid-template-columns: 1fr;
  }
  .document-overview__details {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
