<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { RouterLink } from 'vue-router'

import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 상세 화면에서 목록이나 이전 화면으로 돌아가는 유일한 표현.
 *
 * 화면마다 링크·연한 글자·알약을 섞어 쓰면 같은 행동이 매번 다르게 보인다.
 * 여기서 전역 `.back-link` 알약과 왼쪽 화살표를 고정하고, 위치와 간격만 쓰는 쪽에서 정한다.
 */
defineProps<{ to: RouteLocationRaw }>()
</script>

<template>
  <RouterLink class="back-link" :to="to">
    <AppIcon name="arrow-left" />
    <span><slot /></span>
  </RouterLink>
</template>

<style scoped>
/* grid·flex container 안에서 폭을 늘리지 않고 왼쪽에 붙는다. */
.back-link {
  justify-self: start;
  align-self: start;
}
</style>
