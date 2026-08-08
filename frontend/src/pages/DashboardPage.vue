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
import CareerArtifactSuggestion from '@/features/career-artifacts/CareerArtifactSuggestion.vue'
import { jobCompanyLabel, jobDisplayTitle } from '@/features/jobs/presentation'
import { useJobListQuery } from '@/features/jobs/queries'
import type { DocumentSummaryDto } from '@/shared/api/documentContracts'
import * as dashboardApi from '@/shared/api/dashboardApi'
import type { CareerGuidePostDto, DashboardDeadlineJobDto } from '@/shared/api/dashboardContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useQuery } from '@tanstack/vue-query'

const SEOUL_TIME_ZONE = 'Asia/Seoul'
const DEADLINE_SOON_DAYS = 7
const COUNT_UP_DURATION_MS = 520
const GUIDE_CATEGORY_ICONS: Record<string, GuideIconName> = {
  '공고 분석': 'target',
  '공고 관리': 'flag',
  이력서: 'documents',
  '이력서·자료': 'documents',
  '경험 정리': 'evidence',
  자기소개서: 'pen',
  '강점 선택': 'trophy',
  면접: 'interview',
  '면접 준비': 'interview',
  '최종 점검': 'check',
  '지원 관리': 'calendar',
  '커리어 설계': 'compass',
  성장: 'trend-up',
} as const
/* 한국어 본문을 꼼꼼히 읽을 때의 분당 글자 수(공백 제외). 카드에 표시할 대략적인 분량만 계산한다. */
const GUIDE_CHARS_PER_MINUTE = 350
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
const selectedGuideNumber = computed(() => {
  const index = (guideQuery.data.value ?? []).findIndex(
    (guide) => guide.id === selectedGuide.value?.id,
  )
  return index < 0 ? '01' : String(index + 1).padStart(2, '0')
})
const selectedGuideBlocks = computed(() => guideBlocks(selectedGuide.value?.body ?? ''))
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

const dashboardName = computed(
  () =>
    dashboardQuery.data.value?.profile.displayName.trim() ||
    authStore.currentUser?.displayName.trim() ||
    '',
)
const dashboardTitle = computed(() =>
  dashboardName.value === '' ? '지원 준비 현황' : `${dashboardName.value}님의 지원 준비 현황`,
)
const profile = computed(() => dashboardQuery.data.value?.profile ?? null)
const dashboardUnavailable = computed(() => dashboardQuery.isError.value)
const deadlineDays = computed(() => dashboardQuery.data.value?.deadlineDays ?? [])
const deadlineCount = computed(() =>
  deadlineDays.value.reduce((total, day) => total + day.count, 0),
)
const deadlineCountLabel = computed(() =>
  dashboardQuery.isPending.value || dashboardUnavailable.value ? '—' : `${deadlineCount.value}건`,
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

type GuideIconName =
  | 'target'
  | 'flag'
  | 'documents'
  | 'evidence'
  | 'pen'
  | 'trophy'
  | 'interview'
  | 'check'
  | 'calendar'
  | 'compass'
  | 'trend-up'
  | 'guide'

type SummaryTone = 'primary' | 'success' | 'brand' | 'neutral'
type SummaryCard = {
  key: string
  to: string
  label: string
  icon: 'jobs' | 'check' | 'runs' | 'documents'
  tone: SummaryTone
  value: number | null
  hint: string
}

const summaryCards = computed<SummaryCard[]>(() => {
  const dashboard = dashboardQuery.data.value
  const unavailable = dashboardUnavailable.value
  const registeredJobs = dashboard?.jobs.registeredCount ?? 0
  const processingDocuments = dashboard?.documents.processingCount ?? 0
  return [
    {
      key: 'preparing',
      to: '/jobs?status=IN_PROGRESS',
      label: '준비 중인 공고',
      icon: 'jobs',
      tone: 'primary',
      value: unavailable ? null : (dashboard?.jobs.preparingCount ?? 0),
      hint: unavailable ? '수치를 확인하지 못했어요' : `등록한 공고 ${registeredJobs}건 중`,
    },
    {
      key: 'submitted',
      to: '/jobs?status=SUBMITTED',
      label: '지원 완료',
      icon: 'check',
      tone: 'success',
      value: unavailable ? null : (dashboard?.jobs.submittedCount ?? 0),
      hint: unavailable ? '수치를 확인하지 못했어요' : `등록한 공고 ${registeredJobs}건 중`,
    },
    {
      key: 'runs',
      to: '/agent-runs',
      label: 'AI가 확인 중',
      icon: 'runs',
      tone: 'brand',
      value: unavailable ? null : (dashboard?.agentRuns.activeCount ?? 0),
      hint: unavailable ? '수치를 확인하지 못했어요' : '실행 중인 AI 작업',
    },
    {
      key: 'documents',
      to: '/documents',
      label: '등록한 이력서·자료',
      icon: 'documents',
      tone: 'neutral',
      value: unavailable ? null : (dashboard?.documents.registeredCount ?? 0),
      hint: unavailable
        ? '수치를 확인하지 못했어요'
        : processingDocuments > 0
          ? `분석 중 ${processingDocuments}건`
          : '모든 자료 분석 완료',
    },
  ]
})

const countedValues = ref<Record<string, number>>({})
const countFrames = new Map<string, number>()

watch(
  summaryCards,
  (cards) => {
    for (const card of cards) {
      if (card.value === null) continue
      animateCount(card.key, card.value)
    }
  },
  { immediate: true },
)

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function animateCount(key: string, target: number): void {
  const frame = countFrames.get(key)
  if (frame !== undefined) cancelAnimationFrame(frame)
  const from = countedValues.value[key] ?? 0
  if (from === target) return
  if (typeof requestAnimationFrame !== 'function' || prefersReducedMotion()) {
    countedValues.value = { ...countedValues.value, [key]: target }
    return
  }
  const startedAt = performance.now()
  const step = (now: number): void => {
    const progress = Math.min(1, (now - startedAt) / COUNT_UP_DURATION_MS)
    const eased = 1 - (1 - progress) ** 3
    countedValues.value = {
      ...countedValues.value,
      [key]: Math.round(from + (target - from) * eased),
    }
    if (progress < 1) countFrames.set(key, requestAnimationFrame(step))
    else countFrames.delete(key)
  }
  countFrames.set(key, requestAnimationFrame(step))
}

function summaryValueLabel(card: SummaryCard): string {
  if (card.value === null) return '—'
  return String(countedValues.value[card.key] ?? card.value)
}

function guideIcon(category: string, index: number): GuideIconName {
  const fallbacks: GuideIconName[] = ['target', 'documents', 'pen', 'interview', 'calendar']
  return GUIDE_CATEGORY_ICONS[category.trim()] ?? fallbacks[index % fallbacks.length] ?? 'guide'
}

/* 본문은 빈 줄로 나뉜 문단이고, 모든 줄이 "- "로 시작하는 덩어리만 목록으로 읽는다. */
type GuideBlock = { kind: 'paragraph'; text: string } | { kind: 'list'; items: string[] }

function guideBlocks(body: string): GuideBlock[] {
  return body
    .split(/\n{2,}/)
    .map((block) =>
      block
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean),
    )
    .filter((lines) => lines.length > 0)
    .map((lines) =>
      lines.every((line) => line.startsWith('- '))
        ? { kind: 'list', items: lines.map((line) => line.slice(2).trim()) }
        : { kind: 'paragraph', text: lines.join(' ') },
    )
}

