<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { STATUS_LABELS, WORKFLOW_LABELS } from '@/features/agent-runs/presentation'
import { useAgentRunListQuery } from '@/features/agent-runs/queries'
import {
  DOCUMENT_PARSE_STATUS_LABELS,
  EVIDENCE_EXTRACTION_STATUS_LABELS,
} from '@/features/documents/presentation'
import { useDocumentListQuery } from '@/features/documents/queries'
import { jobCompanyLabel, jobDisplayTitle } from '@/features/jobs/presentation'
import { useJobListQuery } from '@/features/jobs/queries'
import type { DocumentSummaryDto } from '@/shared/api/documentContracts'
import * as dashboardApi from '@/shared/api/dashboardApi'
import type { CareerGuidePostDto, DashboardDeadlineJobDto } from '@/shared/api/dashboardContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuery } from '@tanstack/vue-query'

const SEOUL_TIME_ZONE = 'Asia/Seoul'
const EDUCATION_LEVEL_LABELS = {
  OTHER: '기타 학력',
  HIGH_SCHOOL: '고등학교',
  ASSOCIATE: '전문학사',
  BACHELOR: '학사',
  MASTER: '석사',
  DOCTORATE: '박사',
} as const
const EDUCATION_STATUS_LABELS = {
  ENROLLED: '재학',
  LEAVE_OF_ABSENCE: '휴학',
  EXPECTED_GRADUATION: '졸업 예정',
  GRADUATED: '졸업',
  WITHDRAWN: '중퇴',
} as const

const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const currentMonth = ref(seoulToday().slice(0, 7))
const selectedDate = ref(seoulToday())
const selectedGuide = ref<CareerGuidePostDto | null>(null)
const guideDialog = ref<HTMLElement | null>(null)
const guideCloseButton = ref<HTMLButtonElement | null>(null)
let guideTrigger: HTMLElement | null = null
let bodyOverflowBeforeGuide = ''

