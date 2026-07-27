<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useDocumentListQuery } from '@/features/documents/queries'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
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

const editForm = reactive({ title: '', content: '', metadata: '{}', version: 0 })
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
    metadata: JSON.stringify(evidence.metadata, null, 2),
    version: evidence.version,
  })
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
  if (title.length < 1 || title.length > 250) fieldErrors.value.title = '제목은 1~250자여야 합니다.'
  if (content.length < 1 || content.length > 20000)
    fieldErrors.value.content = '내용은 1~20,000자여야 합니다.'
  const metadata = parseMetadata(editForm.metadata)
  if (metadata === null) fieldErrors.value.metadata = '16KiB 이하의 JSON object를 입력해 주세요.'
  if (Object.keys(fieldErrors.value).length > 0 || metadata === null) return

  const request: EvidenceUpdateRequest = { title, content, metadata, version: editForm.version }
  try {
    await editMutation.mutateAsync({ id: editingId.value, request })
    await evidenceQuery.refetch()
    editingId.value = null
    message.value = '직접 입력 근거를 저장했습니다.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    fieldErrors.value = fieldErrorsToRecord(apiError.fieldErrors)
    if (apiError.code === 'EVIDENCE_SOURCE_DELETED') {
      await evidenceQuery.refetch()
      editingId.value = null
      generalError.value = '원본이 삭제된 근거는 읽기만 할 수 있습니다.'
      return
    }
    if (isVersionConflict(apiError)) {
      const refreshed = await evidenceQuery.refetch()
      const latest = refreshed.data?.items.find((item) => item.id === editingId.value)
      if (latest !== undefined) {
        conflict.value = { draft: { ...request }, latest }
        generalError.value = '최신 근거와 내 입력을 비교해 다시 적용해 주세요.'
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
    message.value = nextStatus === 'VERIFIED' ? '근거를 승인했습니다.' : '근거를 거절했습니다.'
  } catch (error) {
    const apiError = normalizeApiError(error)
    await evidenceQuery.refetch()
    generalError.value = isVersionConflict(apiError)
      ? '근거 상태가 변경되어 최신 목록을 불러왔습니다. 확인 후 다시 시도해 주세요.'
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
  editForm.metadata = JSON.stringify(value.metadata ?? latest.metadata, null, 2)
  editForm.version = latest.version
  conflict.value = null
  message.value = '선택한 내 입력을 최신값에 재적용했습니다. 확인 후 다시 저장해 주세요.'
}

function parseMetadata(value: string): Record<string, EvidenceMetadataValue> | null {
  try {
    if (new TextEncoder().encode(value).byteLength > 16 * 1024) return null
    const parsed: unknown = JSON.parse(value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null
    const entries = Object.entries(parsed)
    if (
      entries.some(
        ([, item]) => item !== null && !['string', 'number', 'boolean'].includes(typeof item),
      )
    ) {
      return null
    }
    return Object.fromEntries(entries) as Record<string, EvidenceMetadataValue>
  } catch {
    return null
  }
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
      title="직접 입력 근거"
      description="프로필과 문서에서 생성된 경력 근거를 검토하고 상태를 관리합니다."
      eyebrow="Profile evidence"
    />
    <p class="alert alert--info evidence-page__guidance">
      직접 입력 근거와 문서에서 추출된 근거를 함께 검토합니다. 삭제된 원천은 읽기 전용입니다.
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
      출처 문서 목록을 불러오지 못했습니다.
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
      aria-label="근거 편집"
    >
      <div class="evidence-editor__header">
        <div>
          <p class="section-kicker">Evidence editor</p>
          <h3 class="section-title">직접 입력 근거 편집</h3>
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
          { key: 'metadata', label: 'Metadata' },
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
        <label class="field">
          <span class="field__label">Metadata JSON</span>
          <textarea
            id="evidence-metadata"
            v-model="editForm.metadata"
            class="control evidence-editor__metadata"
            :aria-invalid="Boolean(fieldErrors.metadata)"
            :aria-describedby="fieldErrors.metadata ? 'evidence-metadata-error' : undefined"
          />
          <span v-if="fieldErrors.metadata" id="evidence-metadata-error" class="inline-error">{{
            fieldErrors.metadata
          }}</span>
        </label>
        <div class="form-actions">
          <button
            type="submit"
            class="button button--primary"
            :disabled="editMutation.isPending.value"
          >
            {{ editMutation.isPending.value ? '저장 중…' : '근거 저장' }}
          </button>
          <button type="button" class="button button--secondary" @click="closeEdit">취소</button>
        </div>
      </form>
    </section>

    <StatePanel
      v-if="evidenceQuery.isPending.value"
      class="evidence-page__state"
      kind="loading"
      title="근거를 불러오는 중…"
      description="저장된 출처와 검토 상태를 확인하고 있습니다."
    />
    <StatePanel
      v-else-if="evidenceQuery.isError.value"
      class="evidence-page__state"
      kind="error"
      title="근거를 불러오지 못했습니다."
      description="연결 상태를 확인한 뒤 다시 시도해 주세요."
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
      title="조건에 맞는 직접 입력 근거가 없습니다."
      description="필터를 조정하거나 프로필·문서에서 근거를 추가해 주세요."
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
          원본이 삭제되어 읽기 전용입니다. 수정·승인·거절할 수 없습니다.
        </p>
      </li>
    </ul>

    <PaginationNav
      v-if="evidenceQuery.data.value && evidenceQuery.data.value.totalPages > 0"
      :page="page"
      :total-pages="evidenceQuery.data.value.totalPages"
      label="근거 페이지"
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
  min-height: 7rem;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: var(--font-size-sm);
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
