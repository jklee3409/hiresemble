<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CoverLetterConflictPanel from '@/features/cover-letters/CoverLetterConflictPanel.vue'
import type { CoverLetterConflict } from '@/features/cover-letters/conflict'
import {
  canonicalCoverLetterQuery,
  coverLetterApiFilters,
  coverLetterQuerySignature,
  parseCoverLetterFilters,
} from '@/features/cover-letters/filters'
import {
  COVER_LETTER_STATUS_LABELS,
  VERIFICATION_STATUS_LABELS,
  coverLetterJobLabel,
  formatCoverLetterInstant,
} from '@/features/cover-letters/presentation'
import {
  useArchiveCoverLetterMutation,
  useCoverLetterListQuery,
  useUnarchiveCoverLetterMutation,
} from '@/features/cover-letters/queries'
import {
  COVER_LETTER_STATUSES,
  type CoverLetterStatus,
  type VerificationStatus,
} from '@/shared/api/coverLetterContracts'
import { COVER_LETTER_SORTS } from '@/shared/api/coverLetterApi'
import { normalizeApiError } from '@/shared/api/errors'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseCoverLetterFilters(route.query))
const list = useCoverLetterListQuery(
  userId,
  computed(() => coverLetterApiFilters(filters.value)),
)
const archiveMutation = useArchiveCoverLetterMutation(userId)
const unarchiveMutation = useUnarchiveCoverLetterMutation(userId)

const search = ref('')
const status = ref('')
const actionError = ref('')
const statusMessage = ref('')
const conflict = ref<CoverLetterConflict | null>(null)
const conflictTargetId = ref('')
const conflictAction = ref<'archive' | 'unarchive' | null>(null)

watch(
  filters,
  (value) => {
    search.value = value.query ?? ''
    status.value = value.status ?? ''
  },
  { immediate: true },
)

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalCoverLetterQuery(parseCoverLetterFilters(query))
    if (coverLetterQuerySignature(query) !== coverLetterQuerySignature(canonical)) {
      void router.replace({ query: canonical })
    }
  },
  { immediate: true },
)

function applyFilters(): void {
  const nextStatus = COVER_LETTER_STATUSES.find((value) => value === status.value)
  void router.push({
    query: canonicalCoverLetterQuery({
      ...filters.value,
      status: nextStatus,
      query: search.value.trim() || undefined,
      page: 0,
    }),
  })
}

function updateSort(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  const sort = COVER_LETTER_SORTS.find((candidate) => candidate === value) ?? 'updatedAt,desc'
  void router.push({
    query: canonicalCoverLetterQuery({ ...filters.value, sort, page: 0 }),
  })
}

function updatePage(page: number): void {
  void router.push({
    query: canonicalCoverLetterQuery({ ...filters.value, page }),
  })
}

async function archive(id: string, version: number, reapply = false): Promise<void> {
  actionError.value = ''
  statusMessage.value = ''
  try {
    await archiveMutation.mutateAsync({ coverLetterId: id, version })
    statusMessage.value = '자기소개서를 보관했어요. 과거 내용은 읽기 전용으로 유지됩니다.'
    conflict.value = null
  } catch (error) {
    await handleLifecycleConflict('archive', id, error, reapply)
  }
}

async function unarchive(id: string, version: number, reapply = false): Promise<void> {
  actionError.value = ''
  statusMessage.value = ''
  try {
    await unarchiveMutation.mutateAsync({ coverLetterId: id, version })
    statusMessage.value = '자기소개서를 DRAFT로 복구했어요.'
    conflict.value = null
  } catch (error) {
    await handleLifecycleConflict('unarchive', id, error, reapply)
  }
}

