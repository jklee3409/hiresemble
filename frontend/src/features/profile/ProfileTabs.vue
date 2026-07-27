<script setup lang="ts">
import { RouterLink } from 'vue-router'

const tabs = [
  ['/profile/basic', '기본 정보'],
  ['/profile/education', '학력'],
  ['/profile/certifications', '자격증'],
  ['/profile/languages', '어학'],
  ['/profile/awards', '수상'],
  ['/profile/careers', '경력'],
  ['/profile/evidence', '경험 정보'],
] as const
</script>

<template>
  <div class="profile-navigation">
    <p class="profile-navigation__label">내 경험</p>
    <nav class="profile-tabs" aria-label="프로필 메뉴">
      <RouterLink v-for="[to, label] in tabs" :key="to" :to="to" class="profile-tab">
        {{ label }}
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.profile-navigation {
  margin: -0.25rem 0 1.75rem;
  border-bottom: 1px solid var(--color-border);
}

.profile-navigation__label {
  margin: 0 0 0.5rem;
  color: var(--color-muted);
  font-size: 0.6875rem;
  font-weight: 760;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.profile-tabs {
  display: flex;
  gap: 0.125rem;
  overflow-x: auto;
  scrollbar-width: thin;
}

.profile-tab {
  position: relative;
  display: inline-flex;
  min-height: 2.625rem;
  flex: 0 0 auto;
  align-items: center;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  color: var(--color-muted-strong);
  padding: 0.5rem 0.75rem;
  font-size: 0.8125rem;
  font-weight: 650;
  text-decoration: none;
}

.profile-tab:hover {
  background: var(--color-surface-subtle);
  color: var(--color-ink);
}

.profile-tab[aria-current='page'] {
  color: var(--color-brand);
}

.profile-tab[aria-current='page']::after {
  position: absolute;
  right: 0.625rem;
  bottom: -1px;
  left: 0.625rem;
  height: 2px;
  border-radius: 999px 999px 0 0;
  background: var(--color-brand);
  content: '';
}

@media (max-width: 639px) {
  .profile-navigation {
    margin-inline: -1rem;
    padding-inline: 1rem;
  }
}
</style>
