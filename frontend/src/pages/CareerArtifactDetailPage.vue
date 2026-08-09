<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { closeAgentRunStreamsForResource } from '@/features/agent-runs/stream'
import CareerArtifactGenerationForm from '@/features/career-artifacts/CareerArtifactGenerationForm.vue'
import CareerArtifactRunMonitor from '@/features/career-artifacts/CareerArtifactRunMonitor.vue'
import PortfolioArtifactPreview from '@/features/career-artifacts/PortfolioArtifactPreview.vue'
import ResumeArtifactPreview from '@/features/career-artifacts/ResumeArtifactPreview.vue'
import { clearCareerArtifactDraftsForArtifact } from '@/features/career-artifacts/drafts'
import {
  ARTIFACT_FILE_LABELS,
  ARTIFACT_GENERATION_LABELS,
  ARTIFACT_LIFECYCLE_LABELS,
  ARTIFACT_TYPE_LABELS,
  careerArtifactErrorMessage,
  formatCareerArtifactInstant,
} from '@/features/career-artifacts/presentation'
import {
  useCareerArtifactDetailQuery,
  useCareerArtifactLifecycleMutation,
  useCareerArtifactVersionsQuery,
  useDeleteCareerArtifactMutation,
} from '@/features/career-artifacts/queries'
import { createCareerArtifactDownloadUrl } from '@/shared/api/careerArtifactApi'
import type {
  CareerArtifactVersionSummaryDto,
  PortfolioArtifactPreviewDto,
  ResumeArtifactPreviewDto,
} from '@/shared/api/careerArtifactContracts'
import type { RunAcceptedDto } from '@/shared/api/agentRunContracts'
import { normalizeApiError } from '@/shared/api/errors'
import BackLink from '@/shared/ui/BackLink.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotifications()
const artifactId = computed(() => String(route.params.careerArtifactId ?? ''))
const userId = computed(() => authStore.currentUser?.id ?? '')
const detail = useCareerArtifactDetailQuery(userId, artifactId)
const versionPage = ref(0)
const versions = useCareerArtifactVersionsQuery(
  userId,
  artifactId,
  computed(() => ({ page: versionPage.value, size: 20, sort: 'versionNo,desc' as const })),
)
const lifecycleMutation = useCareerArtifactLifecycleMutation(userId, artifactId)
const deleteMutation = useDeleteCareerArtifactMutation(userId, artifactId)
const trackedRunId = ref('')
const regenerating = ref(false)
const actionError = ref('')
const selectedVersionId = ref('')
const versionButtons = ref<HTMLButtonElement[]>([])
const downloadInfo = ref<Record<string, { filename: string; expiresAt: string }>>({})
const downloadingVersionId = ref('')
const downloadTickets = new Map<
  string,
  Awaited<ReturnType<typeof createCareerArtifactDownloadUrl>>
>()

const artifact = computed(() => detail.data.value?.artifact ?? null)
const selectedVersion = computed(
  () =>
    versions.data.value?.items.find((version) => version.id === selectedVersionId.value) ?? null,
)
const detailError = computed(() =>
  detail.isError.value ? normalizeApiError(detail.error.value) : null,
)
const lifecycleLabel = computed(() =>
  artifact.value ? ARTIFACT_LIFECYCLE_LABELS[artifact.value.lifecycleStatus] : '',
)
const generationInProgress = computed(
  () => artifact.value !== null && ['QUEUED', 'RUNNING'].includes(artifact.value.generationStatus),
)

watch(
  () => detail.data.value?.latestRun?.id,
  (runId) => {
    if (runId) trackedRunId.value = runId
  },
  { immediate: true },
)

watch(
  () => versions.data.value?.items,
  (items) => {
    if (!items?.length) {
      selectedVersionId.value = ''
      return
    }
    if (!items.some((version) => version.id === selectedVersionId.value)) {
      selectedVersionId.value = items[0]?.id ?? ''
    }
  },
  { immediate: true },
)

async function changeLifecycle(action: 'archive' | 'unarchive'): Promise<void> {
  if (artifact.value === null) return
  actionError.value = ''
  try {
    await lifecycleMutation.mutateAsync({ action, version: artifact.value.version })
  } catch (error) {
    const apiError = normalizeApiError(error)
    actionError.value = careerArtifactErrorMessage(apiError)
    if (apiError.status === 409) await detail.refetch()
  }
}

