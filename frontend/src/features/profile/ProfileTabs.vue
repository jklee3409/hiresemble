<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const sections = [
  {
    to: '/profile/basic',
    label: '기본 정보',
    description: '소개와 희망 조건',
  },
  {
    to: '/profile/education',
    label: '학력',
    description: '학교와 전공',
  },
  {
    to: '/profile/careers',
    label: '경력',
    description: '회사·역할·성과',
  },
  {
    to: '/profile/certifications',
    label: '자격증',
    description: '자격과 증빙 자료',
  },
  {
    to: '/profile/languages',
    label: '어학',
    description: '시험과 점수',
  },
  {
    to: '/profile/awards',
    label: '수상',
    description: '수상 이력과 설명',
  },
  {
    to: '/profile/evidence',
    label: '경험 정보',
    description: '자료에서 정리한 내용',
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
      <p class="profile-outline__eyebrow">지원 정보 작성</p>
      <h2>내 지원 정보</h2>
      <p>필요한 항목부터 차근차근 정리해 여러 지원에 활용하세요.</p>
    </div>

    <nav class="profile-outline__desktop" aria-label="프로필 메뉴">
      <RouterLink
        v-for="(section, index) in sections"
        :key="section.to"
        :to="section.to"
        class="profile-outline__link"
      >
        <span class="profile-outline__number" aria-hidden="true">
          {{ String(index + 1).padStart(2, '0') }}
        </span>
        <span>
          <strong>{{ section.label }}</strong>
          <small>{{ section.description }}</small>
        </span>
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
          {{ section.label }} · {{ section.description }}
        </option>
      </select>
    </label>

    <p class="profile-outline__note">
      모든 항목을 한 번에 채우지 않아도 괜찮아요. 저장한 내용부터 준비에 활용할 수 있어요.
    </p>
  </aside>
</template>

<style scoped>
.profile-outline {
  position: sticky;
  top: 1.5rem;
  align-self: start;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-xs);
}

.profile-outline__intro {
  border-bottom: 1px solid var(--color-border);
  background:
    radial-gradient(circle at 90% 10%, rgb(49 87 255 / 16%), transparent 38%), var(--hs-blue-50);
  padding: 1.25rem;
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

.profile-outline__intro p:last-child {
  margin: 0.5rem 0 0;
  color: var(--color-muted);
  font-size: 0.8125rem;
  line-height: 1.55;
}

.profile-outline__desktop {
  display: grid;
  gap: 0.3rem;
  padding: 0.625rem;
}

.profile-outline__link {
  display: grid;
  min-height: 3.75rem;
  grid-template-columns: 1.75rem minmax(0, 1fr);
  align-items: center;
  gap: 0.5rem;
  border-left: 3px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--color-muted-strong);
  padding: 0.55rem 0.625rem;
  text-decoration: none;
  transition:
    border-color var(--motion-fast),
    background-color var(--motion-fast),
    color var(--motion-fast),
    transform var(--motion-fast);
}

.profile-outline__link:hover {
  background: var(--hs-blue-50);
  color: var(--color-ink);
  transform: translateX(2px);
}

.profile-outline__link[aria-current='page'] {
  border-left-color: var(--color-brand);
  background: var(--hs-blue-100);
  color: var(--hs-blue-800);
}

.profile-outline__number {
  color: var(--hs-blue-500);
  font-size: 0.6875rem;
  font-weight: 760;
  font-variant-numeric: tabular-nums;
}

.profile-outline__link strong,
.profile-outline__link small {
  display: block;
}

.profile-outline__link strong {
  font-size: 0.875rem;
}

.profile-outline__link small {
  margin-top: 0.1rem;
  color: var(--color-muted);
  font-size: 0.6875rem;
  line-height: 1.35;
}

.profile-outline__mobile {
  display: none;
}

.profile-outline__note {
  margin: 0;
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  padding: 1rem 1.25rem;
  font-size: 0.75rem;
  line-height: 1.55;
}

@media (max-width: 63.99rem) {
  .profile-outline {
    position: static;
  }

  .profile-outline__intro,
  .profile-outline__desktop,
  .profile-outline__note {
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
