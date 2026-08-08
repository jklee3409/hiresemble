<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import GitHubRunMonitor from '@/features/github/GitHubRunMonitor.vue'
import CareerArtifactSuggestion from '@/features/career-artifacts/CareerArtifactSuggestion.vue'
import {
  useCreateGitHubSourceMutation,
  useDeleteGitHubSourceMutation,
  useGitHubRepositoryListQuery,
  useGitHubSourceDetailQuery,
  useGitHubSourceListQuery,
  useRefreshGitHubSourceMutation,
  useSelectGitHubRepositoriesMutation,
} from '@/features/github/queries'
import {
  GITHUB_STATUS_LABELS,
  formatGitHubInstant,
  gitHubErrorMessage,
  gitHubStatusTone,
  parsePublicGitHubUrl,
} from '@/features/github/presentation'
import { listGitHubRepositories } from '@/shared/api/githubSourceApi'
import type {
  GitHubRepositoryDto,
  GitHubRepositorySort,
  GitHubSourceSummaryDto,
} from '@/shared/api/githubSourceContracts'
import { normalizeApiError } from '@/shared/api/errors'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import { useAuthStore } from '@/stores/auth'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const sourcePage = ref(0)
const focusedSourceId = ref(validSourceQuery(route.query.source))
const registerForm = reactive({ url: '', participationConfirmed: false })
const registerErrors = ref<Record<string, string>>({})
const actionError = ref('')
const actionErrorCode = ref('')
const actionMessage = ref('')
const actionPanel = ref<HTMLElement | null>(null)
const urlInput = ref<HTMLInputElement | null>(null)
const repositorySearchInput = ref('')
const repositoryQuery = ref('')
const repositoryPage = ref(0)
const repositorySort = ref<GitHubRepositorySort>('pushedAt,desc')
const selectedRepositoryIds = ref<string[]>([])
const knownRepositories = new Map<string, GitHubRepositoryDto>()
const selectionOverrides = new Map<string, boolean>()
const missingRepositoryNames = ref<string[]>([])
const preservedConflictSelection = ref<string[]>([])
let selectionSourceIdentity = ''

const sourceFilters = computed(() => ({
  page: sourcePage.value,
  size: 20,
  sort: 'updatedAt,desc' as const,
}))
const sources = useGitHubSourceListQuery(userId, sourceFilters)
const detail = useGitHubSourceDetailQuery(userId, focusedSourceId)
const repositoryFilters = computed(() => ({
  ...(repositoryQuery.value ? { query: repositoryQuery.value } : {}),
  page: repositoryPage.value,
  size: 20,
  sort: repositorySort.value,
}))
const repositories = useGitHubRepositoryListQuery(
  userId,
  focusedSourceId,
  repositoryFilters,
  computed(
    () =>
      detail.data.value?.source.sourceKind === 'ACCOUNT' &&
      detail.data.value.source.status === 'WAITING_USER',
  ),
)
const createMutation = useCreateGitHubSourceMutation(userId)
const selectionMutation = useSelectGitHubRepositoriesMutation(userId)
const refreshMutation = useRefreshGitHubSourceMutation(userId)
const deleteMutation = useDeleteGitHubSourceMutation(userId)
const hasSuccessfulSource = computed(
  () =>
    sources.data.value?.items.some(
      (source) => source.status === 'READY' || source.status === 'PARTIAL',
    ) ?? false,
)
const selectedCount = computed(() => new Set(selectedRepositoryIds.value).size)
const selectionValid = computed(() => selectedCount.value >= 1 && selectedCount.value <= 10)
const focusedSummary = computed(
  () =>
    detail.data.value?.source ??
    sources.data.value?.items.find((source) => source.id === focusedSourceId.value) ??
    null,
)
const actionErrorTitle = computed(() => {
  if (actionErrorCode.value === 'GITHUB_RATE_LIMITED') return 'GitHub 요청 한도에 도달했어요.'
  if (actionErrorCode.value === 'GITHUB_SOURCE_NOT_ACCESSIBLE')
    return '공개 source를 찾지 못했어요.'
  if (actionErrorCode.value === 'EXTERNAL_SERVICE_UNAVAILABLE') return 'GitHub 연결이 불안정해요.'
  return '요청을 처리하지 못했어요.'
})

