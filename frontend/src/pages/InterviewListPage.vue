<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  canonicalQuestionSetQuery,
  interviewQuerySignature,
  parseQuestionSetFilters,
  questionSetApiFilters,
} from '@/features/interviews/filters'
import {
  AGENT_RUN_STATUS_LABELS,
  RESEARCH_STATUS_LABELS,
  SOURCE_COVERAGE_LABELS,
  coverageTone,
  formatInterviewInstant,
  questionSetJobLabel,
} from '@/features/interviews/presentation'
import { useQuestionSetListQuery } from '@/features/interviews/queries'
import { normalizeApiError } from '@/shared/api/errors'
import {
  RESEARCH_RUN_STATUSES,
  SOURCE_COVERAGES,
  type ResearchRunStatus,
  type SourceCoverage,
} from '@/shared/api/interviewContracts'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseQuestionSetFilters(route.query))
const apiFilters = computed(() => questionSetApiFilters(filters.value))
const list = useQuestionSetListQuery(userId, apiFilters)

const search = ref('')
const sourceCoverage = ref<SourceCoverage | ''>('')
const researchStatus = ref<ResearchRunStatus | ''>('')

watch(
  filters,
  (value) => {
    search.value = value.query ?? ''
    sourceCoverage.value = value.sourceCoverage ?? ''
    researchStatus.value = value.researchStatus ?? ''
  },
  { immediate: true },
)

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalQuestionSetQuery(filters.value)
    if (interviewQuerySignature(query) !== interviewQuerySignature(canonical)) {
      void router.replace({ name: 'interviews', query: canonical })
    }
  },
  { immediate: true },
)

async function applyFilters(): Promise<void> {
  await router.replace({
    name: 'interviews',
    query: canonicalQuestionSetQuery({
      ...filters.value,
      query: normalizedSearch(),
      sourceCoverage: sourceCoverage.value || undefined,
      researchStatus: researchStatus.value || undefined,
      page: 0,
    }),
  })
}

async function updatePage(page: number): Promise<void> {
  await router.replace({
    name: 'interviews',
    query: canonicalQuestionSetQuery({ ...filters.value, page }),
  })
}

async function updateSort(event: Event): Promise<void> {
  const value = event.target instanceof HTMLSelectElement ? event.target.value : ''
  if (value !== 'updatedAt,desc' && value !== 'createdAt,desc') return
  await router.replace({
    name: 'interviews',
    query: canonicalQuestionSetQuery({ ...filters.value, sort: value, page: 0 }),
  })
}

function normalizedSearch(): string | undefined {
  const value = search.value.trim()
  return value.length > 0 ? value : undefined
}
</script>

