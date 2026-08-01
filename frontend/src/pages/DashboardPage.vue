<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { useAgentRunListQuery } from '@/features/agent-runs/queries'
import { STATUS_LABELS, WORKFLOW_LABELS } from '@/features/agent-runs/presentation'
import { useDocumentListQuery } from '@/features/documents/queries'
import {
  DOCUMENT_PARSE_STATUS_LABELS,
  EVIDENCE_EXTRACTION_STATUS_LABELS,
} from '@/features/documents/presentation'
import { useJobListQuery } from '@/features/jobs/queries'
import { jobCompanyLabel, jobDisplayTitle } from '@/features/jobs/presentation'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import type { DocumentSummaryDto } from '@/shared/api/documentContracts'
import * as profileApi from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuery } from '@tanstack/vue-query'

const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const displayName = computed(() => authStore.currentUser?.displayName.trim() || '')
const dashboardTitle = computed(() =>
  displayName.value === '' ? '지금 준비 중인 지원' : `${displayName.value}, 지금 준비 중인 지원`,
)

const profileQuery = useQuery({
  queryKey: computed(() => profileQueryKeys.profile(userId.value)),
  queryFn: profileApi.getProfile,
  enabled: computed(() => userId.value !== ''),
})
const recentDocumentsQuery = useDocumentListQuery(
  userId,
  computed(() => ({ page: 0, size: 5, sort: 'updatedAt,desc' as const })),
)
const recentJobsQuery = useJobListQuery(
  userId,
  computed(() => ({ page: 0, size: 5, sort: 'updatedAt,desc' as const })),
)
const inProgressJobsQuery = useJobListQuery(
  userId,
  computed(() => ({ status: 'IN_PROGRESS' as const, page: 0, size: 1 })),
)
const submittedJobsQuery = useJobListQuery(
  userId,
  computed(() => ({ status: 'SUBMITTED' as const, page: 0, size: 1 })),
)
const closingSoonJobsQuery = useJobListQuery(
  userId,
  computed(() => ({
    status: 'IN_PROGRESS' as const,
    deadlineWithinDays: 14,
    page: 0,
    size: 3,
    sort: 'deadlineAt,asc' as const,
  })),
)
const activeRunsQuery = useAgentRunListQuery(
  userId,
  computed(() => ({
    status: ['QUEUED', 'RUNNING', 'WAITING_USER'] as const,
    page: 0,
    size: 4,
    sort: 'updatedAt,desc' as const,
  })),
)
const recentRunsQuery = useAgentRunListQuery(
  userId,
  computed(() => ({ page: 0, size: 5, sort: 'updatedAt,desc' as const })),
)

const dashboardQueries = [
  profileQuery,
  recentDocumentsQuery,
  recentJobsQuery,
  inProgressJobsQuery,
  submittedJobsQuery,
  closingSoonJobsQuery,
  activeRunsQuery,
  recentRunsQuery,
]

const isInitialLoading = computed(() => dashboardQueries.some((query) => query.isPending.value))
const hasQueryError = computed(() => dashboardQueries.some((query) => query.isError.value))
const completionPercent = computed(() => {
  if (profileQuery.isError.value || profileQuery.data.value === undefined) return null
  const missing = profileQuery.data.value.missingCompletionItems.length
  return (5 - missing) * 20
})
const totalDocuments = computed(() =>
  recentDocumentsQuery.isError.value
    ? null
    : (recentDocumentsQuery.data.value?.totalElements ?? null),
)
const activeRunCount = computed(() =>
  activeRunsQuery.isError.value ? null : (activeRunsQuery.data.value?.totalElements ?? null),
)

type StartItemState = 'completed' | 'pending' | 'unknown'

type StartItem = {
  key: 'profile' | 'documents' | 'jobs'
  icon: 'profile' | 'documents' | 'jobs'
  title: string
  description: string
  to: string
  action: string
  state: StartItemState
}

