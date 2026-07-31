<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  canonicalJobQuery,
  deadlineFromInput,
  deadlineInputValue,
  deadlineToInput,
  jobFiltersForPage,
  jobFiltersForStatus,
  jobQuerySignature,
  parseJobFilters,
} from '@/features/jobs/filters'
import {
  JOB_EXTRACTION_STATUS_LABELS,
  JOB_STATUS_LABELS,
  formatJobInstant,
  jobCompanyLabel,
  jobDisplayTitle,
} from '@/features/jobs/presentation'
import { useJobListQuery, useUpdateJobStatusMutation } from '@/features/jobs/queries'
import {
  JOB_EXTRACTION_STATUSES,
  JOB_STATUSES,
  type JobExtractionStatus,
  type JobStatus,
} from '@/shared/api/jobContracts'
import { JOB_SORTS } from '@/shared/api/jobApi'
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
const filters = computed(() => parseJobFilters(route.query))
const jobs = useJobListQuery(userId, filters)
const statusMutation = useUpdateJobStatusMutation(userId)

const search = ref('')
const extractionStatus = ref('')
const deadlineFrom = ref('')
const deadlineTo = ref('')
const deadlineWithinDays = ref('')
const actionError = ref('')
const message = ref('')

watch(
  filters,
  (value) => {
    search.value = value.query ?? ''
    extractionStatus.value = value.extractionStatus ?? ''
    deadlineFrom.value = deadlineInputValue(value.deadlineFrom)
    deadlineTo.value = deadlineInputValue(value.deadlineTo)
    deadlineWithinDays.value =
      value.deadlineWithinDays === undefined ? '' : String(value.deadlineWithinDays)
  },
  { immediate: true },
)

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalJobQuery(parseJobFilters(query))
    if (jobQuerySignature(query) !== jobQuerySignature(canonical)) {
      void router.replace({ query: canonical })
    }
  },
  { immediate: true },
)

function selectTab(status: JobStatus): void {
  void router.push({ query: canonicalJobQuery(jobFiltersForStatus(filters.value, status)) })
}

function applyFilters(): void {
  const relative = Number(deadlineWithinDays.value)
  const within =
    deadlineWithinDays.value !== '' && Number.isInteger(relative) && relative >= 1 && relative <= 30
      ? relative
      : undefined
  const extraction = JOB_EXTRACTION_STATUSES.find((value) => value === extractionStatus.value) as
    JobExtractionStatus | undefined
  void router.push({
    query: canonicalJobQuery({
      ...filters.value,
      extractionStatus: extraction,
      query: search.value.trim() || undefined,
      deadlineFrom: within === undefined ? deadlineFromInput(deadlineFrom.value) : undefined,
      deadlineTo: within === undefined ? deadlineToInput(deadlineTo.value) : undefined,
      deadlineWithinDays: within,
      page: 0,
    }),
  })
}

function clearDeadline(): void {
  deadlineFrom.value = ''
  deadlineTo.value = ''
  deadlineWithinDays.value = ''
}

function updateSort(event: Event): void {
  const requested = (event.target as HTMLSelectElement).value
  const sort = JOB_SORTS.find((value) => value === requested) ?? 'createdAt,desc'
  void router.push({ query: canonicalJobQuery({ ...filters.value, sort, page: 0 }) })
}

function updatePage(page: number): void {
  void router.push({ query: canonicalJobQuery(jobFiltersForPage(filters.value, page)) })
}

async function changeStatus(
  jobId: string,
  version: number,
  currentStatus: JobStatus,
  event: Event,
): Promise<void> {
  const select = event.target as HTMLSelectElement
  const status = JOB_STATUSES.find((value) => value === select.value)
  if (status === undefined || status === currentStatus) return
  actionError.value = ''
  message.value = ''
  try {
    await statusMutation.mutateAsync({ jobId, version, status })
    message.value = `지원 상태를 ${JOB_STATUS_LABELS[status]}(으)로 변경했어요.`
  } catch (error) {
    const apiError = normalizeApiError(error)
    actionError.value =
      apiError.code === 'RESOURCE_VERSION_CONFLICT'
        ? '공고가 다른 곳에서 변경됐어요. 상세 화면에서 최신 내용과 비교해 다시 적용해 주세요.'
        : apiError.message
    select.value = currentStatus
  }
}

