<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 작성 흐름을 방해하지 않는 보조 영역.
 * 문항 목록(모바일), 작성 도움(모바일), 버전 기록, AI 설정, 작성 완료 점검이 같은 껍데기를 쓴다.
 * 열려 있는 동안 focus를 가두고 Escape로 닫으며 닫을 때 원래 trigger로 focus를 되돌린다.
 */

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    placement?: 'side' | 'center'
    width?: string
  }>(),
  { description: '', placement: 'side', width: '' },
)
const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)
let returnFocus: HTMLElement | null = null

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      restoreFocus()
      return
    }
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    const focusable = panel.value?.querySelector<HTMLElement>(focusableSelector())
    focusable?.focus()
  },
)

onBeforeUnmount(() => restoreFocus())

function restoreFocus(): void {
  returnFocus?.focus()
  returnFocus = null
}

function focusableSelector(): string {
  return 'button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"]'
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || panel.value === null) return
  const focusable = Array.from(panel.value.querySelectorAll<HTMLElement>(focusableSelector()))
  const first = focusable[0]
  const last = focusable.at(-1)
  if (focusable.length === 0) return
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="cover-sheet" :class="`cover-sheet--${placement}`">
      <div class="cover-sheet__scrim" @mousedown.self="emit('close')" />
      <section
        ref="panel"
        class="cover-sheet__panel"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        :style="width ? { '--cover-sheet-width': width } : undefined"
        @keydown="onKeydown"
      >
        <header class="cover-sheet__header">
          <div>
            <h2>{{ title }}</h2>
            <p v-if="description">{{ description }}</p>
          </div>
          <button
            type="button"
            class="button button--ghost button--icon"
            :aria-label="`${title} 닫기`"
            @click="emit('close')"
          >
            <AppIcon name="close" />
          </button>
        </header>
        <div class="cover-sheet__body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="cover-sheet__footer">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
/* toast(1200)와 확인 dialog(1300)보다 아래에 둔다. */
.cover-sheet {
  position: fixed;
  z-index: 1150;
  inset: 0;
  display: flex;
}

.cover-sheet__scrim {
  position: absolute;
  inset: 0;
  background: rgb(15 23 42 / 42%);
}

.cover-sheet__panel {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(var(--cover-sheet-width, 32rem), 100%);
  max-height: 100dvh;
  background: var(--color-surface);
  animation: cover-sheet-enter var(--motion-base) both;
}

.cover-sheet--side {
  justify-content: flex-end;
}

.cover-sheet--center {
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}

.cover-sheet--center .cover-sheet__panel {
  max-height: min(100%, 46rem);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-md);
}

.cover-sheet__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  border-bottom: 1px solid var(--color-border);
  padding: var(--space-5);
}

.cover-sheet__header h2 {
  color: var(--color-ink-title);
  font-size: var(--font-size-lg);
  font-weight: 780;
  letter-spacing: -0.01em;
}

.cover-sheet__header p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.cover-sheet__body {
  overflow: auto;
  padding: var(--space-5);
}

.cover-sheet__footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-subtle);
  padding: var(--space-4) var(--space-5);
}

@media (max-width: 40rem) {
  .cover-sheet--side {
    align-items: flex-end;
  }

  .cover-sheet--side .cover-sheet__panel {
    width: 100%;
    max-height: 88dvh;
    border-radius: var(--radius-panel) var(--radius-panel) 0 0;
  }

  .cover-sheet__footer .button {
    flex: 1 1 10rem;
  }
}

@keyframes cover-sheet-enter {
  from {
    opacity: 0;
    transform: translateY(0.75rem);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cover-sheet__panel {
    animation: none;
  }
}
</style>