const startItems = computed<StartItem[]>(() => [
  {
    key: 'profile',
    icon: 'profile',
    title: '기본 정보 준비',
    description: '희망 직무와 기본 정보를 정리해 다음 준비의 기준을 만들어요.',
    to: '/profile/basic',
    action: '기본 정보 채우기',
    state: profileQuery.isError.value
      ? 'unknown'
      : profileQuery.data.value?.profileCompleted === true
        ? 'completed'
        : 'pending',
  },
  {
    key: 'documents',
    icon: 'documents',
    title: '이력서 또는 포트폴리오 등록',
    description: '자료를 등록하면 내용을 읽고 활용할 경험을 정리할 수 있어요.',
    to: '/documents',
    action: '자료 등록하기',
    state: recentDocumentsQuery.isError.value
      ? 'unknown'
      : (recentDocumentsQuery.data.value?.totalElements ?? 0) > 0
        ? 'completed'
        : 'pending',
  },
  {
    key: 'jobs',
    icon: 'jobs',
    title: '첫 관심 공고 등록',
    description: '관심 공고를 등록하면 본문 확인 뒤 분석이 자동으로 이어져요.',
    to: '/jobs/new',
    action: '공고 등록하기',
    state: recentJobsQuery.isError.value
      ? 'unknown'
      : (recentJobsQuery.data.value?.totalElements ?? 0) > 0
        ? 'completed'
        : 'pending',
  },
])
const completedStartCount = computed(
  () => startItems.value.filter((item) => item.state === 'completed').length,
)
const showStartChecklist = computed(
  () => !startItems.value.every((item) => item.state === 'completed'),
)

const documentNeedsAction = computed(() =>
  (recentDocumentsQuery.data.value?.items ?? []).filter(
    (document) =>
      document.parseStatus === 'NEEDS_MANUAL_TEXT' ||
      document.parseStatus === 'FAILED' ||
      document.evidenceExtractionStatus === 'FAILED',
  ),
)
const waitingRuns = computed(() =>
  (activeRunsQuery.data.value?.items ?? []).filter((run) => run.status === 'WAITING_USER'),
)

type NextTask = {
  key: string
  icon: 'profile' | 'documents' | 'jobs' | 'runs' | 'check'
  title: string
  description: string
  to: string
  action: string
  tone?: 'warning' | 'success'
}

const nextTasks = computed<NextTask[]>(() => {
  const tasks: NextTask[] = []
  const profile = profileQuery.data.value

  if (profile !== undefined && !profile.profileCompleted) {
    tasks.push({
      key: 'profile',
      icon: 'profile',
      title: '프로필 정보 보완',
      description: `필수 항목 ${profile.missingCompletionItems.length}개를 더 채우면 공고 분석에 활용할 정보가 선명해져요.`,
      to: '/profile/basic',
      action: '프로필 보완',
    })
  }

  const document = documentNeedsAction.value[0]
  if (document !== undefined) {
    tasks.push({
      key: `document-${document.id}`,
      icon: 'documents',
      title: '확인이 필요한 자료',
      description:
        documentNeedsAction.value.length === 1
          ? `${document.displayName}에서 확인할 내용이 있어요.`
          : `최근 자료 중 ${documentNeedsAction.value.length}개에 확인이 필요해요.`,
      to: `/documents/${document.id}`,
      action: '자료 확인',
      tone: 'warning',
    })
  }

  const waitingRun = waitingRuns.value[0]
  if (waitingRun !== undefined) {
    tasks.push({
      key: `run-${waitingRun.id}`,
      icon: 'runs',
      title: '입력을 기다리는 분석',
      description:
        waitingRun.requiredUserAction?.message ??
        `${WORKFLOW_LABELS[waitingRun.workflowType]} 작업에 추가 정보가 필요해요.`,
      to: `/agent-runs/${waitingRun.id}`,
      action: '필요 정보 확인',
      tone: 'warning',
    })
  }

  const closingSoon = closingSoonJobsQuery.data.value?.items[0]
  if (closingSoon !== undefined) {
    tasks.push({
      key: `job-${closingSoon.id}`,
      icon: 'jobs',
      title: '마감 임박 공고 확인',
      description: `${jobCompanyLabel(closingSoon.companyName)} · ${jobDisplayTitle(closingSoon)} 공고가 ${formatDeadline(closingSoon.deadlineAt)} 마감이에요.`,
      to: `/jobs/${closingSoon.id}/overview`,
      action: '공고 확인',
    })
  }

  if (tasks.length === 0 && !hasQueryError.value) {
    tasks.push({
      key: 'complete',
      icon: 'check',
      title: '지금 확인할 긴급 항목이 없어요',
      description: '새 공고를 등록하거나 최근 기록을 이어서 준비할 수 있어요.',
      to: '/jobs/new',
      action: '공고 등록',
      tone: 'success',
    })
  }
  return tasks.slice(0, 4)
})

