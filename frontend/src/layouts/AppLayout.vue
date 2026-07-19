<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { authErrorMessage, normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const isLoggingOut = ref(false)
const logoutError = ref('')
const pageTitle = computed(() => route.meta.title ?? 'Hiresemble')
const AgentRunProgressDrawer = defineAsyncComponent(
  () => import('@/features/agent-runs/AgentRunProgressDrawer.vue'),
)

async function logout(): Promise<void> {
  isLoggingOut.value = true
  logoutError.value = ''

  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = authErrorMessage(normalizeApiError(error))
  } finally {
    isLoggingOut.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-950">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-4">
        <div>
          <RouterLink class="font-bold text-indigo-700" to="/dashboard">Hiresemble</RouterLink>
          <h1 class="mt-1 text-xl font-semibold">{{ pageTitle }}</h1>
        </div>
        <div class="flex items-center gap-3">
          <AgentRunProgressDrawer />
          <span class="text-sm text-slate-700">{{ authStore.currentUser?.displayName }}</span>
          <button
            type="button"
            class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="isLoggingOut"
            @click="logout"
          >
            {{ isLoggingOut ? '로그아웃 중…' : '로그아웃' }}
          </button>
        </div>
      </div>
      <nav class="mx-auto flex max-w-6xl flex-wrap gap-4 px-4 pb-3" aria-label="주요 메뉴">
        <RouterLink class="text-sm font-medium text-indigo-700" to="/dashboard">
          대시보드
        </RouterLink>
        <RouterLink class="text-sm font-medium text-indigo-700" to="/onboarding">
          온보딩
        </RouterLink>
        <RouterLink class="text-sm font-medium text-indigo-700" to="/profile/basic">
          내 프로필
        </RouterLink>
        <RouterLink class="text-sm font-medium text-indigo-700" to="/agent-runs">
          작업 기록
        </RouterLink>
      </nav>
    </header>

    <p v-if="logoutError" class="mx-auto mt-4 max-w-6xl px-4 text-sm text-red-700" role="alert">
      {{ logoutError }}
    </p>

    <main class="mx-auto max-w-6xl px-4 py-8">
      <RouterView />
    </main>
  </div>
</template>
