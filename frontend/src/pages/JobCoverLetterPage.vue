<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CoverLetterConflictPanel from '@/features/cover-letters/CoverLetterConflictPanel.vue'
import type { CoverLetterConflict } from '@/features/cover-letters/conflict'
import {
  COVER_LETTER_STATUS_LABELS,
  VERIFICATION_STATUS_LABELS,
  formatCoverLetterInstant,
} from '@/features/cover-letters/presentation'
import {
  useCoverLetterListQuery,
  useCreateCoverLetterMutation,
} from '@/features/cover-letters/queries'
import { jobDisplayTitle } from '@/features/jobs/presentation'
import { useJobDetailQuery } from '@/features/jobs/queries'
import { normalizeApiError } from '@/shared/api/errors'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const coverLetters = useCoverLetterListQuery(
  userId,
  computed(() => ({
    jobId: jobId.value,
    page: 0,
    size: 100,
    sort: 'updatedAt,desc' as const,
  })),
)
const createMutation = useCreateCoverLetterMutation(userId)

const title = ref('')
const actionError = ref('')
const conflict = ref<CoverLetterConflict | null>(null)

const activeCoverLetter = computed(
  () => coverLetters.data.value?.items.find((item) => item.status !== 'ARCHIVED') ?? null,
)
const archivedCoverLetters = computed(
  () => coverLetters.data.value?.items.filter((item) => item.status === 'ARCHIVED') ?? [],
)
const defaultTitle = computed(() => {
  const value = job.data.value
  if (!value) return '자기소개서'
  return `${value.companyName ?? '지원 공고'} ${value.positionName ?? value.title ?? ''} 자기소개서`.trim()
})

async function createCoverLetter(reapply = false): Promise<void> {
  if (createMutation.isPending.value) return
  actionError.value = ''
  conflict.value = null
  try {
    const detail = await createMutation.mutateAsync({
      jobId: jobId.value,
      title: title.value.trim() || defaultTitle.value,
    })
    await router.push({
      name: 'cover-letter-edit',
      params: { coverLetterId: detail.id },
    })
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (apiError.status === 409) {
      await Promise.all([job.refetch(), coverLetters.refetch()])
      conflict.value = {
        kind: apiError.code === 'ACTIVE_COVER_LETTER_EXISTS' ? 'ACTIVE_EXISTS' : 'LIFECYCLE',
        errorCode: apiError.code,
        serverSnapshot: activeCoverLetter.value
          ? `${activeCoverLetter.value.title} · ${COVER_LETTER_STATUS_LABELS[activeCoverLetter.value.status]}`
          : '최신 상태를 다시 불러왔어요.',
        localDraft: title.value.trim() || defaultTitle.value,
      }
      if (reapply) actionError.value = '현재 상태에서는 새 자기소개서를 만들 수 없어요.'
      return
    }
    actionError.value = apiError.message
  }
}
</script>