type ActivityItem = {
  key: string
  at: string
  eyebrow: string
  title: string
  description: string
  to: string
}

const recentActivity = computed<ActivityItem[]>(() => {
  const documents: ActivityItem[] = (recentDocumentsQuery.data.value?.items ?? []).map(
    (document) => ({
      key: `document-${document.id}`,
      at: document.updatedAt,
      eyebrow: '이력서·자료',
      title: document.displayName,
      description: documentStatus(document),
      to: `/documents/${document.id}`,
    }),
  )
  const jobs: ActivityItem[] = (recentJobsQuery.data.value?.items ?? []).map((job) => ({
    key: `job-${job.id}`,
    at: job.updatedAt,
    eyebrow: jobCompanyLabel(job.companyName),
    title: jobDisplayTitle(job),
    description:
      job.status === 'SUBMITTED' ? '서류 제출' : job.status === 'CLOSED' ? '마감' : '지원 중',
    to: `/jobs/${job.id}/overview`,
  }))
  const runs: ActivityItem[] = (recentRunsQuery.data.value?.items ?? []).map((run) => ({
    key: `run-${run.id}`,
    at: run.updatedAt,
    eyebrow: 'AI 작업',
    title: WORKFLOW_LABELS[run.workflowType],
    description: STATUS_LABELS[run.status],
    to: `/agent-runs/${run.id}`,
  }))

  return [...documents, ...jobs, ...runs]
    .sort((left, right) => Date.parse(right.at) - Date.parse(left.at))
    .slice(0, 5)
})

function refetchDashboard(): void {
  for (const query of dashboardQueries) void query.refetch()
}

function refetchStartItem(key: StartItem['key']): void {
  if (key === 'profile') void profileQuery.refetch()
  if (key === 'documents') void recentDocumentsQuery.refetch()
  if (key === 'jobs') void recentJobsQuery.refetch()
}

function documentStatus(document: DocumentSummaryDto): string {
  if (document.parseStatus !== 'PARSED') {
    return DOCUMENT_PARSE_STATUS_LABELS[document.parseStatus]
  }
  return EVIDENCE_EXTRACTION_STATUS_LABELS[document.evidenceExtractionStatus]
}

function formatDeadline(value: string | null): string {
  if (value === null) return '마감일 미입력'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '마감일 미입력'
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(date)
}

function formatActivityDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<template>
  <section class="dashboard app-page" aria-labelledby="dashboard-heading">
    <PageHeader
      heading-id="dashboard-heading"
      :title="dashboardTitle"
      description="오늘 이어서 준비할 공고와 필요한 정보를 한눈에 확인하세요."
      variant="list"
    >
      <template #actions>
        <RouterLink class="button button--secondary" to="/documents">
          <AppIcon name="upload" />
          문서 업로드
        </RouterLink>
        <RouterLink class="button button--primary" to="/jobs/new">
          <AppIcon name="plus" />
          공고 등록
        </RouterLink>
      </template>
    </PageHeader>

    <StatePanel
      v-if="isInitialLoading"
      kind="loading"
      title="지원 준비를 불러오는 중…"
      description="프로필, 공고와 진행 중인 작업을 확인하고 있어요."
    />

    <template v-else>
      <aside v-if="hasQueryError" class="dashboard-error" role="alert">
        <div>
          <strong>일부 지원 정보를 불러오지 못했어요.</strong>
          <p>확인된 정보는 그대로 보여 드리고, 불러오지 못한 항목만 다시 요청할 수 있어요.</p>
        </div>
        <button type="button" class="button button--secondary" @click="refetchDashboard">
          다시 불러오기
        </button>
      </aside>

      <section v-if="showStartChecklist" class="start-checklist" aria-labelledby="start-heading">
        <header class="start-checklist__header">
          <div>
            <p class="section-kicker">첫 사용 준비</p>
            <h2 id="start-heading">시작에 필요한 항목을 확인해 보세요.</h2>
            <p>한 항목을 끝내도 남은 준비는 계속 확인할 수 있어요.</p>
          </div>
          <div class="start-checklist__progress" aria-label="첫 사용 준비 완료율">
            <strong>{{ completedStartCount }} / 3</strong>
            <span>완료</span>
          </div>
        </header>
        <ul class="start-checklist__items">
          <li
            v-for="item in startItems"
            :key="item.key"
            :class="`start-checklist__item--${item.state}`"
          >
            <span class="start-checklist__icon">
              <AppIcon :name="item.state === 'completed' ? 'check' : item.icon" />
            </span>
            <span class="start-checklist__body">
              <strong>{{ item.title }}</strong>
              <small v-if="item.state === 'completed'">준비를 마쳤어요.</small>
              <small v-else-if="item.state === 'unknown'">
                현재 상태를 확인하지 못했어요. 다시 확인해 주세요.
              </small>
              <small v-else>{{ item.description }}</small>
            </span>
            <span v-if="item.state === 'completed'" class="start-checklist__status">
              <AppIcon name="check" />
              완료
            </span>
            <button
              v-else-if="item.state === 'unknown'"
              type="button"
              class="button button--secondary button--compact"
              @click="refetchStartItem(item.key)"
            >
              다시 확인
            </button>
            <RouterLink v-else class="button button--secondary button--compact" :to="item.to">
              {{ item.action }}
            </RouterLink>
          </li>
        </ul>
        <footer class="start-checklist__footer">
          <RouterLink to="/guide" class="text-link">
            전체 이용 순서 보기
            <AppIcon name="arrow-right" />
          </RouterLink>
        </footer>
      </section>

      <section class="dashboard-metrics" aria-labelledby="status-heading">
        <div class="dashboard-section-heading">
          <div>
            <p class="section-kicker">현재 상태</p>
            <h2 id="status-heading">지원 준비 현황</h2>
          </div>
          <RouterLink to="/jobs" class="text-link">
            전체 공고 보기
            <AppIcon name="arrow-right" />
          </RouterLink>
        </div>

        <div class="metric-grid">
          <article class="metric metric--profile">
            <div class="metric__top">
              <span class="metric__icon"><AppIcon name="profile" /></span>
              <span>{{
                profileQuery.isError.value
                  ? '확인하지 못했어요'
                  : profileQuery.data.value?.profileCompleted
                    ? '필수 항목 완료'
                    : '보완 권장'
              }}</span>
            </div>
            <div class="profile-progress">
              <strong>{{ completionPercent === null ? '—' : `${completionPercent}%` }}</strong>
              <div>
                <h3>프로필 완성도</h3>
                <progress
                  v-if="completionPercent !== null"
                  class="progress-track"
                  :value="completionPercent"
                  max="100"
                >
                  {{ completionPercent }}%
                </progress>
                <span v-else class="metric__unknown">상태를 다시 확인해 주세요.</span>
              </div>
            </div>
            <p>
              {{
                profileQuery.isError.value
                  ? '프로필 상태를 불러오지 못했어요.'
                  : profileQuery.data.value?.profileCompleted
                    ? '지원에 필요한 기본 정보를 채웠어요.'
                    : `필수 항목 ${profileQuery.data.value?.missingCompletionItems.length ?? 5}개가 남아 있어요.`
              }}
            </p>
            <RouterLink to="/profile/basic">프로필 확인</RouterLink>
          </article>

          <article class="metric">
            <div class="metric__top">
              <span class="metric__icon"><AppIcon name="jobs" /></span>
              <span>지원 파이프라인</span>
            </div>
            <strong class="metric__value">{{
              inProgressJobsQuery.isError.value
                ? '—'
                : (inProgressJobsQuery.data.value?.totalElements ?? 0)
            }}</strong>
            <h3>지원 중 공고</h3>
            <p>내용을 검토하거나 지원서를 준비하고 있는 공고예요.</p>
            <RouterLink to="/jobs?status=IN_PROGRESS">지원 중 공고 보기</RouterLink>
          </article>

          <article class="metric">
            <div class="metric__top">
              <span class="metric__icon metric__icon--success"><AppIcon name="check" /></span>
              <span>제출 기록</span>
            </div>
            <strong class="metric__value">{{
              submittedJobsQuery.isError.value
                ? '—'
                : (submittedJobsQuery.data.value?.totalElements ?? 0)
            }}</strong>
            <h3>서류 제출 공고</h3>
            <p>제출을 마치고 결과를 기다리는 공고예요.</p>
            <RouterLink to="/jobs?status=SUBMITTED">제출 공고 보기</RouterLink>
          </article>

          <article class="metric">
            <div class="metric__top">
              <span class="metric__icon"><AppIcon name="runs" /></span>
              <span>자동 처리</span>
            </div>
            <strong class="metric__value">{{ activeRunCount ?? '—' }}</strong>
            <h3>진행 중 분석</h3>
            <p>
              {{
                waitingRuns.length > 0
                  ? `${waitingRuns.length}개 작업이 추가 입력을 기다리고 있어요.`
                  : '자료와 공고를 정리하고 있는 작업이에요.'
              }}
            </p>
            <RouterLink to="/agent-runs">AI 작업 보기</RouterLink>
          </article>

          <article class="metric metric--documents">
            <div class="metric__top">
              <span class="metric__icon"><AppIcon name="documents" /></span>
              <span>등록 자료</span>
            </div>
            <strong class="metric__value">{{ totalDocuments ?? '—' }}</strong>
            <h3>이력서·문서</h3>
            <p>
              {{
                documentNeedsAction.length > 0
                  ? `최근 자료 중 ${documentNeedsAction.length}개에 확인이 필요해요.`
                  : '등록한 자료와 내용을 읽은 결과를 확인할 수 있어요.'
              }}
            </p>
            <RouterLink to="/documents">자료 관리</RouterLink>
          </article>
        </div>
      </section>

      <div class="dashboard-columns">
        <section class="dashboard-section" aria-labelledby="next-task-heading">
          <div class="dashboard-section-heading">
            <div>
              <p class="section-kicker">우선 확인</p>
              <h2 id="next-task-heading">다음 할 일</h2>
            </div>
          </div>
          <ul v-if="nextTasks.length" class="task-list">
            <li v-for="task in nextTasks" :key="task.key">
              <RouterLink
                :to="task.to"
                class="task-item"
                :class="`task-item--${task.tone ?? 'default'}`"
              >
                <span class="task-item__icon"><AppIcon :name="task.icon" /></span>
                <span class="task-item__body">
                  <strong>{{ task.title }}</strong>
                  <small>{{ task.description }}</small>
                  <span class="task-item__action">
                    {{ task.action }}
                    <AppIcon name="arrow-right" />
                  </span>
                </span>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="compact-empty">
            <AppIcon :name="hasQueryError ? 'alert' : 'check'" />
            <div>
              <strong>{{
                hasQueryError
                  ? '일부 상태를 확인한 뒤 다음 할 일을 안내할게요.'
                  : '지금 확인할 긴급 항목이 없어요.'
              }}</strong>
              <p>새 공고를 등록하거나 최근 기록을 이어서 준비할 수 있어요.</p>
            </div>
          </div>
        </section>

        <section class="dashboard-section" aria-labelledby="deadline-heading">
          <div class="dashboard-section-heading">
            <div>
              <p class="section-kicker">14일 이내</p>
              <h2 id="deadline-heading">마감 임박 공고</h2>
            </div>
          </div>
          <ul v-if="closingSoonJobsQuery.data.value?.items.length" class="deadline-list">
            <li v-for="job in closingSoonJobsQuery.data.value.items" :key="job.id">
              <RouterLink :to="`/jobs/${job.id}/overview`">
                <span>
                  <strong>{{ jobDisplayTitle(job) }}</strong>
                  <small>{{ jobCompanyLabel(job.companyName) }}</small>
                </span>
                <time :datetime="job.deadlineAt ?? undefined">{{
                  formatDeadline(job.deadlineAt)
                }}</time>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="compact-empty">
            <AppIcon name="clock" />
            <div>
              <strong>{{
                closingSoonJobsQuery.isError.value
                  ? '마감 임박 공고를 확인하지 못했어요.'
                  : '마감이 임박한 공고가 없어요.'
              }}</strong>
              <p>
                {{
                  closingSoonJobsQuery.isError.value
                    ? '위의 다시 불러오기를 눌러 상태를 확인해 주세요.'
                    : '공고에 마감일을 입력하면 여기에서 먼저 알려 드려요.'
                }}
              </p>
            </div>
          </div>
        </section>
      </div>

      <section class="dashboard-section dashboard-activity" aria-labelledby="activity-heading">
        <div class="dashboard-section-heading">
          <div>
            <p class="section-kicker">최근 업데이트</p>
            <h2 id="activity-heading">최근 활동</h2>
          </div>
          <RouterLink to="/agent-runs" class="text-link">
            AI 작업 보기
            <AppIcon name="arrow-right" />
          </RouterLink>
        </div>
        <ul v-if="recentActivity.length" class="activity-list">
          <li v-for="activity in recentActivity" :key="activity.key">
            <RouterLink :to="activity.to">
              <span class="activity-list__body">
                <small>{{ activity.eyebrow }}</small>
                <strong>{{ activity.title }}</strong>
              </span>
              <span class="activity-list__meta">
                <span>{{ activity.description }}</span>
                <time :datetime="activity.at">{{ formatActivityDate(activity.at) }}</time>
              </span>
            </RouterLink>
          </li>
        </ul>
        <div v-else class="compact-empty">
          <AppIcon name="inbox" />
          <div>
            <strong>아직 최근 활동이 없어요.</strong>
            <p>자료나 공고를 등록하면 준비 과정과 최근 기록을 이곳에서 확인할 수 있어요.</p>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.dashboard {
  display: grid;
  gap: var(--space-8);
}