watch(
  () => route.query.source,
  (value) => {
    const next = validSourceQuery(value)
    if (next !== focusedSourceId.value) focusedSourceId.value = next
  },
)

watch(
  [focusedSourceId, () => repositories.data.value],
  ([sourceId, page]) => {
    if (selectionSourceIdentity !== sourceId) {
      selectionSourceIdentity = sourceId
      selectedRepositoryIds.value = []
      knownRepositories.clear()
      selectionOverrides.clear()
      missingRepositoryNames.value = []
      preservedConflictSelection.value = []
      repositoryPage.value = 0
      repositoryQuery.value = ''
      repositorySearchInput.value = ''
    }
    if (page === undefined) return
    const next = new Set(selectedRepositoryIds.value)
    for (const repository of page.items) {
      knownRepositories.set(repository.id, repository)
      const override = selectionOverrides.get(repository.id)
      if (override === true || (override === undefined && repository.selected))
        next.add(repository.id)
      if (override === false) next.delete(repository.id)
    }
    selectedRepositoryIds.value = [...next]
  },
  { immediate: true },
)

function validSourceQuery(value: unknown): string {
  return typeof value === 'string' && UUID_PATTERN.test(value) ? value : ''
}

async function focusSource(sourceId: string): Promise<void> {
  focusedSourceId.value = sourceId
  await router.replace({ query: { source: sourceId } })
}

async function clearFocus(): Promise<void> {
  focusedSourceId.value = ''
  await router.replace({ query: {} })
}

function clearFeedback(): void {
  actionError.value = ''
  actionErrorCode.value = ''
  actionMessage.value = ''
}

async function showError(error: unknown): Promise<void> {
  const normalized = normalizeApiError(error)
  actionErrorCode.value = normalized.code
  actionError.value = gitHubErrorMessage(normalized)
  await nextTick()
  actionPanel.value?.focus()
}

async function registerSource(): Promise<void> {
  clearFeedback()
  registerErrors.value = {}
  const parsed = parsePublicGitHubUrl(registerForm.url)
  if (parsed === null) {
    registerErrors.value.url =
      'https://github.com/계정 또는 https://github.com/계정/저장소 형식으로 입력해 주세요.'
  }
  if (!registerForm.participationConfirmed) {
    registerErrors.value.participationConfirmed = '직접 참여한 공개 source인지 확인해 주세요.'
  }
  if (Object.keys(registerErrors.value).length > 0) {
    await nextTick()
    if (registerErrors.value.url) urlInput.value?.focus()
    else document.querySelector<HTMLInputElement>('#github-participation')?.focus()
    return
  }

  try {
    const accepted = await createMutation.mutateAsync({
      url: registerForm.url,
      participationConfirmed: true,
    })
    registerForm.url = ''
    registerForm.participationConfirmed = false
    actionMessage.value = 'GitHub 연결을 등록했어요. 공개 정보를 확인하고 있습니다.'
    notifications.toast('GitHub 연결을 등록했어요.', 'success')
    await focusSource(accepted.resourceId!)
  } catch (error) {
    await showError(error)
  }
}

function submitRepositorySearch(): void {
  repositoryQuery.value = repositorySearchInput.value.trim()
  repositoryPage.value = 0
}

function changeRepositorySort(event: Event): void {
  repositorySort.value = (event.target as HTMLSelectElement).value as GitHubRepositorySort
  repositoryPage.value = 0
}

function toggleRepository(repository: GitHubRepositoryDto, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  const next = new Set(selectedRepositoryIds.value)
  if (checked) next.add(repository.id)
  else next.delete(repository.id)
  selectionOverrides.set(repository.id, checked)
  knownRepositories.set(repository.id, repository)
  selectedRepositoryIds.value = [...next]
  missingRepositoryNames.value = []
  preservedConflictSelection.value = []
}

