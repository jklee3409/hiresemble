# SuperDesign Components Context

Hiresemble은 Vue 3 SFC, TypeScript, PrimeVue 일부 컴포넌트와 프로젝트 고유 CSS를 사용합니다. 아래에는 페이지 간 공유되는 UI primitive의 실제 전체 소스가 포함됩니다.

## AppIcon

- Path: `frontend/src/shared/ui/AppIcon.vue`
- Description: 프로젝트의 선형 SVG 아이콘 레지스트리이자 공통 아이콘 렌더러입니다.

```vue
<script setup lang="ts">
type AppIconName =
  | 'dashboard'
  | 'profile'
  | 'person-card'
  | 'documents'
  | 'jobs'
  | 'cover-letter'
  | 'interview'
  | 'runs'
  | 'menu'
  | 'close'
  | 'logout'
  | 'arrow-left'
  | 'arrow-right'
  | 'upload'
  | 'plus'
  | 'check'
  | 'clock'
  | 'calendar'
  | 'guide'
  | 'sparkle'
  | 'alert'
  | 'inbox'
  | 'filter'

defineProps<{
  name: AppIconName
}>()
</script>

<template>
  <svg
    class="icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'dashboard'">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </template>
    <template v-else-if="name === 'profile'">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c.8-4 3.2-6 7-6s6.2 2 7 6" />
    </template>
    <template v-else-if="name === 'person-card'">
      <circle cx="12" cy="8.25" r="3.35" />
      <path d="M5.25 20c.55-4.1 3.05-6.15 6.75-6.15S18.2 15.9 18.75 20" />
      <path d="M8.2 15.1c.95 1.2 2.22 1.8 3.8 1.8s2.85-.6 3.8-1.8" />
    </template>
    <template v-else-if="name === 'documents'">
      <path d="M6 3h8l4 4v14H6z" />
      <path d="M14 3v5h5M9 13h6M9 17h6" />
    </template>
    <template v-else-if="name === 'jobs'">
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M9 7V4h6v3M3 12h18M10 12v2h4v-2" />
    </template>
    <template v-else-if="name === 'cover-letter'">
      <path d="M6 3h9l3 3v15H6z" />
      <path d="M14 3v5h5M9 12h6M9 16h6" />
    </template>
    <template v-else-if="name === 'interview'">
      <path d="M4 5h16v11H9l-5 4z" />
      <path d="M8 9h8M8 12h5" />
    </template>
    <template v-else-if="name === 'runs'">
      <path d="M4 6h10M4 12h16M4 18h10" />
      <circle cx="18" cy="6" r="2" />
      <circle cx="16" cy="18" r="2" />
    </template>
    <template v-else-if="name === 'menu'">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </template>
    <template v-else-if="name === 'close'">
      <path d="m6 6 12 12M18 6 6 18" />
    </template>
    <template v-else-if="name === 'logout'">
      <path d="M10 4H5v16h5M14 8l4 4-4 4M8 12h10" />
    </template>
    <template v-else-if="name === 'arrow-left'">
      <path d="m14 6-6 6 6 6M8 12h11" />
    </template>
    <template v-else-if="name === 'arrow-right'">
      <path d="m10 6 6 6-6 6M5 12h11" />
    </template>
    <template v-else-if="name === 'upload'">
      <path d="M12 16V4M7 9l5-5 5 5M4 15v5h16v-5" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>
    <template v-else-if="name === 'check'">
      <path d="m5 12 4 4L19 6" />
    </template>
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </template>
    <template v-else-if="name === 'calendar'">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4M17 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" />
    </template>
    <template v-else-if="name === 'guide'">
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v16H6.5A2.5 2.5 0 0 0 4 21.5z" />
      <path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H13v16h4.5a2.5 2.5 0 0 1 2.5 2.5z" />
    </template>
    <template v-else-if="name === 'sparkle'">
      <path
        d="m12 3 1.3 4.2L17 9l-3.7 1.8L12 15l-1.3-4.2L7 9l3.7-1.8zM18 15l.7 2.3L21 18l-2.3.7L18 21l-.7-2.3L15 18l2.3-.7z"
      />
    </template>
    <template v-else-if="name === 'alert'">
      <path d="M12 3 2.8 20h18.4zM12 9v4M12 17h.01" />
    </template>
    <template v-else-if="name === 'inbox'">
      <path d="M4 5h16v14H4zM4 14h5l1.5 2h3L15 14h5" />
    </template>
    <template v-else>
      <path d="M4 6h16M7 12h10M10 18h4" />
    </template>
  </svg>
</template>

```

## BrandMark

- Path: `frontend/src/shared/ui/BrandMark.vue`
- Description: Hiresemble 로고와 브랜드명을 표시하는 공통 브랜드 마크입니다.

