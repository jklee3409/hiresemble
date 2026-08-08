<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import CareerArtifactAreaSwitch from '@/features/career-artifacts/CareerArtifactAreaSwitch.vue'
import {
  canonicalCareerArtifactListQuery,
  careerArtifactQuerySignature,
  parseCareerArtifactListFilters,
} from '@/features/career-artifacts/filters'
import {
  ARTIFACT_GENERATION_LABELS,
  ARTIFACT_LIFECYCLE_LABELS,
  ARTIFACT_TYPE_LABELS,
  careerArtifactErrorMessage,
  formatCareerArtifactInstant,
} from '@/features/career-artifacts/presentation'
import { careerArtifactQueryKeys } from '@/features/career-artifacts/queryKeys'
import {
  useCareerArtifactListQuery,
  useCareerArtifactReadinessQuery,
} from '@/features/career-artifacts/queries'
import { archiveCareerArtifact, unarchiveCareerArtifact } from '@/shared/api/careerArtifactApi'
import type { CareerArtifactSummaryDto } from '@/shared/api/careerArtifactContracts'
import { normalizeApiError } from '@/shared/api/errors'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cache = useQueryClient()
const userId = computed(() => authStore.currentUser?.id ?? '')
const filters = computed(() => parseCareerArtifactListFilters(route.query))
const list = useCareerArtifactListQuery(userId, filters)
const allArtifacts = useCareerArtifactListQuery(
  userId,
  computed(() => ({ page: 0, size: 1, sort: 'updatedAt,desc' as const })),
)
const readiness = useCareerArtifactReadinessQuery(userId)
const mutationError = ref('')

const lifecycleMutation = useMutation({
  retry: false,
  mutationFn: (input: { artifact: CareerArtifactSummaryDto; action: 'archive' | 'unarchive' }) =>
    input.action === 'archive'
      ? archiveCareerArtifact(input.artifact.id, input.artifact.version)
      : unarchiveCareerArtifact(input.artifact.id, input.artifact.version),
  onSuccess: async (detail) => {
    mutationError.value = ''
    cache.setQueryData(careerArtifactQueryKeys.detail(userId.value, detail.artifact.id), detail)
    await Promise.all([
      cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.listRoot(userId.value) }),
      cache.invalidateQueries({ queryKey: careerArtifactQueryKeys.readiness(userId.value) }),
    ])
  },
  onError: async (error, input) => {
    const apiError = normalizeApiError(error)
    mutationError.value = careerArtifactErrorMessage(apiError)
    if (apiError.status === 409) {
      await Promise.allSettled([
        list.refetch(),
        cache.invalidateQueries({
          queryKey: careerArtifactQueryKeys.detail(userId.value, input.artifact.id),
        }),
      ])
    }
  },
})

watch(
  () => route.query,
  (query) => {
    const canonical = canonicalCareerArtifactListQuery(parseCareerArtifactListFilters(query))
    if (careerArtifactQuerySignature(query) !== careerArtifactQuerySignature(canonical)) {
      void router.replace({ name: 'career-artifacts', query: canonical })
    }
  },
  { immediate: true },
)

function updateFilter(name: 'artifactType' | 'lifecycleStatus' | 'sort', value: string): void {
  const next = canonicalCareerArtifactListQuery({
    ...filters.value,
    ...(name === 'artifactType'
      ? { artifactType: value === '' ? undefined : (value as 'RESUME' | 'PORTFOLIO') }
      : name === 'lifecycleStatus'
        ? { lifecycleStatus: value as 'ACTIVE' | 'ARCHIVED' }
        : { sort: value as 'updatedAt,desc' | 'createdAt,desc' }),
    page: 0,
  })
  void router.replace({ name: 'career-artifacts', query: next })
}

function goToPage(page: number): void {
  void router.replace({
    name: 'career-artifacts',
    query: canonicalCareerArtifactListQuery({ ...filters.value, page }),
  })
}

