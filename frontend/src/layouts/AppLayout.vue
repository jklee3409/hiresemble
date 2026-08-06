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
              <span class="account-trigger__avatar" aria-hidden="true">
                <AppIcon name="profile" />
              </span>
              <span>{{ authStore.currentUser?.displayName || '내 계정' }}</span>
              <span class="account-trigger__chevron" aria-hidden="true">⌄</span>
            </button>

            <div
              v-if="accountMenuOpen"
              id="account-menu"
              ref="accountMenu"
              class="account-menu menu-panel"
              role="menu"
              aria-label="내 계정"
            >
              <div class="account-menu__identity">
                <span class="account-menu__avatar" aria-hidden="true">
                  <AppIcon name="profile" />
                </span>
                <span>
                  <strong>{{ authStore.currentUser?.displayName }}</strong>
                  <small>{{ authStore.currentUser?.email }}</small>
                </span>
              </div>
              <RouterLink
                data-account-first
                role="menuitem"
                class="account-menu__feature"
                to="/guide"
              >
                <AppIcon name="sparkle" />
                <span>이용 가이드</span>
                <em>준비 순서</em>
              </RouterLink>
              <RouterLink role="menuitem" class="menu-panel__item" to="/profile/basic">
                <AppIcon name="profile" />
                내 정보
              </RouterLink>
              <RouterLink role="menuitem" class="menu-panel__item" to="/agent-runs">
                <AppIcon name="runs" />
                AI 작업
              </RouterLink>
              <button
                type="button"
                role="menuitem"
                class="menu-panel__item"
                @click="openNicknameModal"
              >
                <AppIcon name="pen" />
                닉네임 변경
              </button>
              <hr class="menu-panel__divider" aria-hidden="true" />
              <button
                type="button"
                role="menuitem"
                class="menu-panel__item menu-panel__item--danger"
                :disabled="isLoggingOut"
                @click="logout"
              >
                <AppIcon name="logout" />
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
  background: rgb(255 255 255 / 88%);
  box-shadow:
    0 1px 0 rgb(16 24 40 / 4%),
    0 10px 30px -22px rgb(16 24 40 / 30%);
  backdrop-filter: blur(20px) saturate(140%);
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

/* 상단 탐색은 알약 tab 묶음으로 읽힌다. 선택된 여정만 흰 알약으로 떠오른다. */
.desktop-navigation {
  display: none;
  min-width: 0;
  align-items: center;
  justify-self: center;
  gap: 0.125rem;
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  padding: 0.3125rem;
}

.desktop-navigation__link {
  position: relative;
  display: inline-flex;
  min-height: 2.375rem;
  align-items: center;
  border-radius: var(--radius-pill);
  color: var(--color-muted-strong);
  padding: 0.5rem clamp(0.6rem, 0.9vw, 1rem);
  font-size: 0.875rem;
  font-weight: 650;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast),
    box-shadow var(--motion-fast);
}

.desktop-navigation__link:hover {
  color: var(--color-ink);
}

.desktop-navigation__link--active {
  background: var(--color-surface);
  color: var(--color-brand-ink);
  box-shadow: var(--shadow-xs);
  font-weight: 750;
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
  border-radius: var(--radius-pill);
  background: var(--color-fill);
  color: var(--color-ink-soft);
  padding: 0.3125rem 0.875rem 0.3125rem 0.3125rem;
  font-size: 0.875rem;
  font-weight: 700;
  transition:
    background-color var(--motion-fast),
    box-shadow var(--motion-fast);
}

.account-trigger:hover,
.account-trigger[aria-expanded='true'] {
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  color: var(--color-ink);
}

.account-trigger__avatar {
  display: grid;
  width: 2.125rem;
  height: 2.125rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand);
  color: #ffffff;
}

.account-trigger__avatar :deep(.icon) {
  width: 1.0625rem;
  height: 1.0625rem;
}

