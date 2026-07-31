<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { validateDisplayNameForm } from '@/features/auth/formValidation'
import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import { authErrorMessage, fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

const navItems = [
  { to: '/dashboard', label: '지원 홈', icon: 'dashboard', match: '/dashboard' },
  { to: '/profile/basic', label: '내 지원 정보', icon: 'profile', match: '/profile' },
  { to: '/documents', label: '이력서·자료', icon: 'documents', match: '/documents' },
  { to: '/jobs', label: '관심 공고', icon: 'jobs', match: '/jobs' },
  {
    to: '/cover-letters',
    label: '자기소개서',
    icon: 'cover-letter',
    match: '/cover-letters',
  },
  { to: '/interviews', label: '면접 준비', icon: 'interview', match: '/interview' },
  { to: '/agent-runs', label: 'AI 작업 내역', icon: 'runs', match: '/agent-runs' },
] as const

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const isLoggingOut = ref(false)
const logoutError = ref('')
const mobileNavOpen = ref(false)
const mobileNavTrigger = ref<HTMLButtonElement | null>(null)
const mobileNavPanel = ref<HTMLElement | null>(null)
const workspaceContent = ref<HTMLElement | null>(null)
const nicknameModalOpen = ref(false)
const nickname = ref('')
const nicknameFieldError = ref('')
const nicknameGeneralError = ref('')
const nicknameSaving = ref(false)
const nicknameTrigger = ref<HTMLElement | null>(null)
const nicknameDialog = ref<HTMLElement | null>(null)
const nicknameInput = ref<HTMLInputElement | null>(null)
let bodyOverflowBeforeOverlay = ''

const pageTitle = computed(() => route.meta.title ?? 'Hiresemble')
const pageContext = computed(() => {
  if (route.path.startsWith('/profile') || route.path === '/onboarding') return '나의 경험'
  if (route.path.startsWith('/documents')) return '이력서와 자료'
  if (route.path.startsWith('/jobs')) return '지원할 공고'
  if (route.path.startsWith('/cover-letters')) return '지원 문서'
  if (route.path.startsWith('/interviews') || route.path.startsWith('/interview-question-sets')) {
    return '면접 조사와 예상 질문'
  }
  if (route.path.startsWith('/agent-runs')) return '준비 진행 상황'
  return '지원 현황과 다음 할 일'
})
const userInitial = computed(() => authStore.currentUser?.displayName.trim().charAt(0) || 'H')
const overlayOpen = computed(() => mobileNavOpen.value || nicknameModalOpen.value)
const AgentRunProgressDrawer = defineAsyncComponent(
  () => import('@/features/agent-runs/AgentRunProgressDrawer.vue'),
)

watch(
  () => route.fullPath,
  () => {
    closeMobileNav(false)
    closeNicknameModal(false)
    document.title = `${String(route.meta.title ?? 'Hiresemble')} | Hiresemble`
    void nextTick(() => workspaceContent.value?.focus({ preventScroll: true }))
  },
  { immediate: true },
)

watch(mobileNavOpen, async (open) => {
  if (open) {
    await nextTick()
    mobileNavPanel.value?.querySelector<HTMLElement>('[data-mobile-nav-first]')?.focus()
  }
})

watch(nicknameModalOpen, async (open) => {
  if (!open) return
  await nextTick()
  nicknameInput.value?.focus()
  nicknameInput.value?.select()
})

watch(overlayOpen, (open) => {
  if (open) {
    bodyOverflowBeforeOverlay = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return
  }
  document.body.style.overflow = bodyOverflowBeforeOverlay
})

document.addEventListener('keydown', onDocumentKeydown)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
  document.body.style.overflow = bodyOverflowBeforeOverlay
})

function isNavActive(match: string): boolean {
  return match === '/dashboard' ? route.path === match : route.path.startsWith(match)
}

function openMobileNav(): void {
  mobileNavOpen.value = true
}

function closeMobileNav(restoreFocus = true): void {
  if (!mobileNavOpen.value) return
  mobileNavOpen.value = false
  if (restoreFocus) void nextTick(() => mobileNavTrigger.value?.focus())
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (nicknameModalOpen.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeNicknameModal()
      return
    }
    trapFocus(event, nicknameDialog.value)
    return
  }
  if (!mobileNavOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMobileNav()
    return
  }
  trapFocus(event, mobileNavPanel.value)
}

