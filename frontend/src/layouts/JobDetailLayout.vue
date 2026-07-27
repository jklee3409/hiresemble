<template>
  <section>
    <RouterLink class="text-sm font-semibold text-indigo-700" :to="{ name: 'jobs' }">
      ← 공고 목록
    </RouterLink>
    <nav class="mt-4 border-b border-slate-200" aria-label="공고 상세 탭">
      <RouterLink
        class="inline-block border-b-2 border-indigo-700 px-3 py-2 text-sm font-semibold text-indigo-700"
        :to="{ name: 'job-overview', params: { jobId: $route.params.jobId } }"
      >
        개요
      </RouterLink>
    </nav>
    <RouterView />
  </section>
</template>
