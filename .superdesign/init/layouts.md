# SuperDesign Layout Context

아래에는 여러 라우트에서 공유되는 Vue 레이아웃의 실제 전체 소스가 포함됩니다.

## AppLayout

- Path: `frontend/src/layouts/AppLayout.vue`
- Description: 인증 후 전역 데스크톱 상단 내비게이션, 모바일 하단 내비게이션, 사용자 메뉴와 RouterView를 제공하는 앱 셸입니다.

```vue
<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import { validateDisplayNameForm } from '@/features/auth/formValidation'
import { authErrorMessage, fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import AppIcon from '@/shared/ui/AppIcon.vue'
import BrandMark from '@/shared/ui/BrandMark.vue'
import { useAuthStore } from '@/stores/auth'

type NavigationItem = {
  to: string
  label: string
  icon: 'dashboard' | 'profile' | 'documents' | 'jobs' | 'cover-letter' | 'interview' | 'runs'
  matches: readonly string[]
  exact?: boolean
}

const primaryNavigation: readonly NavigationItem[] = [
  {
    to: '/dashboard',
    label: '홈',
    icon: 'dashboard',
    matches: ['/dashboard'],
    exact: true,
  },
  {
    to: '/profile/basic',
    label: '내 정보',
    icon: 'profile',
    matches: ['/profile', '/onboarding'],
  },
  {
    to: '/documents',
    label: '이력서·자료',
    icon: 'documents',
    matches: ['/documents'],
  },
  { to: '/jobs', label: '관심 공고', icon: 'jobs', matches: ['/jobs'] },
  {
    to: '/cover-letters',
    label: '자기소개서',
    icon: 'cover-letter',
    matches: ['/cover-letters'],
  },
  {
    to: '/interviews',
    label: '면접 준비',
    icon: 'interview',
    matches: ['/interviews', '/interview-question-sets'],
  },
] as const

const mobileNavigation = primaryNavigation.filter((item) =>
  ['/dashboard', '/jobs', '/cover-letters', '/interviews'].includes(item.to),
)

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const workspaceContent = ref<HTMLElement | null>(null)
const accountMenuOpen = ref(false)
const accountTrigger = ref<HTMLButtonElement | null>(null)
const accountMenu = ref<HTMLElement | null>(null)
const mobileMoreOpen = ref(false)
const mobileMoreTrigger = ref<HTMLButtonElement | null>(null)
const mobileMorePanel = ref<HTMLElement | null>(null)
const nicknameModalOpen = ref(false)
const nickname = ref('')
const nicknameFieldError = ref('')
const nicknameGeneralError = ref('')
const nicknameSaving = ref(false)
const nicknameTrigger = ref<HTMLElement | null>(null)
const nicknameDialog = ref<HTMLElement | null>(null)
const nicknameInput = ref<HTMLInputElement | null>(null)
const isLoggingOut = ref(false)
const logoutError = ref('')
let bodyOverflowBeforeOverlay = ''

const mobileMoreActive = computed(() =>
  ['/profile', '/documents', '/agent-runs', '/guide', '/onboarding'].some((prefix) =>
    route.path.startsWith(prefix),
  ),
)
const blockingOverlayOpen = computed(() => mobileMoreOpen.value || nicknameModalOpen.value)
const AgentRunProgressDrawer = defineAsyncComponent(
  () => import('@/features/agent-runs/AgentRunProgressDrawer.vue'),
)

watch(
  () => route.fullPath,
  () => {
    closeAccountMenu(false)
    closeMobileMore(false)
    closeNicknameModal(false)
    void nextTick(() => workspaceContent.value?.focus({ preventScroll: true }))
  },
  { immediate: true },
)

watch(accountMenuOpen, async (open) => {
  if (!open) return
  await nextTick()
  accountMenu.value?.querySelector<HTMLElement>('[data-account-first]')?.focus()
})

watch(mobileMoreOpen, async (open) => {
  if (!open) return
  await nextTick()
  mobileMorePanel.value?.querySelector<HTMLElement>('[data-mobile-more-first]')?.focus()
})

watch(nicknameModalOpen, async (open) => {
  if (!open) return
  await nextTick()
  nicknameInput.value?.focus()
  nicknameInput.value?.select()
})

watch(blockingOverlayOpen, (open) => {
  if (open) {
    bodyOverflowBeforeOverlay = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return
  }
  document.body.style.overflow = bodyOverflowBeforeOverlay
})

document.addEventListener('keydown', onDocumentKeydown)
document.addEventListener('pointerdown', onDocumentPointerDown)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.body.style.overflow = bodyOverflowBeforeOverlay
})

function isNavActive(item: NavigationItem): boolean {
  if (item.exact) return route.path === item.to
  return item.matches.some((prefix) => route.path.startsWith(prefix))
}

function toggleAccountMenu(): void {
  accountMenuOpen.value = !accountMenuOpen.value
}

function closeAccountMenu(restoreFocus = true): void {
  if (!accountMenuOpen.value) return
  accountMenuOpen.value = false
  if (restoreFocus) void nextTick(() => accountTrigger.value?.focus())
}

function openMobileMore(): void {
  closeAccountMenu(false)
  mobileMoreOpen.value = true
}

function closeMobileMore(restoreFocus = true): void {
  if (!mobileMoreOpen.value) return
  mobileMoreOpen.value = false
  if (restoreFocus) void nextTick(() => mobileMoreTrigger.value?.focus())
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!accountMenuOpen.value) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (accountMenu.value?.contains(target) || accountTrigger.value?.contains(target)) return
  closeAccountMenu(false)
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
  if (mobileMoreOpen.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeMobileMore()
      return
    }
    trapFocus(event, mobileMorePanel.value)
    return
  }
  if (accountMenuOpen.value && event.key === 'Escape') {
    event.preventDefault()
    closeAccountMenu()
  }
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
  const target = event?.currentTarget
  nicknameTrigger.value =
    target instanceof HTMLElement && target.closest('.account-menu') !== null
      ? accountTrigger.value
      : target instanceof HTMLElement && target.closest('.mobile-more') !== null
        ? mobileMoreTrigger.value
        : target instanceof HTMLElement
          ? target
          : accountTrigger.value
  closeAccountMenu(false)
  closeMobileMore(false)
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
    if (nicknameFieldError.value === '') nicknameGeneralError.value = authErrorMessage(apiError)
  } finally {
    nicknameSaving.value = false
  }
}

async function logout(): Promise<void> {
  isLoggingOut.value = true
  logoutError.value = ''
  closeAccountMenu(false)
  closeMobileMore(false)
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

    <header class="product-header">
      <div class="product-header__inner">
        <RouterLink class="product-brand" to="/dashboard" aria-label="Hiresemble 홈">
          <BrandMark compact />
        </RouterLink>

        <nav class="desktop-navigation" aria-label="서비스 탐색">
          <RouterLink
            v-for="item in primaryNavigation"
            :key="item.to"
            :to="item.to"
            class="desktop-navigation__link"
            :class="{ 'desktop-navigation__link--active': isNavActive(item) }"
            :aria-current="isNavActive(item) ? 'page' : undefined"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="product-header__actions">
          <AgentRunProgressDrawer />
          <div class="account-entry">
            <button
              ref="accountTrigger"
              type="button"
              class="account-trigger"
              aria-haspopup="menu"
              :aria-expanded="accountMenuOpen"
              aria-controls="account-menu"
              @click="toggleAccountMenu"
            >
              <AppIcon name="profile" />
              <span>{{ authStore.currentUser?.displayName || '내 계정' }}</span>
              <span class="account-trigger__chevron" aria-hidden="true">⌄</span>
            </button>

            <div
              v-if="accountMenuOpen"
              id="account-menu"
              ref="accountMenu"
              class="account-menu"
              role="menu"
              aria-label="내 계정"
            >
              <div class="account-menu__identity">
                <AppIcon name="profile" />
                <span>
                  <strong>{{ authStore.currentUser?.displayName }}</strong>
                  <small>{{ authStore.currentUser?.email }}</small>
                </span>
              </div>
              <RouterLink data-account-first role="menuitem" to="/guide">이용 가이드</RouterLink>
              <RouterLink role="menuitem" to="/agent-runs">AI 작업</RouterLink>
              <button type="button" role="menuitem" @click="openNicknameModal">닉네임 변경</button>
              <button type="button" role="menuitem" :disabled="isLoggingOut" @click="logout">
                {{ isLoggingOut ? '로그아웃 중…' : '로그아웃' }}
              </button>
            </div>
          </div>
        </div>
      </div>
      <p v-if="logoutError" class="product-header__error" role="alert">{{ logoutError }}</p>
    </header>

    <main
      id="app-content"
      ref="workspaceContent"
      class="workspace-content"
      :class="{ 'workspace-content--dashboard': route.path === '/dashboard' }"
      tabindex="-1"
    >
      <RouterView />
    </main>

    <nav class="mobile-bottom-navigation" aria-label="모바일 주요 메뉴">
      <RouterLink
        v-for="item in mobileNavigation"
        :key="item.to"
        :to="item.to"
        class="mobile-bottom-navigation__item"
        :class="{ 'mobile-bottom-navigation__item--active': isNavActive(item) }"
        :aria-current="isNavActive(item) ? 'page' : undefined"
      >
        <AppIcon :name="item.icon" />
        <span>{{ item.label === '관심 공고' ? '공고' : item.label }}</span>
      </RouterLink>
      <button
        ref="mobileMoreTrigger"
        type="button"
        class="mobile-bottom-navigation__item"
        :class="{ 'mobile-bottom-navigation__item--active': mobileMoreActive }"
        aria-haspopup="dialog"
        :aria-expanded="mobileMoreOpen"
        aria-controls="mobile-more-menu"
        @click="openMobileMore"
      >
        <AppIcon name="menu" />
        <span>더보기</span>
      </button>
    </nav>

    <Teleport to="body">
      <div v-if="mobileMoreOpen" class="mobile-more-layer">
        <button
          type="button"
          class="mobile-more-overlay"
          aria-label="더보기 메뉴 닫기"
          @click="closeMobileMore()"
        />
        <section
          id="mobile-more-menu"
          ref="mobileMorePanel"
          class="mobile-more"
          role="dialog"
          aria-modal="true"
          aria-labelledby="mobile-more-title"
        >
          <header class="mobile-more__header">
            <div>
              <p>Hiresemble</p>
              <h2 id="mobile-more-title">더보기</h2>
            </div>
            <button
              type="button"
              class="button button--ghost button--icon"
              aria-label="더보기 메뉴 닫기"
              @click="closeMobileMore()"
            >
              <AppIcon name="close" />
            </button>
          </header>

          <nav class="mobile-more__links" aria-label="추가 메뉴">
            <RouterLink data-mobile-more-first to="/profile/basic">
              <AppIcon name="profile" />
              <span><strong>내 정보</strong><small>프로필과 경험 관리</small></span>
              <AppIcon name="arrow-right" />
            </RouterLink>
            <RouterLink to="/documents">
              <AppIcon name="documents" />
              <span><strong>이력서·자료</strong><small>등록한 자료와 확인한 경험</small></span>
              <AppIcon name="arrow-right" />
            </RouterLink>
            <RouterLink to="/agent-runs">
              <AppIcon name="runs" />
              <span><strong>AI 작업</strong><small>진행 중이거나 끝난 작업</small></span>
              <AppIcon name="arrow-right" />
            </RouterLink>
            <RouterLink to="/guide">
              <AppIcon name="dashboard" />
              <span><strong>이용 가이드</strong><small>Hiresemble 활용 순서</small></span>
              <AppIcon name="arrow-right" />
            </RouterLink>
          </nav>

          <div class="mobile-more__account">
            <span class="mobile-more__account-name">
              <AppIcon name="profile" />
              <span
                ><strong>{{ authStore.currentUser?.displayName }}</strong
                ><small>내 계정</small></span
              >
            </span>
            <button type="button" class="button button--secondary" @click="openNicknameModal">
              닉네임 변경
            </button>
            <button
              type="button"
              class="button button--ghost"
              :disabled="isLoggingOut"
              @click="logout"
            >
              로그아웃
            </button>
          </div>
        </section>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="nicknameModalOpen" class="nickname-modal-layer">
        <button
          type="button"
          class="nickname-modal-overlay"
          aria-label="닉네임 변경 닫기"
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
              <p class="page-eyebrow">내 계정</p>
              <h2 id="nickname-modal-title">닉네임 변경</h2>
            </div>
            <button
              type="button"
              class="button button--ghost button--icon"
              aria-label="닉네임 변경 닫기"
              :disabled="nicknameSaving"
              @click="closeNicknameModal()"
            >
              <AppIcon name="close" />
            </button>
          </header>
          <form class="nickname-modal__form" novalidate @submit.prevent="saveNickname">
            <p id="nickname-modal-description" class="nickname-modal__description">
              저장하면 홈과 계정 메뉴에 바로 반영돼요.
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
                {{ nicknameSaving ? '저장 중…' : '변경 사항 저장' }}
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
  min-height: 100dvh;
  background: var(--color-canvas);
}

.skip-link {
  position: fixed;
  top: var(--space-3);
  left: var(--space-3);
  z-index: 100;
  border-radius: var(--radius-control);
  background: var(--color-ink);
  color: white;
  padding: 0.625rem 0.875rem;
  font-weight: 700;
}

.product-header {
  position: sticky;
  top: 0;
  z-index: 40;
  border-bottom: 1px solid var(--color-border);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(16px);
}

.product-header__inner {
  display: grid;
  width: min(100% - 2rem, var(--content-width));
  min-height: var(--global-header-height);
  margin-inline: auto;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: clamp(1rem, 2.4vw, 2rem);
}

.product-brand {
  display: inline-flex;
  border-radius: var(--radius-control);
  text-decoration: none;
}

.desktop-navigation {
  display: none;
  min-width: 0;
  align-self: stretch;
  align-items: center;
  justify-content: center;
  gap: 0.125rem;
}

.desktop-navigation__link {
  position: relative;
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  border-radius: var(--radius-control);
  color: var(--color-muted-strong);
  padding: 0.625rem clamp(0.55rem, 0.85vw, 0.875rem);
  font-size: 0.875rem;
  font-weight: 650;
  text-decoration: none;
  white-space: nowrap;
}

.desktop-navigation__link:hover {
  background: var(--color-surface-subtle);
  color: var(--color-ink);
}

.desktop-navigation__link--active {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
  font-weight: 750;
}

.desktop-navigation__link--active::after {
  position: absolute;
  right: 0.75rem;
  bottom: -0.95rem;
  left: 0.75rem;
  height: 2px;
  border-radius: 999px 999px 0 0;
  background: var(--color-brand);
  content: '';
}

.product-header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.account-entry {
  position: relative;
}

.account-trigger {
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  gap: 0.5rem;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-ink-soft);
  padding: 0.5rem 0.625rem;
  font-size: 0.875rem;
  font-weight: 700;
}

.account-trigger:hover,
.account-trigger[aria-expanded='true'] {
  background: var(--color-neutral-soft);
  color: var(--color-ink);
}

.account-trigger__chevron {
  color: var(--color-muted);
  font-size: 1rem;
  line-height: 1;
}

.account-menu {
  position: absolute;
  top: calc(100% + 0.625rem);
  right: 0;
  display: grid;
  width: 15rem;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-surface);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 0.5rem;
}

.account-menu::before {
  position: absolute;
  top: -0.4rem;
  right: 1.25rem;
  width: 0.75rem;
  height: 0.75rem;
  border-top: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
  background: var(--color-surface);
  content: '';
  transform: rotate(45deg);
}

.account-menu__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
  margin-bottom: 0.375rem;
  border-bottom: 1px solid var(--color-border);
  padding: 0.625rem 0.625rem 0.875rem;
}

.account-menu__identity span {
  min-width: 0;
}

.account-menu__identity strong,
.account-menu__identity small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-menu__identity strong {
  color: var(--color-ink);
  font-size: 0.875rem;
}

.account-menu__identity small {
  margin-top: 0.1rem;
  color: var(--color-muted);
  font-size: 0.75rem;
}

.account-menu > a,
.account-menu > button {
  min-height: 2.5rem;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-ink-soft);
  padding: 0.625rem;
  font-size: 0.875rem;
  font-weight: 600;
  text-align: left;
  text-decoration: none;
}

.account-menu > :is(a, button):hover,
.account-menu > :is(a, button):focus-visible {
  background: var(--color-neutral-soft);
  color: var(--color-ink);
}

.product-header__error {
  width: min(100% - 2rem, var(--content-width));
  margin: -0.375rem auto 0.625rem;
  color: var(--color-danger);
  font-size: 0.8125rem;
  text-align: right;
}

.workspace-content {
  width: min(100% - clamp(2rem, 6vw, 4rem), var(--content-width));
  min-height: calc(100dvh - var(--global-header-height));
  margin-inline: auto;
  padding-block: var(--page-block-start) calc(var(--page-block-end) + var(--mobile-nav-height));
  outline: none;
}

.workspace-content--dashboard {
  width: min(100% - clamp(2rem, 5vw, 4rem), 88rem);
}

.workspace-content[tabindex='-1']:focus,
.workspace-content[tabindex='-1']:focus-visible {
  outline: none;
  box-shadow: none;
}

.mobile-bottom-navigation {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 35;
  display: grid;
  min-height: var(--mobile-nav-height);
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-top: 1px solid var(--color-border);
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 -8px 24px rgb(22 26 43 / 7%);
  padding: 0.375rem max(0.25rem, env(safe-area-inset-right))
    max(0.375rem, env(safe-area-inset-bottom)) max(0.25rem, env(safe-area-inset-left));
  backdrop-filter: blur(16px);
}

.mobile-bottom-navigation__item {
  display: grid;
  min-width: 0;
  min-height: 3rem;
  place-items: center;
  gap: 0.125rem;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-muted);
  padding: 0.25rem 0.125rem;
  font-size: 0.6875rem;
  font-weight: 650;
  line-height: 1.2;
  text-decoration: none;
}

.mobile-bottom-navigation__item .icon {
  width: 1.25rem;
  height: 1.25rem;
}

.mobile-bottom-navigation__item:hover,
.mobile-bottom-navigation__item--active {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
}

.mobile-bottom-navigation__item--active {
  font-weight: 800;
}

.mobile-more-layer,
.nickname-modal-layer {
  position: fixed;
  z-index: 80;
  inset: 0;
}

.mobile-more-overlay,
.nickname-modal-overlay {
  position: absolute;
  border: 0;
  background: rgb(16 24 40 / 48%);
  inset: 0;
}

.mobile-more {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  max-height: min(42rem, calc(100dvh - 2rem));
  overflow-y: auto;
  border-radius: 1.25rem 1.25rem 0 0;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 1rem 1rem max(1.25rem, env(safe-area-inset-bottom));
}

.mobile-more__header,
.nickname-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.mobile-more__header p {
  margin: 0;
  color: var(--color-brand);
  font-size: 0.75rem;
  font-weight: 750;
}

.mobile-more__header h2,
.nickname-modal__header h2 {
  margin: 0.125rem 0 0;
  color: var(--color-ink);
  font-size: 1.25rem;
}

.mobile-more__links {
  display: grid;
  gap: 0.25rem;
  margin-top: 1rem;
}

.mobile-more__links > a {
  display: grid;
  min-height: 4rem;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  border-radius: var(--radius-control);
  color: var(--color-ink-soft);
  padding: 0.625rem 0.75rem;
  text-decoration: none;
}

.mobile-more__links > a:hover,
.mobile-more__links > a[aria-current='page'] {
  background: var(--color-brand-soft);
  color: var(--color-brand-ink);
}

.mobile-more__links strong,
.mobile-more__links small {
  display: block;
}

.mobile-more__links strong {
  font-size: 0.9375rem;
}

.mobile-more__links small {
  margin-top: 0.125rem;
  color: var(--color-muted);
  font-size: 0.75rem;
}

.mobile-more__account {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  border-top: 1px solid var(--color-border);
  padding-top: 1rem;
}

.mobile-more__account-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.625rem;
}

.mobile-more__account-name strong,
.mobile-more__account-name small {
  display: block;
}

.mobile-more__account-name small {
  color: var(--color-muted);
  font-size: 0.75rem;
}

.nickname-modal {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(30rem, calc(100% - 2rem));
  max-height: calc(100dvh - 2rem);
  overflow-y: auto;
  border-radius: var(--radius-surface);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 1.25rem;
  transform: translate(-50%, -50%);
}

.nickname-modal__form {
  display: grid;
  gap: 1rem;
  margin-top: 1rem;
}

.nickname-modal__description {
  margin: 0;
  color: var(--color-muted);
  font-size: 0.875rem;
  line-height: 1.6;
}

.nickname-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (min-width: 70rem) {
  .desktop-navigation {
    display: flex;
  }

  .mobile-bottom-navigation {
    display: none;
  }

  .workspace-content {
    padding-bottom: var(--page-block-end);
  }
}

@media (max-width: 69.99rem) {
  .product-header__inner {
    display: flex;
    width: min(100% - 1.5rem, var(--content-width));
    justify-content: space-between;
  }

  .product-header__actions :deep(.progress-drawer-trigger span:not(.status-badge)) {
    display: none;
  }

  .account-trigger span:not(.account-trigger__chevron) {
    display: none;
  }

  .workspace-content {
    width: min(100% - 2rem, var(--content-width));
  }

  .workspace-content--dashboard {
    width: min(100% - 2rem, 88rem);
  }
}

@media (max-width: 35rem) {
  .product-brand :deep(.brand-lockup__name) {
    display: none;
  }

  .product-header__inner {
    min-height: 3.75rem;
  }

  .workspace-content {
    width: min(100% - 1.5rem, var(--content-width));
    padding-top: var(--space-6);
  }

  .workspace-content--dashboard {
    width: min(100% - 1.5rem, 88rem);
  }

  .mobile-more__account {
    grid-template-columns: 1fr 1fr;
  }

  .mobile-more__account-name {
    grid-column: 1 / -1;
  }

  .mobile-more__account .button {
    width: 100%;
  }

  .nickname-modal {
    top: auto;
    right: 0;
    bottom: 0;
    left: 0;
    width: auto;
    border-radius: 1.25rem 1.25rem 0 0;
    padding-bottom: max(1.25rem, env(safe-area-inset-bottom));
    transform: none;
  }

  .nickname-modal__actions > .button {
    flex: 1 1 0;
  }
}
</style>

```

