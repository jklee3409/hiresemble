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
    message.value = `공고 상태를 ${JOB_STATUS_LABELS[status]}(으)로 변경했습니다.`
  } catch (error) {
    const apiError = normalizeApiError(error)
    actionError.value =
      apiError.code === 'RESOURCE_VERSION_CONFLICT'
        ? '공고가 다른 곳에서 변경되었습니다. 상세 화면에서 최신값을 비교해 다시 적용해 주세요.'
        : apiError.message
    select.value = currentStatus
  }
}
</script>

<template>
  <section aria-labelledby="jobs-heading">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 id="jobs-heading" class="text-2xl font-bold">채용 공고</h2>
        <p class="mt-2 text-slate-600">지원 업무 상태와 URL 추출 상태를 분리해 확인합니다.</p>
      </div>
      <RouterLink
        class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white"
        :to="{ name: 'job-new' }"
      >
        공고 등록
      </RouterLink>
    </div>

    <div class="mt-6 flex flex-wrap gap-2" role="tablist" aria-label="공고 업무 상태">
      <button
        v-for="status in JOB_STATUSES"
        :key="status"
        type="button"
        role="tab"
        class="rounded-full border px-4 py-2 text-sm font-semibold"
        :class="
          filters.status === status
            ? 'border-indigo-700 bg-indigo-700 text-white'
            : 'border-slate-300 bg-white text-slate-700'
        "
        :aria-selected="filters.status === status"
        @click="selectTab(status)"
      >
        {{ JOB_STATUS_LABELS[status] }}
      </button>
    </div>

    <form
      class="mt-5 grid gap-4 rounded-2xl bg-white p-5 shadow-sm md:grid-cols-2 xl:grid-cols-4"
      @submit.prevent="applyFilters"
    >
      <label class="text-sm font-medium xl:col-span-2">
        회사·직무 검색
        <input
          v-model="search"
          type="search"
          maxlength="200"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          placeholder="회사명, 공고 제목, 직무명"
        />
      </label>
      <label class="text-sm font-medium">
        URL 추출 상태
        <select
          v-model="extractionStatus"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
        >
          <option value="">전체</option>
          <option v-for="status in JOB_EXTRACTION_STATUSES" :key="status" :value="status">
            {{ JOB_EXTRACTION_STATUS_LABELS[status] }}
          </option>
        </select>
      </label>
      <label class="text-sm font-medium">
        정렬
        <select
          :value="filters.sort"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          @change="updateSort"
        >
          <option value="createdAt,desc">최근 등록순</option>
          <option value="deadlineAt,asc">마감 임박순</option>
          <option value="updatedAt,desc">최근 수정순</option>
        </select>
      </label>
      <label class="text-sm font-medium">
        마감 시작
        <input
          v-model="deadlineFrom"
          type="date"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          :disabled="deadlineWithinDays !== ''"
        />
      </label>
      <label class="text-sm font-medium">
        마감 종료
        <input
          v-model="deadlineTo"
          type="date"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          :disabled="deadlineWithinDays !== ''"
        />
      </label>
      <label class="text-sm font-medium">
        마감 임박
        <select
          v-model="deadlineWithinDays"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          :disabled="deadlineFrom !== '' || deadlineTo !== ''"
        >
          <option value="">사용 안 함</option>
          <option value="3">3일 이내</option>
          <option value="7">7일 이내</option>
          <option value="14">14일 이내</option>
          <option value="30">30일 이내</option>
        </select>
      </label>
      <div class="flex items-end gap-2">
        <button type="submit" class="rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white">
          필터 적용
        </button>
        <button
          type="button"
          class="rounded-lg border border-slate-300 px-4 py-2"
          @click="clearDeadline"
        >
          마감 초기화
        </button>
      </div>
    </form>

    <p v-if="message" class="mt-4 text-sm text-emerald-700" role="status">{{ message }}</p>
    <p v-if="actionError" class="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-800" role="alert">
      {{ actionError }}
    </p>

    <p v-if="jobs.isPending.value" class="mt-8" aria-live="polite">공고 목록을 불러오는 중…</p>
    <div
      v-else-if="jobs.isError.value"
      class="mt-8 rounded-xl bg-red-50 p-4 text-red-800"
      role="alert"
    >
      공고 목록을 불러오지 못했습니다.
      <button type="button" class="underline" @click="jobs.refetch()">다시 시도</button>
    </div>
    <div
      v-else-if="jobs.data.value?.items.length === 0"
      class="mt-8 rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600"
    >
      조건에 맞는 공고가 없습니다.
    </div>
    <ul v-else class="mt-6 space-y-4">
      <li
        v-for="job in jobs.data.value?.items"
        :key="job.id"
        class="rounded-2xl bg-white p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="text-sm font-medium text-slate-600">{{ jobCompanyLabel(job.companyName) }}</p>
            <RouterLink
              class="mt-1 block break-words text-lg font-semibold text-indigo-700"
              :to="{ name: 'job-overview', params: { jobId: job.id } }"
            >
              {{ jobDisplayTitle(job) }}
            </RouterLink>
            <p v-if="job.title && job.positionName" class="mt-1 text-sm text-slate-600">
              직무: {{ job.positionName }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <span
                data-testid="job-business-status"
                class="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-800"
              >
                업무 · {{ JOB_STATUS_LABELS[job.status] }}
              </span>
              <span
                data-testid="job-extraction-status"
                class="rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-800"
              >
                추출 · {{ JOB_EXTRACTION_STATUS_LABELS[job.extractionStatus] }}
              </span>
              <span
                v-if="job.status === 'CLOSED' && job.submittedAt"
                class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800"
              >
                서류 제출 이력 있음
              </span>
            </div>
            <p class="mt-3 text-sm text-slate-600">마감 {{ formatJobInstant(job.deadlineAt) }}</p>
          </div>
          <label class="text-sm font-medium">
            상태 변경
            <select
              :value="job.status"
              class="mt-1 block rounded-lg border border-slate-300 px-3 py-2"
              :disabled="statusMutation.isPending.value"
              :aria-label="`${jobDisplayTitle(job)} 상태 변경`"
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

    <nav
      v-if="jobs.data.value && jobs.data.value.totalPages > 0"
      class="mt-6 flex items-center justify-between"
      aria-label="공고 페이지"
    >
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="filters.page === 0"
        @click="updatePage(filters.page - 1)"
      >
        이전
      </button>
      <span class="text-sm">{{ filters.page + 1 }} / {{ jobs.data.value.totalPages }} 페이지</span>
      <button
        type="button"
        class="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50"
        :disabled="filters.page + 1 >= jobs.data.value.totalPages"
        @click="updatePage(filters.page + 1)"
      >
        다음
      </button>
    </nav>
  </section>
</template>
