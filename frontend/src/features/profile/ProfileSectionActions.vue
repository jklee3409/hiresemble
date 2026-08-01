<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const sections = [
  { to: '/profile/basic', label: '기본 정보' },
  { to: '/profile/education', label: '학력' },
  { to: '/profile/careers', label: '경력' },
  { to: '/profile/certifications', label: '자격증' },
  { to: '/profile/languages', label: '어학' },
  { to: '/profile/awards', label: '수상' },
  { to: '/profile/activities', label: '대외활동' },
] as const

const currentIndex = computed(() => sections.findIndex((section) => section.to === route.path))
const previous = computed(() =>
  currentIndex.value > 0 ? sections[currentIndex.value - 1] : undefined,
)
const next = computed(() =>
  currentIndex.value >= 0 && currentIndex.value < sections.length - 1
    ? sections[currentIndex.value + 1]
    : undefined,
)
</script>

<template>
  <nav class="profile-section-actions" aria-label="프로필 항목 이동">
    <RouterLink
      v-if="previous"
      :to="previous.to"
      class="button button--secondary"
      :aria-label="`이전 항목: ${previous.label}`"
    >
      ← {{ previous.label }}
    </RouterLink>
    <span v-else />
    <RouterLink
      v-if="next"
      :to="next.to"
      class="button button--secondary"
      :aria-label="`다음 항목: ${next.label}`"
    >
      {{ next.label }} →
    </RouterLink>
    <RouterLink v-else to="/dashboard" class="button button--secondary">
      지원 홈으로 돌아가기
    </RouterLink>
  </nav>
</template>