async function handleLifecycleConflict(
  action: 'archive' | 'unarchive',
  id: string,
  error: unknown,
  reapply: boolean,
): Promise<void> {
  const apiError = normalizeApiError(error)
  if (apiError.status !== 409) {
    actionError.value = apiError.message
    return
  }
  await list.refetch()
  const latest = list.data.value?.items.find((item) => item.id === id)
  conflictTargetId.value = id
  conflictAction.value = action
  conflict.value = {
    kind: apiError.code === 'ACTIVE_COVER_LETTER_EXISTS' ? 'ACTIVE_EXISTS' : 'LIFECYCLE',
    errorCode: apiError.code,
    serverSnapshot: latest
      ? `${COVER_LETTER_STATUS_LABELS[latest.status]} · version ${latest.version}`
      : '최신 목록에서 항목을 찾을 수 없음',
    localDraft: action === 'archive' ? '보관 요청' : 'DRAFT 복구 요청',
  }
  if (reapply) actionError.value = '최신 상태에서도 요청을 적용할 수 없어요.'
}

async function reapplyConflict(): Promise<void> {
  const item = list.data.value?.items.find((candidate) => candidate.id === conflictTargetId.value)
  if (!item || conflictAction.value === null) return
  if (conflictAction.value === 'archive') await archive(item.id, item.version, true)
  else await unarchive(item.id, item.version, true)
}

function statusTone(value: CoverLetterStatus): 'brand' | 'success' | 'neutral' {
  return ({ DRAFT: 'brand', FINALIZED: 'success', ARCHIVED: 'neutral' } as const)[value]
}

function verificationTone(value: VerificationStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  return ({ PENDING: 'neutral', PASSED: 'success', WARNING: 'warning', FAILED: 'danger' } as const)[
    value
  ]
}
</script>

