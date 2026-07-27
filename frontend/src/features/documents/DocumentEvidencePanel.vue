<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'

import { profileQueryKeys } from '@/features/profile/queryKeys'
import type {
  EvidenceDto,
  EvidenceUpdateRequest,
  EvidenceVerificationStatus,
} from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'

const props = defineProps<{ userId: string; documentId: string }>()
const filters = computed<profileApi.EvidenceListParams>(() => ({
  documentId: props.documentId,
  page: 0,
  size: 100,
  sort: 'updatedAt,desc',
}))
const evidence = useQuery({
  queryKey: computed(() => profileQueryKeys.evidence(props.userId, filters.value)),
  queryFn: () => profileApi.listEvidence(filters.value),
  enabled: computed(() => props.userId !== '' && props.documentId !== ''),
})
const editingId = ref('')
const edit = reactive({ title: '', content: '', version: 0 })
const actionError = ref('')
const message = ref('')

const editMutation = useMutation({
  mutationFn: (input: { id: string; request: EvidenceUpdateRequest }) =>
    profileApi.updateEvidence(input.id, input.request),
})
const verifyMutation = useMutation({
  mutationFn: (input: {
    item: EvidenceDto
    status: Extract<EvidenceVerificationStatus, 'VERIFIED' | 'REJECTED'>
  }) =>
    profileApi.verifyEvidence(input.item.id, { status: input.status, version: input.item.version }),
})

function openEdit(item: EvidenceDto): void {
  if (item.verificationStatus === 'SOURCE_DELETED') return
  editingId.value = item.id
  edit.title = item.title
  edit.content = item.content
  edit.version = item.version
  actionError.value = ''
}

async function save(item: EvidenceDto): Promise<void> {
  const title = edit.title.trim()
  const content = edit.content.trim()
  if (title.length < 1 || title.length > 250 || content.length < 1 || content.length > 20_000) {
    actionError.value = '제목 1~250자와 내용 1~20,000자를 확인해 주세요.'
    return
  }
  try {
    await editMutation.mutateAsync({
      id: item.id,
      request: { title, content, metadata: item.metadata, version: edit.version },
    })
    editingId.value = ''
    message.value = '근거를 수정했습니다.'
    await evidence.refetch()
  } catch (error) {
    const apiError = normalizeApiError(error)
    actionError.value =
      apiError.code === 'EVIDENCE_SOURCE_DELETED'
        ? '원본이 삭제된 근거는 읽기만 할 수 있습니다.'
        : apiError.message
    await evidence.refetch()
  }
}

async function verify(
  item: EvidenceDto,
  status: Extract<EvidenceVerificationStatus, 'VERIFIED' | 'REJECTED'>,
): Promise<void> {
  if (item.verificationStatus === 'SOURCE_DELETED') return
  try {
    await verifyMutation.mutateAsync({ item, status })
    message.value = status === 'VERIFIED' ? '근거를 승인했습니다.' : '근거를 거절했습니다.'
    await evidence.refetch()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
    await evidence.refetch()
  }
}

function statusLabel(status: EvidenceVerificationStatus): string {
  return {
    PENDING: '검토 대기',
    VERIFIED: '승인됨',
    REJECTED: '거절됨',
    SOURCE_DELETED: '원본 삭제됨',
  }[status]
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
</script>

<template>
  <section class="document-evidence section-surface" aria-labelledby="document-evidence-heading">
    <p class="section-kicker">Evidence</p>
    <h3 id="document-evidence-heading" class="section-title">추출 근거</h3>
    <p class="document-evidence__description">
      추출 후보는 검토 대기 상태이며 자동으로 승인되지 않습니다.
    </p>
    <p v-if="message" class="alert alert--success document-evidence__message" role="status">
      {{ message }}
    </p>
    <p v-if="actionError" class="alert alert--danger document-evidence__message" role="alert">
      {{ actionError }}
    </p>
    <StatePanel
      v-if="evidence.isPending.value"
      class="document-evidence__state"
      kind="loading"
      title="근거를 불러오는 중…"
    />
    <StatePanel
      v-else-if="evidence.isError.value"
      class="document-evidence__state"
      kind="error"
      title="문서 근거를 불러오지 못했습니다."
    />
    <StatePanel
      v-else-if="evidence.data.value?.items.length === 0"
      class="document-evidence__state"
      kind="empty"
      title="추출된 근거가 없습니다."
      description="근거 추출이 완료되면 검토할 항목이 이곳에 표시됩니다."
    />
    <ul v-else class="document-evidence__list data-list">
      <li v-for="item in evidence.data.value?.items" :key="item.id" class="document-evidence-card">
        <template v-if="editingId === item.id">
          <form class="document-evidence-editor" @submit.prevent="save(item)">
            <label class="field">
              <span class="field__label">제목</span>
              <input v-model="edit.title" class="control" maxlength="250" />
            </label>
            <label class="field">
              <span class="field__label">내용</span>
              <textarea
                v-model="edit.content"
                class="control document-evidence-editor__content"
                maxlength="20000"
              />
            </label>
            <div class="form-actions">
              <button class="button button--primary button--compact" type="submit">저장</button>
              <button
                class="button button--secondary button--compact"
                type="button"
                @click="editingId = ''"
              >
                취소
              </button>
            </div>
          </form>
        </template>
        <template v-else>
          <div class="document-evidence-card__header">
            <div class="document-evidence-card__identity">
              <div class="document-evidence-card__title">
                <h4>{{ item.title }}</h4>
                <StatusBadge
                  :label="statusLabel(item.verificationStatus)"
                  :tone="statusTone(item.verificationStatus)"
                />
              </div>
              <p>{{ item.evidenceCategory }}</p>
            </div>
            <div
              v-if="item.verificationStatus !== 'SOURCE_DELETED'"
              class="document-evidence-card__actions"
            >
              <button
                class="button button--ghost button--compact"
                type="button"
                @click="openEdit(item)"
              >
                수정</button
              ><button
                class="button button--secondary button--compact"
                type="button"
                :disabled="verifyMutation.isPending.value"
                @click="verify(item, 'VERIFIED')"
              >
                승인</button
              ><button
                class="button button--danger button--compact"
                type="button"
                :disabled="verifyMutation.isPending.value"
                @click="verify(item, 'REJECTED')"
              >
                거절
              </button>
            </div>
          </div>
          <p class="document-evidence-card__content">{{ item.content }}</p>
          <p
            v-if="item.verificationStatus === 'SOURCE_DELETED'"
            class="alert alert--warning document-evidence-card__readonly"
          >
            원본이 삭제된 근거는 읽기 전용입니다. 수정·승인·거절할 수 없습니다.
          </p>
        </template>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.document-evidence {
  margin-top: var(--space-5);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.document-evidence__description {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.document-evidence__message,
.document-evidence__state,
.document-evidence__list {
  margin-top: var(--space-4);
}

.document-evidence-card {
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-border);
}

.document-evidence-card:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.document-evidence-card__header,
.document-evidence-card__title,
.document-evidence-card__actions {
  display: flex;
  align-items: center;
}

.document-evidence-card__header {
  justify-content: space-between;
  gap: var(--space-4);
}

.document-evidence-card__title,
.document-evidence-card__actions {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.document-evidence-card__title h4 {
  font-weight: 700;
}

.document-evidence-card__identity > p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.document-evidence-card__content {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.document-evidence-card__readonly {
  margin-top: var(--space-3);
}

.document-evidence-editor {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-brand-border);
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
}

.document-evidence-editor__content {
  min-height: 9rem;
}

@media (max-width: 40rem) {
  .document-evidence-card__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
