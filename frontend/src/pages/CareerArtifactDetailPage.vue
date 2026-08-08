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
    title: '이 생성 자료를 삭제할까요?',
    message:
      '생성한 파일과 모든 버전은 제거됩니다. 원본으로 사용한 업로드 문서와 경험 보관함의 경험은 그대로 유지됩니다.',
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
    notifications.toast('생성 자료를 삭제했어요.', 'success')
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
    <RouterLink class="text-link" to="/career-artifacts">← AI로 만든 초안 목록</RouterLink>

    <section v-if="detail.isPending.value" class="state-panel" aria-busy="true" role="status">
      생성 자료를 불러오는 중…
    </section>
    <section v-else-if="detailError" class="state-panel state-panel--error">
      <h1 v-if="detailError.status === 404">이 자료를 찾을 수 없어요</h1>
      <h1 v-else-if="detailError.code === 'INVALID_SERVER_RESPONSE'">
        자료 정보를 안전하게 표시하지 못했어요
      </h1>
      <h1 v-else>생성 자료를 불러오지 못했어요</h1>
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
              artifact.currentVersionNo ? `현재 v${artifact.currentVersionNo}` : '성공한 버전 없음'
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
            <p class="section-kicker">현재 성공 버전</p>
            <h2>v{{ detail.data.value.currentVersion.versionNo }} 미리보기</h2>
            <p>새 생성이 진행되거나 실패해도 이 성공 버전은 계속 이용할 수 있어요.</p>
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
          {{ downloadInfo[detail.data.value.currentVersion.id]?.filename }} · 링크 만료
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

      <section v-else class="state-panel">
        <h2>아직 성공한 파일이 없어요</h2>
        <p>
          실패·취소·중단된 작업은 원본 경험을 바꾸지 않습니다. 최근 AI 작업에서 안전한 오류를
          확인하고 다시 시도하거나 설정을 다시 선택하세요.
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
            <p class="section-kicker">버전 기록</p>
            <h2>성공한 파일 버전</h2>
            <p>
              과거 버전은 구조화 미리보기가 제공되지 않으며, 선택한 파일만 다운로드할 수 있어요.
            </p>
          </div>
        </header>
        <p v-if="versions.isPending.value" role="status">버전 기록을 불러오는 중…</p>
        <div v-else-if="versions.isError.value" class="alert alert--warning">
          버전 기록을 불러오지 못했어요.
          <button type="button" class="text-link" @click="versions.refetch()">다시 불러오기</button>
        </div>
        <p v-else-if="versions.data.value?.items.length === 0">아직 성공한 파일 버전이 없어요.</p>
        <div v-else class="career-artifact-detail__version-layout">
          <div
            role="listbox"
            aria-label="다운로드할 버전"
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
            <h3>v{{ selectedVersion.versionNo }} 파일</h3>
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
                <dt>생성 시각</dt>
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
              {{ downloadInfo[selectedVersion.id]?.filename }} · 링크 만료
              {{ formatCareerArtifactInstant(downloadInfo[selectedVersion.id]!.expiresAt) }}
            </p>
          </article>
        </div>
        <nav
          v-if="versions.data.value && versions.data.value.totalPages > 1"
          class="pagination-controls"
          aria-label="버전 페이지"
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
          <p class="section-kicker">자료 관리</p>
          <h2>새 버전과 보관 상태</h2>
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
          <p class="section-kicker">새 버전</p>
          <h2>경험과 표시 정보를 다시 확인하세요</h2>
          <p>과거 연락처와 전체 경험 선택은 공개 API에 포함되지 않으므로 추측해 채우지 않습니다.</p>
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
.career-artifact-detail {
  min-width: 0;
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
  padding: clamp(1.25rem, 4vw, 2rem);
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
  border-color: var(--color-primary);
  background: var(--hs-blue-50);
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