async function saveSelection(): Promise<void> {
  const source = detail.data.value?.source
  if (source === undefined || !selectionValid.value) return
  clearFeedback()
  const attempted = [...new Set(selectedRepositoryIds.value)]
  try {
    const accepted = await selectionMutation.mutateAsync({
      sourceId: source.id,
      request: { repositoryIds: attempted, version: source.version },
    })
    actionMessage.value = '선택한 저장소 분석을 시작했어요.'
    notifications.toast('선택한 저장소 분석을 시작했어요.', 'success')
    await focusSource(accepted.resourceId!)
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (normalized.code === 'RESOURCE_VERSION_CONFLICT') {
      await reconcileVersionConflict(source.id, attempted)
      return
    }
    await showError(error)
  }
}

async function reconcileVersionConflict(sourceId: string, attempted: string[]): Promise<void> {
  preservedConflictSelection.value = attempted
  await Promise.all([detail.refetch(), repositories.refetch()])
  const checks = await Promise.all(
    attempted.map(async (repositoryId) => {
      const known = knownRepositories.get(repositoryId)
      if (known === undefined) return { repositoryId, exists: false, name: repositoryId }
      try {
        const page = await listGitHubRepositories(sourceId, {
          query: known.repositoryName,
          page: 0,
          size: 100,
          sort: 'repositoryName,asc',
        })
        return {
          repositoryId,
          exists: page.items.some((item) => item.id === repositoryId),
          name: `${known.ownerLogin}/${known.repositoryName}`,
        }
      } catch {
        return { repositoryId, exists: false, name: `${known.ownerLogin}/${known.repositoryName}` }
      }
    }),
  )
  selectedRepositoryIds.value = checks
    .filter((check) => check.exists)
    .map((check) => check.repositoryId)
  missingRepositoryNames.value = checks.filter((check) => !check.exists).map((check) => check.name)
  actionErrorCode.value = 'RESOURCE_VERSION_CONFLICT'
  actionError.value =
    '최신 저장소 목록을 다시 확인했어요. 남아 있는 선택을 검토한 뒤 저장 버튼을 다시 눌러 주세요.'
  await nextTick()
  actionPanel.value?.focus()
}

async function refreshSource(source: GitHubSourceSummaryDto): Promise<void> {
  clearFeedback()
  try {
    const result = await refreshMutation.mutateAsync({
      sourceId: source.id,
      request: { version: source.version },
    })
    await focusSource(source.id)
    if (result.changed && result.run !== null) {
      actionMessage.value = '새 변경을 확인해 GitHub 분석을 다시 시작했어요.'
      notifications.toast('GitHub 분석을 다시 시작했어요.', 'success')
    } else {
      actionMessage.value = 'GitHub에 새로운 변경이 없어 기존 분석 결과를 유지합니다.'
      notifications.toast('새로운 GitHub 변경이 없어요.', 'info')
    }
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (normalized.code === 'RESOURCE_VERSION_CONFLICT') {
      await Promise.all([sources.refetch(), detail.refetch()])
    }
    await showError(error)
  }
}

async function removeSource(source: GitHubSourceSummaryDto): Promise<void> {
  const confirmed = await notifications.confirm({
    title: '이 GitHub 연결을 삭제할까요?',
    message:
      'GitHub 연결과 provenance 원본은 삭제되지만, 이미 검토하고 승인한 경험은 경험 보관함에 유지될 수 있어요.',
    confirmLabel: 'GitHub 연결 삭제',
    tone: 'danger',
  })
  if (!confirmed) return
  clearFeedback()
  try {
    await deleteMutation.mutateAsync({ sourceId: source.id, version: source.version })
    if (focusedSourceId.value === source.id) await clearFocus()
    actionMessage.value = 'GitHub 연결을 삭제했어요.'
    notifications.toast('GitHub 연결을 삭제했어요.', 'success')
  } catch (error) {
    const normalized = normalizeApiError(error)
    if (normalized.code === 'RESOURCE_VERSION_CONFLICT') {
      await Promise.all([sources.refetch(), detail.refetch()])
    }
    await showError(error)
  }
}