```vue
<script setup lang="ts">
import hiresembleLogoUrl from './hiresemble-logo.png'

withDefaults(
  defineProps<{
    compact?: boolean
    inverse?: boolean
    showName?: boolean
  }>(),
  {
    compact: false,
    inverse: false,
    showName: true,
  },
)
</script>

<template>
  <span
    class="brand-lockup"
    :class="{
      'brand-lockup--compact': compact,
      'brand-lockup--inverse': inverse,
    }"
    data-testid="brand-mark"
    aria-hidden="true"
  >
    <img class="brand-symbol" :src="hiresembleLogoUrl" alt="" draggable="false" />
    <span v-if="showName" class="brand-lockup__name">Hiresemble</span>
  </span>
</template>

<style scoped>
.brand-lockup {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 0.7rem;
  color: var(--color-ink);
}

.brand-symbol {
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  object-fit: contain;
  user-select: none;
}

.brand-lockup__name {
  overflow: hidden;
  font-size: 1.125rem;
  font-weight: 820;
  letter-spacing: -0.045em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-lockup--compact {
  gap: 0.5rem;
}

.brand-lockup--compact .brand-symbol {
  width: 2rem;
  height: 2rem;
}

.brand-lockup--compact .brand-lockup__name {
  font-size: 1rem;
}

.brand-lockup--inverse {
  color: #ffffff;
}
</style>

```

## PageHeader

- Path: `frontend/src/shared/ui/PageHeader.vue`
- Description: 페이지 제목·설명·액션 슬롯을 정렬하는 공통 헤더입니다.

```vue
<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    eyebrow?: string
    headingId?: string
    level?: 1 | 2
    variant?: 'default' | 'list' | 'detail' | 'editor' | 'compact'
  }>(),
  {
    description: '',
    eyebrow: '',
    headingId: undefined,
    level: 1,
    variant: 'default',
  },
)
</script>

<template>
  <header class="page-header" :class="`page-header--${variant}`">
    <div class="page-header__body">
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <component :is="level === 1 ? 'h1' : 'h2'" :id="headingId" class="page-title">
        <slot name="title">{{ title }}</slot>
      </component>
      <p v-if="description" class="page-description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

```

## StatusBadge

- Path: `frontend/src/shared/ui/StatusBadge.vue`
- Description: 중립·브랜드·상태 의미를 전달하는 공통 상태 배지입니다.

```vue
<script setup lang="ts">
type StatusTone = 'neutral' | 'brand' | 'info' | 'success' | 'warning' | 'danger'

withDefaults(
  defineProps<{
    label: string
    prefix?: string
    tone?: StatusTone
  }>(),
  {
    prefix: '',
    tone: 'neutral',
  },
)
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span v-if="prefix">{{ prefix }} · </span>{{ label }}
  </span>
</template>

```

## StatePanel

- Path: `frontend/src/shared/ui/StatePanel.vue`
- Description: 로딩·빈 상태·오류와 후속 행동을 안내하는 공통 상태 패널입니다.

```vue
<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(
  defineProps<{
    kind?: 'loading' | 'empty' | 'error' | 'info'
    title: string
    description?: string
  }>(),
  {
    kind: 'info',
    description: '',
  },
)
</script>

<template>
  <section
    class="state-panel"
    :class="{
      'state-panel--error': kind === 'error',
      'state-panel--loading': kind === 'loading',
    }"
    :role="kind === 'error' ? 'alert' : kind === 'loading' ? 'status' : undefined"
    :aria-live="kind === 'loading' ? 'polite' : undefined"
  >
    <AppIcon v-if="kind === 'error'" name="alert" />
    <AppIcon v-else-if="kind === 'empty'" name="inbox" />
    <AppIcon v-else-if="kind !== 'loading'" name="clock" />
    <div v-if="kind === 'loading'" class="skeleton-stack" aria-hidden="true">
      <div class="skeleton-line" />
      <div class="skeleton-line" />
      <div class="skeleton-line" />
    </div>
    <h3 class="state-panel__title">{{ title }}</h3>
    <p v-if="description" class="state-panel__description">{{ description }}</p>
    <div v-if="$slots.actions" class="state-panel__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

```

## PaginationNav

- Path: `frontend/src/shared/ui/PaginationNav.vue`
- Description: 목록 페이지 이동을 제공하는 공통 페이지네이션입니다.

```vue
<script setup lang="ts">
defineProps<{
  page: number
  totalPages: number
  label: string
}>()

defineEmits<{
  change: [page: number]
}>()
</script>

<template>
  <nav class="pagination" :aria-label="label">
    <button
      type="button"
      class="button button--secondary button--compact"
      :disabled="page === 0"
      @click="$emit('change', page - 1)"
    >
      이전
    </button>
    <span class="pagination__summary">{{ page + 1 }} / {{ totalPages }} 페이지</span>
    <button
      type="button"
      class="button button--secondary button--compact"
      :disabled="page + 1 >= totalPages"
      @click="$emit('change', page + 1)"
    >
      다음
    </button>
  </nav>
</template>

```

## AppNotifications

- Path: `frontend/src/shared/ui/AppNotifications.vue`
- Description: 전역 알림 피드백을 표시하는 공통 알림 영역입니다.

```vue
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
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
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

```


