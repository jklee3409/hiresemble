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
</script>

<template>
  <section class="mt-6 rounded-xl bg-white p-5" aria-labelledby="document-evidence-heading">
    <h3 id="document-evidence-heading" class="font-semibold">문서 근거</h3>
    <p class="mt-1 text-sm text-slate-600">추출 후보는 PENDING이며 자동 승인되지 않습니다.</p>
    <p v-if="message" class="mt-3 text-sm text-emerald-700" role="status">{{ message }}</p>
    <p v-if="actionError" class="mt-3 text-sm text-red-700" role="alert">{{ actionError }}</p>
    <p v-if="evidence.isPending.value" class="mt-4">근거를 불러오는 중…</p>
    <p v-else-if="evidence.isError.value" class="mt-4 text-red-700" role="alert">
      문서 근거를 불러오지 못했습니다.
    </p>
    <p v-else-if="evidence.data.value?.items.length === 0" class="mt-4 text-sm text-slate-600">
      추출된 근거가 없습니다.
    </p>
    <ul v-else class="mt-4 space-y-4">
      <li
        v-for="item in evidence.data.value?.items"
        :key="item.id"
        class="rounded-lg border border-slate-200 p-4"
      >
        <template v-if="editingId === item.id">
          <form class="space-y-3" @submit.prevent="save(item)">
            <label class="block text-sm font-medium"
              >제목<input
                v-model="edit.title"
                class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                maxlength="250" /></label
            ><label class="block text-sm font-medium"
              >내용<textarea
                v-model="edit.content"
                class="mt-1 min-h-32 w-full rounded-lg border border-slate-300 px-3 py-2"
                maxlength="20000"
              />
            </label>
            <div class="flex gap-2">
              <button
                class="rounded-lg bg-indigo-700 px-3 py-2 text-sm font-semibold text-white"
                type="submit"
              >
                저장</button
              ><button
                class="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                type="button"
                @click="editingId = ''"
              >
                취소
              </button>
            </div>
          </form>
        </template>
        <template v-else>
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h4 class="font-medium">{{ item.title }}</h4>
              <p class="mt-1 text-xs text-slate-500">
                {{ item.evidenceCategory }} · {{ statusLabel(item.verificationStatus) }}
              </p>
            </div>
            <div class="flex gap-2">
              <button
                class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
                type="button"
                :disabled="item.verificationStatus === 'SOURCE_DELETED'"
                @click="openEdit(item)"
              >
                수정</button
              ><button
                class="rounded-lg border border-emerald-300 px-3 py-1.5 text-sm text-emerald-800 disabled:opacity-40"
                type="button"
                :disabled="
                  item.verificationStatus === 'SOURCE_DELETED' || verifyMutation.isPending.value
                "
                @click="verify(item, 'VERIFIED')"
              >
                승인</button
              ><button
                class="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 disabled:opacity-40"
                type="button"
                :disabled="
                  item.verificationStatus === 'SOURCE_DELETED' || verifyMutation.isPending.value
                "
                @click="verify(item, 'REJECTED')"
              >
                거절
              </button>
            </div>
          </div>
          <p class="mt-3 whitespace-pre-wrap text-sm">{{ item.content }}</p>
          <p
            v-if="item.verificationStatus === 'SOURCE_DELETED'"
            class="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
          >
            원본이 삭제된 tombstone은 읽기 전용입니다.
          </p>
        </template>
      </li>
    </ul>
  </section>
</template>
