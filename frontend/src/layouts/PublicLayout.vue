<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <main class="min-h-screen bg-slate-50 px-4 py-10 text-slate-950">
    <div class="mx-auto w-full max-w-md">
      <RouterLink class="block text-center text-2xl font-bold text-indigo-700" to="/">
        Hiresemble
      </RouterLink>
      <section class="mt-6 rounded-2xl bg-white p-6 shadow-sm" aria-label="인증">
        <RouterView />
      </section>
      <p class="mt-4 text-center text-sm leading-6 text-slate-600">
        가입과 로그인 과정에서 개인정보 처리 및 AI 처리 동의를 확인합니다.
      </p>
    </div>
  </main>
</template>
