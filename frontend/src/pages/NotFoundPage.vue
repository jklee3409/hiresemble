<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main class="grid min-h-screen place-items-center bg-slate-50 px-4 text-center">
    <section aria-labelledby="not-found-heading">
      <p class="text-sm font-semibold text-indigo-700">404</p>
      <h1 id="not-found-heading" class="mt-2 text-3xl font-bold">페이지를 찾을 수 없습니다</h1>
      <p class="mt-3 text-slate-700">주소를 확인하거나 시작 화면으로 이동해 주세요.</p>
      <RouterLink
        class="mt-6 inline-block rounded-lg bg-indigo-700 px-4 py-2 font-semibold text-white"
        to="/"
      >
        시작 화면으로
      </RouterLink>
    </section>
  </main>
</template>
