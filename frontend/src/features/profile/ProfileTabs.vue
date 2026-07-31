<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const sections = [
  {
    to: '/profile/basic',
    label: '기본 정보',
  },
  {
    to: '/profile/education',
    label: '학력',
  },
  {
    to: '/profile/careers',
    label: '경력',
  },
  {
    to: '/profile/certifications',
    label: '자격증',
  },
  {
    to: '/profile/languages',
    label: '어학',
  },
  {
    to: '/profile/awards',
    label: '수상',
  },
  {
    to: '/profile/evidence',
    label: '경험 정보',
  },
] as const

const currentSection = computed(
  () => sections.find((section) => route.path === section.to) ?? sections[0],
)

function changeSection(event: Event): void {
  const target = event.target as HTMLSelectElement
  if (target.value !== route.path) void router.push(target.value)
}
</script>

<template>
  <aside class="profile-outline" aria-label="내 지원 정보 항목">
    <div class="profile-outline__intro">
      <p class="profile-outline__eyebrow">프로필 관리</p>
      <h2>내 지원 정보</h2>
    </div>

    <nav class="profile-outline__desktop" aria-label="프로필 메뉴">
      <RouterLink
        v-for="section in sections"
        :key="section.to"
        :to="section.to"
        class="profile-outline__link"
      >
        <strong>{{ section.label }}</strong>
        <span v-if="route.path === section.to" class="profile-outline__current">현재</span>
      </RouterLink>
    </nav>

    <label class="profile-outline__mobile">
      <span>작성할 항목</span>
      <select
        class="control"
        :value="currentSection.to"
        aria-label="프로필 항목 선택"
        @change="changeSection"
      >
        <option v-for="section in sections" :key="section.to" :value="section.to">
          {{ section.label }}
        </option>
      </select>
    </label>
  </aside>
</template>

<style scoped>
.profile-outline {
  position: sticky;
  top: calc(4.5rem + var(--space-5));
  align-self: start;
  border-right: 1px solid var(--color-border);
  padding-right: var(--space-5);
}

.profile-outline__intro {
  padding: 0 var(--space-3) var(--space-5);
}

.profile-outline__eyebrow {
  margin: 0 0 0.35rem;
  color: var(--hs-blue-700);
  font-size: 0.6875rem;
  font-weight: 760;
  letter-spacing: 0.08em;
}

.profile-outline__intro h2 {
  margin: 0;
  color: var(--color-ink);
  font-size: 1.25rem;
  letter-spacing: -0.03em;
}

.profile-outline__desktop {
  display: grid;
  gap: var(--space-1);
}

.profile-outline__link {
  display: grid;
  min-height: 3rem;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  border-left: 2px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--color-muted-strong);
  padding: var(--space-2) var(--space-3);
  text-decoration: none;
  transition:
    border-color var(--motion-fast),
    background-color var(--motion-fast),
    color var(--motion-fast),
    transform var(--motion-fast);
}

.profile-outline__link:hover {
  background: var(--color-surface-subtle);
  color: var(--color-ink);
}

.profile-outline__link[aria-current='page'] {
  border-left-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--hs-blue-800);
}

.profile-outline__current {
  color: var(--color-brand);
  font-size: 0.6875rem;
  font-weight: 750;
}

.profile-outline__link strong {
  font-size: 0.875rem;
}

.profile-outline__mobile {
  display: none;
}

@media (max-width: 63.99rem) {
  .profile-outline {
    position: static;
    border-right: 0;
    padding-right: 0;
  }

  .profile-outline__intro,
  .profile-outline__desktop {
    display: none;
  }

  .profile-outline__mobile {
    display: grid;
    gap: 0.375rem;
    padding: 0.875rem;
  }

  .profile-outline__mobile > span {
    color: var(--color-muted-strong);
    font-size: 0.75rem;
    font-weight: 680;
  }
}
</style>