function guideReadMinutes(post: CareerGuidePostDto): number {
  const characters = post.body.replace(/\s/g, '').length
  return Math.max(1, Math.round(characters / GUIDE_CHARS_PER_MINUTE))
}

type DeadlineTone = 'passed' | 'today' | 'urgent' | 'soon' | 'normal'

function daysUntil(date: string): number | null {
  const target = Date.parse(`${date}T00:00:00+09:00`)
  const today = Date.parse(`${seoulToday()}T00:00:00+09:00`)
  if (Number.isNaN(target) || Number.isNaN(today)) return null
  return Math.round((target - today) / 86_400_000)
}

function ddayLabel(value: string): string {
  const date = value.length > 10 ? seoulDate(value) : value
  const days = daysUntil(date)
  if (days === null) return '마감일 미확인'
  if (days === 0) return 'D-DAY'
  return days > 0 ? `D-${days}` : `D+${Math.abs(days)}`
}

function deadlineTone(value: string): DeadlineTone {
  const date = value.length > 10 ? seoulDate(value) : value
  const days = daysUntil(date)
  if (days === null) return 'normal'
  if (days < 0) return 'passed'
  if (days === 0) return 'today'
  if (days <= 3) return 'urgent'
  if (days <= DEADLINE_SOON_DAYS) return 'soon'
  return 'normal'
}

function seoulDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: SEOUL_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

type ActivityIconName = 'documents' | 'jobs' | 'runs'

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

type ActivityItem = {
  key: string
  at: string
  icon: ActivityIconName
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
      icon: 'documents',
      eyebrow: '이력서·자료',
      title: document.displayName,
      description: documentStatus(document),
      to: `/documents/${document.id}`,
    }),
  )
  const jobs: ActivityItem[] = (recentJobsQuery.data.value?.items ?? []).map((job) => ({
    key: `job-${job.id}`,
    at: job.updatedAt,
    icon: 'jobs',
    eyebrow: jobCompanyLabel(job.companyName),
    title: jobDisplayTitle(job),
    description:
      job.status === 'SUBMITTED' ? '지원 완료' : job.status === 'CLOSED' ? '마감' : '준비 중',
    to: `/jobs/${job.id}/overview`,
  }))
  const runs: ActivityItem[] = (recentRunsQuery.data.value?.items ?? []).map((run) => ({
    key: `run-${run.id}`,
    at: run.updatedAt,
    icon: 'runs',
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
  for (const frame of countFrames.values()) cancelAnimationFrame(frame)
  countFrames.clear()
})

function moveMonth(offset: number): void {
  const [year, month] = currentMonth.value.split('-').map(Number)
  const next = new Date(Date.UTC(year, month - 1 + offset, 1))
  currentMonth.value = `${next.getUTCFullYear()}-${String(next.getUTCMonth() + 1).padStart(2, '0')}`
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
  weekday: number | null
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
    weekday: null,
    count: 0,
    isToday: false,
  }))
  for (let day = 1; day <= lastDay; day += 1) {
    const date = `${monthValue}-${String(day).padStart(2, '0')}`
    cells.push({
      key: date,
      date,
      day,
      weekday: new Date(Date.UTC(year, month - 1, day)).getUTCDay(),
      count: counts.get(date) ?? 0,
      isToday: date === seoulToday(),
    })
  }
  while (cells.length % 7 !== 0) {
    cells.push({
      key: `after-${cells.length}`,
      date: null,
      day: null,
      weekday: null,
      count: 0,
      isToday: false,
    })
  }
  return cells
}
</script>