async function deleteArtifact(): Promise<void> {
  if (artifact.value === null || artifact.value.lifecycleStatus !== 'ACTIVE') return
  const confirmedArtifactId = artifact.value.id
  const confirmedVersion = artifact.value.version
  const confirmed = await notifications.confirm({
    title: '이 자료를 삭제할까요?',
    message:
      '만들어 둔 파일과 지난 기록이 모두 사라져요. 업로드한 자료와 경험 보관함의 내용은 그대로 남아요.',
    confirmLabel: '자료 삭제',
    cancelLabel: '취소',
    tone: 'danger',
  })
  if (!confirmed) return
  actionError.value = ''
  try {
    await deleteMutation.mutateAsync(confirmedVersion)
    closeAgentRunStreamsForResource(userId.value, 'CAREER_ARTIFACT', confirmedArtifactId)
    clearCareerArtifactDraftsForArtifact(userId.value, confirmedArtifactId)
    notifications.toast('자료를 삭제했어요.', 'success')
    await router.replace({ name: 'career-artifacts' })
  } catch (error) {
    const apiError = normalizeApiError(error)
    actionError.value = careerArtifactErrorMessage(apiError)
    if (apiError.status === 409) await detail.refetch()
  }
}

async function downloadVersion(version: CareerArtifactVersionSummaryDto): Promise<void> {
  if (downloadingVersionId.value !== '') return
  downloadingVersionId.value = version.id
  actionError.value = ''
  try {
    const cached = downloadTickets.get(version.id)
    const issued =
      cached && Date.parse(cached.expiresAt) > Date.now()
        ? cached
        : await createCareerArtifactDownloadUrl(artifactId.value, version.id)
    downloadTickets.set(version.id, issued)
    downloadInfo.value = {
      ...downloadInfo.value,
      [version.id]: { filename: issued.filename, expiresAt: issued.expiresAt },
    }
    const anchor = document.createElement('a')
    anchor.href = issued.url
    anchor.download = issued.filename
    anchor.rel = 'noopener'
    anchor.hidden = true
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
  } catch (error) {
    actionError.value = careerArtifactErrorMessage(normalizeApiError(error))
  } finally {
    downloadingVersionId.value = ''
  }
}

function onRegenerated(accepted: RunAcceptedDto): void {
  if (accepted.resourceType !== 'CAREER_ARTIFACT' || accepted.resourceId !== artifactId.value)
    return
  trackedRunId.value = accepted.agentRunId
  regenerating.value = false
  void detail.refetch()
}

function onGenerationConflict(): void {
  void detail.refetch()
}

async function selectVersion(index: number, focus = false): Promise<void> {
  const item = versions.data.value?.items[index]
  if (!item) return
  selectedVersionId.value = item.id
  if (focus) {
    await nextTick()
    versionButtons.value[index]?.focus()
  }
}

function onVersionKeydown(event: KeyboardEvent, index: number): void {
  const length = versions.data.value?.items.length ?? 0
  if (length === 0) return
  let next: number
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % length
  else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft')
    next = (index - 1 + length) % length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = length - 1
  else return
  event.preventDefault()
  void selectVersion(next, true)
}

function setVersionButtonRef(
  element: Element | ComponentPublicInstance | null,
  index: number,
): void {
  if (element instanceof HTMLButtonElement) versionButtons.value[index] = element
}

function generationTone(status: string) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED' || status === 'INTERRUPTED') return 'warning'
  if (status === 'RUNNING') return 'info'
  return 'neutral'
}
</script>