function generationTone(status: CareerArtifactSummaryDto['generationStatus']) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELLED' || status === 'INTERRUPTED') return 'warning'
  if (status === 'RUNNING') return 'info'
  return 'neutral'
}
</script>

<template>
  <main class="career-artifact-list page-stack">
    <h1 class="sr-only">AI로 만든 이력서·포트폴리오 초안</h1>
    <CareerArtifactAreaSwitch />

    <section class="career-artifact-list__intro section-surface">
      <div>
        <p class="section-kicker">AI로 만든 초안</p>
        <h2>검증된 경험으로 만든 파일을 확인하세요</h2>
        <p>
          초안의 구조화 내용을 먼저 검토하고, 성공한 버전의 Word 또는 PowerPoint 파일을 받을 수
          있어요.
        </p>
      </div>
      <div
        v-if="readiness.data.value?.verifiedExperienceCount"
        class="career-artifact-list__create"
      >
        <RouterLink
          v-if="readiness.data.value.canGenerateResume"
          class="button button--primary"
          :to="{ name: 'career-artifact-new', query: { type: 'RESUME' } }"
        >
          이력서 DOCX 만들기
        </RouterLink>
        <RouterLink
          v-if="readiness.data.value.canGeneratePortfolio"
          class="button button--secondary"
          :to="{ name: 'career-artifact-new', query: { type: 'PORTFOLIO' } }"
        >
          포트폴리오 PPTX 만들기
        </RouterLink>
      </div>
      <RouterLink
        v-else-if="readiness.data.value"
        class="button button--secondary"
        to="/profile/experiences"
      >
        경험 보관함에서 경험 확인하기
      </RouterLink>
    </section>

    <section class="career-artifact-list__filters" aria-label="생성 자료 필터">
      <label>
        종류
        <select
          class="control"
          :value="filters.artifactType ?? ''"
          @change="updateFilter('artifactType', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">전체</option>
          <option value="RESUME">이력서</option>
          <option value="PORTFOLIO">포트폴리오</option>
        </select>
      </label>
      <label>
        상태
        <select
          class="control"
          :value="filters.lifecycleStatus"
          @change="updateFilter('lifecycleStatus', ($event.target as HTMLSelectElement).value)"
        >
          <option value="ACTIVE">사용 중</option>
          <option value="ARCHIVED">보관</option>
        </select>
      </label>
      <label>
        정렬
        <select
          class="control"
          :value="filters.sort"
          @change="updateFilter('sort', ($event.target as HTMLSelectElement).value)"
        >
          <option value="updatedAt,desc">최근 수정순</option>
          <option value="createdAt,desc">최근 생성순</option>
        </select>
      </label>
    </section>

    <p v-if="mutationError" class="alert alert--warning" role="alert">{{ mutationError }}</p>
    <section :aria-busy="list.isPending.value" aria-live="polite">
      <p v-if="list.isPending.value" class="state-panel" role="status">
        생성 자료 목록을 불러오는 중…
      </p>
      <div v-else-if="list.isError.value" class="state-panel state-panel--error">
        <p>생성 자료 목록을 불러오지 못했어요.</p>
        <button type="button" class="button button--secondary" @click="list.refetch()">
          다시 불러오기
        </button>
      </div>
      <div v-else-if="list.data.value?.items.length === 0" class="state-panel">
        <template v-if="allArtifacts.data.value?.totalElements === 0">
          <h2>아직 만든 초안이 없어요</h2>
          <p>확인된 경험을 선택해 첫 파일을 만들어 보세요.</p>
        </template>
        <template v-else>
          <h2>선택한 조건에 맞는 자료가 없어요</h2>
          <p>종류나 상태 필터를 바꿔 보세요.</p>
        </template>
      </div>
      <ul v-else class="career-artifact-list__items">
        <li v-for="artifact in list.data.value?.items" :key="artifact.id">
          <article>
            <header>
              <div>
                <p class="section-kicker">{{ ARTIFACT_TYPE_LABELS[artifact.artifactType] }}</p>
                <h2>{{ artifact.title }}</h2>
              </div>
              <StatusBadge
                :label="ARTIFACT_GENERATION_LABELS[artifact.generationStatus]"
                :tone="generationTone(artifact.generationStatus)"
              />
            </header>
            <dl>
              <div>
                <dt>이용 상태</dt>
                <dd>{{ ARTIFACT_LIFECYCLE_LABELS[artifact.lifecycleStatus] }}</dd>
              </div>
              <div>
                <dt>현재 버전</dt>
                <dd>
                  {{ artifact.currentVersionNo ? `v${artifact.currentVersionNo}` : '아직 없음' }}
                </dd>
              </div>
              <div>
                <dt>최근 수정</dt>
                <dd>{{ formatCareerArtifactInstant(artifact.updatedAt) }}</dd>
              </div>
            </dl>
            <footer>
              <RouterLink class="button button--primary" :to="`/career-artifacts/${artifact.id}`"
                >상세 보기</RouterLink
              >
              <RouterLink
                v-if="artifact.latestAgentRunId"
                class="text-link"
                :to="`/agent-runs/${artifact.latestAgentRunId}`"
                >최근 AI 작업</RouterLink
              >
              <button
                type="button"
                class="button button--secondary"
                :disabled="lifecycleMutation.isPending.value"
                @click="
                  lifecycleMutation.mutate({
                    artifact,
                    action: artifact.lifecycleStatus === 'ACTIVE' ? 'archive' : 'unarchive',
                  })
                "
              >
                {{ artifact.lifecycleStatus === 'ACTIVE' ? '보관' : '다시 사용' }}
              </button>
            </footer>
          </article>
        </li>
      </ul>
    </section>

    <nav
      v-if="list.data.value && list.data.value.totalPages > 1"
      class="pagination-controls"
      aria-label="생성 자료 페이지"
    >
      <button
        type="button"
        class="button button--secondary"
        :disabled="filters.page === 0"
        @click="goToPage(filters.page - 1)"
      >
        이전
      </button>
      <span>{{ filters.page + 1 }} / {{ list.data.value.totalPages }}</span>
      <button
        type="button"
        class="button button--secondary"
        :disabled="filters.page + 1 >= list.data.value.totalPages"
        @click="goToPage(filters.page + 1)"
      >
        다음
      </button>
    </nav>
  </main>
