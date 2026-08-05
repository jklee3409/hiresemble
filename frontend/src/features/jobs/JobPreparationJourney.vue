<script setup lang="ts">
import { computed } from 'vue'

import { jobAnalysisFailureCopy } from '@/features/jobs/analysisPresentation'
import type { JobDetailDto } from '@/shared/api/jobContracts'

const props = defineProps<{ job: JobDetailDto }>()

type StepState = 'done' | 'active' | 'pending' | 'attention'
interface JourneyStep {
  label: string
  state: StepState
}

const steps = computed<JourneyStep[]>(() => {
  const hasContent = Boolean(props.job.descriptionText?.trim())
  const analysisDone = props.job.latestAnalysis !== null
  const automatic = props.job.automaticAnalysis.state
  const blocked = automatic === 'BLOCKED' || automatic === 'WAITING_FOR_CONTENT'
  return [
    {
      label: hasContent ? '공고 내용을 읽었어요' : '공고 내용을 읽고 있어요',
      state: hasContent ? 'done' : blocked ? 'attention' : 'active',
    },
    {
      label: analysisDone
        ? '주요 업무와 지원 조건을 정리했어요'
        : '주요 업무와 지원 조건을 정리하고 있어요',
      state: analysisDone
        ? 'done'
        : hasContent && !blocked
          ? 'active'
          : blocked
            ? 'attention'
            : 'pending',
    },
    {
      label: analysisDone ? '내 경험과 비교했어요' : '내 경험과 비교할 차례예요',
      state: analysisDone ? 'done' : 'pending',
    },
    {
      label: analysisDone ? '분석이 끝났어요' : '분석 결과를 준비하고 있어요',
      state: analysisDone ? 'done' : 'pending',
    },
  ]
})
const automaticFailure = computed(() => {
  const error = props.job.automaticAnalysis.error
  return error === null ? null : jobAnalysisFailureCopy(error.code, error.message)
})
</script>

<template>
  <section class="job-journey" aria-labelledby="job-journey-title" aria-live="polite">
    <div class="job-journey__heading">
      <h2 id="job-journey-title">공고 분석 진행 상황</h2>
      <span>공고 저장 후 자동으로 진행돼요</span>
    </div>
    <ol class="job-journey__steps">
      <li v-for="step in steps" :key="step.label" :data-state="step.state">
        <span class="job-journey__marker" aria-hidden="true">{{
          step.state === 'done' ? '✓' : ''
        }}</span>
        <span>{{ step.label }}</span>
      </li>
    </ol>
    <p v-if="automaticFailure" class="job-journey__error" role="status">
      <strong>{{ automaticFailure.title }}</strong>
      <span>{{ automaticFailure.description }}</span>
    </p>
  </section>
</template>

<style scoped>
.job-journey {
  border-radius: var(--radius-panel);
  background: var(--color-surface);
  padding: var(--space-6) clamp(var(--space-5), 3vw, var(--space-7));
  box-shadow: var(--shadow-panel);
}

.job-journey__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.job-journey__heading h2 {
  font-size: 1rem;
  font-weight: 760;
}

.job-journey__heading span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.job-journey__steps {
  display: grid;
  grid-template-columns: repeat(4, max-content);
  justify-content: space-between;
  column-gap: var(--space-4);
  row-gap: var(--space-3);
  margin-top: var(--space-4);
}

.job-journey__steps li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.45;
}

.job-journey__steps li > span:last-child {
  white-space: nowrap;
}

.job-journey__marker {
  display: grid;
  width: 0.75rem;
  height: 0.75rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 50%;
  background: var(--color-surface-subtle);
  font-size: 0.625rem;
  font-weight: 800;
}

.job-journey__steps li[data-state='active'] {
  color: var(--color-brand-strong);
  font-weight: 700;
}

.job-journey__steps li[data-state='active'] .job-journey__marker,
.job-journey__steps li[data-state='done'] .job-journey__marker {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: white;
}

/*
 * 진행 중인 단계 하나만 반복 모션을 갖는다.
 * 정보 접근을 지연시키지 않고 "지금 이 단계"만 전달한다.
 */
.job-journey__steps li[data-state='active'] .job-journey__marker {
  position: relative;
  background: var(--color-brand-soft);
  box-shadow: 0 0 0 3px var(--color-brand-soft);
}

.job-journey__steps li[data-state='active'] .job-journey__marker::after {
  content: '';
  position: absolute;
  inset: -1px;
  border: 2px solid var(--color-brand);
  border-radius: 50%;
  animation: job-journey-pulse 1.6s ease-out infinite;
}

@keyframes job-journey-pulse {
  from {
    transform: scale(1);
    opacity: 0.9;
  }

  to {
    transform: scale(1.8);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .job-journey__steps li[data-state='active'] .job-journey__marker::after {
    animation: none;
  }
}

.job-journey__steps li[data-state='done'] .job-journey__marker {
  width: 1rem;
  height: 1rem;
}

.job-journey__steps li[data-state='done'] {
  color: var(--color-text-secondary);
}

.job-journey__steps li[data-state='attention'] {
  color: var(--color-warning-strong);
}

.job-journey__error {
  display: grid;
  gap: var(--space-1);
  margin-top: var(--space-4);
  color: var(--color-warning-strong);
  font-size: var(--font-size-sm);
}

.job-journey__error span {
  line-height: 1.55;
}

@media (max-width: 70rem) {
  .job-journey__steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 48rem) {
  .job-journey__steps {
    grid-template-columns: 1fr;
  }

  .job-journey__heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