function trapFocus(event: KeyboardEvent, container: HTMLElement | null): void {
  if (event.key !== 'Tab' || container === null) return
  const focusable = Array.from(
    container.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  )
  if (focusable.length === 0) return
  const first = focusable[0]
  const last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function openNicknameModal(event?: Event): void {
  const currentTarget = event?.currentTarget
  nicknameTrigger.value =
    currentTarget instanceof HTMLElement && currentTarget.closest('.mobile-drawer') === null
      ? currentTarget
      : mobileNavTrigger.value
  closeMobileNav(false)
  nickname.value = authStore.currentUser?.displayName ?? ''
  nicknameFieldError.value = ''
  nicknameGeneralError.value = ''
  nicknameModalOpen.value = true
}

function closeNicknameModal(restoreFocus = true): void {
  if (!nicknameModalOpen.value) return
  nicknameModalOpen.value = false
  nicknameFieldError.value = ''
  nicknameGeneralError.value = ''
  if (restoreFocus) void nextTick(() => nicknameTrigger.value?.focus())
}

async function saveNickname(): Promise<void> {
  nicknameFieldError.value = ''
  nicknameGeneralError.value = ''
  const validation = validateDisplayNameForm({ displayName: nickname.value })
  if (validation.data === null) {
    nicknameFieldError.value = validation.fieldErrors.displayName ?? '닉네임을 확인해 주세요.'
    await nextTick()
    nicknameInput.value?.focus()
    return
  }

  nicknameSaving.value = true
  try {
    await authStore.updateDisplayName(validation.data)
    closeNicknameModal()
  } catch (error) {
    const apiError = normalizeApiError(error)
    nicknameFieldError.value = fieldErrorsToRecord(apiError.fieldErrors).displayName ?? ''
    if (nicknameFieldError.value === '') {
      nicknameGeneralError.value = authErrorMessage(apiError)
    }
  } finally {
    nicknameSaving.value = false
  }
}

async function logout(): Promise<void> {
  isLoggingOut.value = true
  logoutError.value = ''

  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = authErrorMessage(normalizeApiError(error))
  } finally {
    isLoggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <a class="sr-only-focusable skip-link" href="#app-content">본문으로 건너뛰기</a>

    <aside class="desktop-sidebar" aria-label="서비스 탐색">
      <RouterLink class="sidebar-brand" to="/dashboard" aria-label="Hiresemble 지원 홈">
        <BrandMark inverse />
        <span>
          <small>나의 지원 준비</small>
        </span>
      </RouterLink>

      <nav class="sidebar-nav" aria-label="주요 메뉴">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="sidebar-nav__link"
          :class="{ 'sidebar-nav__link--active': isNavActive(item.match) }"
          :aria-current="isNavActive(item.match) ? 'page' : undefined"
        >
          <AppIcon :name="item.icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <RouterLink
          class="onboarding-link"
          to="/onboarding"
          :aria-current="route.path === '/onboarding' ? 'page' : undefined"
        >
          <span class="onboarding-link__label">프로필 설정 안내</span>
          <span>온보딩 다시 보기</span>
          <AppIcon name="arrow-right" />
        </RouterLink>
        <div class="sidebar-user">
          <span class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
          <span class="sidebar-user__text">
            <strong>{{ authStore.currentUser?.displayName }}</strong>
            <small>{{ authStore.currentUser?.email }}</small>
          </span>
        </div>
      </div>
    </aside>

    <div class="app-workspace">
      <header class="workspace-header">
        <div class="workspace-header__identity">
          <button
            ref="mobileNavTrigger"
            type="button"
            class="button button--secondary button--icon mobile-menu-button"
            aria-label="주요 메뉴 열기"
            aria-controls="mobile-navigation"
            :aria-expanded="mobileNavOpen"
            @click="openMobileNav"
          >
            <AppIcon name="menu" />
          </button>
          <RouterLink class="mobile-brand" to="/dashboard" aria-label="Hiresemble 지원 홈">
            <BrandMark compact :show-name="false" />
          </RouterLink>
          <div class="workspace-title">
            <p>{{ pageContext }}</p>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>

        <div class="workspace-header__actions">
          <AgentRunProgressDrawer />
          <button
            type="button"
            class="header-user"
            :aria-label="`닉네임 수정: ${authStore.currentUser?.displayName ?? ''}`"
            aria-haspopup="dialog"
            @click="openNicknameModal"
          >
            <span class="user-avatar user-avatar--small" aria-hidden="true">{{ userInitial }}</span>
            <span class="header-user__name">{{ authStore.currentUser?.displayName }}</span>
            <span class="header-user__hint" aria-hidden="true">수정</span>
          </button>
          <button
            type="button"
            class="button button--ghost header-logout"
            :disabled="isLoggingOut"
            @click="logout"
          >
            <AppIcon name="logout" />
            {{ isLoggingOut ? '로그아웃 중…' : '로그아웃' }}
          </button>
        </div>

        <p v-if="logoutError" class="workspace-header__error" role="alert">
          {{ logoutError }}
        </p>
      </header>

      <main id="app-content" ref="workspaceContent" class="workspace-content" tabindex="-1">
        <RouterView />
      </main>
    </div>

    <Teleport to="body">
      <div v-if="mobileNavOpen" class="mobile-drawer-layer">
        <button
          type="button"
          class="mobile-drawer-overlay"
          aria-label="주요 메뉴 닫기"
          @click="closeMobileNav()"
        />
        <aside
          id="mobile-navigation"
          ref="mobileNavPanel"
          class="mobile-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="mobile-navigation-title"
        >
          <div class="mobile-drawer__header">
            <div>
              <p class="page-eyebrow">나의 지원 준비</p>
              <h2 id="mobile-navigation-title">Hiresemble 메뉴</h2>
            </div>
            <button
              type="button"
              class="button button--ghost button--icon"
              aria-label="주요 메뉴 닫기"
              @click="closeMobileNav()"
            >
              <AppIcon name="close" />
            </button>
          </div>

          <nav class="mobile-drawer__nav" aria-label="모바일 주요 메뉴">
            <RouterLink
              v-for="(item, index) in navItems"
              :key="item.to"
              :data-mobile-nav-first="index === 0 ? '' : undefined"
              :to="item.to"
              class="mobile-nav-link"
              :class="{ 'mobile-nav-link--active': isNavActive(item.match) }"
              :aria-current="isNavActive(item.match) ? 'page' : undefined"
              @click="closeMobileNav(false)"
            >
              <AppIcon :name="item.icon" />
              <span>{{ item.label }}</span>
            </RouterLink>
          </nav>

          <div class="mobile-drawer__footer">
            <RouterLink
              class="mobile-nav-link mobile-nav-link--secondary"
              to="/onboarding"
              :aria-current="route.path === '/onboarding' ? 'page' : undefined"
              @click="closeMobileNav(false)"
            >
              <AppIcon name="profile" />
              <span>온보딩 다시 보기</span>
            </RouterLink>
            <button
              type="button"
              class="mobile-drawer__user mobile-drawer__user--button"
              aria-haspopup="dialog"
              @click="openNicknameModal"
            >
              <span class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
              <span>
                <strong>{{ authStore.currentUser?.displayName }}</strong>
                <small>닉네임 수정</small>
              </span>
            </button>
          </div>
        </aside>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="nicknameModalOpen" class="nickname-modal-layer">
        <button
          type="button"
          class="nickname-modal-overlay"
          aria-label="닉네임 수정 닫기"
          :disabled="nicknameSaving"
          @click="closeNicknameModal()"
        />
        <section
          ref="nicknameDialog"
          class="nickname-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="nickname-modal-title"
          aria-describedby="nickname-modal-description"
        >
          <header class="nickname-modal__header">
            <div>
              <p class="page-eyebrow">계정 표시 정보</p>
              <h2 id="nickname-modal-title">닉네임 수정</h2>
            </div>
            <button
              type="button"
              class="button button--ghost button--icon"
              aria-label="닉네임 수정 닫기"
              :disabled="nicknameSaving"
              @click="closeNicknameModal()"
            >
              <AppIcon name="close" />
            </button>
          </header>
          <form class="nickname-modal__form" novalidate @submit.prevent="saveNickname">
            <p id="nickname-modal-description" class="nickname-modal__description">
              변경한 닉네임은 헤더와 지원 홈에 바로 표시돼요.
            </p>
            <label class="field" for="nickname-modal-input">
              <span class="field__label">닉네임</span>
              <input
                id="nickname-modal-input"
                ref="nicknameInput"
                v-model="nickname"
                class="control"
                maxlength="100"
                autocomplete="nickname"
                :aria-invalid="Boolean(nicknameFieldError)"
                :aria-describedby="nicknameFieldError ? 'nickname-modal-error' : undefined"
              />
              <span v-if="nicknameFieldError" id="nickname-modal-error" class="field-error">
                {{ nicknameFieldError }}
              </span>
            </label>
            <p v-if="nicknameGeneralError" class="alert alert--danger" role="alert">
              {{ nicknameGeneralError }}
            </p>
            <footer class="nickname-modal__actions">
              <button
                type="button"
                class="button button--secondary"
                :disabled="nicknameSaving"
                @click="closeNicknameModal()"
              >
                취소
              </button>
              <button type="submit" class="button button--primary" :disabled="nicknameSaving">
                {{ nicknameSaving ? '저장 중…' : '저장' }}
              </button>
            </footer>
          </form>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100dvh;
  background: var(--color-canvas);
}

.skip-link {
  position: fixed;
  top: 0.75rem;
  left: 0.75rem;
  z-index: 100;
  border-radius: var(--radius-md);
  background: var(--color-ink);
  color: white;
  padding: 0.625rem 0.875rem;
  font-weight: 700;
}

.desktop-sidebar {
  position: sticky;
  top: 0;
  display: none;
  width: var(--sidebar-width);
  height: 100dvh;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  overflow-y: auto;
  border-right: 1px solid #263254;
  background: #11182d;
  color: #edf1ff;
  padding: 1.25rem 1rem;
}

.sidebar-brand {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 0.125rem;
  border-radius: var(--radius-md);
  padding: 0.375rem;
  text-decoration: none;
}

.sidebar-brand small {
  display: block;
  margin-left: 3.2rem;
  color: #9eabd1;
  font-size: 0.6875rem;
  letter-spacing: 0.02em;
}

.sidebar-nav {
  display: grid;
  gap: 0.25rem;
  margin-top: 2rem;
}

.sidebar-nav__link {
  position: relative;
  display: flex;
  min-height: 2.75rem;
  align-items: center;
  gap: 0.75rem;
  border-radius: var(--radius-md);
  color: #bac4e3;
  padding: 0.625rem 0.75rem;
  font-size: 0.875rem;
  font-weight: 620;
  text-decoration: none;
  transition:
    background-color 140ms ease,
    color 140ms ease;
}

.sidebar-nav__link:hover {
  background: #1c2745;
  color: white;
}

.sidebar-nav__link--active {
  background: #24335a;
  color: white;
  box-shadow: inset 3px 0 var(--hs-blue-400);
}

.sidebar-footer {
  display: grid;
  gap: 1rem;
  margin-top: auto;
  padding-top: 2rem;
}

.onboarding-link {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.125rem 0.5rem;
  align-items: center;
  border: 1px solid #354466;
  border-radius: var(--radius-md);
  color: #dbe2f8;
  padding: 0.75rem;
  font-size: 0.8125rem;
  text-decoration: none;
}

.onboarding-link:hover,
.onboarding-link[aria-current='page'] {
  border-color: #617ad0;
  background: #1c2745;
}

.onboarding-link__label {
  grid-column: 1 / -1;
  color: #94a2ca;
  font-size: 0.6875rem;
}

.onboarding-link .icon {
  grid-column: 2;
  grid-row: 2;
}

.sidebar-user {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
  border-top: 1px solid #34405f;
  padding: 1rem 0.375rem 0;
}

.user-avatar {
  display: inline-grid;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 999px;
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  font-size: 0.875rem;
  font-weight: 800;
}

.user-avatar--small {
  width: 1.875rem;
  height: 1.875rem;
  font-size: 0.75rem;
}

.sidebar-user .user-avatar {
  background: #dce4ff;
}

.sidebar-user__text {
  min-width: 0;
}

.sidebar-user__text strong,
.sidebar-user__text small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-user__text strong {
  color: #f3f5ff;
  font-size: 0.8125rem;
}

.sidebar-user__text small {
  margin-top: 0.125rem;
  color: #9eabd1;
  font-size: 0.6875rem;
}

.app-workspace {
  min-width: 0;
  flex: 1 1 auto;
}

.workspace-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  min-height: 4.5rem;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  padding: 0.75rem clamp(1rem, 2.5vw, 2rem);
}

