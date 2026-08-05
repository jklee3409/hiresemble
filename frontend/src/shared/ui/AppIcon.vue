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
  | 'target'
  | 'half'
  | 'cross'
  | 'question'
  | 'shield'
  | 'spark'
  | 'lift'
  | 'evidence'
  | 'chart'
  | 'history'
  | 'refresh'
  | 'scale'
  | 'flow'
  | 'info'
  | 'rocket'
  | 'flag'
  | 'trend-up'
  | 'bolt'
  | 'pen'
  | 'bookmark'
  | 'trophy'
  | 'compass'

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
    <template v-else-if="name === 'target'">
      <circle cx="12" cy="12" r="8.2" />
      <circle cx="12" cy="12" r="4.2" />
      <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" />
      <path d="M12 1.6v2.4M12 20v2.4M1.6 12h2.4M20 12h2.4" />
    </template>
    <template v-else-if="name === 'half'">
      <circle cx="12" cy="12" r="8.4" />
      <path d="M12 3.6a8.4 8.4 0 0 1 0 16.8z" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="name === 'cross'">
      <path d="M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6" stroke-width="2.1" />
    </template>
    <template v-else-if="name === 'question'">
      <circle cx="12" cy="12" r="9" />
      <path d="M8.9 8.9a3.2 3.2 0 1 1 4.3 3.02c-.75.3-1.2 1-1.2 1.8v.68" />
      <circle cx="12" cy="18.1" r="1.15" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="name === 'shield'">
      <path d="M12 2.8 19.2 5.7v5.6c0 4.5-3 8.1-7.2 9.9-4.2-1.8-7.2-5.4-7.2-9.9V5.7z" />
      <path d="m8.9 11.9 2.2 2.2 4-4.4" />
    </template>
    <template v-else-if="name === 'spark'">
      <path d="M12 2.8l2.05 5.5 5.5 2.05-5.5 2.05L12 17.9l-2.05-5.5-5.5-2.05 5.5-2.05z" />
      <path d="M18.4 16.6l.85 2.15 2.15.85-2.15.85-.85 2.15-.85-2.15-2.15-.85 2.15-.85z" />
    </template>
    <template v-else-if="name === 'lift'">
      <path d="M12 20.4V6.2" />
      <path d="M6.6 11.6 12 6.2l5.4 5.4" />
      <path d="M4.4 3.4h15.2" />
    </template>
    <template v-else-if="name === 'evidence'">
      <rect x="3.4" y="6.4" width="17.2" height="14.2" rx="2" />
      <path d="M8.4 6.4V4.6a1.7 1.7 0 0 1 1.7-1.7h3.8a1.7 1.7 0 0 1 1.7 1.7v1.8" />
      <path d="m9.6 13.4 1.8 1.8 3.4-3.6" />
    </template>
    <template v-else-if="name === 'chart'">
      <path d="M4.2 20.2h15.6" />
      <path d="M7.4 20.2v-6.4M12 20.2V7.4M16.6 20.2v-9.4" />
    </template>
    <template v-else-if="name === 'history'">
      <path d="M3.6 12a8.4 8.4 0 1 0 2.5-6" />
      <path d="M3.4 3.8v4.4h4.4" />
      <path d="M12 7.8V12l3 1.8" />
    </template>
    <template v-else-if="name === 'refresh'">
      <path d="M20.2 11.2a8.2 8.2 0 0 0-14.3-4.4L3.8 8.9" />
      <path d="M3.8 4.4v4.5h4.5" />
      <path d="M3.8 12.8a8.2 8.2 0 0 0 14.3 4.4l2.1-2.1" />
      <path d="M20.2 19.6v-4.5h-4.5" />
    </template>
    <template v-else-if="name === 'scale'">
      <path d="M12 4v16M6.4 20.4h11.2M4 9.6h16" />
      <path d="M4 9.6 1.8 15a3.2 3.2 0 0 0 4.4 0zM20 9.6 17.8 15a3.2 3.2 0 0 0 4.4 0z" />
      <circle cx="12" cy="4" r="1.4" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="name === 'flow'">
      <rect x="2.8" y="9.4" width="5.4" height="5.2" rx="1.4" />
      <rect x="15.8" y="3.4" width="5.4" height="5.2" rx="1.4" />
      <rect x="15.8" y="15.4" width="5.4" height="5.2" rx="1.4" />
      <path d="M8.2 12h3.4V6h4.2M11.6 12v6h4.2" />
    </template>
    <template v-else-if="name === 'info'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11.2v5.2" />
      <circle cx="12" cy="7.9" r="1.05" fill="currentColor" stroke="none" />
    </template>
    <template v-else-if="name === 'rocket'">
      <path d="M12 2.8c2.5 2.1 3.9 5 3.9 8.4v3.6l1.5 2.1H6.6l1.5-2.1v-3.6c0-3.4 1.4-6.3 3.9-8.4z" />
      <circle cx="12" cy="9.6" r="1.75" />
      <path d="M8.1 12.9 5.3 14.9v2.6M15.9 12.9l2.8 2v2.6" />
      <path d="M9.7 18.3c.35 1.7 1.12 2.75 2.3 3.15 1.18-.4 1.95-1.45 2.3-3.15" />
    </template>
    <template v-else-if="name === 'flag'">
      <path d="M6.2 3.2v17.6" />
      <path d="M6.2 4.4h11.2l-2.1 3.7 2.1 3.7H6.2z" />
    </template>
    <template v-else-if="name === 'trend-up'">
      <path d="M3.6 16.6 9.2 11l3.3 3.3 6.9-6.9" />
      <path d="M14.9 7.4h5.1v5.1" />
      <path d="M3.6 20.4h16.8" />
    </template>
    <template v-else-if="name === 'bolt'">
      <path d="M13.5 2.6 5.8 13.2h5l-.7 8.2 7.9-10.9h-5.1z" />
    </template>
    <template v-else-if="name === 'pen'">
      <path
        d="m4.2 19.8.75-3.9L16 4.85a1.9 1.9 0 0 1 2.7 0l.45.45a1.9 1.9 0 0 1 0 2.7L8.1 19.05z"
      />
      <path d="m14.6 6.25 3.15 3.15" />
      <path d="M13.4 20.4h6.4" />
    </template>
    <template v-else-if="name === 'bookmark'">
      <path d="M6.4 3.4h11.2v17.2L12 16.5l-5.6 4.1z" />
      <path d="M9.4 8.4h5.2" />
    </template>
    <template v-else-if="name === 'trophy'">
      <path d="M7.4 3.4h9.2v4.9a4.6 4.6 0 0 1-9.2 0z" />
      <path d="M7.4 5.1H4.6v1.4a3.1 3.1 0 0 0 2.8 3.05M16.6 5.1h2.8v1.4a3.1 3.1 0 0 1-2.8 3.05" />
      <path d="M12 12.9v3.3" />
      <path d="M9.2 20.6h5.6l-.7-2.5h-4.2z" />
    </template>
    <template v-else-if="name === 'compass'">
      <circle cx="12" cy="12" r="9" />
      <path d="m15.4 8.6-1.9 4.9-4.9 1.9 1.9-4.9z" />
    </template>
    <template v-else>
      <path d="M4 6h16M7 12h10M10 18h4" />
    </template>
  </svg>
</template>
