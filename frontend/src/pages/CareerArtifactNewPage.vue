<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import CareerArtifactGenerationForm from '@/features/career-artifacts/CareerArtifactGenerationForm.vue'
import {
  canonicalCareerArtifactNewQuery,
  careerArtifactQuerySignature,
  parseCareerArtifactNewQuery,
} from '@/features/career-artifacts/filters'
import type { CareerArtifactType } from '@/shared/api/careerArtifactContracts'
import type { RunAcceptedDto } from '@/shared/api/agentRunContracts'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const parsed = computed(() => parseCareerArtifactNewQuery(route.query))

watch(
  () => route.query,
  (query) => {
    const value = parseCareerArtifactNewQuery(query)
    const canonical = canonicalCareerArtifactNewQuery(value.type, value.step)
    if (careerArtifactQuerySignature(query) !== careerArtifactQuerySignature(canonical)) {
      void router.replace({ name: 'career-artifact-new', query: canonical })
    }
  },
  { immediate: true },
)

function updateStep(step: number): void {
  void router.replace({
    name: 'career-artifact-new',
    query: canonicalCareerArtifactNewQuery(parsed.value.type, step),
  })
}

function updateType(type: CareerArtifactType | null): void {
  void router.replace({
    name: 'career-artifact-new',
    query: canonicalCareerArtifactNewQuery(type, 1),
  })
}

function onSubmitted(accepted: RunAcceptedDto): void {
  if (accepted.resourceType !== 'CAREER_ARTIFACT' || accepted.resourceId === null) return
  void router.replace({
    name: 'career-artifact-detail',
    params: { careerArtifactId: accepted.resourceId },
  })
}

function cancel(): void {
  void router.replace({ name: 'career-artifacts' })
}
</script>

<template>
  <main class="career-artifact-new page-stack">
    <h1 class="sr-only">새 이력서·포트폴리오 초안 만들기</h1>
    <RouterLink class="text-link" to="/career-artifacts">← AI로 만든 초안 목록</RouterLink>
    <CareerArtifactGenerationForm
      v-if="authStore.currentUser"
      :user-id="authStore.currentUser.id"
      :display-name="authStore.currentUser.displayName"
      :email="authStore.currentUser.email"
      :artifact-type="parsed.type"
      :initial-step="parsed.step"
      @submitted="onSubmitted"
      @cancelled="cancel"
      @step-change="updateStep"
      @type-change="updateType"
    />
  </main>
</template>

<style scoped>
.career-artifact-new {
  width: min(100%, 66rem);
  min-width: 0;
  margin-inline: auto;
}
</style>