## PublicLayout

- Path: `frontend/src/layouts/PublicLayout.vue`
- Description: 로그인·회원가입에 공통 브랜드 패널과 폼 영역을 제공하는 공개 레이아웃입니다.

```vue
<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

import BrandMark from '@/shared/ui/BrandMark.vue'
</script>

<template>
  <main class="public-shell" data-testid="public-layout">
    <section class="auth-panel" aria-label="인증" data-testid="public-auth-panel">
      <RouterLink class="auth-brand auth-brand--mobile" to="/" aria-label="Hiresemble 시작 화면">
        <BrandMark />
      </RouterLink>
      <div class="auth-form">
        <RouterView />
      </div>
      <p class="auth-privacy-note">
        계정 정보와 등록한 자료는 취업 준비 기능을 제공하는 데 사용해요. 회원가입할 때 개인정보와 AI
        처리 동의를 직접 확인할 수 있어요.
      </p>
    </section>

    <section class="brand-canvas" aria-label="Hiresemble 서비스 안내">
      <RouterLink class="auth-brand" to="/" aria-label="Hiresemble 시작 화면">
        <BrandMark inverse />
      </RouterLink>

      <div class="brand-orbit" aria-hidden="true">
        <span class="brand-orbit__ring brand-orbit__ring--outer" />
        <span class="brand-orbit__ring brand-orbit__ring--inner" />
        <span class="brand-orbit__line brand-orbit__line--one" />
        <span class="brand-orbit__line brand-orbit__line--two" />
        <span class="brand-orbit__node brand-orbit__node--one" />
        <span class="brand-orbit__node brand-orbit__node--two" />
        <span class="brand-orbit__node brand-orbit__node--three" />
        <span class="brand-orbit__node brand-orbit__node--four" />
      </div>

      <div class="auth-context">
        <p class="auth-context__eyebrow">한곳에서 이어가는 지원 준비</p>
        <h2 class="auth-context__title">
          흩어진 경험을 모아
          <span>나답게 지원해요.</span>
        </h2>
        <p class="auth-context__description">
          프로필부터 이력서, 관심 공고까지 한곳에 정리하고 필요한 준비를 이어갈 수 있어요.
        </p>
        <ol class="auth-context__steps">
          <li>
            <span>01</span>
            <strong>경험을 정리해요</strong>
          </li>
          <li>
            <span>02</span>
            <strong>공고를 모아 봐요</strong>
          </li>
          <li>
            <span>03</span>
            <strong>준비 과정을 확인해요</strong>
          </li>
        </ol>
      </div>

      <p class="auth-context__footnote">
        AI는 사용자가 요청한 준비 과정에서만 활용해요. 중요한 선택과 경력 정보의 주인은 사용자예요.
      </p>
    </section>
  </main>
</template>

<style scoped>
.public-shell {
  position: relative;
  display: grid;
  min-height: 100dvh;
  grid-template-columns: minmax(0, 56rem) minmax(27rem, 35rem);
  justify-content: center;
  gap: clamp(2rem, 4vw, 4rem);
  overflow: hidden;
  background: #11182d;
  padding: clamp(1.5rem, 4vw, 4rem);
}

.brand-canvas {
  position: relative;
  display: flex;
  grid-column: 1;
  grid-row: 1;
  min-width: 0;
  min-height: calc(100dvh - clamp(3rem, 8vw, 8rem));
  flex-direction: column;
  color: white;
  isolation: isolate;
}

.auth-brand {
  position: relative;
  z-index: 2;
  width: max-content;
  text-decoration: none;
}

.auth-brand--mobile {
  display: none;
}

.auth-context {
  position: relative;
  z-index: 2;
  max-width: 47rem;
  margin-block: auto;
  padding-block: clamp(4rem, 11vh, 8rem);
}

.auth-context__eyebrow {
  margin: 0 0 1rem;
  color: var(--hs-blue-300);
  font-size: 0.8125rem;
  font-weight: 780;
  letter-spacing: 0.04em;
}

.auth-context__title {
  max-width: 43rem;
  margin: 0;
  color: white;
  font-size: clamp(3rem, 5vw, 5.25rem);
  font-weight: 830;
  line-height: 0.99;
  letter-spacing: -0.075em;
  word-break: keep-all;
}

.auth-context__title span {
  display: block;
  margin-top: 0.15em;
  color: var(--hs-blue-300);
}

.auth-context__description {
  max-width: 36rem;
  margin: 1.75rem 0 0;
  color: #c8d2ed;
  font-size: clamp(0.9375rem, 1.4vw, 1.125rem);
  line-height: 1.75;
  word-break: keep-all;
}

.auth-context__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 2.5rem 0 0;
  padding: 0;
  list-style: none;
}

.auth-context__steps li {
  position: relative;
  display: grid;
  min-width: 10rem;
  gap: 0.25rem;
  border-top: 1px solid rgb(255 255 255 / 28%);
  padding: 0.875rem 1.5rem 0 0;
}

.auth-context__steps li:not(:last-child)::after {
  position: absolute;
  top: -0.25rem;
  right: 0.9rem;
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: var(--hs-blue-400);
  content: '';
}

.auth-context__steps span {
  color: #7e91c4;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
}

.auth-context__steps strong {
  color: #e7ecfa;
  font-size: 0.8125rem;
  font-weight: 650;
}

.auth-context__footnote {
  position: relative;
  z-index: 2;
  margin: 0;
  color: #8897bc;
  font-size: 0.75rem;
  line-height: 1.65;
}

.brand-orbit {
  position: absolute;
  top: 50%;
  left: 58%;
  z-index: 1;
  width: min(34vw, 30rem);
  aspect-ratio: 1;
  opacity: 0.32;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.brand-orbit__ring,
.brand-orbit__line,
.brand-orbit__node {
  position: absolute;
}

.brand-orbit__ring {
  border: 1px solid #5571bd;
  border-radius: 50%;
}

.brand-orbit__ring--outer {
  inset: 0;
  animation: orbit-turn 28s linear infinite;
}

.brand-orbit__ring--inner {
  inset: 24%;
  border-style: dashed;
  animation: orbit-turn 22s linear infinite reverse;
}

.brand-orbit__line {
  top: 50%;
  left: 10%;
  width: 80%;
  height: 1px;
  background: #4965ad;
  transform-origin: center;
}

.brand-orbit__line--one {
  transform: rotate(24deg);
}

.brand-orbit__line--two {
  transform: rotate(-38deg);
}

.brand-orbit__node {
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid #11182d;
  border-radius: 50%;
  background: var(--hs-blue-300);
  box-shadow: 0 0 0 1px var(--hs-blue-300);
  animation: node-drift 20s ease-in-out infinite;
}

.brand-orbit__node--one {
  top: 8%;
  left: 48%;
}

.brand-orbit__node--two {
  top: 48%;
  right: 4%;
  width: 1rem;
  height: 1rem;
  background: var(--hs-blue-400);
  box-shadow: 0 0 0 1px var(--hs-blue-400);
  animation-delay: -5s;
}

.brand-orbit__node--three {
  bottom: 12%;
  left: 25%;
  animation-delay: -10s;
}

.brand-orbit__node--four {
  top: 36%;
  left: 21%;
  width: 0.5rem;
  height: 0.5rem;
  background: white;
  box-shadow: 0 0 0 1px white;
  animation-delay: -14s;
}

.auth-panel {
  position: relative;
  z-index: 3;
  align-self: center;
  grid-column: 2;
  grid-row: 1;
  width: 100%;
  max-height: calc(100dvh - 3rem);
  overflow-y: auto;
  border: 1px solid rgb(255 255 255 / 55%);
  border-radius: 1rem;
  background: var(--color-surface);
  box-shadow: 0 28px 70px rgb(2 7 26 / 36%);
  padding: clamp(2rem, 4vw, 3.75rem);
}

.auth-form {
  width: 100%;
  max-width: 27rem;
  margin-inline: auto;
}

.auth-privacy-note {
  width: 100%;
  max-width: 27rem;
  margin: 2rem auto 0;
  border-top: 1px solid var(--color-border);
  color: var(--color-muted);
  padding-top: 1rem;
  font-size: 0.75rem;
  line-height: 1.65;
}

@keyframes orbit-turn {
  to {
    transform: rotate(360deg);
  }
}

@keyframes node-drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0.5rem, -0.75rem, 0);
  }
}

@media (max-width: 1099px) {
  .public-shell {
    grid-template-columns: minmax(0, 1fr) minmax(25rem, 31rem);
    gap: 2rem;
  }

  .auth-context__title {
    font-size: clamp(3rem, 5.8vw, 4.75rem);
  }

  .auth-context__steps {
    display: grid;
    max-width: 24rem;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .auth-context__steps li {
    min-width: 0;
    padding-right: 0.75rem;
  }
}

@media (max-width: 767px) {
  .public-shell {
    display: flex;
    min-height: 100dvh;
    flex-direction: column;
    background: var(--color-canvas);
    padding: 0;
  }

  .auth-panel {
    max-height: none;
    min-height: auto;
    align-self: stretch;
    border: 0;
    border-radius: 0;
    box-shadow: none;
    padding: 1.25rem clamp(1.25rem, 7vw, 2.5rem) 2.5rem;
  }

  .auth-brand--mobile {
    display: block;
    margin-bottom: 2.5rem;
  }

  .brand-canvas {
    min-height: 24rem;
    background: #11182d;
    padding: 2.5rem clamp(1.25rem, 7vw, 2.5rem);
  }

  .brand-canvas > .auth-brand,
  .auth-context__steps {
    display: none;
  }

  .auth-context {
    margin: auto 0;
    padding: 1rem 0;
  }

  .auth-context__title {
    max-width: 22rem;
    font-size: clamp(2.25rem, 12vw, 3.5rem);
  }

  .auth-context__description {
    margin-top: 1.25rem;
    font-size: 0.875rem;
  }

  .auth-context__footnote {
    margin-top: 2rem;
  }

  .brand-orbit {
    top: 56%;
    left: 75%;
    width: 23rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-orbit__ring,
  .brand-orbit__node {
    animation: none;
  }
}
</style>

```