<template>
  <section class="dashboard app-page" aria-labelledby="dashboard-heading">
    <!-- 제목 줄 없이 등록 동작만 남긴다. 화면 이름은 낭독기용으로만 유지한다. -->
    <h1 id="dashboard-heading" class="sr-only">{{ dashboardTitle }}</h1>
    <div class="dashboard-quick-entry">
      <RouterLink class="button button--secondary" to="/documents">
        <AppIcon name="upload" />
        자료 등록
      </RouterLink>
      <RouterLink class="button button--primary" to="/jobs/new">
        <AppIcon name="plus" />
        공고 등록
      </RouterLink>
    </div>

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

      <div class="dashboard-layout">
        <div class="dashboard-content">
          <section
            id="dashboard-overview"
            class="dashboard-hero"
            aria-label="사용자 커리어와 다음 행동"
          >
            <article class="career-card">
              <div class="career-card__cover" aria-hidden="true">
                <span class="career-card__sheen" />
              </div>

              <div class="career-card__main">
                <div class="career-card__profile">
                  <span class="career-card__person" aria-hidden="true">
                    <AppIcon name="person-card" />
                  </span>
                  <p class="career-card__eyebrow">MY CAREER</p>
                  <h2>{{ profile?.legalName || profile?.displayName || '지원자' }}</h2>
                  <span
                    class="career-card__readiness"
                    :class="{ 'career-card__readiness--unknown': dashboardUnavailable }"
                  >
                    {{
                      dashboardUnavailable
                        ? '준비도 확인 필요'
                        : `준비도 ${profile?.completionPercent ?? 0}%`
                    }}
                  </span>
                  <RouterLink to="/profile/basic" class="career-card__cta">
                    지원 정보 확인
                    <AppIcon name="arrow-right" />
                  </RouterLink>
                </div>

                <div class="career-card__details">
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
                </div>

                <div class="career-card__status">
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

                  <div
                    v-if="showStartChecklist"
                    class="start-checklist"
                    aria-labelledby="start-heading"
                  >
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
                </div>
              </div>
            </article>
          </section>

          <section class="summary-section" aria-labelledby="summary-heading">
            <h2 id="summary-heading" class="sr-only">지원 준비 요약</h2>
            <div class="summary-section__actions">
              <RouterLink to="/jobs" class="text-link"
                >전체 공고 <AppIcon name="arrow-right"
              /></RouterLink>
            </div>
            <div class="summary-grid">
              <RouterLink
                v-for="card in summaryCards"
                :key="card.key"
                :to="card.to"
                class="summary-card"
                :class="`summary-card--${card.tone}`"
              >
                <span class="summary-card__icon"><AppIcon :name="card.icon" /></span>
                <span class="summary-card__body">
                  <small>{{ card.label }}</small>
                  <strong>{{ summaryValueLabel(card) }}</strong>
                  <em>{{ card.hint }}</em>
                </span>
                <span class="summary-card__go" aria-hidden="true"
                  ><AppIcon name="arrow-right"
                /></span>
              </RouterLink>
            </div>
          </section>

          <CareerArtifactSuggestion compact />

          <section
            id="dashboard-deadlines"
            class="deadline-section"
            aria-labelledby="deadline-heading"
          >
            <div class="deadline-section__heading">
              <div>
                <p class="section-kicker">다가오는 일정</p>
                <h2 id="deadline-heading">공고 마감 캘린더</h2>
              </div>
              <div class="deadline-section__summary" aria-label="이번 달 마감 공고 수">
                <span aria-hidden="true"><AppIcon name="calendar" /></span>
                <div>
                  <small>이번 달 마감</small>
                  <strong>{{ deadlineCountLabel }}</strong>
                </div>
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
                <div class="calendar-card__toolbar">
                  <div class="calendar-card__month">
                    <span class="calendar-card__month-icon" aria-hidden="true">
                      <AppIcon name="calendar" />
                    </span>
                    <div>
                      <small>MONTHLY DEADLINE</small>
                      <strong aria-live="polite">{{ monthLabel }}</strong>
                    </div>
                  </div>
                  <div class="calendar-controls" aria-label="캘린더 월 이동">
                    <span class="calendar-controls__step">
                      <button type="button" aria-label="이전 달" @click="moveMonth(-1)">
                        <AppIcon name="arrow-left" />
                      </button>
                      <button type="button" aria-label="다음 달" @click="moveMonth(1)">
                        <AppIcon name="arrow-right" />
                      </button>
                    </span>
                  </div>
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
                      :class="[
                        {
                          'calendar-day--selected': selectedDate === cell.date,
                          'calendar-day--today': cell.isToday,
                          'calendar-day--has-deadline': cell.count > 0,
                          'calendar-day--sunday': cell.weekday === 0,
                          'calendar-day--saturday': cell.weekday === 6,
                        },
                        cell.count > 0 ? `calendar-day--${deadlineTone(cell.date)}` : '',
                      ]"
                      :aria-pressed="selectedDate === cell.date"
                      :aria-label="`${cell.date}, 마감 공고 ${cell.count}건${cell.isToday ? ', 오늘' : ''}`"
                      @click="selectedDate = cell.date"
                    >
                      <span>{{ cell.day }}</span>
                      <strong v-if="cell.count > 0" aria-hidden="true">{{ cell.count }}건</strong>
                      <small v-if="cell.isToday">오늘</small>
                    </button>
                  </template>
                </div>
                <p class="calendar-legend">
                  <span class="calendar-legend__today">오늘</span>
                  <span class="calendar-legend__urgent">3일 이내 마감</span>
                  <span class="calendar-legend__soon">7일 이내 마감</span>
                  <span class="calendar-legend__normal">마감 예정</span>
                </p>
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
                  <li
                    v-for="job in selectedDeadlineItems"
                    :key="job.id"
                    :data-tone="deadlineTone(job.deadlineAt)"
                  >
                    <span class="deadline-items__badges">
                      <span class="deadline-items__dday">{{ ddayLabel(job.deadlineAt) }}</span>
                      <span class="deadline-items__status">{{
                        job.status === 'SUBMITTED' ? '지원 완료' : '준비 중'
                      }}</span>
                    </span>
                    <strong>{{ deadlineTitle(job) }}</strong>
                    <small>{{ jobCompanyLabel(job.companyName) }}</small>
                    <time :datetime="job.deadlineAt">{{
                      formatDeadlineDateTime(job.deadlineAt)
                    }}</time>
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
                  <li
                    v-for="job in selectedDeadlineItems"
                    :key="job.id"
                    :data-tone="deadlineTone(job.deadlineAt)"
                  >
                    <span class="deadline-items__badges">
                      <span class="deadline-items__dday">{{ ddayLabel(job.deadlineAt) }}</span>
                      <span class="deadline-items__status">{{
                        job.status === 'SUBMITTED' ? '지원 완료' : '준비 중'
                      }}</span>
                    </span>
                    <strong>{{ deadlineTitle(job) }}</strong>
                    <small>{{ jobCompanyLabel(job.companyName) }}</small>
                    <time :datetime="job.deadlineAt">{{
                      formatDeadlineDateTime(job.deadlineAt)
                    }}</time>
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
            <section
              id="dashboard-activity"
              class="dashboard-section"
              aria-labelledby="activity-heading"
            >
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
                    <span class="activity-list__icon" aria-hidden="true">
                      <AppIcon :name="activity.icon" />
                    </span>
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
              <h2 id="workspace-note-heading">
                <span>한 번 정리한 정보는</span> <span>다음 지원에도 이어져요.</span>
              </h2>
              <p>
                내 정보와 등록한 이력서·자료를 먼저 다듬어 두면 공고 분석부터 자기소개서와 면접
                준비까지 같은 근거를 활용할 수 있어요.
              </p>
              <RouterLink to="/guide" class="text-link"
                >전체 이용 순서 보기 <AppIcon name="arrow-right"
              /></RouterLink>
            </aside>
          </div>

          <section id="dashboard-guides" class="guide-section" aria-labelledby="guide-heading">
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
                <span class="guide-card__mark" aria-hidden="true">{{
                  String(index + 1).padStart(2, '0')
                }}</span>
                <span class="guide-card__meta">
                  <span class="guide-card__tag">
                    <AppIcon :name="guideIcon(post.category, index)" />
                    {{ post.category }}
                  </span>
                  <span class="guide-card__time">{{ guideReadMinutes(post) }}분 분량</span>
                </span>
                <strong class="guide-card__title">{{ post.title }}</strong>
                <span class="guide-card__summary">{{ post.summary }}</span>
                <span class="guide-card__foot">
                  <em>읽어보기</em>
                  <AppIcon name="arrow-right" />
                </span>
              </button>
            </div>
            <div v-else class="guide-state">
              <AppIcon name="inbox" />
              <span>현재 게시된 취업 준비 가이드가 없어요.</span>
            </div>
          </section>
        </div>

        <aside class="dashboard-toc" aria-label="대시보드 바로가기">
          <p>바로가기</p>
          <nav>
            <a href="#dashboard-overview"><AppIcon name="profile" />지원 현황</a>
            <a href="#dashboard-deadlines"><AppIcon name="calendar" />마감 캘린더</a>
            <a href="#dashboard-activity"><AppIcon name="clock" />최근 활동</a>
            <a href="#dashboard-guides"><AppIcon name="guide" />취업 준비 가이드</a>
          </nav>
        </aside>
      </div>
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
          <header class="guide-modal__topbar">
            <span class="guide-modal__category">
              <AppIcon name="guide" />
              {{ selectedGuide.category }}
            </span>
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
            <section class="guide-modal__hero">
              <span class="guide-modal__number" aria-hidden="true">{{ selectedGuideNumber }}</span>
              <p class="section-kicker">CAREER NOTE {{ selectedGuideNumber }}</p>
              <h2 :id="`guide-modal-title-${selectedGuide.id}`">{{ selectedGuide.title }}</h2>
              <p class="guide-modal__summary">{{ selectedGuide.summary }}</p>
            </section>
            <div class="guide-modal__content" aria-label="가이드 본문">
              <template v-for="(block, index) in selectedGuideBlocks" :key="index">
                <ul v-if="block.kind === 'list'">
                  <li v-for="(item, itemIndex) in block.items" :key="itemIndex">{{ item }}</li>
                </ul>
                <p v-else>{{ block.text }}</p>
              </template>
            </div>
          </div>
          <footer>
            <span class="guide-modal__meta">
              <small>{{ guideReadMinutes(selectedGuide) }}분이면 다 읽어요</small>
              <em
                >{{
                  new Intl.DateTimeFormat('ko-KR', {
                    timeZone: SEOUL_TIME_ZONE,
                    dateStyle: 'medium',
                  }).format(new Date(selectedGuide.publishedAt))
                }}
                업데이트</em
              >
            </span>
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
.dashboard,
.guide-modal {
  --color-primary: var(--color-brand);
  --color-subtle: var(--color-muted);
}
.dashboard {
  --dashboard-toc-width: 11.5rem;
  --dashboard-layout-gap: 1.25rem;
  width: min(100%, 88rem);
  margin-inline: auto;
  display: grid;
  gap: clamp(1.25rem, 2vw, 1.75rem);
}