<template>
  <section class="job-cover-letter app-page" aria-labelledby="job-cover-letter-heading">
    <PageHeader
      heading-id="job-cover-letter-heading"
      title="자기소개서"
      :description="
        job.data.value
          ? `${jobDisplayTitle(job.data.value)}의 자기소개서 진행 상황을 확인하세요.`
          : '공고별 자기소개서 진행 상황을 확인하세요.'
      "
      eyebrow="공고별 작성"
    />

    <StatePanel
      v-if="job.isLoading.value || coverLetters.isLoading.value"
      kind="loading"
      title="자기소개서 상태를 확인하는 중…"
      description="공고와 연결된 active 자기소개서를 찾고 있어요."
    />
    <StatePanel
      v-else-if="job.isError.value || coverLetters.isError.value"
      kind="error"
      :title="
        normalizeApiError(job.error.value ?? coverLetters.error.value).status === 404
          ? '공고를 찾을 수 없어요.'
          : '자기소개서 상태를 불러오지 못했어요.'
      "
      :description="normalizeApiError(job.error.value ?? coverLetters.error.value).message"
    >
      <template #actions>
        <RouterLink class="button button--secondary" :to="{ name: 'jobs' }">
          관심 공고로 돌아가기
        </RouterLink>
      </template>
    </StatePanel>

    <template v-else-if="job.data.value">
      <p v-if="actionError" class="job-cover-letter__error" role="alert">{{ actionError }}</p>
      <CoverLetterConflictPanel
        v-if="conflict"
        :conflict="conflict"
        :reapplying="createMutation.isPending.value"
        @reapply="createCoverLetter(true)"
        @cancel="conflict = null"
      />

      <article v-if="activeCoverLetter" class="job-cover-letter__active">
        <header>
          <div>
            <p class="page-eyebrow">현재 자기소개서</p>
            <h2>{{ activeCoverLetter.title }}</h2>
          </div>
          <StatusBadge
            :label="COVER_LETTER_STATUS_LABELS[activeCoverLetter.status]"
            :tone="activeCoverLetter.status === 'FINALIZED' ? 'success' : 'brand'"
          />
        </header>
        <div class="job-cover-letter__progress">
          <div>
            <span>답변 진행</span>
            <strong>
              {{ activeCoverLetter.answeredQuestionCount }}/{{ activeCoverLetter.questionCount }}
            </strong>
          </div>
          <div>
            <span>최신 검증</span>
            <strong>
              {{
                activeCoverLetter.latestVerificationStatus
                  ? VERIFICATION_STATUS_LABELS[activeCoverLetter.latestVerificationStatus]
                  : '검증 전'
              }}
            </strong>
          </div>
          <div>
            <span>최근 수정</span>
            <strong>{{ formatCoverLetterInstant(activeCoverLetter.updatedAt) }}</strong>
          </div>
        </div>
        <RouterLink
          class="button button--primary"
          :to="{
            name: 'cover-letter-edit',
            params: { coverLetterId: activeCoverLetter.id },
          }"
        >
          편집 화면으로 이동
        </RouterLink>
      </article>

      <article v-else class="job-cover-letter__empty">
        <div>
          <p class="page-eyebrow">아직 자기소개서가 없어요</p>
          <h2>공고 분석을 바탕으로 문항 작성을 시작하세요.</h2>
          <p>
            생성 후 canonical 편집 화면에서 문항, 경험 근거, 버전과 검증을 함께 관리할 수 있어요.
          </p>
        </div>
        <label class="field">
          <span class="field__label">자기소개서 제목</span>
          <input v-model="title" class="control" maxlength="300" :placeholder="defaultTitle" />
        </label>
        <div class="job-cover-letter__actions">
          <button
            type="button"
            class="button button--primary"
            :disabled="createMutation.isPending.value"
            data-testid="create-cover-letter"
            @click="createCoverLetter()"
          >
            {{ createMutation.isPending.value ? '생성 중…' : '자기소개서 생성' }}
          </button>
          <RouterLink
            class="button button--secondary"
            :to="{ name: 'job-analysis', params: { jobId } }"
          >
            공고 분석 결과 확인
          </RouterLink>
        </div>
      </article>

      <section v-if="archivedCoverLetters.length > 0" class="job-cover-letter__history">
        <div>
          <p class="page-eyebrow">보관 이력</p>
          <h2>과거 자기소개서</h2>
        </div>
        <ul>
          <li v-for="item in archivedCoverLetters" :key="item.id">
            <div>
              <strong>{{ item.title }}</strong>
              <span>
                읽기 전용 · {{ item.answeredQuestionCount }}/{{ item.questionCount }} 답변
              </span>
            </div>
            <RouterLink
              class="button button--secondary"
              :to="{ name: 'cover-letter-edit', params: { coverLetterId: item.id } }"
            >
              기록 열기
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.job-cover-letter {
  min-width: 0;
}

.job-cover-letter__error {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
  padding: var(--space-3) var(--space-4);
}

.job-cover-letter__active,
.job-cover-letter__empty,
.job-cover-letter__history {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: var(--space-6);
  box-shadow: var(--shadow-sm);
}

.job-cover-letter__active header,
.job-cover-letter__history li,
.job-cover-letter__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.job-cover-letter h2 {
  margin-top: var(--space-1);
}

.job-cover-letter__progress {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin: var(--space-5) 0;
}

.job-cover-letter__progress div {
  display: grid;
  gap: var(--space-1);
  border-radius: var(--radius-sm);
  background: var(--color-surface-subtle);
  padding: var(--space-4);
}

.job-cover-letter__progress span,
.job-cover-letter__history span,
.job-cover-letter__empty p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.job-cover-letter__empty {
  display: grid;
  gap: var(--space-5);
}

.job-cover-letter__empty p {
  margin-top: var(--space-3);
}

.job-cover-letter__actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.job-cover-letter__history {
  margin-top: var(--space-5);
}

.job-cover-letter__history ul {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.job-cover-letter__history li {
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
}

.job-cover-letter__history li > div {
  display: grid;
  gap: var(--space-1);
}

@media (max-width: 48rem) {
  .job-cover-letter__progress {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 40rem) {
  .job-cover-letter__active header,
  .job-cover-letter__history li {
    align-items: stretch;
    flex-direction: column;
  }

  .job-cover-letter__actions .button,
  .job-cover-letter__history .button {
    width: 100%;
  }
}
</style>