const dashboardQuery = useQuery({
  queryKey: computed(() => ['user', userId.value, 'dashboard', currentMonth.value]),
  queryFn: () => dashboardApi.getDashboard(currentMonth.value),
  enabled: computed(() => userId.value !== ''),
})
const guideQuery = useQuery({
  queryKey: computed(() => ['user', userId.value, 'career-guides']),
  queryFn: dashboardApi.listCareerGuides,
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

const dashboardTitle = computed(() => {
  const name =
    dashboardQuery.data.value?.profile.displayName.trim() ||
    authStore.currentUser?.displayName.trim() ||
    ''
  return name === '' ? '지원 준비 현황' : `${name}님의 지원 준비 현황`
})
const profile = computed(() => dashboardQuery.data.value?.profile ?? null)
const dashboardUnavailable = computed(() => dashboardQuery.isError.value)
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
const deadlineDays = computed(() => dashboardQuery.data.value?.deadlineDays ?? [])
const deadlineCount = computed(() =>
  deadlineDays.value.reduce((total, day) => total + day.count, 0),
)
const selectedDeadlineDay = computed(
  () => deadlineDays.value.find((day) => day.date === selectedDate.value) ?? null,
)
const selectedDeadlineItems = computed(() => selectedDeadlineDay.value?.items ?? [])
const calendarCells = computed(() => buildCalendar(currentMonth.value, deadlineDays.value))
const monthLabel = computed(() => {
  const [year, month] = currentMonth.value.split('-').map(Number)
  return `${year}년 ${month}월`
})

type StartItemState = 'completed' | 'pending' | 'unknown'
type StartItem = {
  key: 'profile' | 'documents' | 'jobs'
  title: string
  to: string
  action: string
  state: StartItemState
}

const startItems = computed<StartItem[]>(() => {
  const dashboard = dashboardQuery.data.value
  return [
    {
      key: 'profile',
      title: '지원 정보',
      to: '/profile/basic',
      action: '정보 채우기',
      state: dashboardUnavailable.value
        ? 'unknown'
        : dashboard?.profile.completed
          ? 'completed'
          : 'pending',
    },
    {
      key: 'documents',
      title: '이력서·자료',
      to: '/documents',
      action: '자료 등록',
      state: dashboardUnavailable.value
        ? 'unknown'
        : (dashboard?.documents.registeredCount ?? 0) > 0
          ? 'completed'
          : 'pending',
    },
    {
      key: 'jobs',
      title: '관심 공고',
      to: '/jobs/new',
      action: '공고 등록',
      state: dashboardUnavailable.value
        ? 'unknown'
        : (dashboard?.jobs.registeredCount ?? 0) > 0
          ? 'completed'
          : 'pending',
    },
  ]
})
const completedStartCount = computed(
  () => startItems.value.filter((item) => item.state === 'completed').length,
)
const showStartChecklist = computed(
  () => !startItems.value.every((item) => item.state === 'completed'),
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
  const dashboard = dashboardQuery.data.value

  if (dashboard !== undefined && !dashboard.profile.completed) {
    tasks.push({
      key: 'profile',
      icon: 'profile',
      title: '지원 정보를 조금 더 채워 보세요',
      description: `필수 항목 ${dashboard.profile.missingItems.length}개를 채우면 공고 분석에 활용할 기준이 선명해져요.`,
      to: '/profile/basic',
      action: '지원 정보 보완',
    })
  }

  const document = documentNeedsAction.value[0]
  if (document !== undefined) {
    tasks.push({
      key: `document-${document.id}`,
      icon: 'documents',
      title: '확인이 필요한 자료가 있어요',
      description:
        documentNeedsAction.value.length === 1
          ? `${document.displayName}의 내용을 확인해 주세요.`
          : `최근 자료 ${documentNeedsAction.value.length}개에 확인이 필요해요.`,
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
      title: 'AI 작업이 입력을 기다리고 있어요',
      description:
        waitingRun.requiredUserAction?.message ??
        `${WORKFLOW_LABELS[waitingRun.workflowType]} 작업에 추가 정보가 필요해요.`,
      to: `/agent-runs/${waitingRun.id}`,
      action: '필요 정보 확인',
      tone: 'warning',
    })
  }

  const nearestDeadline = deadlineDays.value[0]?.items[0]
  if (nearestDeadline !== undefined) {
    tasks.push({
      key: `deadline-${nearestDeadline.id}`,
      icon: 'jobs',
      title: '다가오는 공고 마감을 확인하세요',
      description: `${jobCompanyLabel(nearestDeadline.companyName)} · ${deadlineTitle(nearestDeadline)}가 ${formatDeadlineDateTime(nearestDeadline.deadlineAt)} 마감이에요.`,
      to: `/jobs/${nearestDeadline.id}/overview`,
      action: '공고 확인',
    })
  }

  if (tasks.length === 0 && !dashboardUnavailable.value) {
    tasks.push({
      key: 'complete',
      icon: 'check',
      title: '지금 바로 확인할 긴급 항목이 없어요',
      description: '새 공고를 등록하거나 최근 준비 기록을 이어갈 수 있어요.',
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
      job.status === 'SUBMITTED' ? '지원 완료' : job.status === 'CLOSED' ? '마감' : '준비 중',
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

watch(currentMonth, (month) => {
  const today = seoulToday()
  selectedDate.value = today.startsWith(month) ? today : `${month}-01`
})

watch(selectedGuide, async (guide) => {
  if (guide === null) return
  bodyOverflowBeforeGuide = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', handleGuideKeydown)
  await nextTick()
  guideCloseButton.value?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleGuideKeydown)
  if (selectedGuide.value !== null) document.body.style.overflow = bodyOverflowBeforeGuide
})

function moveMonth(offset: number): void {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const next = new Date(Date.UTC(year, month - 1 + offset, 1))
  currentMonth.value = `${next.getUTCFullYear()}-${String(next.getUTCMonth() + 1).padStart(2, '0')}`
}

function returnToToday(): void {
  const today = seoulToday()
  currentMonth.value = today.slice(0, 7)
  selectedDate.value = today
}

function openGuide(post: CareerGuidePostDto, event: MouseEvent): void {
  guideTrigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  selectedGuide.value = post
}

function closeGuide(): void {
  if (selectedGuide.value === null) return
  selectedGuide.value = null
  document.removeEventListener('keydown', handleGuideKeydown)
  document.body.style.overflow = bodyOverflowBeforeGuide
  const trigger = guideTrigger
  guideTrigger = null
  void nextTick(() => trigger?.focus())
}

function handleGuideKeydown(event: KeyboardEvent): void {
  if (selectedGuide.value === null) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeGuide()
    return
  }
  if (event.key !== 'Tab' || guideDialog.value === null) return
  const focusable = Array.from(
    guideDialog.value.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), [tabindex]:not([tabindex="-1"])',
    ),
  )
  if (focusable.length === 0) {
    event.preventDefault()
    guideDialog.value.focus()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function refetchDashboard(): void {
  void dashboardQuery.refetch()
  void recentDocumentsQuery.refetch()
  void recentJobsQuery.refetch()
  void activeRunsQuery.refetch()
  void recentRunsQuery.refetch()
}

function documentStatus(document: DocumentSummaryDto): string {
  if (document.parseStatus !== 'PARSED') return DOCUMENT_PARSE_STATUS_LABELS[document.parseStatus]
  return EVIDENCE_EXTRACTION_STATUS_LABELS[document.evidenceExtractionStatus]
}

function deadlineTitle(job: DashboardDeadlineJobDto): string {
  return job.positionName?.trim() || job.title?.trim() || '채용 공고'
}

function profileInitial(): string {
  const name = profile.value?.displayName || authStore.currentUser?.displayName || 'H'
  return Array.from(name.trim())[0] || 'H'
}

function primaryEducationLabel(): string {
  const education = profile.value?.primaryEducation
  if (education === null || education === undefined) return '최종 학력 미입력'
  const detail = education.major?.trim() || education.degree?.trim()
  const level = EDUCATION_LEVEL_LABELS[education.educationLevel]
  const status = EDUCATION_STATUS_LABELS[education.educationStatus]
  return [education.schoolName, detail, `${level} · ${status}`].filter(Boolean).join(' · ')
}

function formatDeadlineDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '마감 시각 미확인'
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL_TIME_ZONE,
    month: 'short',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function formatActivityDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL_TIME_ZONE,
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function seoulToday(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: SEOUL_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

type DeadlineDay = { date: string; count: number }
type CalendarCell = {
  key: string
  date: string | null
  day: number | null
  count: number
  isToday: boolean
}

function buildCalendar(monthValue: string, days: DeadlineDay[]): CalendarCell[] {
  const [year, month] = monthValue.split('-').map(Number)
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay()
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const counts = new Map(days.map((day) => [day.date, day.count]))
  const cells: CalendarCell[] = Array.from({ length: firstWeekday }, (_, index) => ({
    key: `before-${index}`,
    date: null,
    day: null,
    count: 0,
    isToday: false,
  }))
  for (let day = 1; day <= lastDay; day += 1) {
    const date = `${monthValue}-${String(day).padStart(2, '0')}`
    cells.push({
      key: date,
      date,
      day,
      count: counts.get(date) ?? 0,
      isToday: date === seoulToday(),
    })
  }
  while (cells.length % 7 !== 0) {
    cells.push({ key: `after-${cells.length}`, date: null, day: null, count: 0, isToday: false })
  }
  return cells
}
</script>

<template>
  <section class="dashboard app-page" aria-labelledby="dashboard-heading">
    <PageHeader
      heading-id="dashboard-heading"
      :title="dashboardTitle"
      description="마감 일정과 다음 할 일을 한눈에 확인하세요."
      variant="list"
    >
      <template #actions>
        <RouterLink class="button button--secondary" to="/documents">
          <AppIcon name="upload" />
          자료 등록
        </RouterLink>
        <RouterLink class="button button--primary" to="/jobs/new">
          <AppIcon name="plus" />
          공고 등록
        </RouterLink>
      </template>
    </PageHeader>

    <StatePanel
      v-if="dashboardQuery.isPending.value"
      kind="loading"
      title="지원 준비를 불러오는 중…"
      description="프로필과 이번 달 마감 일정을 확인하고 있어요."
    />

    <template v-else>
      <aside v-if="dashboardUnavailable" class="dashboard-error" role="alert">
        <span class="dashboard-error__icon"><AppIcon name="alert" /></span>
        <div>
          <strong>지원 준비 요약과 마감 일정을 불러오지 못했어요.</strong>
          <p>
            최근 활동처럼 확인된 정보는 그대로 보여 드립니다. 요약 수치는 0으로 표시하지 않아요.
          </p>
        </div>
        <button type="button" class="button button--secondary" @click="refetchDashboard">
          다시 불러오기
        </button>
      </aside>

      <section class="dashboard-hero" aria-label="사용자 커리어와 다음 행동">
        <article class="career-card">
          <div class="career-card__identity">
            <span class="career-card__monogram" aria-hidden="true">{{ profileInitial() }}</span>
            <div>
              <p>MY CAREER</p>
              <h2>{{ profile?.legalName || profile?.displayName || '지원자' }}</h2>
            </div>
            <span
              class="career-card__readiness"
              :class="{ 'career-card__readiness--unknown': dashboardUnavailable }"
            >
              {{
                dashboardUnavailable ? '확인 필요' : `준비도 ${profile?.completionPercent ?? 0}%`
              }}
            </span>
          </div>

          <div class="career-card__role">
            <span class="career-card__role-icon"><AppIcon name="jobs" /></span>
            <div>
              <small>희망 직무</small>
              <strong>{{ profile?.desiredRoles.join(', ') || '희망 직무 미입력' }}</strong>
            </div>
          </div>

          <dl class="career-card__facts">
            <div>
              <dt><AppIcon name="profile" /> 희망 지역</dt>
              <dd>{{ profile?.desiredLocations.join(', ') || '미입력' }}</dd>
            </div>
            <div>
              <dt><AppIcon name="documents" /> 최종 학력</dt>
              <dd>{{ primaryEducationLabel() }}</dd>
            </div>
          </dl>

          <div class="career-card__progress">
            <span>
              <strong>지원 정보 준비도</strong>
              <small v-if="!dashboardUnavailable">
                {{
                  profile?.completed
                    ? '지원에 필요한 기본 정보를 채웠어요.'
                    : `${profile?.missingItems.length ?? 5}개 항목이 남아 있어요.`
                }}
              </small>
              <small v-else>현재 준비도를 확인하지 못했어요.</small>
            </span>
            <strong>{{
              dashboardUnavailable ? '—' : `${profile?.completionPercent ?? 0}%`
            }}</strong>
          </div>
          <progress
            v-if="!dashboardUnavailable"
            class="career-card__track"
            :value="profile?.completionPercent ?? 0"
            max="100"
          >
            {{ profile?.completionPercent ?? 0 }}%
          </progress>

          <div v-if="showStartChecklist" class="start-checklist" aria-labelledby="start-heading">
            <div class="start-checklist__heading">
              <h3 id="start-heading">첫 지원 준비</h3>
              <span>{{ completedStartCount }} / 3 완료</span>
            </div>
            <ul>
              <li v-for="item in startItems" :key="item.key" :data-state="item.state">
                <AppIcon
                  :name="
                    item.state === 'completed'
                      ? 'check'
                      : item.state === 'unknown'
                        ? 'alert'
                        : 'plus'
                  "
                />
                <span>{{ item.title }}</span>
                <button
                  v-if="item.state === 'unknown'"
                  type="button"
                  @click="dashboardQuery.refetch()"
                >
                  다시 확인
                </button>
                <RouterLink v-else-if="item.state === 'pending'" :to="item.to">{{
                  item.action
                }}</RouterLink>
                <small v-else>완료</small>
              </li>
            </ul>
          </div>

          <RouterLink to="/profile/basic" class="career-card__cta">
            지원 정보 확인
            <AppIcon name="arrow-right" />
          </RouterLink>
        </article>

        <article class="priority-card">
          <header>
            <span class="priority-card__icon"><AppIcon name="sparkle" /></span>
            <div>
              <p class="section-kicker">지금 먼저</p>
              <h2>다음 할 일</h2>
            </div>
          </header>
          <ul v-if="nextTasks.length" class="task-list">
            <li v-for="(task, index) in nextTasks" :key="task.key">
              <RouterLink :to="task.to" :class="`task-item--${task.tone ?? 'default'}`">
                <span class="task-item__order">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="task-item__icon"><AppIcon :name="task.icon" /></span>
                <span class="task-item__body">
                  <strong>{{ task.title }}</strong>
                  <small>{{ task.description }}</small>
                </span>
                <span class="task-item__action" :aria-label="task.action"
                  ><AppIcon name="arrow-right"
                /></span>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="compact-empty">
            <AppIcon name="alert" />
            <div>
              <strong>다음 할 일을 아직 정리하지 못했어요.</strong>
              <p>지원 준비 현황을 다시 불러오면 우선순위를 안내할게요.</p>
            </div>
          </div>
        </article>
      </section>

      <section class="summary-section" aria-labelledby="summary-heading">
        <div class="dashboard-section-heading">
          <div>
            <p class="section-kicker">한눈에 보기</p>
            <h2 id="summary-heading">지원 준비 요약</h2>
          </div>
          <RouterLink to="/jobs" class="text-link"
            >전체 공고 <AppIcon name="arrow-right"
          /></RouterLink>
        </div>
        <div class="summary-grid">
          <RouterLink to="/jobs?status=IN_PROGRESS" class="summary-card summary-card--primary">
            <span class="summary-card__icon"><AppIcon name="jobs" /></span>
            <span>
              <small>준비 중인 공고</small>
              <strong>{{
                dashboardUnavailable ? '—' : (dashboardQuery.data.value?.jobs.preparingCount ?? 0)
              }}</strong>
            </span>
            <AppIcon name="arrow-right" />
          </RouterLink>
          <RouterLink to="/jobs?status=SUBMITTED" class="summary-card">
            <span class="summary-card__icon summary-card__icon--success"
              ><AppIcon name="check"
            /></span>
            <span>
              <small>지원 완료</small>
              <strong>{{
                dashboardUnavailable ? '—' : (dashboardQuery.data.value?.jobs.submittedCount ?? 0)
              }}</strong>
            </span>
            <AppIcon name="arrow-right" />
          </RouterLink>
          <RouterLink to="/agent-runs" class="summary-card">
            <span class="summary-card__icon"><AppIcon name="runs" /></span>
            <span>
              <small>AI가 확인 중</small>
              <strong>{{
                dashboardUnavailable ? '—' : (dashboardQuery.data.value?.agentRuns.activeCount ?? 0)
              }}</strong>
            </span>
            <AppIcon name="arrow-right" />
          </RouterLink>
          <RouterLink to="/documents" class="summary-card">
            <span class="summary-card__icon"><AppIcon name="documents" /></span>
            <span>
              <small>등록한 이력서·자료</small>
              <strong>{{
                dashboardUnavailable
                  ? '—'
                  : (dashboardQuery.data.value?.documents.registeredCount ?? 0)
              }}</strong>
            </span>
            <AppIcon name="arrow-right" />
          </RouterLink>
        </div>
      </section>

      <section class="deadline-section" aria-labelledby="deadline-heading">
        <div class="deadline-section__heading">
          <div>
            <p class="section-kicker">다가오는 일정</p>
            <h2 id="deadline-heading">공고 마감 캘린더</h2>
            <p>모든 날짜와 시각은 Asia/Seoul 기준으로 표시합니다.</p>
          </div>
          <div class="calendar-controls" aria-label="캘린더 월 이동">
            <button type="button" aria-label="이전 달" @click="moveMonth(-1)">
              <AppIcon name="arrow-left" />
            </button>
            <strong aria-live="polite">{{ monthLabel }}</strong>
            <button type="button" aria-label="다음 달" @click="moveMonth(1)">
              <AppIcon name="arrow-right" />
            </button>
            <button type="button" class="calendar-controls__today" @click="returnToToday">
              오늘
            </button>
          </div>
        </div>

        <div
          v-if="dashboardQuery.isFetching.value && !dashboardQuery.data.value"
          class="calendar-state"
        >
          <AppIcon name="clock" />
          <span>이번 달 마감 일정을 불러오는 중…</span>
        </div>
        <div
          v-else-if="dashboardUnavailable"
          class="calendar-state calendar-state--error"
          role="alert"
        >
          <AppIcon name="alert" />
          <span>이 달의 마감 일정을 확인하지 못했어요.</span>
          <button
            type="button"
            class="button button--secondary button--compact"
            @click="dashboardQuery.refetch()"
          >
            다시 확인
          </button>
        </div>
        <div v-else class="calendar-layout">
          <div class="calendar-card">
            <div class="calendar-card__meta">
              <span>{{ monthLabel }} 활성 마감</span>
              <strong>{{ deadlineCount }}건</strong>
            </div>
            <div class="calendar-weekdays" aria-hidden="true">
              <span>일</span><span>월</span><span>화</span><span>수</span><span>목</span
              ><span>금</span><span>토</span>
            </div>
            <div class="calendar-grid" role="grid" :aria-label="`${monthLabel} 공고 마감 일정`">
              <template v-for="cell in calendarCells" :key="cell.key">
                <span
                  v-if="cell.date === null"
                  class="calendar-day calendar-day--blank"
                  aria-hidden="true"
                />
                <button
                  v-else
                  type="button"
                  class="calendar-day"
                  :class="{
                    'calendar-day--selected': selectedDate === cell.date,
                    'calendar-day--today': cell.isToday,
                    'calendar-day--has-deadline': cell.count > 0,
                  }"
                  :aria-pressed="selectedDate === cell.date"
                  :aria-label="`${cell.date}, 마감 공고 ${cell.count}건${cell.isToday ? ', 오늘' : ''}`"
                  @click="selectedDate = cell.date"
                >
                  <span>{{ cell.day }}</span>
                  <strong v-if="cell.count > 0">{{ cell.count }}</strong>
                  <small v-if="cell.isToday">오늘</small>
                </button>
              </template>
            </div>
          </div>

          <aside class="deadline-detail deadline-detail--desktop" aria-live="polite">
            <header>
              <div>
                <small>선택한 날짜</small>
                <h3>{{ selectedDate }}</h3>
              </div>
              <span>{{ selectedDeadlineItems.length }}건</span>
            </header>
            <ul v-if="selectedDeadlineItems.length" class="deadline-items">
              <li v-for="job in selectedDeadlineItems" :key="job.id">
                <span class="deadline-items__status">{{
                  job.status === 'SUBMITTED' ? '지원 완료' : '준비 중'
                }}</span>
                <strong>{{ deadlineTitle(job) }}</strong>
                <small>{{ jobCompanyLabel(job.companyName) }}</small>
                <time :datetime="job.deadlineAt">{{ formatDeadlineDateTime(job.deadlineAt) }}</time>
                <RouterLink :to="`/jobs/${job.id}/overview`"
                  >공고 상세 <AppIcon name="arrow-right"
                /></RouterLink>
              </li>
            </ul>
            <div v-else class="compact-empty compact-empty--calendar">
              <AppIcon name="calendar" />
              <div>
                <strong>이날 마감되는 공고가 없어요.</strong>
                <p>다른 날짜를 선택하거나 새 공고에 마감 시각을 입력해 보세요.</p>
              </div>
            </div>
          </aside>

          <details class="deadline-detail deadline-detail--mobile" open>
            <summary>{{ selectedDate }} 마감 공고 {{ selectedDeadlineItems.length }}건</summary>
            <ul v-if="selectedDeadlineItems.length" class="deadline-items">
              <li v-for="job in selectedDeadlineItems" :key="job.id">
                <span class="deadline-items__status">{{
                  job.status === 'SUBMITTED' ? '지원 완료' : '준비 중'
                }}</span>
                <strong>{{ deadlineTitle(job) }}</strong>
                <small>{{ jobCompanyLabel(job.companyName) }}</small>
                <time :datetime="job.deadlineAt">{{ formatDeadlineDateTime(job.deadlineAt) }}</time>
                <RouterLink :to="`/jobs/${job.id}/overview`"
                  >공고 상세 <AppIcon name="arrow-right"
                /></RouterLink>
              </li>
            </ul>
            <p v-else class="deadline-detail__empty">이날 마감되는 공고가 없어요.</p>
          </details>
        </div>
      </section>

      <div class="dashboard-columns">
        <section class="dashboard-section" aria-labelledby="activity-heading">
          <div class="dashboard-section-heading">
            <div>
              <p class="section-kicker">최근 업데이트</p>
              <h2 id="activity-heading">최근 활동</h2>
            </div>
            <RouterLink to="/agent-runs" class="text-link"
              >AI 작업 <AppIcon name="arrow-right"
            /></RouterLink>
          </div>
          <ul v-if="recentActivity.length" class="activity-list">
            <li v-for="activity in recentActivity" :key="activity.key">
              <RouterLink :to="activity.to">
                <span>
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
            <AppIcon
              :name="
                recentDocumentsQuery.isError.value ||
                recentJobsQuery.isError.value ||
                recentRunsQuery.isError.value
                  ? 'alert'
                  : 'inbox'
              "
            />
            <div>
              <strong>{{
                recentDocumentsQuery.isError.value ||
                recentJobsQuery.isError.value ||
                recentRunsQuery.isError.value
                  ? '최근 활동을 모두 확인하지 못했어요.'
                  : '아직 최근 활동이 없어요.'
              }}</strong>
              <p>
                {{
                  recentDocumentsQuery.isError.value ||
                  recentJobsQuery.isError.value ||
                  recentRunsQuery.isError.value
                    ? '확인되지 않은 항목을 0건으로 계산하지 않았어요.'
                    : '자료나 공고를 등록하면 준비 기록이 이곳에 나타나요.'
                }}
              </p>
            </div>
          </div>
        </section>

        <aside class="workspace-note" aria-labelledby="workspace-note-heading">
          <span class="workspace-note__art" aria-hidden="true"><AppIcon name="guide" /></span>
          <p class="section-kicker">준비 워크스페이스</p>
          <h2 id="workspace-note-heading">한 번 정리한 정보는 다음 지원에도 이어져요.</h2>
          <p>
            내 정보와 등록한 이력서·자료를 먼저 다듬어 두면 공고 분석부터 자기소개서와 면접 준비까지
            같은 근거를 활용할 수 있어요.
          </p>
          <RouterLink to="/guide" class="text-link"
            >전체 이용 순서 보기 <AppIcon name="arrow-right"
          /></RouterLink>
        </aside>
      </div>

      <section class="guide-section" aria-labelledby="guide-heading">
        <div class="dashboard-section-heading">
          <div>
            <p class="section-kicker">5분 커리어 노트</p>
            <h2 id="guide-heading">취업 준비 가이드</h2>
            <p>지금 필요한 주제만 짧게 읽고 바로 준비에 적용해 보세요.</p>
          </div>
        </div>
        <StatePanel
          v-if="guideQuery.isPending.value"
          kind="loading"
          title="가이드를 불러오는 중…"
          description="준비에 도움이 될 내용을 확인하고 있어요."
        />
        <div v-else-if="guideQuery.isError.value" class="guide-state" role="alert">
          <AppIcon name="alert" />
          <span>취업 준비 가이드를 불러오지 못했어요.</span>
          <button
            type="button"
            class="button button--secondary button--compact"
            @click="guideQuery.refetch()"
          >
            다시 확인
          </button>
        </div>
        <div v-else-if="guideQuery.data.value?.length" class="guide-grid">
          <button
            v-for="(post, index) in guideQuery.data.value"
            :key="post.id"
            type="button"
            class="guide-card"
            @click="openGuide(post, $event)"
          >
            <span class="guide-card__number">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="guide-card__icon"
              ><AppIcon :name="index === 3 ? 'interview' : index === 4 ? 'check' : 'guide'"
            /></span>
            <small>{{ post.category }}</small>
            <strong>{{ post.title }}</strong>
            <span>{{ post.summary }}</span>
            <em>읽어보기 <AppIcon name="arrow-right" /></em>
          </button>
        </div>
        <div v-else class="guide-state">
          <AppIcon name="inbox" />
          <span>현재 게시된 취업 준비 가이드가 없어요.</span>
        </div>
      </section>
    </template>

    <Teleport to="body">
      <div v-if="selectedGuide" class="guide-modal-backdrop" @click.self="closeGuide">
        <article
          ref="guideDialog"
          class="guide-modal"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="`guide-modal-title-${selectedGuide.id}`"
          tabindex="-1"
        >
          <header>
            <span class="guide-modal__category">{{ selectedGuide.category }}</span>
            <button
              ref="guideCloseButton"
              type="button"
              aria-label="가이드 닫기"
              @click="closeGuide"
            >
              <AppIcon name="close" />
            </button>
          </header>
          <div class="guide-modal__body">
            <p class="section-kicker">CAREER NOTE</p>
            <h2 :id="`guide-modal-title-${selectedGuide.id}`">{{ selectedGuide.title }}</h2>
            <p class="guide-modal__summary">{{ selectedGuide.summary }}</p>
            <div class="guide-modal__content">{{ selectedGuide.body }}</div>
          </div>
          <footer>
            <small
              >{{
                new Intl.DateTimeFormat('ko-KR', {
                  timeZone: SEOUL_TIME_ZONE,
                  dateStyle: 'medium',
                }).format(new Date(selectedGuide.publishedAt))
              }}
              게시</small
            >
            <button type="button" class="button button--primary" @click="closeGuide">
              확인했어요
            </button>
          </footer>
        </article>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.dashboard {
  width: min(100%, 88rem);
  margin-inline: auto;
  display: grid;
  gap: clamp(2rem, 4vw, 3.5rem);
}

.dashboard :deep(.page-header) {
  align-items: center;
}
.dashboard :deep(.page-header__description) {
  max-width: 38rem;
}
.section-kicker {
  margin: 0 0 0.35rem;
  color: var(--color-primary);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.dashboard h2,
.dashboard h3,
.dashboard p {
  margin-top: 0;
}
.dashboard h2 {
  letter-spacing: -0.035em;
}

.dashboard-error {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 1rem;
  align-items: center;
  padding: 1rem 1.125rem;
  border: 1px solid color-mix(in srgb, var(--color-danger) 24%, var(--color-border));
  border-radius: var(--radius-lg);
  background: var(--color-danger-soft);
}
.dashboard-error__icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: 0.8rem;
  color: var(--color-danger);
  background: var(--color-surface);
}
.dashboard-error strong {
  color: var(--color-ink);
}
.dashboard-error p {
  margin: 0.2rem 0 0;
  color: var(--color-muted);
  font-size: 0.875rem;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(25rem, 1.1fr);
  gap: 1.25rem;
  align-items: stretch;
}
.career-card,
.priority-card,
.deadline-section,
.dashboard-section,
.workspace-note,
.guide-section {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}
.career-card {
  position: relative;
  overflow: hidden;
  padding: clamp(1.25rem, 2.5vw, 2rem);
  color: white;
  border: 0;
  background: linear-gradient(145deg, var(--hs-blue-900) 0%, var(--hs-blue-700) 58%, #3157ff 100%);
  box-shadow: 0 24px 56px rgb(22 58 170 / 20%);
}
.career-card::before,
.career-card::after {
  position: absolute;
  content: '';
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 50%;
  pointer-events: none;
}
.career-card::before {
  width: 15rem;
  height: 15rem;
  top: -8rem;
  right: -4rem;
}
.career-card::after {
  width: 9rem;
  height: 9rem;
  right: 2rem;
  bottom: -6rem;
  background: rgb(255 255 255 / 5%);
}
.career-card > * {
  position: relative;
  z-index: 1;
}
.career-card__identity {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.85rem;
  align-items: center;
}
.career-card__monogram {
  display: grid;
  width: 3.25rem;
  height: 3.25rem;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 1rem;
  background: rgb(255 255 255 / 13%);
  font-size: 1.25rem;
  font-weight: 800;
}
.career-card__identity p {
  margin-bottom: 0.1rem;
  color: rgb(255 255 255 / 65%);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}
.career-card__identity h2 {
  margin: 0;
  color: white;
  font-size: clamp(1.25rem, 2vw, 1.55rem);
}
.career-card__readiness {
  padding: 0.38rem 0.65rem;
  border-radius: 999px;
  color: var(--hs-blue-900);
  background: #dff9b8;
  font-size: 0.75rem;
  font-weight: 800;
}
.career-card__readiness--unknown {
  color: white;
  background: rgb(255 255 255 / 15%);
}
.career-card__role {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-top: 1.75rem;
  padding: 1rem;
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 1rem;
  background: rgb(255 255 255 / 8%);
}
.career-card__role-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: 0.75rem;
  color: var(--hs-blue-100);
  background: rgb(255 255 255 / 10%);
}
.career-card__role small,
.career-card__facts dt,
.career-card__progress small {
  display: block;
  color: rgb(255 255 255 / 65%);
  font-size: 0.75rem;
}
.career-card__role strong {
  display: block;
  margin-top: 0.18rem;
  font-size: 1.05rem;
}
.career-card__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 1rem 0 1.35rem;
}
.career-card__facts div {
  min-width: 0;
}
.career-card__facts dt {
  display: flex;
  gap: 0.35rem;
  align-items: center;
}
.career-card__facts dt :deep(.icon) {
  width: 0.9rem;
  height: 0.9rem;
}
.career-card__facts dd {
  margin: 0.3rem 0 0;
  overflow: hidden;
  font-size: 0.82rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.career-card__progress {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}
.career-card__progress > strong {
  font-size: 1.35rem;
}
.career-card__track {
  width: 100%;
  height: 0.42rem;
  margin-top: 0.6rem;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  background: rgb(255 255 255 / 18%);
}
.career-card__track::-webkit-progress-bar {
  background: rgb(255 255 255 / 18%);
}
.career-card__track::-webkit-progress-value {
  border-radius: 999px;
  background: #dff9b8;
}
.career-card__track::-moz-progress-bar {
  border-radius: 999px;
  background: #dff9b8;
}
.career-card__cta {
  display: inline-flex;
  gap: 0.35rem;
  align-items: center;
  margin-top: 1.2rem;
  color: white;
  font-size: 0.875rem;
  font-weight: 800;
}
.career-card__cta :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.start-checklist {
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid rgb(255 255 255 / 14%);
}
.start-checklist__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.start-checklist__heading h3 {
  margin: 0;
  color: white;
  font-size: 0.9rem;
}
.start-checklist__heading span {
  color: rgb(255 255 255 / 65%);
  font-size: 0.75rem;
}
.start-checklist ul {
  display: grid;
  gap: 0.45rem;
  margin: 0.75rem 0 0;
  padding: 0;
  list-style: none;
}
.start-checklist li {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.5rem;
  align-items: center;
  min-height: 2rem;
  color: rgb(255 255 255 / 78%);
  font-size: 0.78rem;
}
.start-checklist li :deep(.icon) {
  width: 0.95rem;
  height: 0.95rem;
}
.start-checklist li[data-state='completed'] {
  color: #dff9b8;
}
.start-checklist a,
.start-checklist button,
.start-checklist small {
  padding: 0;
  color: white;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 0.18rem;
}

.priority-card {
  padding: clamp(1.25rem, 2.5vw, 2rem);
  background: linear-gradient(160deg, var(--color-surface) 0%, var(--hs-blue-50) 100%);
}
.priority-card > header {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  margin-bottom: 1rem;
}
.priority-card__icon {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border-radius: 0.9rem;
  color: var(--color-primary);
  background: var(--hs-blue-100);
}
.priority-card h2 {
  margin: 0;
  font-size: 1.35rem;
}
.task-list {
  display: grid;
  gap: 0.65rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.task-list a {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  gap: 0.75rem;
  align-items: center;
  min-height: 5rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
  transition:
    border-color 160ms ease,
    transform 160ms ease,
    box-shadow 160ms ease;
}
.task-list a:hover {
  border-color: var(--hs-blue-300);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.task-item__order {
  color: var(--color-subtle);
  font-size: 0.7rem;
  font-weight: 800;
}
.task-item__icon {
  display: grid;
  width: 2.35rem;
  height: 2.35rem;
  place-items: center;
  border-radius: 0.75rem;
  color: var(--color-primary);
  background: var(--hs-blue-50);
}
.task-item--warning .task-item__icon {
  color: var(--color-warning);
  background: var(--color-warning-soft);
}
.task-item--success .task-item__icon {
  color: var(--color-success);
  background: var(--color-success-soft);
}
.task-item__body {
  min-width: 0;
}
.task-item__body strong,
.task-item__body small {
  display: block;
}
.task-item__body strong {
  color: var(--color-ink);
  font-size: 0.9rem;
}
.task-item__body small {
  margin-top: 0.22rem;
  color: var(--color-muted);
  line-height: 1.45;
}
.task-item__action {
  color: var(--color-primary);
}
.task-item__action :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.dashboard-section-heading,
.deadline-section__heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: end;
}
.dashboard-section-heading h2,
.deadline-section__heading h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: clamp(1.25rem, 2vw, 1.6rem);
}
.dashboard-section-heading p:last-child,
.deadline-section__heading p:last-child {
  margin: 0.35rem 0 0;
  color: var(--color-muted);
  font-size: 0.875rem;
}
.text-link {
  display: inline-flex;
  gap: 0.3rem;
  align-items: center;
  color: var(--color-primary);
  font-size: 0.85rem;
  font-weight: 800;
}
.text-link :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.summary-section {
  display: grid;
  gap: 1rem;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}
.summary-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.8rem;
  align-items: center;
  min-height: 6.25rem;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}
.summary-card--primary {
  color: white;
  border-color: var(--hs-blue-700);
  background: var(--hs-blue-800);
}
.summary-card__icon {
  display: grid;
  width: 2.6rem;
  height: 2.6rem;
  place-items: center;
  border-radius: 0.82rem;
  color: var(--color-primary);
  background: var(--hs-blue-50);
}
.summary-card--primary .summary-card__icon {
  color: white;
  background: rgb(255 255 255 / 13%);
}
.summary-card__icon--success {
  color: var(--color-success);
  background: var(--color-success-soft);
}
.summary-card small,
.summary-card strong {
  display: block;
}
.summary-card small {
  color: var(--color-muted);
  font-size: 0.76rem;
}
.summary-card--primary small {
  color: rgb(255 255 255 / 70%);
}
.summary-card strong {
  margin-top: 0.15rem;
  font-size: 1.65rem;
  line-height: 1;
}
.summary-card > :deep(.icon) {
  width: 1rem;
  height: 1rem;
  color: var(--color-subtle);
}
.summary-card--primary > :deep(.icon) {
  color: rgb(255 255 255 / 70%);
}

.deadline-section {
  padding: clamp(1.25rem, 2.8vw, 2rem);
}
.calendar-controls {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}
.calendar-controls button {
  display: grid;
  min-width: 2.5rem;
  min-height: 2.5rem;
  place-items: center;
  padding: 0 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 0.7rem;
  color: var(--color-ink);
  background: var(--color-surface);
}
.calendar-controls strong {
  min-width: 7.5rem;
  text-align: center;
}
.calendar-controls__today {
  color: var(--color-primary) !important;
  font-weight: 800;
}
.calendar-state,
.guide-state {
  display: flex;
  gap: 0.65rem;
  align-items: center;
  justify-content: center;
  min-height: 9rem;
  margin-top: 1.25rem;
  padding: 1rem;
  border-radius: var(--radius-lg);
  color: var(--color-muted);
  background: var(--color-canvas);
}
.calendar-state--error {
  color: var(--color-danger);
}
.calendar-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(18rem, 0.75fr);
  gap: 1rem;
  margin-top: 1.25rem;
}
.calendar-card {
  min-width: 0;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-canvas);
}
.calendar-card__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.8rem;
  color: var(--color-muted);
  font-size: 0.78rem;
}
.calendar-card__meta strong {
  color: var(--color-primary);
}
.calendar-weekdays,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}
.calendar-weekdays span {
  padding: 0.35rem;
  color: var(--color-subtle);
  font-size: 0.72rem;
  font-weight: 800;
  text-align: center;
}
.calendar-weekdays span:first-child {
  color: var(--color-danger);
}
.calendar-day {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 5rem;
  align-content: start;
  justify-items: start;
  padding: 0.55rem;
  border: 1px solid transparent;
  border-radius: 0.8rem;
  color: var(--color-ink);
  background: transparent;
}
.calendar-day:hover {
  background: var(--hs-blue-50);
}
.calendar-day > span {
  font-size: 0.8rem;
  font-weight: 700;
}
.calendar-day > strong {
  display: grid;
  min-width: 1.45rem;
  height: 1.45rem;
  place-items: center;
  margin-top: 0.45rem;
  padding-inline: 0.3rem;
  border-radius: 999px;
  color: white;
  background: var(--color-primary);
  font-size: 0.7rem;
}
.calendar-day > small {
  position: absolute;
  top: 0.5rem;
  right: 0.45rem;
  color: var(--color-primary);
  font-size: 0.62rem;
  font-weight: 800;
}
.calendar-day--selected {
  border-color: var(--color-primary);
  background: var(--hs-blue-50);
  box-shadow: 0 0 0 1px var(--color-primary);
}
.calendar-day--today > span {
  color: var(--color-primary);
}
.calendar-day--blank {
  display: block;
  min-height: 5rem;
}
.deadline-detail {
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  background: var(--color-surface);
}
.deadline-detail > header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
  padding-bottom: 0.8rem;
  border-bottom: 1px solid var(--color-border);
}
.deadline-detail header small {
  color: var(--color-muted);
}
.deadline-detail header h3 {
  margin: 0.15rem 0 0;
  font-size: 1.05rem;
}
.deadline-detail header > span {
  padding: 0.3rem 0.5rem;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--hs-blue-50);
  font-size: 0.75rem;
  font-weight: 800;
}
.deadline-items {
  display: grid;
  gap: 0.65rem;
  max-height: 28rem;
  margin: 0.8rem 0 0;
  padding: 0;
  overflow: auto;
  list-style: none;
}
.deadline-items li {
  display: grid;
  gap: 0.2rem;
  padding: 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 0.85rem;
}
.deadline-items__status {
  width: fit-content;
  padding: 0.2rem 0.4rem;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--hs-blue-50);
  font-size: 0.66rem;
  font-weight: 800;
}
.deadline-items strong {
  margin-top: 0.2rem;
  font-size: 0.88rem;
}
.deadline-items small,
.deadline-items time {
  color: var(--color-muted);
  font-size: 0.75rem;
}
.deadline-items a {
  display: inline-flex;
  gap: 0.25rem;
  align-items: center;
  width: fit-content;
  margin-top: 0.35rem;
  color: var(--color-primary);
  font-size: 0.78rem;
  font-weight: 800;
}
.deadline-items a :deep(.icon) {
  width: 0.9rem;
  height: 0.9rem;
}
.deadline-detail--mobile {
  display: none;
}