## JobDetailLayout

- Path: `frontend/src/layouts/JobDetailLayout.vue`
- Description: 개별 공고의 회사·직무 컨텍스트, 분석/자기소개서/면접 탭과 중첩 RouterView를 제공하는 레이아웃입니다.

```vue
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import {
  JOB_STATUS_LABELS,
  formatJobInstant,
  jobCompanyLabel,
  jobDisplayTitle,
} from '@/features/jobs/presentation'
import { useJobDetailQuery } from '@/features/jobs/queries'
import AppIcon from '@/shared/ui/AppIcon.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const userId = computed(() => authStore.currentUser?.id ?? '')
const jobId = computed(() => String(route.params.jobId ?? ''))
const job = useJobDetailQuery(userId, jobId)
const titleElement = ref<HTMLElement | null>(null)
const titleOverflow = ref(0)
let titleResizeObserver: ResizeObserver | null = null
const titleStyle = computed(() => ({ '--job-title-shift': `${titleOverflow.value}px` }))
const activeTab = computed(() => {
  if (route.name === 'job-analysis') return 'analysis'
  if (route.name === 'job-cover-letter') return 'cover-letter'
  if (route.name === 'job-interview') return 'interview'
  return 'overview'
})
const analysisLabel = computed(() => {
  const value = job.data.value
  if (!value) return ''
  if (value.latestAnalysis) return value.analysisOutdated ? '분석 업데이트 필요' : '분석 완료'
  return {
    WAITING_FOR_CONTENT: '본문 확인 필요',
    NOT_REQUESTED: '분석 준비',
    PENDING: '자동 분석 준비 중',
    LAUNCHED: '자동 분석 중',
    BLOCKED: '분석 확인 필요',
    SUPERSEDED: '최신 내용 확인 중',
  }[value.automaticAnalysis.state]
})

function measureTitleOverflow(): void {
  const element = titleElement.value
  titleOverflow.value =
    element === null ? 0 : Math.max(0, element.scrollWidth - element.clientWidth)
}

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined') {
    titleResizeObserver = new ResizeObserver(measureTitleOverflow)
    if (titleElement.value !== null) titleResizeObserver.observe(titleElement.value)
  }
  void nextTick(measureTitleOverflow)
})

watch(
  () => job.data.value,
  async () => {
    await nextTick()
    if (titleElement.value !== null) titleResizeObserver?.observe(titleElement.value)
    measureTitleOverflow()
  },
)

onBeforeUnmount(() => titleResizeObserver?.disconnect())
</script>

<template>
  <section class="job-detail-shell">
    <RouterLink class="job-detail-back" :to="{ name: 'jobs' }">
      <AppIcon name="arrow-left" />
      공고 목록
    </RouterLink>
    <header v-if="job.data.value" class="job-resource-header">
      <div class="job-resource-header__main">
        <p>{{ jobCompanyLabel(job.data.value.companyName) }}</p>
        <h1
          ref="titleElement"
          class="job-resource-title"
          :class="{ 'job-resource-title--overflowing': titleOverflow > 0 }"
          :style="titleStyle"
          :title="jobDisplayTitle(job.data.value)"
          tabindex="0"
        >
          <span>{{ jobDisplayTitle(job.data.value) }}</span>
        </h1>
        <div class="job-resource-header__meta">
          <span v-if="job.data.value.positionName">{{ job.data.value.positionName }}</span>
          <span v-if="job.data.value.location">{{ job.data.value.location }}</span>
          <span v-if="job.data.value.employmentType">{{ job.data.value.employmentType }}</span>
          <span v-if="job.data.value.deadlineAt"
            >마감 {{ formatJobInstant(job.data.value.deadlineAt) }}</span
          >
        </div>
      </div>
      <div class="job-resource-header__aside">
        <div class="job-resource-header__badges">
          <StatusBadge :label="JOB_STATUS_LABELS[job.data.value.status]" tone="brand" />
          <StatusBadge
            :label="analysisLabel"
            :tone="job.data.value.latestAnalysis ? 'success' : 'info'"
          />
        </div>
        <a
          :href="job.data.value.sourceUrl"
          class="job-resource-header__source"
          target="_blank"
          rel="noopener noreferrer"
        >
          원본 공고 보기
          <AppIcon name="arrow-right" />
        </a>
      </div>
    </header>
    <nav class="job-detail-tabs" aria-label="공고 상세 탭">
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'overview' }"
        :to="{ name: 'job-overview', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'overview' ? 'page' : undefined"
      >
        공고 정보
      </RouterLink>
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'analysis' }"
        :to="{ name: 'job-analysis', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'analysis' ? 'page' : undefined"
      >
        공고 분석
      </RouterLink>
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'cover-letter' }"
        :to="{ name: 'job-cover-letter', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'cover-letter' ? 'page' : undefined"
      >
        자기소개서
      </RouterLink>
      <RouterLink
        class="job-detail-tab"
        :class="{ 'job-detail-tab--active': activeTab === 'interview' }"
        :to="{ name: 'job-interview', params: { jobId: route.params.jobId } }"
        :aria-current="activeTab === 'interview' ? 'page' : undefined"
      >
        면접 준비
      </RouterLink>
    </nav>
    <div class="job-detail-body"><RouterView /></div>
  </section>
</template>

<style scoped>
.job-detail-shell {
  min-width: 0;
}

.job-detail-back {
  display: inline-flex;
  min-height: 2.25rem;
  align-items: center;
  gap: 0.375rem;
  color: var(--color-muted-strong);
  font-size: 0.8125rem;
  font-weight: 680;
  text-decoration: none;
}

.job-resource-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-7);
  padding: var(--space-5) 0 var(--space-6);
}

.job-resource-header__main {
  min-width: 0;
  flex: 1 1 auto;
}

.job-resource-header__main > p {
  color: var(--color-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 750;
}

.job-resource-header h1 {
  width: 100%;
  max-width: 58rem;
  margin-top: var(--space-2);
  overflow-x: auto;
  overflow-y: hidden;
  font-size: clamp(1.4rem, 2.8vw, 2.2rem);
  font-weight: 790;
  letter-spacing: -0.035em;
  line-height: 1.17;
  overflow-wrap: normal;
  scrollbar-width: none;
  white-space: nowrap;
}

.job-resource-header h1::-webkit-scrollbar {
  display: none;
}

.job-resource-title > span {
  display: inline-block;
  transform: translateX(0);
  transition: transform 4s ease-in-out 180ms;
}

.job-resource-title--overflowing:hover > span,
.job-resource-title--overflowing:focus-visible > span {
  transform: translateX(calc(var(--job-title-shift) * -1));
}

.job-resource-header__meta,
.job-resource-header__badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.job-resource-header__meta {
  margin-top: var(--space-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.job-resource-header__meta span + span::before {
  margin-right: var(--space-2);
  color: var(--color-border-strong);
  content: '·';
}

.job-resource-header__aside {
  display: grid;
  flex: 0 0 auto;
  justify-items: end;
  gap: var(--space-3);
}

.job-resource-header__source {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 680;
  text-decoration: none;
}

.job-resource-header__source:hover {
  color: var(--color-brand-strong);
}

.job-detail-back:hover {
  color: var(--color-brand);
}

.job-detail-tabs {
  position: sticky;
  z-index: 20;
  top: var(--global-header-height);
  display: flex;
  gap: 0.25rem;
  margin-inline: calc(var(--space-2) * -1);
  overflow-x: auto;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-canvas) 94%, transparent);
  padding-inline: var(--space-2);
  scrollbar-width: none;
  backdrop-filter: blur(14px);
}

.job-detail-tab {
  min-height: 3.25rem;
  flex: 0 0 auto;
  border-bottom: 2px solid transparent;
  color: var(--color-text-secondary);
  padding: 0.875rem 1rem;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}

.job-detail-tab:hover {
  background: var(--color-surface-subtle);
  color: var(--color-brand-strong);
}

.job-detail-tab--active {
  border-bottom-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
  font-weight: 780;
}

.job-detail-tab--active:hover,
.job-detail-tab--active:focus-visible {
  border-bottom-color: var(--color-brand);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.job-detail-body {
  min-width: 0;
  padding-top: var(--layout-tabs-body-gap);
}

@media (max-width: 48rem) {
  .job-resource-header {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-4);
  }

  .job-resource-header__aside {
    width: 100%;
    justify-items: start;
  }

  .job-resource-header__main,
  .job-resource-header h1 {
    width: 100%;
    max-width: 100%;
  }

  .job-detail-tabs {
    top: var(--global-header-height);
    margin-inline: calc(var(--space-4) * -1);
    padding-inline: var(--space-4);
  }

  .job-detail-tab {
    padding-inline: var(--space-3);
  }
}

@media (prefers-reduced-motion: reduce) {
  .job-resource-title > span {
    transition: none;
  }

  .job-resource-title--overflowing:hover > span,
  .job-resource-title--overflowing:focus-visible > span {
    transform: none;
  }
}

@media (max-width: 35rem) {
  .job-detail-tabs {
    margin-inline: calc(var(--space-3) * -1);
    padding-inline: var(--space-3);
  }
}
</style>

```


