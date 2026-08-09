<script setup lang="ts">
import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 본문 흐름 안에서 한 가지 상황을 알리는 알림 줄.
 *
 * `.alert`는 면 전체를 상태색으로 채우기 때문에 문장 하나를 알릴 때 쓰면 화면에서 과하게 튄다.
 * 여기서는 표면을 흰 카드로 두고 색은 왼쪽 아이콘에만 남겨, 심각도는 알리되 시선은 뺏지 않는다.
 */

withDefaults(
  defineProps<{
    title: string
    description?: string
    tone?: 'notice' | 'warning' | 'danger' | 'info'
    role?: 'status' | 'alert'
  }>(),
  { description: '', tone: 'notice', role: 'status' },
)

const ICONS = {
  notice: 'info',
  info: 'info',
  warning: 'alert',
  danger: 'alert',
} as const
</script>

<template>
  <div class="inline-notice" :class="`inline-notice--${tone}`" :role="role">
    <span class="inline-notice__icon" aria-hidden="true">
      <AppIcon :name="ICONS[tone]" />
    </span>
    <div class="inline-notice__body">
      <strong class="inline-notice__title">{{ title }}</strong>
      <p v-if="description" class="inline-notice__description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="inline-notice__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.inline-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: inset 0 0 0 1px var(--color-border);
  padding: 0.875rem 1rem;
}

.inline-notice__icon {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--color-notice-soft);
  color: var(--color-notice-strong);
}

.inline-notice__icon :deep(.icon) {
  width: 1rem;
  height: 1rem;
}

.inline-notice--warning .inline-notice__icon {
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
}

.inline-notice--danger .inline-notice__icon {
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
}

.inline-notice--info .inline-notice__icon {
  background: var(--color-info-soft);
  color: var(--color-info-strong);
}

.inline-notice__body {
  display: grid;
  min-width: 0;
  gap: 0.1875rem;
}

.inline-notice__title {
  color: var(--color-ink-title);
  font-size: var(--font-size-sm);
  font-weight: 720;
  letter-spacing: -0.01em;
}

.inline-notice__description {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.inline-notice__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}

@media (max-width: 35rem) {
  .inline-notice {
    flex-wrap: wrap;
  }

  .inline-notice__actions {
    width: 100%;
    margin-left: 2.5rem;
  }
}
</style>