.dashboard-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(20rem, 0.65fr);
  gap: 1rem;
  align-items: stretch;
}
.dashboard-section,
.workspace-note,
.guide-section {
  padding: clamp(1.2rem, 2.5vw, 1.75rem);
}
.activity-list {
  margin: 0.85rem 0 0;
  padding: 0;
  list-style: none;
}
.activity-list li + li {
  border-top: 1px solid var(--color-border);
}
.activity-list a {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 1rem;
  align-items: center;
  padding: 0.8rem 0;
}
.activity-list small,
.activity-list strong {
  display: block;
}
.activity-list small {
  color: var(--color-muted);
  font-size: 0.72rem;
}
.activity-list strong {
  margin-top: 0.16rem;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-list__meta {
  display: grid;
  justify-items: end;
  color: var(--color-muted);
  font-size: 0.72rem;
}
.activity-list__meta > span {
  color: var(--color-primary);
  font-weight: 800;
}
.workspace-note {
  position: relative;
  overflow: hidden;
  color: white;
  border: 0;
  background: linear-gradient(150deg, #17265f, #3157ff);
}
.workspace-note::after {
  position: absolute;
  width: 10rem;
  height: 10rem;
  right: -4rem;
  bottom: -4rem;
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 50%;
  content: '';
}
.workspace-note > * {
  position: relative;
  z-index: 1;
}
.workspace-note__art {
  display: grid;
  width: 3.25rem;
  height: 3.25rem;
  place-items: center;
  margin-bottom: 1.5rem;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 1rem;
  background: rgb(255 255 255 / 10%);
}
.workspace-note .section-kicker {
  color: var(--hs-blue-100);
}
.workspace-note h2 {
  color: white;
  font-size: 1.25rem;
  line-height: 1.35;
}
.workspace-note p:not(.section-kicker) {
  color: rgb(255 255 255 / 72%);
  font-size: 0.85rem;
  line-height: 1.65;
}
.workspace-note .text-link {
  color: white;
}
.compact-empty {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  padding: 1rem;
  border-radius: 0.85rem;
  color: var(--color-muted);
  background: var(--color-canvas);
}
.compact-empty strong {
  color: var(--color-ink);
}
.compact-empty p {
  margin: 0.25rem 0 0;
  font-size: 0.8rem;
}
.compact-empty--calendar {
  margin-top: 0.8rem;
}

.guide-section {
  display: grid;
  gap: 1rem;
}
.guide-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.75rem;
}
.guide-card {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 16rem;
  align-content: start;
  justify-items: start;
  padding: 1rem;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 1rem;
  color: var(--color-ink);
  background: var(--color-surface);
  text-align: left;
  transition:
    border-color 160ms ease,
    transform 160ms ease,
    box-shadow 160ms ease;
}
.guide-card:hover {
  border-color: var(--hs-blue-300);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.guide-card__number {
  position: absolute;
  top: 0.8rem;
  right: 0.8rem;
  color: var(--hs-blue-100);
  font-size: 2rem;
  font-weight: 900;
  line-height: 1;
}
.guide-card__icon {
  display: grid;
  width: 2.65rem;
  height: 2.65rem;
  place-items: center;
  margin-bottom: 1.2rem;
  border-radius: 0.8rem;
  color: var(--color-primary);
  background: var(--hs-blue-50);
}
.guide-card small {
  color: var(--color-primary);
  font-size: 0.7rem;
  font-weight: 800;
}
.guide-card > strong {
  margin-top: 0.45rem;
  font-size: 0.92rem;
  line-height: 1.45;
}
.guide-card > span:not(.guide-card__number, .guide-card__icon) {
  margin-top: 0.55rem;
  color: var(--color-muted);
  font-size: 0.78rem;
  line-height: 1.55;
}
.guide-card em {
  display: inline-flex;
  gap: 0.25rem;
  align-items: center;
  align-self: end;
  margin-top: 1rem;
  color: var(--color-primary);
  font-size: 0.76rem;
  font-style: normal;
  font-weight: 800;
}
.guide-card em :deep(.icon) {
  width: 0.9rem;
  height: 0.9rem;
}

.guide-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgb(12 18 38 / 66%);
  backdrop-filter: blur(5px);
}
.guide-modal {
  width: min(100%, 42rem);
  max-height: min(48rem, calc(100dvh - 2rem));
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 1.25rem;
  background: var(--color-surface);
  box-shadow: 0 30px 90px rgb(8 18 48 / 35%);
}
.guide-modal > header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--color-border);
  background: rgb(255 255 255 / 96%);
  backdrop-filter: blur(12px);
}
.guide-modal__category {
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--hs-blue-50);
  font-size: 0.75rem;
  font-weight: 800;
}
.guide-modal > header button {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 0;
  border-radius: 0.75rem;
  color: var(--color-ink);
  background: var(--color-canvas);
}
.guide-modal__body {
  padding: clamp(1.25rem, 4vw, 2.25rem);
}
.guide-modal__body h2 {
  margin-bottom: 0.8rem;
  color: var(--color-ink);
  font-size: clamp(1.5rem, 4vw, 2rem);
}
.guide-modal__summary {
  color: var(--color-muted);
  font-size: 0.95rem;
  line-height: 1.65;
}
.guide-modal__content {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-border);
  color: var(--color-ink);
  font-size: 0.95rem;
  line-height: 1.85;
  white-space: pre-line;
}
.guide-modal > footer {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  padding: 1rem 1.25rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-canvas);
}
.guide-modal > footer small {
  color: var(--color-muted);
}