<template>
  <section class="cover-list app-page" aria-labelledby="cover-list-heading">
    <PageHeader
      heading-id="cover-list-heading"
      title="자기소개서"
      description="공고별 자기소개서의 작성·검증·보관 상태를 한곳에서 관리하세요."
      eyebrow="지원 문서"
    />

    <form class="cover-list__filters filter-toolbar" @submit.prevent="applyFilters">
      <label class="field">
        <span class="field__label">회사·공고·제목 검색</span>
        <input
          v-model="search"
          class="control control--compact"
          type="search"
          maxlength="200"
          placeholder="회사, 직무, 자기소개서 제목"
        />
      </label>
      <label class="field">
        <span class="field__label">상태</span>
        <select v-model="status" class="control control--compact">
          <option value="">전체</option>
          <option v-for="value in COVER_LETTER_STATUSES" :key="value" :value="value">
            {{ COVER_LETTER_STATUS_LABELS[value] }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">정렬</span>
        <select :value="filters.sort" class="control control--compact" @change="updateSort">
          <option value="updatedAt,desc">최근 수정순</option>
          <option value="createdAt,desc">최근 생성순</option>
          <option value="title,asc">제목순</option>
        </select>
      </label>
      <button type="submit" class="button button--secondary">필터 적용</button>
    </form>

    <p v-if="statusMessage" class="cover-list__notice" role="status">{{ statusMessage }}</p>
    <p v-if="actionError" class="cover-list__error" role="alert">{{ actionError }}</p>
    <CoverLetterConflictPanel
      v-if="conflict"
      :conflict="conflict"
      :reapplying="archiveMutation.isPending.value || unarchiveMutation.isPending.value"
      @reapply="reapplyConflict"
      @cancel="conflict = null"
    />

    <StatePanel
      v-if="list.isLoading.value"
      kind="loading"
      title="자기소개서를 불러오는 중…"
      description="공고별 작성 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="list.isError.value"
      kind="error"
      title="자기소개서 목록을 불러오지 못했어요."
      :description="normalizeApiError(list.error.value).message"
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="list.refetch()">
          다시 불러오기
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="list.data.value?.items.length === 0"
      kind="empty"
      title="조건에 맞는 자기소개서가 없어요."
      description="관심 공고 상세의 자기소개서 탭에서 새 자기소개서를 만들 수 있어요."
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="{ name: 'jobs' }">
          관심 공고 보기
        </RouterLink>
      </template>
    </StatePanel>

    <div v-else-if="list.data.value" class="cover-list__items">
      <article
        v-for="item in list.data.value.items"
        :key="item.id"
        class="cover-card"
        :class="{ 'cover-card--archived': item.status === 'ARCHIVED' }"
        :data-testid="`cover-letter-row-${item.id}`"
      >
        <header class="cover-card__header">
          <div>
            <p class="page-eyebrow">{{ coverLetterJobLabel(item.job) }}</p>
            <h2>{{ item.title }}</h2>
          </div>
          <div class="cover-card__badges">
            <StatusBadge
              :label="COVER_LETTER_STATUS_LABELS[item.status]"
              :tone="statusTone(item.status)"
            />
            <StatusBadge
              v-if="item.latestVerificationStatus"
              :label="VERIFICATION_STATUS_LABELS[item.latestVerificationStatus]"
              :tone="verificationTone(item.latestVerificationStatus)"
            />
          </div>
        </header>

        <dl class="cover-card__metrics">
          <div>
            <dt>문항</dt>
            <dd>{{ item.questionCount }}개</dd>
          </div>
          <div>
            <dt>답변 완료</dt>
            <dd>{{ item.answeredQuestionCount }}/{{ item.questionCount }}</dd>
          </div>
          <div>
            <dt>경고</dt>
            <dd>{{ item.warningCount }}개</dd>
          </div>
          <div>
            <dt>최근 수정</dt>
            <dd>{{ formatCoverLetterInstant(item.updatedAt) }}</dd>
          </div>
        </dl>

        <p v-if="item.status === 'ARCHIVED'" class="cover-card__readonly">
          읽기 전용 · 과거 버전과 검증 기록은 계속 확인할 수 있어요.
        </p>
        <div class="cover-card__actions">
          <RouterLink
            class="button button--primary"
            :to="{ name: 'cover-letter-edit', params: { coverLetterId: item.id } }"
          >
            {{ item.status === 'ARCHIVED' ? '읽기 전용으로 열기' : '편집하기' }}
          </RouterLink>
          <button
            v-if="item.canArchive"
            type="button"
            class="button button--secondary"
            :disabled="archiveMutation.isPending.value"
            @click="archive(item.id, item.version)"
          >
            보관
          </button>
          <button
            v-if="item.canUnarchive"
            type="button"
            class="button button--secondary"
            :disabled="unarchiveMutation.isPending.value"
            @click="unarchive(item.id, item.version)"
          >
            DRAFT로 복구
          </button>
        </div>
      </article>
    </div>

    <PaginationNav
      v-if="list.data.value && list.data.value.totalPages > 1"
      :page="list.data.value.page"
      :total-pages="list.data.value.totalPages"
      label="자기소개서 목록 페이지"
      @change="updatePage"
    />
  </section>
</template>

<style scoped>
.cover-list {
  min-width: 0;
}

.cover-list__filters {
  display: grid;
  grid-template-columns: minmax(14rem, 1.5fr) minmax(10rem, 0.7fr) minmax(10rem, 0.8fr) auto;
  align-items: end;
}

.cover-list__notice,
.cover-list__error {
  margin-top: var(--space-4);
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
}

.cover-list__notice {
  background: var(--color-success-soft);
  color: var(--color-success-strong);
}

.cover-list__error {
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
}

.cover-list__items {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.cover-card {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-5);
  box-shadow: var(--shadow-sm);
}

.cover-card--archived {
  background: var(--color-surface-subtle);
}

.cover-card__header,
.cover-card__actions,
.cover-card__badges {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.cover-card h2 {
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
}

.cover-card__badges,
.cover-card__actions {
  flex-wrap: wrap;
}

.cover-card__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.cover-card__metrics div {
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.cover-card__metrics dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.cover-card__metrics dd {
  margin-top: var(--space-1);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.cover-card__readonly {
  margin-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.cover-card__actions {
  justify-content: flex-start;
  margin-top: var(--space-4);
}

@media (max-width: 64rem) {
  .cover-list__filters,
  .cover-card__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .cover-list__filters,
  .cover-card__metrics {
    grid-template-columns: 1fr;
  }

  .cover-card__header {
    align-items: stretch;
    flex-direction: column;
  }

  .cover-card__badges {
    justify-content: flex-start;
  }

  .cover-card__actions .button {
    width: 100%;
  }
}
</style>
