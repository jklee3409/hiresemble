<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'
import { useAuthStore } from '@/stores/auth'

import { useCareerArtifactReadinessQuery } from './queries'

defineProps<{ compact?: boolean }>()

const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const readiness = useCareerArtifactReadinessQuery(
  userId,
  computed(() => featureFlags.careerArtifactEnabled),
)
const dismissed = ref(new Set<'RESUME' | 'PORTFOLIO'>())

const suggestions = computed(() => {
  const value = readiness.data.value
  if (value === undefined || readiness.isError.value || value.verifiedGitHubExperienceCount < 1) {
    return []
  }
  const items: Array<{ type: 'RESUME' | 'PORTFOLIO'; title: string; description: string }> = []
  if (!value.hasUploadedResume && !value.hasGeneratedResume && !dismissed.value.has('RESUME')) {
    items.push({
      type: 'RESUME',
      title: '확인한 GitHub 경험으로 이력서 초안을 만들어 보세요',
      description: '선택한 경험과 모델을 마지막 단계에서 확인한 뒤 Word 파일 생성을 요청합니다.',
    })
  }
  if (
    !value.hasUploadedPortfolio &&
    !value.hasGeneratedPortfolio &&
    !dismissed.value.has('PORTFOLIO')
  ) {
    items.push({
      type: 'PORTFOLIO',
      title: '확인한 GitHub 경험으로 포트폴리오 초안을 만들어 보세요',
      description: '검증된 근거를 구조화한 슬라이드를 미리 보고 PowerPoint 파일로 받을 수 있어요.',
    })
  }
  return items
})

function dismiss(type: 'RESUME' | 'PORTFOLIO'): void {
  dismissed.value = new Set([...dismissed.value, type])
}
</script>

<template>
  <section
    v-if="featureFlags.careerArtifactEnabled && suggestions.length > 0"
    class="artifact-suggestions"
    :class="{ 'artifact-suggestions--compact': compact }"
    aria-label="생성 자료 제안"
  >
    <article v-for="suggestion in suggestions" :key="suggestion.type" class="artifact-suggestion">
      <div>
        <p class="section-kicker">선택 기능</p>
        <h2>{{ suggestion.title }}</h2>
        <p>{{ suggestion.description }}</p>
      </div>
      <div class="artifact-suggestion__actions">
        <RouterLink
          class="button button--secondary"
          :to="{ name: 'career-artifact-new', query: { type: suggestion.type } }"
        >
          {{ suggestion.type === 'RESUME' ? '이력서 초안 만들기' : '포트폴리오 초안 만들기' }}
        </RouterLink>
        <button type="button" class="text-button" @click="dismiss(suggestion.type)">나중에</button>
      </div>
    </article>
  </section>
</template>

<style scoped>
.artifact-suggestions {
  display: grid;
  gap: var(--space-3);
}

.artifact-suggestion {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.artifact-suggestion h2 {
  margin: var(--space-1) 0 0;
  font-size: 1rem;
}

.artifact-suggestion p:not(.section-kicker) {
  margin: var(--space-2) 0 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
}

.artifact-suggestion__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-3);
}

.text-button {
  min-height: 2.75rem;
  border: 0;
  color: var(--color-muted);
  background: transparent;
  font-weight: 700;
}

.artifact-suggestions--compact .artifact-suggestion {
  padding: var(--space-4);
}

@media (max-width: 42rem) {
  .artifact-suggestion,
  .artifact-suggestion__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .artifact-suggestion__actions .button,
  .artifact-suggestion__actions .text-button {
    width: 100%;
  }
}
</style>