function businessTone(value: JobStatus): 'brand' | 'info' | 'neutral' {
  return (
    {
      IN_PROGRESS: 'brand',
      SUBMITTED: 'info',
      CLOSED: 'neutral',
    } as const
  )[value]
}

function extractionTone(
  value: JobExtractionStatus,
): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  return (
    {
      QUEUED: 'neutral',
      EXTRACTING: 'info',
      EXTRACTED: 'success',
      MANUAL_INPUT_PROVIDED: 'success',
      NEEDS_MANUAL_INPUT: 'warning',
      FAILED: 'danger',
    } as const
  )[value]
}
</script>

<template>
  <section class="jobs-page app-page" aria-labelledby="jobs-heading">
    <PageHeader
      heading-id="jobs-heading"
      title="관심 공고"
      description="관심 있는 공고를 모아 두고 지원 상태를 이어서 확인하세요."
      eyebrow="지원할 공고"
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="{ name: 'job-new' }">
          공고 등록
        </RouterLink>
      </template>
    </PageHeader>

    <div class="job-tabs" role="tablist" aria-label="공고 지원 상태">
      <button
        v-for="status in JOB_STATUSES"
        :key="status"
        type="button"
        role="tab"
        class="job-tab"
        :class="{ 'job-tab--active': filters.status === status }"
        :aria-selected="filters.status === status"
        @click="selectTab(status)"
      >
        {{ JOB_STATUS_LABELS[status] }}
      </button>
    </div>

    <details class="filter-disclosure jobs-page__filters" open>
      <summary>공고 검색·필터</summary>
      <form class="filter-toolbar job-filters" @submit.prevent="applyFilters">
        <label class="field job-filters__search">
          <span class="field__label">회사·직무 검색</span>
          <input
            v-model="search"
            type="search"
            maxlength="200"
            class="control control--compact"
            placeholder="회사명, 공고 제목, 직무명"
          />
        </label>
        <label class="field">
          <span class="field__label">공고 불러오기 상태</span>
          <select v-model="extractionStatus" class="control control--compact">
            <option value="">전체</option>
            <option v-for="status in JOB_EXTRACTION_STATUSES" :key="status" :value="status">
              {{ JOB_EXTRACTION_STATUS_LABELS[status] }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">정렬</span>
          <select :value="filters.sort" class="control control--compact" @change="updateSort">
            <option value="createdAt,desc">최근 등록순</option>
            <option value="deadlineAt,asc">마감 임박순</option>
            <option value="updatedAt,desc">최근 수정순</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">마감 시작</span>
          <input
            v-model="deadlineFrom"
            type="date"
            class="control control--compact"
            :disabled="deadlineWithinDays !== ''"
          />
        </label>
        <label class="field">
          <span class="field__label">마감 종료</span>
          <input
            v-model="deadlineTo"
            type="date"
            class="control control--compact"
            :disabled="deadlineWithinDays !== ''"
          />
        </label>
        <label class="field">
          <span class="field__label">마감 임박</span>
          <select
            v-model="deadlineWithinDays"
            class="control control--compact"
            :disabled="deadlineFrom !== '' || deadlineTo !== ''"
          >
            <option value="">사용 안 함</option>
            <option value="3">3일 이내</option>
            <option value="7">7일 이내</option>
            <option value="14">14일 이내</option>
            <option value="30">30일 이내</option>
          </select>
        </label>
        <div class="job-filters__actions">
          <button type="submit" class="button button--primary button--compact">필터 적용</button>
          <button type="button" class="button button--ghost button--compact" @click="clearDeadline">
            마감 초기화
          </button>
        </div>
      </form>
    </details>

    <p v-if="message" class="alert alert--success jobs-page__message" role="status">
      {{ message }}
    </p>
    <p v-if="actionError" class="alert alert--danger jobs-page__message" role="alert">
      {{ actionError }}
    </p>

    <StatePanel
      v-if="jobs.isPending.value"
      class="jobs-page__state"
      kind="loading"
      title="공고 목록을 불러오는 중…"
      description="지원 상태와 공고 불러오기 상태를 확인하고 있어요."
    />
    <StatePanel
      v-else-if="jobs.isError.value"
      class="jobs-page__state"
      kind="error"
      title="공고를 불러오지 못했어요."
      description="잠시 후 다시 시도해 주세요."
    >
      <template #actions>
        <button type="button" class="button button--secondary" @click="jobs.refetch()">
          다시 시도
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="jobs.data.value?.items.length === 0"
      class="jobs-page__state"
      kind="empty"
      title="조건에 맞는 공고가 없어요."
      description="필터를 바꾸거나 관심 있는 공고를 새로 등록해 주세요."
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="{ name: 'job-new' }">공고 등록</RouterLink>
      </template>
    </StatePanel>
    <ul v-else class="job-list data-list">
      <li v-for="job in jobs.data.value?.items" :key="job.id" class="job-row data-card">
        <div class="job-row__content">
          <div class="job-row__identity">
            <p class="job-row__company">{{ jobCompanyLabel(job.companyName) }}</p>
            <RouterLink
              class="job-row__title"
              :to="{ name: 'job-overview', params: { jobId: job.id } }"
            >
              {{ jobDisplayTitle(job) }}
            </RouterLink>
            <p v-if="job.title && job.positionName" class="job-row__position">
              직무: {{ job.positionName }}
            </p>
            <p class="job-row__deadline">마감 {{ formatJobInstant(job.deadlineAt) }}</p>
            <div class="job-row__statuses">
              <StatusBadge
                data-testid="job-business-status"
                prefix="지원"
                :label="JOB_STATUS_LABELS[job.status]"
                :tone="businessTone(job.status)"
              />
              <StatusBadge
                data-testid="job-extraction-status"
                prefix="공고"
                :label="JOB_EXTRACTION_STATUS_LABELS[job.extractionStatus]"
                :tone="extractionTone(job.extractionStatus)"
              />
              <StatusBadge
                v-if="job.status === 'CLOSED' && job.submittedAt"
                label="서류 제출 이력 있음"
                tone="success"
              />
            </div>
          </div>
          <label class="field job-row__status-control">
            <span class="field__label">상태 변경</span>
            <select
              :value="job.status"
              class="control control--compact"
              :disabled="statusMutation.isPending.value"
              :aria-label="`${jobDisplayTitle(job)} 지원 상태 변경`"
              @change="changeStatus(job.id, job.version, job.status, $event)"
            >
              <option v-for="status in JOB_STATUSES" :key="status" :value="status">
                {{ JOB_STATUS_LABELS[status] }}
              </option>
            </select>
          </label>
        </div>
      </li>
    </ul>

    <PaginationNav
      v-if="jobs.data.value && jobs.data.value.totalPages > 0"
      :page="filters.page"
      :total-pages="jobs.data.value.totalPages"
      label="공고 페이지"
      @change="updatePage"
    />
  </section>
</template>

<style scoped>
.job-tabs,
.jobs-page__filters,
.jobs-page__message,
.jobs-page__state,
.job-list {
  margin-top: var(--space-5);
}

.job-tabs {
  display: inline-flex;
  max-width: 100%;
  overflow-x: auto;
  padding: 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.job-tab {
  min-height: 2.75rem;
  padding: 0 var(--space-4);
  border-radius: calc(var(--radius-sm) - 2px);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  white-space: nowrap;
}

.job-tab:hover {
  background: var(--color-surface-subtle);
  color: var(--color-text);
}

.job-tab--active {
  background: var(--color-brand);
  color: white;
}

.job-tab--active:hover {
  background: var(--color-brand-hover);
  color: white;
}

.job-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: end;
  gap: var(--space-3);
}

.jobs-page__filters > .job-filters {
  margin-top: var(--space-5);
}

.job-filters__search {
  grid-column: span 2;
}

.job-filters__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.job-row {
  padding: var(--space-5);
}

.job-row__content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
}

.job-row__identity {
  min-width: 0;
}

.job-row__company,
.job-row__position,
.job-row__deadline {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.job-row__company {
  font-weight: 650;
}

.job-row__title {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-brand-strong);
  font-size: var(--font-size-lg);
  font-weight: 750;
  overflow-wrap: anywhere;
}

.job-row__position {
  margin-top: var(--space-1);
}

.job-row__statuses {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.job-row__deadline {
  margin-top: var(--space-3);
}

.job-row__status-control {
  width: 9.5rem;
  flex: 0 0 auto;
}

@media (max-width: 64rem) {
  .job-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .job-filters {
    grid-template-columns: 1fr;
  }

  .job-filters__search {
    grid-column: auto;
  }

  .job-row__content {
    flex-direction: column;
  }

  .job-row__status-control {
    width: 100%;
  }
}
</style>