.workspace-header__identity,
.workspace-header__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
}

.workspace-title {
  min-width: 0;
}

.workspace-title p {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: none;
}

.workspace-title h1 {
  margin: 0.1rem 0 0;
  overflow: hidden;
  color: var(--color-ink);
  font-size: 1.125rem;
  font-weight: 720;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-user {
  display: none;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-ink-soft);
  padding: 0.25rem 0.4rem;
  font-size: 0.8125rem;
  font-weight: 620;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background-color 140ms ease;
}

.header-user:hover,
.header-user:focus-visible {
  border-color: var(--color-border);
  background: var(--color-neutral-soft);
}

.header-user__name {
  max-width: 10rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-user__hint {
  color: var(--color-brand-ink);
  font-size: 0.6875rem;
}

.workspace-header__error {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 1rem;
  max-width: min(26rem, calc(100vw - 2rem));
  border: 1px solid #efc0bb;
  border-radius: var(--radius-md);
  background: var(--color-danger-soft);
  color: #8e1c14;
  padding: 0.75rem 1rem;
  font-size: 0.8125rem;
  box-shadow: var(--shadow-md);
}

.workspace-content {
  width: 100%;
  max-width: var(--content-width);
  min-width: 0;
  margin: 0 auto;
  padding: clamp(1.25rem, 3vw, 2.25rem);
}

.mobile-drawer-layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
}

