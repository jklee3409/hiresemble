<script setup lang="ts">
type AppIconName =
  | 'dashboard'
  | 'profile'
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