function sourceKindLabel(source: GitHubSourceSummaryDto): string {
  if (source.sourceKind === 'REPOSITORY') return '단일 저장소'
  return source.accountType === 'ORGANIZATION' ? '조직 계정' : '사용자 계정'
}

function sourceDisplayName(source: GitHubSourceSummaryDto): string {
  return source.repositoryName ? `${source.ownerLogin}/${source.repositoryName}` : source.ownerLogin
}

function safeSourceUrl(source: GitHubSourceSummaryDto): string | null {
  return parsePublicGitHubUrl(source.canonicalUrl)?.canonicalUrl ?? null
}
</script>

<template>
  <section class="github-page app-page profile-workspace-shell" aria-labelledby="github-heading">
    <ProfileTabs />
    <div class="profile-workspace-shell__content">
      <PageHeader
        heading-id="github-heading"
        title="GitHub 연결"
        description="직접 참여한 공개 GitHub 계정이나 저장소에서 검토할 경험 근거를 찾아요."
        variant="compact"
      />

      <aside class="github-policy" aria-label="GitHub 분석 범위 안내">
        <strong>공개 source만 안전하게 확인해요.</strong>
        <ul>
          <li>
            계정 URL을 등록해도 모든 저장소를 자동 분석하지 않고, 직접 고른 저장소만 확인합니다.
          </li>
          <li>
            저장소 코드를 실행하지 않으며, 추출 결과는 검토·승인 전까지 확정 경험으로 쓰지 않습니다.
          </li>
          <li>비공개 저장소, PAT, GitHub App 또는 OAuth 권한 연결은 지원하지 않습니다.</li>
        </ul>
      </aside>

      <form class="github-register section-surface" novalidate @submit.prevent="registerSource">
        <div class="section-header">
          <div>
            <p class="section-kicker">새 연결</p>
            <h2 class="section-title">공개 GitHub 주소 등록</h2>
          </div>
        </div>
        <label class="field">
          <span class="field__label">GitHub 계정 또는 저장소 URL</span>
          <input
            ref="urlInput"
            v-model="registerForm.url"
            class="control"
            type="url"
            maxlength="500"
            autocomplete="url"
            placeholder="https://github.com/owner 또는 https://github.com/owner/repository"
            :aria-invalid="Boolean(registerErrors.url)"
            :aria-describedby="registerErrors.url ? 'github-url-error' : undefined"
          />
          <span v-if="registerErrors.url" id="github-url-error" class="inline-error">
            {{ registerErrors.url }}
          </span>
        </label>
        <label class="github-participation">
          <input
            id="github-participation"
            v-model="registerForm.participationConfirmed"
            class="checkbox-control"
            type="checkbox"
            :aria-invalid="Boolean(registerErrors.participationConfirmed)"
          />
          <span>
            <strong>제가 직접 참여한 공개 계정 또는 저장소입니다.</strong>
            <small>공개 정보만 확인하며, 다른 사람의 작업을 내 경험으로 등록하지 않습니다.</small>
          </span>
        </label>
        <span v-if="registerErrors.participationConfirmed" class="inline-error">
          {{ registerErrors.participationConfirmed }}
        </span>
        <div class="form-actions">
          <button
            type="submit"
            class="button button--primary"
            :disabled="createMutation.isPending.value"
          >
            {{ createMutation.isPending.value ? '등록하는 중…' : 'GitHub 연결 등록' }}
          </button>
        </div>
      </form>

      <section
        v-if="actionError"
        ref="actionPanel"
        class="alert alert--danger github-feedback"
        role="alert"
        tabindex="-1"
      >
        <strong>{{ actionErrorTitle }}</strong>
        <p>{{ actionError }}</p>
        <p v-if="preservedConflictSelection.length">
          시도한 선택 {{ preservedConflictSelection.length }}개를 보존했습니다.
          <span v-if="missingRepositoryNames.length">
            최신 목록에서 사라진 저장소: {{ missingRepositoryNames.join(', ') }}
          </span>
        </p>
      </section>
      <p v-if="actionMessage" class="alert alert--success github-feedback" role="status">
        {{ actionMessage }}
      </p>

      <StatePanel
        v-if="sources.isPending.value"
        class="github-state"
        kind="loading"
        title="GitHub 연결을 불러오는 중…"
      />
      <StatePanel
        v-else-if="sources.isError.value"
        class="github-state"
        kind="error"
        title="GitHub 연결을 불러오지 못했어요."
        :description="gitHubErrorMessage(normalizeApiError(sources.error.value))"
      >
        <template #actions>
          <button type="button" class="button button--secondary" @click="sources.refetch()">
            다시 시도
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="sources.data.value?.items.length === 0"
        class="github-state"
        kind="empty"
        title="아직 등록한 GitHub 연결이 없어요."
        description="위에 직접 참여한 공개 GitHub 주소를 입력해 첫 경험 근거를 찾아보세요."
      />

      <section
        v-else-if="sources.data.value"
        class="github-sources"
        aria-labelledby="source-list-heading"
      >
        <div class="github-sources__heading">
          <div>
            <p class="section-kicker">등록된 연결</p>
            <h2 id="source-list-heading" class="section-title">
              GitHub source {{ sources.data.value.totalElements }}개
            </h2>
          </div>
        </div>
        <ul class="github-source-list data-list">
          <li
            v-for="source in sources.data.value.items"
            :key="source.id"
            class="github-source-card data-card"
            :class="{ 'github-source-card--focused': source.id === focusedSourceId }"
            :data-testid="`github-source-${source.id}`"
          >
            <div class="github-source-card__header">
              <div>
                <div class="github-source-card__badges">
                  <StatusBadge :label="sourceKindLabel(source)" tone="neutral" />
                  <StatusBadge
                    :label="GITHUB_STATUS_LABELS[source.status]"
                    :tone="gitHubStatusTone(source.status)"
                  />
                </div>
                <h3>{{ sourceDisplayName(source) }}</h3>
                <a
                  v-if="safeSourceUrl(source)"
                  class="text-link github-source-card__url"
                  :href="safeSourceUrl(source)!"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ source.canonicalUrl }}
                </a>
                <span v-else class="github-source-card__url">{{ source.canonicalUrl }}</span>
              </div>
              <div class="github-source-card__actions">
                <button
                  type="button"
                  class="button button--secondary button--compact"
                  @click="focusSource(source.id)"
                >
                  {{ source.id === focusedSourceId ? '선택됨' : '상세 보기' }}
                </button>
                <button
                  v-if="source.status === 'READY' || source.status === 'PARTIAL'"
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="refreshMutation.isPending.value"
                  @click="refreshSource(source)"
                >
                  새로고침
                </button>
                <button
                  type="button"
                  class="button button--danger button--compact"
                  :disabled="deleteMutation.isPending.value"
                  @click="removeSource(source)"
                >
                  삭제
                </button>
              </div>
            </div>

            <dl class="github-source-card__meta">
              <div>
                <dt>저장소</dt>
                <dd>
                  선택 {{ source.selectedRepositoryCount }} / 발견
                  {{ source.discoveredRepositoryCount }}
                </dd>
              </div>
              <div>
                <dt>마지막 성공 동기화</dt>
                <dd>{{ formatGitHubInstant(source.lastSuccessfulSyncAt) }}</dd>
              </div>
              <div>
                <dt>버전</dt>
                <dd>{{ source.version }}</dd>
              </div>
              <div>
                <dt>최근 AI 작업</dt>
                <dd>
                  <RouterLink
                    v-if="source.latestAgentRunId"
                    :to="`/agent-runs/${source.latestAgentRunId}`"
                  >
                    상세 보기
                  </RouterLink>
                  <span v-else>아직 없음</span>
                </dd>
              </div>
            </dl>

            <div
              v-if="source.status === 'READY' || source.status === 'PARTIAL'"
              class="github-result-grid"
              aria-label="GitHub 경험 추출 결과"
            >
              <div>
                <span>새 경험</span><strong>{{ source.newExperienceCount }}</strong>
              </div>
              <div>
                <span>기존 경험 보강</span><strong>{{ source.corroboratedExperienceCount }}</strong>
              </div>
              <div>
                <span>검토 필요</span><strong>{{ source.reviewRequiredCount }}</strong>
              </div>
              <div>
                <span>제외된 후보</span><strong>{{ source.rejectedCandidateCount }}</strong>
              </div>
            </div>
            <p
              v-if="source.repositoryDiscoveryTruncated"
              class="alert alert--warning"
              role="status"
            >
              GitHub에서 발견한 저장소가 많아 일부 목록만 표시합니다. 검색으로 원하는 저장소를
              찾아보세요.
            </p>
            <p v-if="source.snapshotIncomplete" class="alert alert--warning" role="status">
              일부 파일은 안전 한도 안에서 확인하지 못해 결과가 부분적일 수 있어요.
            </p>
            <p v-if="source.status === 'FAILED'" class="alert alert--danger" role="status">
              이 연결의 분석을 마치지 못했어요.
              <RouterLink
                v-if="source.latestAgentRunId"
                :to="`/agent-runs/${source.latestAgentRunId}`"
              >
                AI 작업 상세에서 재시도 가능 여부 확인
              </RouterLink>
            </p>
          </li>
        </ul>
        <PaginationNav
          v-if="sources.data.value.totalPages > 1"
          :page="sources.data.value.page"
          :total-pages="sources.data.value.totalPages"
          label="GitHub source 페이지"
          @change="sourcePage = $event"
        />
      </section>

      <CareerArtifactSuggestion v-if="hasSuccessfulSource" compact />

      <section
        v-if="focusedSourceId"
        class="github-focused section-surface"
        aria-labelledby="github-focused-heading"
      >
        <div class="github-focused__header">
          <div>
            <p class="section-kicker">선택한 연결</p>
            <h2 id="github-focused-heading" class="section-title">상태와 저장소 확인</h2>
          </div>
          <button type="button" class="button button--ghost button--compact" @click="clearFocus">
            닫기
          </button>
        </div>
        <StatePanel v-if="detail.isPending.value" kind="loading" title="연결 상태를 확인하는 중…" />
        <StatePanel
          v-else-if="detail.isError.value"
          kind="error"
          title="연결 상세를 불러오지 못했어요."
          :description="gitHubErrorMessage(normalizeApiError(detail.error.value))"
        />
        <template v-else-if="focusedSummary">
          <p class="github-focused__status">
            <StatusBadge
              :label="GITHUB_STATUS_LABELS[focusedSummary.status]"
              :tone="gitHubStatusTone(focusedSummary.status)"
            />
            <span v-if="focusedSummary.status === 'DISCOVERING'">공개 저장소를 찾고 있어요.</span>
            <span v-else-if="focusedSummary.status === 'QUEUED'">분석 순서를 기다리고 있어요.</span>
            <span v-else-if="focusedSummary.status === 'RUNNING'"
              >선택한 공개 저장소를 분석하고 있어요.</span
            >
            <span v-else-if="focusedSummary.status === 'WAITING_USER'"
              >분석할 저장소를 직접 골라 주세요.</span
            >
          </p>

          <GitHubRunMonitor
            v-if="focusedSummary.latestAgentRunId"
            :user-id="userId"
            :source-id="focusedSummary.id"
            :agent-run-id="focusedSummary.latestAgentRunId"
          />

          <section
            v-if="
              focusedSummary.sourceKind === 'ACCOUNT' && focusedSummary.status === 'WAITING_USER'
            "
            class="repository-selector"
            aria-labelledby="repository-selector-heading"
          >
            <div class="repository-selector__heading">
              <div>
                <p class="section-kicker">저장소 선택</p>
                <h3 id="repository-selector-heading" class="section-title">분석할 저장소 1~10개</h3>
              </div>
              <strong>{{ selectedCount }}개 선택</strong>
            </div>
            <p
              v-if="focusedSummary.repositoryDiscoveryTruncated"
              class="alert alert--warning"
              role="status"
            >
              저장소가 많아 일부만 발견했습니다. 이름 검색으로 확인할 수 있는 공개 저장소 안에서
              선택해 주세요.
            </p>
            <form class="repository-toolbar" role="search" @submit.prevent="submitRepositorySearch">
              <label class="field">
                <span class="field__label">저장소 검색</span>
                <input
                  v-model="repositorySearchInput"
                  class="control"
                  maxlength="200"
                  placeholder="저장소 이름 또는 설명"
                />
              </label>
              <button type="submit" class="button button--secondary">검색</button>
              <label class="field">
                <span class="field__label">정렬</span>
                <select class="control" :value="repositorySort" @change="changeRepositorySort">
                  <option value="pushedAt,desc">최근 push 순</option>
                  <option value="repositoryName,asc">저장소 이름 순</option>
                </select>
              </label>
            </form>

            <StatePanel
              v-if="repositories.isPending.value"
              kind="loading"
              title="공개 저장소를 불러오는 중…"
            />
            <StatePanel
              v-else-if="repositories.isError.value"
              kind="error"
              title="저장소 목록을 불러오지 못했어요."
              :description="gitHubErrorMessage(normalizeApiError(repositories.error.value))"
            />
            <StatePanel
              v-else-if="repositories.data.value?.items.length === 0"
              kind="empty"
              title="조건에 맞는 공개 저장소가 없어요."
              description="검색어를 바꾸거나 GitHub 공개 상태를 확인해 주세요."
            />
            <ul v-else class="repository-list" aria-label="발견한 공개 저장소">
              <li v-for="repository in repositories.data.value?.items" :key="repository.id">
                <label class="repository-choice">
                  <input
                    class="checkbox-control"
                    type="checkbox"
                    :checked="selectedRepositoryIds.includes(repository.id)"
                    :disabled="
                      !selectedRepositoryIds.includes(repository.id) && selectedCount >= 10
                    "
                    :aria-label="`${repository.ownerLogin}/${repository.repositoryName} 선택`"
                    @change="toggleRepository(repository, $event)"
                  />
                  <span class="repository-choice__body">
                    <span class="repository-choice__title">
                      <strong>{{ repository.ownerLogin }}/{{ repository.repositoryName }}</strong>
                      <StatusBadge v-if="repository.fork" label="Fork" tone="neutral" />
                      <StatusBadge v-if="repository.archived" label="보관됨" tone="warning" />
                    </span>
                    <span>{{ repository.description || '설명이 없는 저장소' }}</span>
                    <small>마지막 push {{ formatGitHubInstant(repository.pushedAt) }}</small>
                  </span>
                </label>
              </li>
            </ul>
            <PaginationNav
              v-if="repositories.data.value && repositories.data.value.totalPages > 1"
              :page="repositories.data.value.page"
              :total-pages="repositories.data.value.totalPages"
              label="GitHub 저장소 페이지"
              @change="repositoryPage = $event"
            />
            <p v-if="selectedCount === 0" class="inline-error" role="status">
              분석할 저장소를 1개 이상 선택해 주세요.
            </p>
            <div class="repository-selector__actions">
              <span>최대 10개까지 선택할 수 있어요.</span>
              <button
                type="button"
                class="button button--primary"
                :disabled="!selectionValid || selectionMutation.isPending.value"
                @click="saveSelection"
              >
                {{ selectionMutation.isPending.value ? '저장하는 중…' : '선택 저장하고 분석 시작' }}
              </button>
            </div>
          </section>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.github-policy,