.mobile-drawer-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  border: 0;
  border-radius: 0;
  background: rgb(9 14 32 / 64%);
  padding: 0;
}

.mobile-drawer {
  position: relative;
  display: flex;
  width: min(20rem, calc(100vw - 2rem));
  max-height: 100dvh;
  flex-direction: column;
  overflow-y: auto;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  animation: mobile-drawer-enter 240ms cubic-bezier(0.2, 0, 0, 1) both;
}

.mobile-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--color-border);
  padding: 1rem;
}

.mobile-drawer__header h2 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 720;
}

.mobile-drawer__nav {
  display: grid;
  gap: 0.25rem;
  padding: 1rem;
}

.mobile-nav-link {
  display: flex;
  min-height: 2.875rem;
  align-items: center;
  gap: 0.75rem;
  border-radius: var(--radius-md);
  color: var(--color-ink-soft);
  padding: 0.625rem 0.75rem;
  font-size: 0.9375rem;
  font-weight: 650;
  text-decoration: none;
}

.mobile-nav-link:hover {
  background: var(--color-neutral-soft);
}

.mobile-nav-link--active {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  box-shadow: inset 3px 0 var(--color-brand);
}

.mobile-nav-link--secondary {
  border: 1px solid var(--color-border);
}

.mobile-drawer__footer {
  display: grid;
  gap: 1rem;
  margin-top: auto;
  border-top: 1px solid var(--color-border);
  padding: 1rem;
}