.account-trigger__chevron {
  color: var(--color-muted);
  font-size: 1rem;
  line-height: 1;
}

.account-menu {
  position: absolute;
  top: calc(100% + 0.75rem);
  right: 0;
  width: 17.5rem;
}

.account-menu__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.375rem;
  padding: 0.5rem 0.625rem 0.875rem;
}

.account-menu__avatar {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--color-brand-soft);
  color: var(--color-brand);
}

.account-menu__identity > span {
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
  color: var(--color-ink-title);
  font-size: 0.9375rem;
  letter-spacing: -0.01em;
}

.account-menu__identity small {
  margin-top: 0.1rem;
  color: var(--color-muted);
  font-size: 0.75rem;
}

/* 계정 menu의 대표 진입점 한 개만 채워진 brand 행으로 강조한다. */
.account-menu__feature {
  display: flex;
  min-height: 2.875rem;
  align-items: center;
  gap: 0.625rem;
  margin-bottom: 0.25rem;
  border-radius: var(--radius-md);
  background: linear-gradient(120deg, var(--hs-blue-600), var(--hs-blue-400));
  box-shadow: var(--shadow-brand);
  color: #ffffff;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  font-weight: 750;
  text-decoration: none;
}

.account-menu__feature:hover {
  box-shadow: var(--shadow-brand-hover);
}

.account-menu__feature > span {
  min-width: 0;
  flex: 1 1 auto;
}

.account-menu__feature em {
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 22%);
  padding: 0.1875rem 0.5rem;
  font-size: 0.6875rem;
  font-style: normal;
  font-weight: 750;
}

.account-menu :deep(.icon) {
  width: 1.0625rem;
  height: 1.0625rem;
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

/* 떠 있는 알약 bar. 선택된 여정은 brand 채움면으로 분명히 드러난다. */
.mobile-bottom-navigation {
  position: fixed;
  right: max(0.75rem, env(safe-area-inset-right));
  bottom: max(0.75rem, env(safe-area-inset-bottom));
  left: max(0.75rem, env(safe-area-inset-left));
  z-index: 35;
  display: grid;
  min-height: 3.75rem;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-radius: var(--radius-xl);
  background: rgb(255 255 255 / 92%);
  box-shadow: var(--shadow-md);
  padding: 0.375rem 0.375rem;
  backdrop-filter: blur(20px) saturate(140%);
}

.mobile-bottom-navigation__item {
  display: grid;
  min-width: 0;
  min-height: 3rem;
  place-items: center;
  gap: 0.1875rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-muted);
  padding: 0.25rem 0.125rem;
  font-size: 0.6875rem;
  font-weight: 650;
  line-height: 1.2;
  text-decoration: none;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
}

.mobile-bottom-navigation__item .icon {
  width: 1.25rem;
  height: 1.25rem;
}

.mobile-bottom-navigation__item:hover {
  background: var(--color-fill);
  color: var(--color-ink);
}

.mobile-bottom-navigation__item--active,
.mobile-bottom-navigation__item--active:hover {
  background: var(--color-brand);
  color: #ffffff;
  box-shadow: var(--shadow-brand);
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
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 1.25rem 1.25rem max(1.25rem, env(safe-area-inset-bottom));
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
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  color: var(--color-ink-soft);
  padding: 0.75rem 0.875rem;
  text-decoration: none;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
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
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: 1.5rem;
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

  .product-header__actions :deep(.run-progress__trigger-label) {
    display: none;
  }

  /* 좁은 화면에서는 이름만 감추고 avatar와 chevron은 남긴다. */
  .account-trigger > span:not(.account-trigger__chevron, .account-trigger__avatar) {
    display: none;
  }

  .account-trigger {
    padding-right: 0.5rem;
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
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding-bottom: max(1.25rem, env(safe-area-inset-bottom));
    transform: none;
  }

  .nickname-modal__actions > .button {
    flex: 1 1 0;
  }
}
</style>
