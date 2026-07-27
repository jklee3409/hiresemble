<template>
  <main class="root-loading" aria-live="polite">
    <div class="root-loading__brand" aria-hidden="true">H</div>
    <div>
      <strong>Hiresemble</strong>
      <p>인증 상태를 확인하고 있습니다…</p>
    </div>
  </main>
</template>

<style scoped>
.root-loading {
  display: flex;
  min-height: 100dvh;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-5);
  background: var(--color-canvas);
  color: var(--color-text);
}

.root-loading__brand {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-xs);
  background: var(--color-brand-strong);
  color: white;
  font-weight: 800;
}

.root-loading strong {
  font-size: var(--font-size-sm);
}

.root-loading p {
  margin-top: 0.15rem;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}
</style>