.mobile-drawer__user {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
}

.mobile-drawer__user strong,
.mobile-drawer__user small {
  display: block;
  overflow: hidden;
  max-width: 13rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-drawer__user strong {
  color: var(--color-ink);
  font-size: 0.875rem;
}

.mobile-drawer__user small {
  color: var(--color-muted);
  font-size: 0.75rem;
}

@media (min-width: 640px) {
  .header-user {
    display: flex;
  }
}

.mobile-drawer__user--button {
  width: 100%;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: inherit;
  padding: 0.5rem;
  text-align: left;
}

.mobile-drawer__user--button:hover,
.mobile-drawer__user--button:focus-visible {
  background: var(--color-neutral-soft);
}

.nickname-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: var(--space-4);
}

.nickname-modal-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  border: 0;
  border-radius: 0;
  background: rgb(9 14 32 / 58%);
  padding: 0;
}

.nickname-modal {
  position: relative;
  width: min(28rem, 100%);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.nickname-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  border-bottom: 1px solid var(--color-border);
  padding: var(--space-5);
}

.nickname-modal__header h2 {
  margin: var(--space-1) 0 0;
  color: var(--color-ink);
  font-size: 1.25rem;
}

.nickname-modal__form {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
}

.nickname-modal__description {
  margin: 0;
  color: var(--color-muted);
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.nickname-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (min-width: 1024px) {
  .desktop-sidebar {
    display: flex;
  }

  .mobile-menu-button,
  .mobile-brand {
    display: none;
  }
}

@media (max-width: 767px) {
  .workspace-header {
    min-height: 4rem;
    padding-inline: 0.75rem;
  }

  .workspace-header__actions {
    gap: 0.25rem;
  }

  .header-logout {
    width: 2.625rem;
    padding: 0;
    font-size: 0;
  }

  .header-logout .icon {
    width: 1.125rem;
    height: 1.125rem;
  }
}

@media (max-width: 479px) {
  .workspace-title p {
    display: none;
  }

  .workspace-title h1 {
    max-width: 7.25rem;
    font-size: 1rem;
  }

  .workspace-content {
    padding-inline: 1rem;
  }
}

@keyframes mobile-drawer-enter {
  from {
    opacity: 0;
    transform: translateX(-1rem);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mobile-drawer {
    animation: none;
  }
}
</style>