.dashboard :deep(.page-header) {
  align-items: center;
}

.dashboard-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  border-left: 3px solid var(--color-warning);
  background: var(--color-warning-soft);
  padding: var(--space-4) var(--space-5);
}

.dashboard-error strong,
.dashboard-error p {
  margin: 0;
}

.dashboard-error p {
  margin-top: var(--space-1);
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

.start-checklist {
  border: 1px solid var(--color-brand-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.start-checklist__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-6);
}

.start-checklist__header h2,
.dashboard-section-heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 1.25rem;
  letter-spacing: -0.025em;
}

.start-checklist__header p:last-child {
  margin: var(--space-2) 0 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.start-checklist__progress {
  display: grid;
  flex: 0 0 auto;
  justify-items: end;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.start-checklist__progress strong {
  color: var(--color-brand-strong);
  font-size: var(--font-size-xl);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.start-checklist__items {
  margin: 0;
  padding: 0 var(--space-6);
  list-style: none;
}

.start-checklist__items li {
  display: grid;
  grid-template-columns: 2.25rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  padding-block: var(--space-4);
}

.start-checklist__icon {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.start-checklist__item--completed .start-checklist__icon {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.start-checklist__item--unknown .start-checklist__icon {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.start-checklist__icon .icon {
  width: 1rem;
}

.start-checklist__body strong,
.start-checklist__body small {
  display: block;
}

.start-checklist__body strong {
  color: var(--color-ink-soft);
  font-size: var(--font-size-sm);
}

.start-checklist__body small {
  margin-top: var(--space-1);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.start-checklist__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-success-strong);
  font-size: var(--font-size-xs);
  font-weight: 750;
}

.start-checklist__status .icon {
  width: 0.875rem;
}

.start-checklist__footer {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-subtle);
  padding: var(--space-3) var(--space-6);
}

.start-checklist__footer .text-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-sm);
}

.dashboard-metrics,
.dashboard-section {
  display: grid;
  gap: var(--space-5);
}

.dashboard-section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.metric {
  grid-column: span 2;
  min-width: 0;
  border-right: 1px solid var(--color-border);
  padding: var(--space-6);
}

.metric--profile {
  grid-column: span 4;
}

.metric--documents {
  grid-column: span 2;
  border-right: 0;
}

.metric__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  font-weight: 650;
}

.metric__icon {
  display: inline-grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.metric__icon--success {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.metric__icon .icon {
  width: 1rem;
  height: 1rem;
}

.metric__value {
  display: block;
  margin-top: var(--space-5);
  color: var(--color-ink);
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -0.05em;
}

.metric h3 {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-md);
}

.metric p {
  min-height: 2.8rem;
  margin: var(--space-2) 0 var(--space-4);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.metric > a,
.text-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-brand);
  font-size: var(--font-size-sm);
  font-weight: 700;
  text-decoration: none;
}

.metric > a:hover,
.text-link:hover {
  text-decoration: underline;
  text-underline-offset: 0.2rem;
}

.text-link .icon {
  width: 0.875rem;
  height: 0.875rem;
}

.profile-progress {
  display: grid;
  grid-template-columns: auto minmax(8rem, 1fr);
  gap: var(--space-4);
  align-items: center;
  margin-top: var(--space-5);
}

.profile-progress > strong {
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -0.05em;
}

.profile-progress h3 {
  margin: 0 0 var(--space-2);
}

.metric__unknown {
  color: var(--color-warning-strong);
  font-size: var(--font-size-xs);
}

.dashboard-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(18rem, 0.8fr);
  gap: clamp(2rem, 4vw, 4rem);
}