@media (max-width: 74rem) {
  .dashboard-hero,
  .calendar-layout {
    grid-template-columns: 1fr;
  }
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .guide-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .deadline-detail--desktop {
    display: none;
  }
  .deadline-detail--mobile {
    display: block;
    padding: 0;
    overflow: hidden;
  }
  .deadline-detail--mobile summary {
    padding: 1rem;
    color: var(--color-ink);
    font-weight: 800;
    cursor: pointer;
  }
  .deadline-detail--mobile .deadline-items,
  .deadline-detail__empty {
    margin: 0;
    padding: 0 1rem 1rem;
  }
}

@media (max-width: 52rem) {
  .dashboard-columns {
    grid-template-columns: 1fr;
  }
  .guide-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .dashboard {
    gap: 2rem;
  }
  .dashboard-error {
    grid-template-columns: auto 1fr;
  }
  .dashboard-error .button {
    grid-column: 1 / -1;
    width: 100%;
  }
  .career-card__identity {
    grid-template-columns: auto 1fr;
  }
  .career-card__readiness {
    grid-column: 1 / -1;
    width: fit-content;
  }
  .career-card__facts {
    grid-template-columns: 1fr;
  }
  .summary-grid {
    grid-template-columns: 1fr;
  }
  .summary-card {
    min-height: 5.4rem;
  }
  .deadline-section__heading {
    align-items: stretch;
    flex-direction: column;
  }
  .calendar-controls {
    display: grid;
    grid-template-columns: auto 1fr auto auto;
  }
  .calendar-controls strong {
    min-width: 0;
  }
  .calendar-card {
    padding: 0.5rem;
  }
  .calendar-day,
  .calendar-day--blank {
    min-height: 3.75rem;
    padding: 0.35rem;
  }
  .calendar-day > strong {
    min-width: 1.15rem;
    height: 1.15rem;
    margin-top: 0.25rem;
    font-size: 0.62rem;
  }
  .calendar-day > small {
    display: none;
  }
  .activity-list a {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.35rem;
  }
  .activity-list__meta {
    display: flex;
    justify-content: space-between;
  }
  .guide-grid {
    grid-template-columns: 1fr;
  }
  .guide-card {
    min-height: 12rem;
  }
  .guide-modal-backdrop {
    align-items: end;
    padding: 0;
  }
  .guide-modal {
    max-height: 92dvh;
    border-radius: 1.25rem 1.25rem 0 0;
  }
  .guide-modal > footer {
    align-items: stretch;
    flex-direction: column;
  }
  .guide-modal > footer .button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .task-list a,
  .guide-card {
    transition: none;
  }
}
</style>