<template>
  <section class="interview-list app-page" aria-labelledby="interview-list-heading">
    <PageHeader
      heading-id="interview-list-heading"
      title="면접 준비"
      description="공고별 회사 조사와 예상 질문 세트를 한곳에서 확인하세요."
      variant="list"
    />

    <form class="interview-list__filters filter-toolbar" @submit.prevent="applyFilters">
      <label class="field">
        <span class="field__label">회사·직무·제목 검색</span>
        <input
          v-model="search"
          class="control control--compact"
          type="search"
          maxlength="200"
          placeholder="회사, 직무, 질문 세트"
        />
      </label>
      <label class="field">
        <span class="field__label">출처 범위</span>
        <select v-model="sourceCoverage" class="control control--compact">
          <option value="">전체</option>
          <option v-for="value in SOURCE_COVERAGES" :key="value" :value="value">
            {{ SOURCE_COVERAGE_LABELS[value] }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">조사 상태</span>
        <select v-model="researchStatus" class="control control--compact">
          <option value="">전체</option>
          <option v-for="value in RESEARCH_RUN_STATUSES" :key="value" :value="value">
            {{ RESEARCH_STATUS_LABELS[value] }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">정렬</span>
        <select :value="filters.sort" class="control control--compact" @change="updateSort">
          <option value="updatedAt,desc">최근 수정순</option>
          <option value="createdAt,desc">최근 생성순</option>
        </select>
      </label>
      <button type="submit" class="button button--secondary">필터 적용</button>
    </form>

    <StatePanel
      v-if="list.isLoading.value"
      kind="loading"
      title="예상 질문 세트를 불러오는 중…"
      description="조사 상태와 출처 coverage를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="list.isError.value"
      kind="error"
      title="예상 질문 세트를 불러오지 못했어요."
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
      title="조건에 맞는 예상 질문 세트가 없어요."
      description="관심 공고의 면접 준비 탭에서 회사 조사와 질문 생성을 시작할 수 있어요."
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="{ name: 'jobs' }">
          관심 공고 보기
        </RouterLink>
      </template>
    </StatePanel>

    <div v-else-if="list.data.value" class="interview-list__items">
      <article
        v-for="item in list.data.value.items"
        :key="item.id"
        class="question-set-card"
        :data-testid="`question-set-row-${item.id}`"
      >
        <header>
          <div>
            <p class="page-eyebrow">{{ questionSetJobLabel(item.job) }}</p>
            <h2>{{ item.title }}</h2>
            <p>{{ item.coverLetter.title }}</p>
          </div>
          <div class="question-set-card__badges">
            <StatusBadge
              :label="AGENT_RUN_STATUS_LABELS[item.agentRun.status]"
              :tone="
                item.agentRun.status === 'SUCCEEDED'
                  ? 'success'
                  : item.agentRun.status === 'FAILED'
                    ? 'danger'
                    : item.agentRun.status === 'RUNNING'
                      ? 'info'
                      : 'neutral'
              "
            />
            <StatusBadge
              v-if="item.sourceCoverage"
              :label="SOURCE_COVERAGE_LABELS[item.sourceCoverage]"
              :tone="coverageTone(item.sourceCoverage)"
            />
          </div>
        </header>
        <dl>
          <div>
            <dt>예상 질문</dt>
            <dd>{{ item.questionCount }}개</dd>
          </div>
          <div>
            <dt>최근 갱신</dt>
            <dd>{{ formatInterviewInstant(item.updatedAt) }}</dd>
          </div>
          <div>
            <dt>연결 자기소개서</dt>
            <dd>{{ item.coverLetter.status === 'ARCHIVED' ? '보관됨' : '사용 가능' }}</dd>
          </div>
        </dl>
        <div class="question-set-card__actions">
          <RouterLink
            class="button button--primary"
            :to="{ name: 'interview-question-set', params: { questionSetId: item.id } }"
          >
            질문과 출처 보기
          </RouterLink>
          <RouterLink
            class="button button--secondary"
            :to="{ name: 'agent-run-detail', params: { agentRunId: item.agentRun.id } }"
          >
            AI 작업 상세
          </RouterLink>
        </div>
      </article>
    </div>

    <PaginationNav
      v-if="list.data.value && list.data.value.totalPages > 1"
      :page="list.data.value.page"
      :total-pages="list.data.value.totalPages"
      label="예상 질문 세트 목록 페이지"
      @change="updatePage"
    />
  </section>
</template>

<style scoped>
.interview-list {
  min-width: 0;
}

.interview-list__filters {
  display: grid;
  grid-template-columns:
    minmax(13rem, 1.4fr) minmax(9rem, 0.7fr) minmax(9rem, 0.7fr)
    minmax(9rem, 0.7fr) auto;
  align-items: end;
  gap: var(--space-3);
}

.interview-list__items {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-5);
}

.question-set-card {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-5);
  box-shadow: var(--shadow-sm);
}

.question-set-card header,
.question-set-card__badges,
.question-set-card__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.question-set-card h2 {
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
}

.question-set-card header p:last-child {
  margin-top: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.question-set-card__badges,
.question-set-card__actions {
  flex-wrap: wrap;
}

.question-set-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.question-set-card dl div {
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-3);
}

.question-set-card dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.question-set-card dd {
  margin-top: var(--space-1);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.question-set-card__actions {
  justify-content: flex-start;
  margin-top: var(--space-4);
}

@media (max-width: 72rem) {
  .interview-list__filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .interview-list__filters,
  .question-set-card dl {
    grid-template-columns: 1fr;
  }

  .question-set-card header {
    align-items: stretch;
    flex-direction: column;
  }

  .question-set-card__badges {
    justify-content: flex-start;
  }

  .question-set-card__actions .button {
    width: 100%;
  }
}
</style>
