<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useDocumentListQuery } from '@/features/documents/queries'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import VersionConflictPanel from '@/features/profile/VersionConflictPanel.vue'
import { isVersionConflict } from '@/features/profile/conflict'
import { profileQueryKeys } from '@/features/profile/queryKeys'
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
  return {
    EDUCATION: '학력',
    CERTIFICATION: '자격증',
    LANGUAGE_SCORE: '어학',
    AWARD: '수상',
    CAREER: '경력',
    DOCUMENT_CHUNK: '문서',
    MANUAL: '수동',
  }[value]
}
</script>

<template>
  <section aria-labelledby="evidence-heading">
    <ProfileTabs />
    <h2 id="evidence-heading" class="text-2xl font-bold">직접 입력 근거</h2>
    <p class="mt-2 text-slate-600">구조화 프로필에서 동기화된 근거를 검토하고 편집합니다.</p>
    <p class="mt-4 rounded-lg bg-sky-50 p-3 text-sm text-sky-900">
      직접 입력 근거와 문서에서 추출된 근거를 함께 검토합니다. 삭제된 원천은 읽기 전용입니다.
    </p>

    <form
      class="mt-5 flex flex-wrap items-end gap-3 rounded-xl bg-white p-4"
      @submit.prevent="applyFilters"
    >
      <label class="text-sm font-medium"
        >상태<select
          v-model="status"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="">전체</option>
          <option value="PENDING">PENDING</option>
          <option value="VERIFIED">VERIFIED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="SOURCE_DELETED">SOURCE_DELETED</option>
        </select></label
      >
      <label class="text-sm font-medium"
        >카테고리<input
          v-model="category"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
          maxlength="80"
      /></label>
      <label class="text-sm font-medium"
        >정렬<select
          v-model="sort"
          class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
          @change="applyFilters"
        >
          <option value="updatedAt,desc">최근 수정순</option>
          <option value="confidence,desc">신뢰도순</option>
        </select></label
      >
      <button type="submit" class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white">
        필터 적용
      </button>
      <label class="text-sm font-medium"
        >출처 문서<select
          id="evidence-document-filter"
          v-model="documentId"
          class="mt-1 block max-w-72 rounded-lg border border-slate-300 px-3 py-2"
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
        </select></label
      >
    </form>
    <p v-if="documents.isError.value" class="mt-1 text-xs text-red-700" role="alert">
      출처 문서 목록을 불러오지 못했습니다.
    </p>

    <p v-if="message" class="mt-4 text-sm text-emerald-700" role="status">{{ message }}</p>
    <p v-if="generalError" class="mt-4 text-sm text-red-700" role="alert">{{ generalError }}</p>

    <section
      v-if="editingId"
      class="mt-6 rounded-2xl border border-slate-200 bg-white p-6"
      role="dialog"
      aria-label="근거 편집"
    >
      <h3 class="text-lg font-semibold">직접 입력 근거 편집</h3>
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
      <form class="mt-4 space-y-4" novalidate @submit.prevent="saveEdit">
        <label class="block text-sm font-medium"
          >제목<input
            id="evidence-title"
            v-model="editForm.title"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            maxlength="250"
          /><span v-if="fieldErrors.title" class="mt-1 block text-red-700">{{
            fieldErrors.title
          }}</span></label
        >
        <label class="block text-sm font-medium"
          >내용<textarea
            id="evidence-content"
            v-model="editForm.content"
            class="mt-1 min-h-36 w-full rounded-lg border border-slate-300 px-3 py-2"
            maxlength="20000"
          /><span v-if="fieldErrors.content" class="mt-1 block text-red-700">{{
            fieldErrors.content
          }}</span></label
        >
        <label class="block text-sm font-medium"
          >Metadata JSON<textarea
            id="evidence-metadata"
            v-model="editForm.metadata"
            class="mt-1 min-h-28 w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm"
          /><span v-if="fieldErrors.metadata" class="mt-1 block text-red-700">{{
            fieldErrors.metadata
          }}</span></label
        >
        <div class="flex gap-2">
          <button
            type="submit"
            class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white disabled:opacity-50"
            :disabled="editMutation.isPending.value"
          >
            {{ editMutation.isPending.value ? '저장 중…' : '근거 저장' }}</button
          ><button
            type="button"
            class="rounded-lg border border-slate-300 px-4 py-2"
            @click="closeEdit"
          >
            취소
          </button>
        </div>
      </form>
    </section>

    <p v-if="evidenceQuery.isPending.value" class="mt-8" aria-live="polite">근거를 불러오는 중…</p>
    <div
      v-else-if="evidenceQuery.isError.value"
      class="mt-8 rounded-xl bg-red-50 p-4 text-red-800"
      role="alert"
    >
      근거를 불러오지 못했습니다.
      <button type="button" class="underline" @click="evidenceQuery.refetch()">다시 시도</button>
    </div>
    <div
      v-else-if="evidenceQuery.data.value?.items.length === 0"
      class="mt-8 rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600"
    >
      조건에 맞는 직접 입력 근거가 없습니다.
    </div>
    <ul v-else class="mt-8 space-y-4">
      <li
        v-for="evidence in evidenceQuery.data.value?.items"
        :key="evidence.id"
        class="rounded-2xl bg-white p-5 shadow-sm"
      >
        <div class="flex flex-wrap justify-between gap-3">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="font-semibold">{{ evidence.title }}</h3>
              <span class="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold">{{
                statusLabel(evidence.verificationStatus)
              }}</span>
            </div>
            <p class="mt-1 text-xs text-slate-500">
              {{ sourceLabel(evidence.sourceType) }} · {{ evidence.evidenceCategory }}
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="evidence.verificationStatus === 'SOURCE_DELETED'"
              @click="openEdit(evidence)"
            >
              수정
            </button>
            <button
              type="button"
              class="rounded-lg border border-emerald-300 px-3 py-1.5 text-sm text-emerald-800 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="
                evidence.verificationStatus === 'SOURCE_DELETED' ||
                verificationMutation.isPending.value
              "
              @click="verify(evidence, 'VERIFIED')"
            >
              승인
            </button>
            <button
              type="button"
              class="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="
                evidence.verificationStatus === 'SOURCE_DELETED' ||
                verificationMutation.isPending.value
              "
              @click="verify(evidence, 'REJECTED')"
            >
              거절
            </button>
          </div>
        </div>
        <p class="mt-3 whitespace-pre-wrap text-sm text-slate-700">{{ evidence.content }}</p>
        <p
          v-if="evidence.verificationStatus === 'SOURCE_DELETED'"
          class="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
        >
          원본이 삭제되어 읽기 전용입니다. 수정·승인·거절할 수 없습니다.
        </p>
      </li>
    </ul>

    <nav
      v-if="evidenceQuery.data.value && evidenceQuery.data.value.totalPages > 0"
      class="mt-6 flex items-center justify-between"
      aria-label="근거 페이지"
    >
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="page === 0"
        @click="page -= 1"
      >
        이전</button
      ><span class="text-sm">{{ page + 1 }} / {{ evidenceQuery.data.value.totalPages }} 페이지</span
      ><button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="page + 1 >= evidenceQuery.data.value.totalPages"
        @click="page += 1"
      >
        다음
      </button>
    </nav>
  </section>
</template>