<template>
  <main class="career-artifact-detail page-stack">
    <BackLink to="/career-artifacts">AI로 만든 초안 목록</BackLink>

    <section v-if="detail.isPending.value" class="state-panel" aria-busy="true" role="status">
      자료를 불러오는 중…
    </section>
    <section v-else-if="detailError" class="state-panel state-panel--error">
      <h1 v-if="detailError.status === 404">이 자료를 찾을 수 없어요</h1>
      <h1 v-else-if="detailError.code === 'INVALID_SERVER_RESPONSE'">
        자료 내용을 표시하지 못했어요
      </h1>
      <h1 v-else>자료를 불러오지 못했어요</h1>
      <p v-if="detailError.status === 404">삭제되었거나 이 계정에서 볼 수 없는 자료예요.</p>
      <p v-else>{{ careerArtifactErrorMessage(detailError) }}</p>
      <button type="button" class="button button--secondary" @click="detail.refetch()">
        다시 불러오기
      </button>
    </section>

    <template v-else-if="artifact && detail.data.value">
      <header class="career-artifact-detail__header section-surface">
        <div>
          <p class="section-kicker">
            {{ ARTIFACT_TYPE_LABELS[artifact.artifactType] }} · {{ lifecycleLabel }}
          </p>
          <h1>{{ artifact.title }}</h1>
          <p>
            {{ ARTIFACT_GENERATION_LABELS[artifact.generationStatus] }} ·
            {{
              artifact.currentVersionNo
                ? `현재 v${artifact.currentVersionNo}`
                : '아직 받을 파일 없음'
            }}
          </p>
        </div>
        <StatusBadge
          :label="ARTIFACT_GENERATION_LABELS[artifact.generationStatus]"
          :tone="generationTone(artifact.generationStatus)"
        />
      </header>

      <p v-if="actionError" class="alert alert--warning" role="alert">{{ actionError }}</p>

      <CareerArtifactRunMonitor
        v-if="artifact.lifecycleStatus === 'ACTIVE' && trackedRunId"
        :user-id="userId"
        :artifact-id="artifact.id"
        :artifact-type="artifact.artifactType"
        :agent-run-id="trackedRunId"
        @track-run="trackedRunId = $event"
      />

      <section
        v-if="detail.data.value.currentVersion && detail.data.value.preview"
        class="career-artifact-detail__preview"
      >
        <header class="career-artifact-detail__section-heading">
          <div>
            <p class="section-kicker">지금 받을 수 있는 파일</p>
            <h2>v{{ detail.data.value.currentVersion.versionNo }} 내용 살펴보기</h2>
            <p>새로 만드는 중이거나 실패하더라도 이 파일은 그대로 받을 수 있어요.</p>
          </div>
          <button
            type="button"
            class="button button--primary"
            :disabled="downloadingVersionId !== ''"
            @click="downloadVersion(detail.data.value.currentVersion)"
          >
            {{ ARTIFACT_FILE_LABELS[artifact.artifactType] }} 다운로드
          </button>
        </header>
        <p
          v-if="downloadInfo[detail.data.value.currentVersion.id]"
          class="career-artifact-detail__download-info"
          role="status"
        >
          {{ downloadInfo[detail.data.value.currentVersion.id]?.filename }} · 다운로드 링크 만료
          {{
            formatCareerArtifactInstant(
              downloadInfo[detail.data.value.currentVersion.id]!.expiresAt,
            )
          }}
        </p>
        <ResumeArtifactPreview
          v-if="artifact.artifactType === 'RESUME'"
          :preview="detail.data.value.preview as ResumeArtifactPreviewDto"
        />
        <PortfolioArtifactPreview
          v-else
          :preview="detail.data.value.preview as PortfolioArtifactPreviewDto"
        />
      </section>

      <!-- 만드는 중일 때는 위 진행 카드가 이미 상태를 알려 주므로 실패 안내를 겹쳐 보여 주지 않는다. -->
      <section v-else-if="generationInProgress" class="state-panel">
        <h2>파일을 만들고 있어요</h2>
        <p>다 만들면 여기에서 내용을 살펴보고 파일로 받을 수 있어요.</p>
      </section>

      <section v-else class="state-panel">
        <h2>아직 받을 수 있는 파일이 없어요</h2>
        <p>
          작업이 실패하거나 중단돼도 내 경험 기록은 그대로예요. 무엇이 문제였는지 확인하고 다시
          만들어 보세요.
        </p>
        <RouterLink
          v-if="artifact.latestAgentRunId"
          class="button button--secondary"
          :to="`/agent-runs/${artifact.latestAgentRunId}`"
          >AI 작업 상세 보기</RouterLink
        >
      </section>

      <section class="career-artifact-detail__versions section-surface">
        <header class="career-artifact-detail__section-heading">
          <div>
            <p class="section-kicker">지난 기록</p>
            <h2>만들어 둔 파일</h2>
            <p>예전에 만든 파일은 내용 미리보기 없이 다운로드만 할 수 있어요.</p>
          </div>
        </header>
        <p v-if="versions.isPending.value" role="status">지난 기록을 불러오는 중…</p>
        <div v-else-if="versions.isError.value" class="alert alert--warning">
          지난 기록을 불러오지 못했어요.
          <button type="button" class="text-link" @click="versions.refetch()">다시 불러오기</button>
        </div>
        <p v-else-if="versions.data.value?.items.length === 0">아직 만들어 둔 파일이 없어요.</p>
        <div v-else class="career-artifact-detail__version-layout">
          <div
            role="listbox"
            aria-label="받을 파일 고르기"
            class="career-artifact-detail__version-list"
          >
            <button
              v-for="(version, index) in versions.data.value?.items"
              :key="version.id"
              :ref="(element) => setVersionButtonRef(element, index)"
              type="button"
              role="option"
              :aria-selected="selectedVersionId === version.id"
              :tabindex="selectedVersionId === version.id ? 0 : -1"
              @click="selectVersion(index)"
              @keydown="onVersionKeydown($event, index)"
            >
              <strong>v{{ version.versionNo }}</strong>
              <span>{{ version.model }}</span>
              <small>{{ formatCareerArtifactInstant(version.createdAt) }}</small>
            </button>
          </div>
          <article v-if="selectedVersion" class="career-artifact-detail__selected-version">
            <h3>v{{ selectedVersion.versionNo }}</h3>
            <dl>
              <div>
                <dt>AI 모델</dt>
                <dd>{{ selectedVersion.model }}</dd>
              </div>
              <div>
                <dt>파일 크기</dt>
                <dd>{{ Math.ceil(selectedVersion.fileSizeBytes / 1024) }} KB</dd>
              </div>
              <div>
                <dt>만든 시각</dt>
                <dd>{{ formatCareerArtifactInstant(selectedVersion.createdAt) }}</dd>
              </div>
            </dl>
            <button
              type="button"
              class="button button--primary"
              :disabled="downloadingVersionId !== ''"
              @click="downloadVersion(selectedVersion)"
            >
              {{ ARTIFACT_FILE_LABELS[artifact.artifactType] }} 다운로드
            </button>
            <p v-if="downloadInfo[selectedVersion.id]" role="status">
              {{ downloadInfo[selectedVersion.id]?.filename }} · 다운로드 링크 만료
              {{ formatCareerArtifactInstant(downloadInfo[selectedVersion.id]!.expiresAt) }}
            </p>
          </article>
        </div>
        <nav
          v-if="versions.data.value && versions.data.value.totalPages > 1"
          class="pagination-controls"
          aria-label="기록 페이지"
        >
          <button
            type="button"
            class="button button--secondary"
            :disabled="versionPage === 0"
            @click="versionPage -= 1"
          >
            이전
          </button>
          <span>{{ versionPage + 1 }} / {{ versions.data.value.totalPages }}</span>
          <button
            type="button"
            class="button button--secondary"
            :disabled="versionPage + 1 >= versions.data.value.totalPages"
            @click="versionPage += 1"
          >
            다음
          </button>
        </nav>
      </section>

      <section
        v-if="artifact.lifecycleStatus === 'ACTIVE'"
        class="career-artifact-detail__actions section-surface"
      >
        <header>
          <p class="section-kicker">이 자료 관리</p>
          <h2>다시 만들기와 보관</h2>
        </header>
        <div>
          <button
            type="button"
            class="button button--primary"
            @click="regenerating = !regenerating"
          >
            {{ regenerating ? '설정 닫기' : '새 버전 만들기' }}
          </button>
          <button
            type="button"
            class="button button--secondary"
            :disabled="lifecycleMutation.isPending.value"
            @click="changeLifecycle('archive')"
          >
            보관
          </button>
          <button
            type="button"
            class="button button--danger"
            :disabled="deleteMutation.isPending.value"
            @click="deleteArtifact"
          >
            삭제
          </button>
        </div>
      </section>
      <section v-else class="career-artifact-detail__actions section-surface">
        <div>
          <button
            type="button"
            class="button button--primary"
            :disabled="lifecycleMutation.isPending.value"
            @click="changeLifecycle('unarchive')"
          >
            다시 사용
          </button>
        </div>
      </section>

      <section
        v-if="regenerating && artifact.lifecycleStatus === 'ACTIVE'"
        class="career-artifact-detail__regenerate"
      >
        <header>
          <p class="section-kicker">다시 만들기</p>
          <h2>어떤 내용으로 만들까요</h2>
          <p>
            지난번에 고른 경험과 연락처는 저장해 두지 않아요. 이번에 넣을 내용을 다시 골라 주세요.
          </p>
        </header>
        <CareerArtifactGenerationForm
          v-if="authStore.currentUser"
          :key="`${artifact.id}:${artifact.version}`"
          mode="regenerate"
          :user-id="authStore.currentUser.id"
          :display-name="authStore.currentUser.displayName"
          :email="authStore.currentUser.email"
          :artifact-id="artifact.id"
          :artifact-version="artifact.version"
          :artifact-type="artifact.artifactType"
          :fixed-title="artifact.title"
          :initial-model="detail.data.value.currentVersion?.model ?? ''"
          @submitted="onRegenerated"
          @cancelled="regenerating = false"
          @conflict="onGenerationConflict"
        />
      </section>
    </template>
  </main>
