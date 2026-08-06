<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

import { useNotifications } from './notifications'

const { state, dismissToast, resolveConfirmation } = useNotifications()
const dialog = ref<HTMLElement | null>(null)
const cancelButton = ref<HTMLButtonElement | null>(null)
let returnFocus: HTMLElement | null = null

watch(
  () => state.confirmation,
  async (request) => {
    if (request === null) {
      document.body.style.removeProperty('overflow')
      returnFocus?.focus()
      returnFocus = null
      return
    }
    returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    document.body.style.overflow = 'hidden'
    await nextTick()
    cancelButton.value?.focus()
  },
)

onBeforeUnmount(() => document.body.style.removeProperty('overflow'))

function onDialogKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    resolveConfirmation(false)
    return
  }
  if (event.key !== 'Tab' || dialog.value === null) return
  const controls = Array.from(
    dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), [href], [tabindex="0"]'),
  )
  if (controls.length === 0) return
  const first = controls[0]
  const last = controls[controls.length - 1]
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
    <div class="toast-region" aria-live="polite" aria-label="알림">
      <article
        v-for="item in state.toasts"
        :key="item.id"
        class="app-toast"
        :class="`app-toast--${item.tone}`"
        :role="item.tone === 'error' ? 'alert' : 'status'"
      >
        <span class="app-toast__marker" aria-hidden="true" />
        <p>{{ item.message }}</p>
        <button type="button" aria-label="알림 닫기" @click="dismissToast(item.id)">×</button>
      </article>
    </div>

    <div
      v-if="state.confirmation"
      class="confirm-backdrop"
      @mousedown.self="resolveConfirmation(false)"
    >
      <section
        ref="dialog"
        class="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        @keydown="onDialogKeydown"
      >
        <p class="section-kicker">확인해 주세요</p>
        <h2 id="confirm-title">{{ state.confirmation.title }}</h2>
        <p id="confirm-message">{{ state.confirmation.message }}</p>
        <div class="confirm-dialog__actions">
          <button
            ref="cancelButton"
            type="button"
            class="button button--secondary"
            @click="resolveConfirmation(false)"
          >
            {{ state.confirmation.cancelLabel ?? '취소' }}
          </button>
          <button
            type="button"
            class="button"
            :class="state.confirmation.tone === 'primary' ? 'button--primary' : 'button--danger'"
            @click="resolveConfirmation(true)"
          >
            {{ state.confirmation.confirmLabel ?? '확인' }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-region {
  position: fixed;
  z-index: 1200;
  top: 1rem;
  right: 1rem;
  display: grid;
  width: min(24rem, calc(100vw - 2rem));
  gap: 0.625rem;
  pointer-events: none;
}

.app-toast {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 0.75rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-surface-raised);
  padding: 0.875rem 1rem;
  box-shadow: var(--shadow-md);
  pointer-events: auto;
}

.app-toast__marker {
  width: 0.625rem;
  height: 0.625rem;
  margin-top: 0.4rem;
  border-radius: 999px;
  background: var(--color-info);
}

.app-toast--success .app-toast__marker {
  background: var(--color-success);
}
.app-toast--error .app-toast__marker {
  background: var(--color-danger);
}
.app-toast p {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 0.875rem;
}
.app-toast button {
  border: 0;
  background: transparent;
  color: var(--color-muted);
  font-size: 1.25rem;
  line-height: 1;
}

.confirm-backdrop {
  position: fixed;
  z-index: 1300;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgb(15 23 42 / 55%);
  padding: 1rem;
}

.confirm-dialog {
  width: min(29rem, 100%);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  padding: clamp(1.25rem, 4vw, 1.75rem);
  box-shadow: var(--shadow-md);
}

.confirm-dialog h2 {
  margin: 0;
  font-size: 1.25rem;
}
.confirm-dialog > p:not(.section-kicker) {
  margin: 0.75rem 0 0;
  color: var(--color-muted);
}
.confirm-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

@media (max-width: 479px) {
  .confirm-dialog__actions {
    flex-direction: column-reverse;
  }
  .confirm-dialog__actions .button {
    width: 100%;
  }
}
</style>