/* 제목 줄을 없앤 자리. 등록 동작만 오른쪽에 붙는다. */
.dashboard-quick-entry {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  width: calc(
    100% - var(--dashboard-toc-width) - var(--dashboard-layout-gap) - var(--dashboard-toc-width) -
      var(--dashboard-layout-gap)
  );
  margin-inline: auto;
}

.dashboard-layout {
  display: grid;
  grid-template-columns:
    var(--dashboard-toc-width) minmax(0, 1fr)
    var(--dashboard-toc-width);
  column-gap: var(--dashboard-layout-gap);
  align-items: start;
  min-width: 0;
}

.dashboard-content {
  display: grid;
  grid-column: 2;
  min-width: 0;
  gap: clamp(1.5rem, 2.4vw, 2.25rem);
}

.dashboard-content > [id] {
  scroll-margin-top: calc(var(--global-header-height) + 1rem);
}

.dashboard-toc {
  position: sticky;
  top: calc(var(--global-header-height) + 1rem);
  grid-column: 3;
  padding: 0.75rem;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.dashboard-toc > p {
  margin: 0 0 0.55rem;
  padding-inline: 0.45rem;
  color: var(--color-muted);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.dashboard-toc nav {
  display: grid;
  gap: 0.18rem;
}

.dashboard-toc a {
  display: flex;
  min-height: 2.45rem;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.7rem;
  border-radius: var(--radius-pill);
  color: var(--color-muted-strong);
  font-size: 0.78rem;
  font-weight: 720;
  text-decoration: none;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.dashboard-toc a:hover,
.dashboard-toc a:focus-visible {
  color: var(--color-primary);
  background: var(--hs-blue-50);
}

.dashboard-toc a :deep(.icon) {
  width: 1rem;
  height: 1rem;
  flex: 0 0 auto;
}

.dashboard > :deep(.state-panel),
.dashboard-error {
  width: calc(
    100% - var(--dashboard-toc-width) - var(--dashboard-layout-gap) - var(--dashboard-toc-width) -
      var(--dashboard-layout-gap)
  );
  margin-inline: auto;
}
.section-kicker {
  margin: 0 0 0.4rem;
  color: var(--color-primary);
  font-size: 0.75rem;
  font-weight: 780;
  letter-spacing: 0.01em;
}
.dashboard h2,
.dashboard h3,
.dashboard p {
  margin-top: 0;
}
.dashboard h2 {
  font-family: var(--font-display);
  font-weight: 780;
  letter-spacing: -0.032em;
  text-wrap: balance;
}

.dashboard-error {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 1rem;
  align-items: center;
  padding: 1.125rem 1.25rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-danger-soft);
  box-shadow: inset 0 0 0 1px var(--color-danger-border);
}
.dashboard-error__icon {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border-radius: var(--radius-md);
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
  gap: 1.25rem;
}

/* Dashboard의 모든 큰 면은 같은 모서리와 그림자를 쓴다. */
.career-card,
.deadline-section,
.dashboard-section,
.workspace-note,
.guide-section {
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

/*
 * 프로필 카드. 서비스의 첫인상을 담당하므로 다른 카드보다 한 단계 더 크게 다룬다.
 * 위쪽 aurora 띠 위로 아바타가 걸치고, 아래 흰 면을 세 칸으로 나눠
 * "나는 누구인가 / 무엇을 원하는가 / 얼마나 준비됐는가"를 왼쪽부터 읽게 한다.
 * 띠 색은 제품 brand blue를 중심에 두고 보라·시안으로만 번지게 해 테마를 벗어나지 않는다.
 */
.career-card {
  position: relative;
  overflow: hidden;
  color: var(--color-ink);
  border: 0;
  background: var(--color-surface);
}

.career-card__cover {
  position: relative;
  height: clamp(5rem, 8vw, 6.5rem);
  overflow: hidden;
  background:
    radial-gradient(120% 150% at 4% 12%, var(--hs-blue-600) 0%, transparent 58%),
    radial-gradient(110% 150% at 34% 108%, #6b4bff 0%, transparent 62%),
    radial-gradient(95% 150% at 66% -14%, #12b8e6 0%, transparent 60%),
    radial-gradient(120% 180% at 102% 74%, var(--hs-blue-300) 0%, transparent 62%),
    linear-gradient(112deg, var(--hs-blue-800) 0%, var(--hs-blue-500) 48%, #37c8e8 100%);
}

/* 띠 위에 옅은 원 하나만 남겨 평면적인 색면으로 보이지 않게 한다. */
.career-card__cover::after {
  position: absolute;
  right: -4rem;
  bottom: -7rem;
  width: 15rem;
  height: 15rem;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.career-card__sheen {
  position: absolute;
  top: -40%;
  left: 0;
  width: 24%;
  height: 180%;
  background: linear-gradient(90deg, transparent, rgb(255 255 255 / 24%), transparent);
  pointer-events: none;
  animation: career-sheen 6s ease-in-out 1.2s infinite;
}

.career-card__main {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(14rem, 0.95fr) minmax(0, 1.15fr) minmax(0, 1.15fr);
  align-items: stretch;
  gap: clamp(1.25rem, 2.4vw, 2.25rem);
  padding: 0 clamp(1.5rem, 2.5vw, 2rem) clamp(1.5rem, 2.5vw, 2rem);
}

/* 가운데·오른쪽 칸은 얇은 선으로만 나눈다. */
.career-card__details,
.career-card__status {
  min-width: 0;
  padding-top: clamp(1.25rem, 2vw, 1.75rem);
}

.career-card__details {
  padding-left: clamp(1.25rem, 2.4vw, 2.25rem);
  border-left: 1px solid var(--color-border);
}

.career-card__status {
  padding-left: clamp(1.25rem, 2.4vw, 2.25rem);
  border-left: 1px solid var(--color-border);
}

.career-card__profile {
  display: grid;
  min-width: 0;
  align-content: start;
  justify-items: start;
}

/* 아바타는 띠 위로 걸치고 흰 링으로 배경과 분리한다. */
.career-card__person {
  display: grid;
  width: 5rem;
  height: 5rem;
  place-items: center;
  margin-top: -2.5rem;
  border-radius: 50%;
  color: var(--color-brand);
  background: linear-gradient(160deg, #ffffff, var(--hs-blue-50));
  box-shadow:
    0 0 0 0.375rem var(--color-surface),
    0 12px 24px -12px rgb(32 51 152 / 45%);
}

.career-card__person :deep(.icon) {
  width: 2.5rem;
  height: 2.5rem;
  stroke-width: 1.6;
}

.career-card__eyebrow {
  margin: 1rem 0 0.25rem;
  color: var(--color-muted);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.career-card__profile h2 {
  margin: 0;
  color: var(--color-ink-title);
  font-size: clamp(1.375rem, 2.2vw, 1.75rem);
  overflow-wrap: anywhere;
}

.career-card__readiness {
  margin-top: 0.75rem;
  padding: 0.375rem 0.8125rem;
  border-radius: 999px;
  color: var(--color-success-strong);
  background: var(--color-success-soft);
  font-size: 0.75rem;
  font-weight: 800;
}

.career-card__readiness--unknown {
  color: var(--color-muted-strong);
  background: var(--color-fill);
}

.career-card__cta {
  display: inline-flex;
  gap: 0.35rem;
  align-items: center;
  margin-top: 1rem;
  color: var(--color-brand);
  font-size: 0.875rem;
  font-weight: 800;
  transition: gap var(--motion-base) var(--ease-emphasized);
}

.career-card__cta :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.career-card:hover .career-card__cta {
  gap: 0.6rem;
}

.career-card__role {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  padding: 0.875rem 1rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.career-card__role-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-md);
  color: #ffffff;
  background: var(--color-brand);
  box-shadow: var(--shadow-brand);
}

.career-card__role small,
.career-card__facts dt,
.career-card__progress small {
  display: block;
  color: var(--color-muted);
  font-size: 0.75rem;
}

.career-card__role > div {
  min-width: 0;
}

.career-card__role strong {
  display: block;
  margin-top: 0.18rem;
  overflow: hidden;
  color: var(--color-ink-title);
  font-size: 1rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.career-card__facts {
  display: grid;
  gap: 0.875rem;
  margin: 1rem 0 0;
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
  color: var(--color-ink);
  font-size: 0.875rem;
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

.career-card__progress > span strong {
  color: var(--color-ink-title);
  font-size: 0.9375rem;
}

.career-card__progress > strong {
  color: var(--color-brand);
  font-size: 1.625rem;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
  line-height: 1;
}

.career-card__track {
  width: 100%;
  height: 0.5rem;
  margin-top: 0.6rem;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  background: var(--color-fill-strong);
  animation: career-track-fill 900ms var(--ease-emphasized) 200ms both;
}

.career-card__track::-webkit-progress-bar {
  border-radius: 999px;
  background: var(--color-fill-strong);
}

.career-card__track::-webkit-progress-value {
  border-radius: 999px;
  background: linear-gradient(90deg, var(--hs-blue-500), #37c8e8);
}

.career-card__track::-moz-progress-bar {
  border-radius: 999px;
  background: linear-gradient(90deg, var(--hs-blue-500), #37c8e8);
}

.start-checklist {
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--color-border);
}

.start-checklist__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.start-checklist__heading h3 {
  margin: 0;
  color: var(--color-ink-title);
  font-size: 0.9rem;
}

.start-checklist__heading span {
  color: var(--color-muted);
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
  color: var(--color-muted-strong);
  font-size: 0.78rem;
}

.start-checklist li :deep(.icon) {
  width: 0.95rem;
  height: 0.95rem;
}

.start-checklist li[data-state='completed'] {
  color: var(--color-success-strong);
}

.start-checklist a,
.start-checklist button {
  padding: 0;
  color: var(--color-brand);
  border: 0;
  background: none;
  font: inherit;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 0.18rem;
}

.start-checklist small {
  color: var(--color-success-strong);
  font-weight: 800;
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
.summary-section__actions {
  display: flex;
  justify-content: flex-end;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}
.summary-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.8rem;
  align-items: center;
  min-height: 6.75rem;
  padding: 1.125rem;
  overflow: hidden;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  transition:
    transform var(--motion-base),
    box-shadow var(--motion-base);
}
.summary-card:hover,
.summary-card:focus-visible {
  box-shadow: var(--shadow-lift);
  transform: translateY(-3px);
}
.summary-card--primary {
  color: white;
  background:
    radial-gradient(circle at 88% 12%, rgb(255 255 255 / 16%), transparent 46%),
    linear-gradient(145deg, var(--hs-blue-800), var(--hs-blue-600));
}
.summary-card__icon {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--hs-blue-50);
  transition:
    transform var(--motion-base) var(--ease-emphasized),
    background-color var(--motion-base);
}
.summary-card:hover .summary-card__icon {
  transform: translateY(-1px) scale(1.06);
}
.summary-card--primary .summary-card__icon {
  color: white;
  background: rgb(255 255 255 / 15%);
}
.summary-card--success .summary-card__icon {
  color: var(--color-success);
  background: var(--color-success-soft);
}
.summary-card--neutral .summary-card__icon {
  color: var(--color-muted-strong);
  background: var(--color-neutral-soft);
}
.summary-card__body {
  min-width: 0;
}
.summary-card small,
.summary-card strong,
.summary-card em {
  display: block;
}
.summary-card small {
  color: var(--color-muted);
  font-size: 0.76rem;
  font-weight: 700;
}
.summary-card--primary small {
  color: rgb(255 255 255 / 74%);
}
.summary-card strong {
  margin-top: 0.15rem;
  font-size: 1.85rem;
  font-variant-numeric: tabular-nums;
  line-height: 1.05;
  letter-spacing: -0.03em;
}
.summary-card em {
  margin-top: 0.22rem;
  overflow: hidden;
  color: var(--color-subtle);
  font-size: 0.7rem;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-card--primary em {
  color: rgb(255 255 255 / 62%);
}
.summary-card__go {
  display: grid;
  width: 1.85rem;
  height: 1.85rem;
  place-items: center;
  border-radius: 999px;
  color: var(--color-subtle);
  background: transparent;
  transition:
    color var(--motion-base),
    background-color var(--motion-base),
    transform var(--motion-base) var(--ease-emphasized);
}
.summary-card__go :deep(.icon) {
  width: 1rem;
  height: 1rem;
}
.summary-card:hover .summary-card__go {
  color: var(--color-primary);
  background: var(--hs-blue-50);
  transform: translateX(2px);
}
.summary-card--primary .summary-card__go {
  color: rgb(255 255 255 / 72%);
}
.summary-card--primary:hover .summary-card__go {
  color: white;
  background: rgb(255 255 255 / 16%);
}

.deadline-section {
  overflow: hidden;
  padding: clamp(1.25rem, 2.8vw, 2rem);
  background: var(--color-surface);
}
.deadline-section__heading {
  align-items: center;
}
.deadline-section__summary {
  display: flex;
  gap: 0.7rem;
  align-items: center;
  min-width: 9.5rem;
  padding: 0.7rem 0.9rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--hs-blue-50);
}
.deadline-section__summary > span {
  display: grid;
  width: 2.35rem;
  height: 2.35rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}
.deadline-section__summary > span :deep(.icon) {
  width: 1.15rem;
  height: 1.15rem;
}
.deadline-section__summary small,
.deadline-section__summary strong {
  display: block;
}
.deadline-section__summary small {
  color: var(--color-muted);
  font-size: 0.7rem;
}
.deadline-section__summary strong {
  margin-top: 0.08rem;
  color: var(--color-ink);
  font-size: 1.05rem;
}
/*
 * 마감 캘린더. 레퍼런스처럼 얇은 격자 위에 날짜를 올린다.
 * - 날짜 숫자는 칸 오른쪽 위, 마감 건수는 칸 왼쪽 아래 작은 알약으로 둔다.
 * - 격자선은 컨테이너 배경 + 1px gap으로 만들어 칸마다 테두리를 그리지 않는다.
 */
.calendar-card__toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.calendar-card__month {
  display: flex;
  grid-column: 2;
  gap: 0.7rem;
  align-items: center;
  justify-content: center;
}

.calendar-card__month-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--hs-blue-50);
}

.calendar-card__month-icon :deep(.icon) {
  width: 1.15rem;
  height: 1.15rem;
}

.calendar-card__month small,
.calendar-card__month strong {
  display: block;
}

.calendar-card__month small {
  color: var(--color-muted);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.calendar-card__month strong {
  margin-top: 0.08rem;
  color: var(--color-ink-title);
  font-size: 1.125rem;
  letter-spacing: -0.02em;
}

/* 이전·다음 달 이동은 달 이름 양옆에 원형으로 붙인다. */
.calendar-controls {
  display: contents;
}

.calendar-controls__step {
  display: contents;
}

.calendar-controls button {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: var(--color-muted-strong);
  background: var(--color-fill);
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.calendar-controls button:first-child {
  grid-column: 1;
  grid-row: 1;
}

.calendar-controls button:last-child {
  grid-column: 3;
  grid-row: 1;
}

.calendar-controls button:hover {
  color: #ffffff;
  background: var(--color-primary);
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
  background: var(--color-fill);
}

.calendar-state--error {
  color: var(--color-danger);
}

.calendar-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(18rem, 0.75fr);
  gap: 1.15rem;
  align-items: stretch;
  margin-top: 1.25rem;
}

.calendar-card {
  min-width: 0;
  padding: clamp(0.85rem, 1.6vw, 1.25rem);
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

/* 격자: 컨테이너 배경이 선이 되고 칸은 흰 면으로 얹힌다. */
.calendar-weekdays,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px;
  background: var(--color-border);
}

.calendar-weekdays {
  overflow: hidden;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.calendar-grid {
  overflow: hidden;
  border-top: 1px solid var(--color-border);
  border-radius: 0 0 var(--radius-md) var(--radius-md);
}

.calendar-weekdays span {
  background: var(--color-fill);
  padding: 0.6rem 0.2rem;
  color: var(--color-muted-strong);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-align: center;
}

.calendar-weekdays span:first-child {
  color: var(--color-danger);
}

.calendar-weekdays span:last-child {
  color: var(--color-primary);
}

.calendar-day {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 4.5rem;
  grid-template-rows: auto minmax(0, 1fr) auto;
  justify-items: end;
  overflow: hidden;
  padding: 0.5rem 0.5rem 0.4rem;
  border: 0;
  border-radius: 0;
  color: var(--color-ink);
  background: var(--color-surface);
  transition: background-color 160ms ease;
}

.calendar-day:hover {
  background: var(--hs-blue-50);
}

.calendar-day:focus-visible {
  z-index: 2;
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* 날짜 숫자는 칸 오른쪽 위. */
.calendar-day > span {
  display: grid;
  min-width: 1.75rem;
  height: 1.75rem;
  place-items: center;
  border-radius: 50%;
  font-size: 0.8125rem;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}

/*
 * 마감 건수는 색 띠 대신 작은 알약 하나로 알린다.
 * 앞의 점이 긴급도를 색으로, 숫자가 건수를 글자로 전한다.
 */
.calendar-day > strong {
  display: inline-flex;
  min-height: 1.375rem;
  align-items: center;
  gap: 0.3rem;
  grid-row: 3;
  justify-self: start;
  border: 0;
  border-radius: 999px;
  color: var(--color-brand-ink);
  background: var(--hs-blue-50);
  padding: 0.1rem 0.5rem 0.1rem 0.4rem;
  font-size: 0.6875rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  white-space: nowrap;
}

.calendar-day > strong::before {
  width: 0.3125rem;
  height: 0.3125rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

/* "오늘" 표식은 숫자 반대편인 왼쪽 위에 둔다. */
.calendar-day > small {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--hs-blue-50);
  padding: 0.1rem 0.35rem;
  font-size: 0.58rem;
  font-weight: 800;
}

.calendar-day--urgent > strong,
.calendar-day--today.calendar-day--has-deadline > strong {
  color: var(--color-danger-strong);
  background: var(--color-danger-soft);
}

.calendar-day--soon > strong {
  color: var(--color-warning-strong);
  background: var(--color-warning-soft);
}

.calendar-day--passed {
  opacity: 0.72;
}

.calendar-day--sunday > span {
  color: var(--color-danger);
}

.calendar-day--saturday > span {
  color: var(--color-primary);
}

.calendar-day--today > span {
  color: #ffffff;
  background: var(--color-primary);
}

/* 다른 달의 칸은 빗금으로 눌러 둔다. */
.calendar-day--blank {
  display: block;
  min-height: 4.5rem;
  background: repeating-linear-gradient(
    -45deg,
    var(--color-fill) 0 6px,
    var(--color-surface) 6px 12px
  );
}

.calendar-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.9rem;
  margin: 0.85rem 0 0;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
  color: var(--color-subtle);
  font-size: 0.68rem;
  font-weight: 700;
}
.calendar-legend span {
  display: inline-flex;
  gap: 0.32rem;
  align-items: center;
}
.calendar-legend span::before {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 0.16rem;
  content: '';
}
.calendar-legend__today::before {
  background: var(--color-primary);
}
.calendar-legend__urgent::before {
  background: var(--color-danger);
}
.calendar-legend__soon::before {
  background: var(--color-warning);
}
.calendar-legend__normal::before {
  background: var(--hs-blue-200);
}
.calendar-day--selected {
  z-index: 1;
  background: var(--hs-blue-50);
  box-shadow: inset 0 0 0 2px var(--color-primary);
}
.calendar-day--selected > strong {
  color: #ffffff;
  background: var(--color-primary);
}
.deadline-detail {
  display: flex;
  flex-direction: column;
  padding: 1.125rem;
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

/* 고른 날짜에 마감이 없으면 안내를 남는 면 한가운데로 모은다. */
.deadline-detail--desktop .compact-empty--calendar {
  align-items: center;
  flex-direction: column;
  gap: 0.5rem;
  margin-block: auto;
  text-align: center;
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
/*
 * 공고 카드. 왼쪽 색 띠 없이 흰 면과 옅은 그림자만으로 카드를 세운다.
 * 마감 임박 여부는 카드가 아니라 D-day 알약이 색으로 알린다.
 */
.deadline-items li {
  position: relative;
  display: grid;
  gap: 0.2rem;
  padding: 0.9rem 1rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  transition:
    box-shadow var(--motion-base),
    transform var(--motion-base);
}
.deadline-items li:hover {
  box-shadow: var(--shadow-lift);
  transform: translateY(-1px);
}
.deadline-items__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  align-items: center;
}
.deadline-items__dday {
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  color: white;
  background: var(--color-muted-strong);
  font-size: 0.6875rem;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}
.deadline-items li[data-tone='today'] .deadline-items__dday,
.deadline-items li[data-tone='urgent'] .deadline-items__dday {
  background: var(--color-danger);
}
.deadline-items li[data-tone='soon'] .deadline-items__dday {
  background: var(--color-warning);
}
.deadline-items li[data-tone='normal'] .deadline-items__dday {
  background: var(--color-primary);
}
.deadline-items li[data-tone='today'] .deadline-items__dday {
  animation: dday-pulse 1.8s ease-in-out infinite;
}
.deadline-items__status {
  width: fit-content;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  color: var(--color-muted-strong);
  background: var(--color-fill);
  font-size: 0.6875rem;
  font-weight: 750;
}
@keyframes dday-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgb(180 35 45 / 32%);
  }
  60% {
    box-shadow: 0 0 0 0.35rem rgb(180 35 45 / 0%);
  }
}
.deadline-items strong {
  margin-top: 0.45rem;
  color: var(--color-ink-title);
  font-size: 0.9375rem;
  letter-spacing: -0.015em;
  line-height: 1.4;
}
.deadline-items small,
.deadline-items time {
  color: var(--color-muted);
  font-size: 0.75rem;
}
.deadline-items small {
  margin-top: 0.1rem;
  font-weight: 700;
}
/* 동작은 얇은 구분선 아래 한 줄로 모아 카드 바닥을 정리한다. */
.deadline-items a {
  display: inline-flex;
  gap: 0.25rem;
  align-items: center;
  justify-content: flex-end;
  margin-top: 0.65rem;
  padding-top: 0.6rem;
  border-top: 1px solid var(--color-border);
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
.dashboard-columns > .dashboard-section {
  display: flex;
  flex-direction: column;
}
.dashboard-columns > .dashboard-section > .compact-empty {
  flex: 1 1 auto;
  align-items: center;
  margin-top: 0.85rem;
}
.activity-list {
  margin: 0.85rem -0.55rem 0;
  padding: 0;
  list-style: none;
}
.activity-list li + li {
  border-top: 1px solid var(--color-border);
}
.activity-list a {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.85rem;
  align-items: center;
  padding: 0.8rem 0.75rem;
  border-radius: var(--radius-md);
  transition: background-color var(--motion-base);
}
.activity-list a:hover {
  background: var(--hs-blue-50);
}
.activity-list__icon {
  display: grid;
  width: 2.4rem;
  height: 2.4rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--hs-blue-50);
  transition:
    transform var(--motion-base) var(--ease-emphasized),
    background-color var(--motion-base);
}
.activity-list__icon :deep(.icon) {
  width: 1.05rem;
  height: 1.05rem;
}
.activity-list a:hover .activity-list__icon {
  background: var(--hs-blue-100);
  transform: scale(1.06);
}
.activity-list__body {
  min-width: 0;
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
  display: flex;
  flex-direction: column;
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
  border: 0;
  border-radius: var(--radius-lg);
  background: rgb(255 255 255 / 14%);
}
.workspace-note .section-kicker {
  color: var(--hs-blue-100);
}
.workspace-note h2 {
  margin-bottom: 0.7rem;
  color: white;
  font-size: 1.25rem;
  line-height: 1.35;
}
.workspace-note h2 span {
  display: inline-block;
}
.workspace-note p:not(.section-kicker) {
  margin-top: 0;
  color: rgb(255 255 255 / 72%);
  font-size: 0.85rem;
  line-height: 1.65;
}
.workspace-note .text-link {
  align-self: flex-start;
  margin-top: auto;
  padding-top: 1.5rem;
  color: white;
}
.compact-empty {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  padding: 1.125rem;
  border-radius: var(--radius-lg);
  color: var(--color-muted);
  background: var(--color-fill);
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
/*
 * 가이드 카드. 아이콘 타일과 큰 번호 배지를 걷어내고 글이 주인공인 표지로 바꿨다.
 * 위에서부터 분류 태그 → 제목 → 요약 → 얇은 선 아래 읽기 동작 순으로 읽힌다.
 * 마지막 줄에 카드가 두세 장만 남아도 빈칸이 생기지 않도록 grid 대신 flex로 늘린다.
 */
.guide-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.guide-card {
  position: relative;
  display: flex;
  flex: 1 1 16rem;
  flex-direction: column;
  min-width: 0;
  min-height: 12.5rem;
  padding: 1.25rem;
  overflow: hidden;
  border: 0;
  border-radius: var(--radius-lg);
  color: var(--color-ink);
  background: var(--color-fill);
  text-align: left;
  transition:
    transform var(--motion-base) var(--ease-emphasized),
    box-shadow var(--motion-base),
    background-color var(--motion-base);
}
.guide-card:hover,
.guide-card:focus-visible {
  background: var(--color-surface);
  box-shadow: var(--shadow-lift);
  transform: translateY(-3px);
}

/* 카드마다 다른 표지 역할만 하는 배경 숫자. 정보는 담지 않으므로 낭독기에서 감춘다. */
.guide-card__mark {
  position: absolute;
  top: -0.9rem;
  right: 0.7rem;
  color: var(--hs-blue-100);
  font-family: var(--font-display);
  font-size: 4rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.06em;
  pointer-events: none;
  transition: color var(--motion-base);
}
.guide-card:hover .guide-card__mark {
  color: var(--hs-blue-200);
}

.guide-card > *:not(.guide-card__mark) {
  position: relative;
}
.guide-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: center;
}
.guide-card__tag {
  display: inline-flex;
  gap: 0.3rem;
  align-items: center;
  padding: 0.22rem 0.55rem 0.22rem 0.42rem;
  border-radius: var(--radius-pill);
  color: var(--color-brand-ink);
  background: var(--hs-blue-100);
  font-size: 0.7rem;
  font-weight: 800;
}
.guide-card__tag :deep(.icon) {
  width: 0.85rem;
  height: 0.85rem;
}
.guide-card__time {
  color: var(--color-muted);
  font-size: 0.7rem;
  font-weight: 700;
}
.guide-card__title {
  max-width: 24rem;
  margin-top: 0.85rem;
  color: var(--color-ink-title);
  font-family: var(--font-display);
  font-size: 1.0625rem;
  font-weight: 780;
  letter-spacing: -0.024em;
  line-height: 1.4;
  text-wrap: balance;
}
.guide-card__summary {
  max-width: 26rem;
  margin-top: 0.5rem;
  color: var(--color-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
.guide-card__foot {
  display: flex;
  gap: 0.35rem;
  align-items: center;
  margin-top: auto;
  padding-top: 0.9rem;
  border-top: 1px solid var(--color-border-strong);
  color: var(--color-primary);
  font-size: 0.78rem;
  font-weight: 800;
}
.guide-card__foot em {
  font-style: normal;
}
.guide-card__foot :deep(.icon) {
  width: 1rem;
  height: 1rem;
  transition: transform var(--motion-base) var(--ease-emphasized);
}
.guide-card:hover .guide-card__foot,
.guide-card:focus-visible .guide-card__foot {
  color: var(--color-brand-hover);
}
.guide-card:hover .guide-card__foot :deep(.icon) {
  transform: translateX(0.25rem);
}

.guide-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgb(12 18 38 / 72%);
  backdrop-filter: blur(7px);
}
.guide-modal {
  width: min(100%, 46rem);
  max-height: min(48rem, calc(100dvh - 2rem));
  overflow: auto;
  border: 0;
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  box-shadow: 0 34px 100px rgb(8 18 48 / 44%);
}
.guide-modal__topbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.85rem 1.15rem;
  border-bottom: 1px solid rgb(209 219 237 / 72%);
  background: rgb(255 255 255 / 96%);
  backdrop-filter: blur(12px);
}
.guide-modal__category {
  display: inline-flex;
  gap: 0.4rem;
  align-items: center;
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  color: var(--color-primary);
  background: var(--hs-blue-50);
  font-size: 0.75rem;
  font-weight: 800;
}
.guide-modal__category :deep(.icon) {
  width: 0.95rem;
  height: 0.95rem;
}
.guide-modal__topbar button {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 0;
  border-radius: var(--radius-pill);
  color: var(--color-ink);
  background: var(--color-fill);
}
.guide-modal__body {
  padding: 0;
}
.guide-modal__hero {
  position: relative;
  overflow: hidden;
  padding: clamp(1.5rem, 5vw, 2.75rem);
  border-bottom: 1px solid var(--hs-blue-100);
  background:
    radial-gradient(circle at 92% 20%, rgb(49 87 255 / 12%), transparent 34%),
    linear-gradient(145deg, #f8faff 0%, var(--hs-blue-50) 100%);
}
.guide-modal__hero > *:not(.guide-modal__number) {
  position: relative;
  z-index: 1;
}
.guide-modal__number {
  position: absolute;
  right: clamp(1rem, 4vw, 2rem);
  bottom: -1.35rem;
  color: rgb(49 87 255 / 8%);
  font-size: clamp(6rem, 18vw, 9rem);
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.08em;
  pointer-events: none;
}
.guide-modal__hero h2 {
  max-width: 32rem;
  margin: 0.4rem 0 0.85rem;
  color: var(--color-ink);
  font-size: clamp(1.55rem, 4vw, 2.2rem);
  line-height: 1.3;
}
.guide-modal__summary {
  max-width: 34rem;
  margin: 0;
  color: var(--color-muted);
  font-size: 1rem;
  line-height: 1.7;
}
/* 문단마다 붙던 점과 구분선을 없애고 읽는 글에 맞는 여백만 남긴다. */
.guide-modal__content {
  display: grid;
  gap: 1.15rem;
  padding: clamp(1.5rem, 5vw, 2.75rem);
  color: var(--color-ink-soft);
  font-size: 0.98rem;
  line-height: 1.9;
}
.guide-modal__content p {
  margin: 0;
}
.guide-modal__content ul {
  display: grid;
  gap: 0.5rem;
  margin: 0.15rem 0;
  padding: 1.1rem 1.25rem;
  border-radius: var(--radius-lg);
  background: var(--color-surface-subtle);
  list-style: none;
}
.guide-modal__content li {
  position: relative;
  padding-left: 1.15rem;
  color: var(--color-ink);
  font-size: 0.92rem;
  font-weight: 600;
  line-height: 1.7;
}
.guide-modal__content li::before {
  position: absolute;
  top: 0.72em;
  left: 0.15rem;
  width: 0.34rem;
  height: 0.34rem;
  border-radius: 50%;
  background: var(--color-primary);
  content: '';
}
.guide-modal > footer {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  padding: 1rem 1.25rem;
  border-top: 1px solid var(--color-border);
  background: rgb(247 249 253 / 96%);
  backdrop-filter: blur(12px);
}
.guide-modal__meta {
  display: grid;
  gap: 0.15rem;
}
.guide-modal__meta small {
  color: var(--color-muted);
}
.guide-modal__meta em {
  color: var(--color-subtle);
  font-size: 0.7rem;
  font-style: normal;
}

/* 진입 시 위에서부터 순서대로 드러나는 절제된 stagger. */
@keyframes dashboard-rise {
  from {
    opacity: 0;
    transform: translateY(0.75rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes career-sheen {
  from {
    transform: translateX(-120%) rotate(12deg);
  }
  to {
    transform: translateX(320%) rotate(12deg);
  }
}
@keyframes career-track-fill {
  from {
    clip-path: inset(0 100% 0 0);
  }
  to {
    clip-path: inset(0 0 0 0);
  }
}

.dashboard-content > * {
  animation: dashboard-rise var(--motion-slow) var(--ease-emphasized) both;
}
.dashboard-content > *:nth-child(1) {
  animation-delay: 0ms;
}
.dashboard-content > *:nth-child(2) {
  animation-delay: 60ms;
}
.dashboard-content > *:nth-child(3) {
  animation-delay: 120ms;
}
.dashboard-content > *:nth-child(4) {
  animation-delay: 180ms;
}
.dashboard-content > *:nth-child(n + 5) {
  animation-delay: 240ms;
}

.career-card__sheen {
  position: absolute;
  top: -40%;
  left: 0;
  width: 28%;
  height: 180%;
  background: linear-gradient(90deg, transparent, rgb(255 255 255 / 22%), transparent);
  pointer-events: none;
  animation: career-sheen 5.5s ease-in-out 1.2s infinite;
}
.career-card__track {
  animation: career-track-fill 900ms var(--ease-emphasized) 200ms both;
}
@media (max-width: 87rem) {
  .dashboard-quick-entry,
  .dashboard > :deep(.state-panel),
  .dashboard-error {
    width: 100%;
  }
  .dashboard-layout {
    grid-template-columns: 1fr;
  }
  .dashboard-content,
  .dashboard-toc {
    grid-column: 1;
  }
  .dashboard-toc {
    position: static;
    order: -1;
    overflow-x: auto;
  }
  .dashboard-toc > p {
    display: none;
  }
  .dashboard-toc nav {
    display: flex;
    width: max-content;
    min-width: 100%;
  }
  .dashboard-toc a {
    flex: 1 0 auto;
    white-space: nowrap;
  }
  .calendar-layout {
    grid-template-columns: 1fr;
  }
  /* 프로필 카드는 준비도 칸부터 아래로 내려 보낸다. */
  .career-card__main {
    grid-template-columns: minmax(12rem, 0.9fr) minmax(0, 1fr);
  }
  .career-card__status {
    grid-column: 1 / -1;
    margin-top: 0.25rem;
    padding-top: 1.25rem;
    padding-left: 0;
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
}

@media (max-width: 52rem) {
  .career-card__main {
    grid-template-columns: minmax(0, 1fr);
  }
  .career-card__details {
    grid-column: 1 / -1;
    margin-top: 0.25rem;
    padding-top: 1.25rem;
    padding-left: 0;
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }
}

@media (max-width: 40rem) {
  .dashboard {
    gap: 1.25rem;
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
    align-self: start;
  }
  .career-card__facts {
    grid-template-columns: 1fr;
  }
  .summary-grid {
    grid-template-columns: 1fr;
  }
  .summary-card {
    min-height: 5.6rem;
  }
  .deadline-section__heading {
    align-items: stretch;
    flex-direction: column;
  }
  .deadline-section__summary {
    align-self: flex-start;
  }
  .calendar-card__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .calendar-controls {
    justify-content: space-between;
  }
  .calendar-card {
    padding: 0.65rem;
  }
  .calendar-day,
  .calendar-day--blank {
    min-height: 3.85rem;
    padding: 0.3rem;
  }
  .calendar-day > span {
    min-width: 1.4rem;
    height: 1.4rem;
    font-size: 0.7rem;
  }
  .calendar-day > strong {
    min-height: 1.05rem;
    gap: 0.2rem;
    padding: 0.1rem 0.35rem 0.1rem 0.3rem;
    font-size: 0.58rem;
  }
  .calendar-day > strong::before {
    width: 0.25rem;
    height: 0.25rem;
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
  .guide-card {
    flex-basis: 100%;
    min-height: 11.5rem;
  }
  .guide-card__mark {
    font-size: 3.25rem;
  }
  .guide-modal-backdrop {
    align-items: end;
    padding: 0;
  }
  .guide-modal {
    max-height: 92dvh;
    border-radius: var(--radius-panel) var(--radius-panel) 0 0;
  }
  .guide-modal__hero {
    padding: 1.5rem 1.25rem;
  }
  .guide-modal__content {
    padding: 1.5rem 1.25rem 2rem;
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
  .guide-card,
  .summary-card,
  .activity-list a,
  .deadline-items li {
    transition: none;
  }
  .dashboard-content > *,
  .career-card__sheen,
  .career-card__track,
  .deadline-items__dday {
    animation: none;
  }
  .career-card__sheen {
    display: none;
  }
  .summary-card:hover,
  .guide-card:hover {
    transform: none;
  }
}
</style>
