<script setup lang="ts">
import { RouterLink } from 'vue-router'

const tabs = [
  ['/profile/basic', '기본 프로필'],
  ['/profile/education', '학력'],
  ['/profile/certifications', '자격증'],
  ['/profile/languages', '어학'],
  ['/profile/awards', '수상'],
  ['/profile/careers', '경력'],
  ['/profile/evidence', '직접 입력 근거'],
] as const
</script>

<template>
  <nav class="mb-6 flex flex-wrap gap-2" aria-label="프로필 메뉴">
    <RouterLink
      v-for="[to, label] in tabs"
      :key="to"
      :to="to"
      class="rounded-full border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 aria-[current=page]:border-indigo-700 aria-[current=page]:bg-indigo-50 aria-[current=page]:text-indigo-800"
    >
      {{ label }}
    </RouterLink>
  </nav>
</template>