.github-register,
.github-feedback,
.github-state,
.github-sources,
.github-focused {
  margin-top: var(--space-5);
}

.github-policy {
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  padding: var(--space-4) var(--space-5);
}

.github-policy strong {
  color: var(--color-brand-strong);
}

.github-policy ul {
  display: grid;
  gap: var(--space-1);
  margin-top: var(--space-2);
  padding-left: var(--space-5);
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
  list-style: disc;
}

.github-register,
.github-focused {
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.github-register {
  display: grid;
  gap: var(--space-4);
}

.github-participation,
.repository-choice {
  display: flex;
  min-height: 2.75rem;
  cursor: pointer;
  align-items: flex-start;
  gap: var(--space-3);
}

.github-participation span,
.repository-choice__body {
  display: grid;
  gap: var(--space-1);
}

.github-participation small,
.repository-choice__body > span,
.repository-choice small {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
}

.github-sources__heading,
.github-source-card__header,
.github-source-card__badges,
.github-source-card__actions,
.github-focused__header,
.github-focused__status,
.repository-selector__heading,
.repository-choice__title,
.repository-selector__actions {
  display: flex;
  align-items: center;
}

.github-sources__heading,
.github-source-card__header,
.github-focused__header,
.repository-selector__heading,
.repository-selector__actions {
  justify-content: space-between;
  gap: var(--space-4);
}

.github-source-card {
  display: grid;
  gap: var(--space-4);
}

.github-source-card--focused {
  box-shadow:
    inset 0 0 0 2px var(--color-brand-border),
    var(--shadow-card);
}

.github-source-card__badges,
.github-source-card__actions,
.github-focused__status,
.repository-choice__title {
  flex-wrap: wrap;
  gap: var(--space-2);
}

.github-source-card h3 {
  margin-top: var(--space-2);
  font-size: 1.125rem;
}

.github-source-card__url {
  display: inline-block;
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
  font-size: var(--font-size-sm);
}

.github-source-card__meta,
.github-result-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.github-source-card__meta dt,
.github-result-grid span {
  color: var(--color-muted);
  font-size: var(--font-size-xs);
}

.github-source-card__meta dd,
.github-result-grid strong {
  display: block;
  margin-top: var(--space-1);
  overflow-wrap: anywhere;
  font-size: var(--font-size-sm);
}

.github-result-grid > div {
  border-radius: var(--radius-md);
  background: var(--color-fill);
  padding: var(--space-3);
}

.github-result-grid strong {
  font-size: 1.25rem;
}

.github-focused {
  display: grid;
  gap: var(--space-5);
  scroll-margin-top: 5rem;
}

.repository-selector {
  display: grid;
  gap: var(--space-4);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-5);
}

.repository-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(11rem, 14rem);
  align-items: end;
  gap: var(--space-3);
}

.repository-list {
  display: grid;
  gap: var(--space-2);
}

.repository-list li {
  border-radius: var(--radius-md);
  background: var(--color-fill);
  padding: var(--space-4);
}

.repository-choice__body {
  min-width: 0;
}

.repository-choice__title strong {
  overflow-wrap: anywhere;
}

.repository-selector__actions > span {
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.github-feedback:focus {
  outline: 3px solid var(--color-focus-ring);
  outline-offset: 2px;
}

@media (max-width: 64rem) {
  .github-source-card__meta,
  .github-result-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .github-source-card__header,
  .github-focused__header,
  .repository-selector__heading,
  .repository-selector__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .github-source-card__actions,
  .github-source-card__actions .button,
  .repository-selector__actions .button {
    width: 100%;
  }

  .github-source-card__actions .button {
    flex: 1;
  }

  .github-source-card__meta,
  .github-result-grid,
  .repository-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