.task-list,
.deadline-list,
.activity-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.task-list,
.deadline-list {
  border-top: 1px solid var(--color-border);
}

.task-list li,
.deadline-list li {
  border-bottom: 1px solid var(--color-border);
}

.task-item {
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr);
  gap: var(--space-3);
  color: var(--color-ink);
  padding: var(--space-4) var(--space-2);
  text-decoration: none;
  transition: background-color var(--motion-fast);
}

.task-item:hover {
  background: var(--color-surface-subtle);
}

.task-item__icon {
  display: inline-grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.task-item--warning .task-item__icon {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.task-item--success .task-item__icon {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.task-item__icon .icon {
  width: 1.125rem;
}

.task-item__body strong,
.task-item__body small {
  display: block;
}

.task-item__body small {
  margin-top: var(--space-1);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.task-item__action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-2);
  color: var(--color-brand);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.task-item__action .icon {
  width: 0.75rem;
}

.deadline-list a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-2);
  text-decoration: none;
}

.deadline-list a:hover strong {
  color: var(--color-brand);
}

.deadline-list strong,
.deadline-list small {
  display: block;
}

.deadline-list small {
  margin-top: var(--space-1);
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.deadline-list time {
  flex: 0 0 auto;
  color: var(--color-warning-strong);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.compact-empty {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  padding: var(--space-5) var(--space-2);
}

.compact-empty > .icon {
  width: 1.25rem;
  flex: 0 0 auto;
  color: var(--color-muted-strong);
}

.compact-empty strong {
  color: var(--color-ink-soft);
  font-size: var(--font-size-sm);
}

.compact-empty p {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-xs);
}

.dashboard-activity {
  padding-top: var(--space-2);
}

.activity-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-top: 1px solid var(--color-border-strong);
  border-bottom: 1px solid var(--color-border-strong);
}

.activity-list li {
  min-width: 0;
  border-right: 1px solid var(--color-border);
}

.activity-list li:last-child {
  border-right: 0;
}

.activity-list a {
  display: flex;
  min-height: 9.5rem;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5);
  text-decoration: none;
}