</template>

<style scoped>
/* 뒤로 가기 → 자료 요약 → 진행 상태 → 결과 → 버전 → 관리 순으로 같은 간격을 두고 쌓는다. */
.career-artifact-detail {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
  align-content: start;
}

.career-artifact-detail > .back-link {
  margin-bottom: 0;
  justify-self: start;
}

.career-artifact-detail__header,
.career-artifact-detail__section-heading,
.career-artifact-detail__actions,
.career-artifact-detail__actions > div,
.pagination-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.career-artifact-detail__header,
.career-artifact-detail__versions,
.career-artifact-detail__actions,
.career-artifact-detail__regenerate {
  padding: var(--space-5) var(--space-6);
}

.career-artifact-detail__header h1 {
  margin: var(--space-1) 0 0;
  color: var(--color-ink-title);
  font-size: clamp(1.375rem, 1.2rem + 0.4vw, 1.625rem);
  font-weight: 760;
  letter-spacing: -0.03em;
  line-height: 1.3;
}

.career-artifact-detail__section-heading h2,
.career-artifact-detail__actions h2,
.career-artifact-detail__regenerate h2 {
  color: var(--color-ink-title);
  font-size: 1.0625rem;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.career-artifact-detail__header h1,
.career-artifact-detail__header p,
.career-artifact-detail__section-heading h2,
.career-artifact-detail__section-heading p,
.career-artifact-detail__actions h2,
.career-artifact-detail__actions p,
.career-artifact-detail__regenerate h2,
.career-artifact-detail__regenerate p {
  margin: 0;
}

.career-artifact-detail__header p:last-child,
.career-artifact-detail__section-heading p:last-child,
.career-artifact-detail__regenerate header p:last-child {
  margin-top: var(--space-2);
  color: var(--color-muted);
}

.career-artifact-detail__preview,
.career-artifact-detail__versions,
.career-artifact-detail__regenerate {
  display: grid;
  min-width: 0;
  gap: var(--space-5);
}

.career-artifact-detail__download-info {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.career-artifact-detail__version-layout {
  display: grid;
  grid-template-columns: minmax(12rem, 0.8fr) minmax(0, 1.2fr);
  gap: var(--space-4);
}

.career-artifact-detail__version-list {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.career-artifact-detail__version-list button {
  display: grid;
  min-height: 4.5rem;
  gap: var(--space-1);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-ink);
  background: var(--color-surface);
  text-align: left;
}

.career-artifact-detail__version-list button[aria-selected='true'] {
  border-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
}

.career-artifact-detail__version-list span,
.career-artifact-detail__version-list small,
.career-artifact-detail__selected-version p {
  color: var(--color-muted);
}

.career-artifact-detail__selected-version {
  min-width: 0;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.career-artifact-detail__selected-version h3 {
  margin-top: 0;
}

.career-artifact-detail__selected-version dl {
  display: grid;
  gap: var(--space-2);
}

.career-artifact-detail__selected-version dl div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}

.career-artifact-detail__selected-version dd {
  margin: 0;
  overflow-wrap: anywhere;
  text-align: right;
}

.pagination-controls {
  justify-content: center;
}

@media (max-width: 46rem) {
  .career-artifact-detail__header,
  .career-artifact-detail__section-heading,
  .career-artifact-detail__actions,
  .career-artifact-detail__actions > div {
    align-items: stretch;
    flex-direction: column;
  }

  .career-artifact-detail__version-layout {
    grid-template-columns: 1fr;
  }

  .career-artifact-detail__header .button,
  .career-artifact-detail__section-heading .button,
  .career-artifact-detail__actions .button {
    width: 100%;
  }
}
</style>