</template>

<style scoped>
.career-artifact-list {
  min-width: 0;
}

.career-artifact-list__intro {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-6);
  padding: clamp(1.25rem, 4vw, 2rem);
}

.career-artifact-list__intro h2,
.career-artifact-list__intro p {
  margin: 0;
}

.career-artifact-list__intro p:last-child {
  margin-top: var(--space-2);
  color: var(--color-muted);
}

.career-artifact-list__create,
.career-artifact-list__filters,
.career-artifact-list__items article > footer,
.pagination-controls {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.career-artifact-list__filters {
  flex-wrap: wrap;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.career-artifact-list__filters label {
  display: grid;
  min-width: min(100%, 11rem);
  gap: var(--space-1);
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.career-artifact-list__items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 22rem), 1fr));
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.career-artifact-list__items article {
  display: grid;
  height: 100%;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.career-artifact-list__items header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}

.career-artifact-list__items h2 {
  margin: var(--space-1) 0 0;
  overflow-wrap: anywhere;
  font-size: 1.1rem;
}

.career-artifact-list__items dl {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.career-artifact-list__items dl div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}

.career-artifact-list__items dt {
  color: var(--color-muted);
}

.career-artifact-list__items dd {
  margin: 0;
  text-align: right;
}

.career-artifact-list__items article > footer {
  flex-wrap: wrap;
  margin-top: auto;
}

.pagination-controls {
  justify-content: center;
}

@media (max-width: 42rem) {
  .career-artifact-list__intro,
  .career-artifact-list__create {
    align-items: stretch;
    flex-direction: column;
  }

  .career-artifact-list__filters label {
    width: 100%;
  }

  .career-artifact-list__items article > footer .button {
    width: 100%;
  }
}
</style>