.activity-list a:hover {
  background: var(--color-surface-subtle);
}

.activity-list__body small,
.activity-list__body strong,
.activity-list__meta span,
.activity-list__meta time {
  display: block;
}

.activity-list__body small {
  overflow: hidden;
  color: var(--color-muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-list__body strong {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--space-2);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.45;
}

.activity-list__meta {
  color: var(--color-muted);
  font-size: 0.6875rem;
}

.activity-list__meta span {
  color: var(--color-ink-soft);
  font-weight: 650;
}

.activity-list__meta time {
  margin-top: var(--space-1);
}

@media (max-width: 74rem) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric,
  .metric--documents {
    grid-column: span 1;
    border-right: 0;
  }

  .metric--profile {
    grid-column: 1 / -1;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .metric:nth-child(even) {
    border-right: 1px solid var(--color-border);
  }

  .metric:nth-child(n + 4) {
    border-top: 1px solid var(--color-border);
  }

  .activity-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .activity-list li:nth-child(3) {
    border-right: 0;
  }

  .activity-list li:nth-child(n + 4) {
    border-top: 1px solid var(--color-border);
  }
}

@media (max-width: 56rem) {
  .dashboard-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 639px) {
  .dashboard {
    gap: var(--space-7);
  }

  .dashboard-error {
    align-items: stretch;
    flex-direction: column;
  }

  .start-checklist__header {
    align-items: flex-start;
  }

  .start-checklist__items li {
    grid-template-columns: 2.25rem minmax(0, 1fr);
  }

  .start-checklist__items li > :last-child {
    grid-column: 2;
    justify-self: start;
  }

  .metric-grid,
  .activity-list {
    display: block;
  }

  .metric,
  .metric:nth-child(even),
  .metric:nth-child(3),
  .activity-list li,
  .activity-list li:nth-child(3) {
    border-top: 0;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .metric:last-child,
  .activity-list li:last-child {
    border-bottom: 0;
  }

  .metric p {
    min-height: 0;
  }

  .activity-list a {
    min-height: 0;
  }

  .dashboard-section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 399px) {
  .dashboard :deep(.page-header__actions) {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
