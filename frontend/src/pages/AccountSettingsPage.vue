<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { validateDisplayNameForm } from '@/features/auth/formValidation'
import { changeAccountPassword, deleteAccount } from '@/shared/api/accountApi'
import { passwordChangeRequestSchema } from '@/shared/api/accountContracts'
import { fieldErrorsToRecord, normalizeApiError } from '@/shared/api/errors'
import PageHeader from '@/shared/ui/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const displayName = ref(authStore.currentUser?.displayName ?? '')
const displayNameError = ref('')
const displayNameMessage = ref('')
const savingDisplayName = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', newPasswordConfirm: '' })
const passwordErrors = ref<Record<string, string>>({})
const passwordMessage = ref('')
const changingPassword = ref(false)
const logoutError = ref('')
const loggingOut = ref(false)
const deletionDialogOpen = ref(false)
const deletePassword = ref('')
const permanentDeletionConfirmed = ref(false)
const deletionError = ref('')
const deleting = ref(false)
const deletionPasswordInput = ref<HTMLInputElement | null>(null)
const deletionDialog = ref<HTMLElement | null>(null)
let dialogTrigger: HTMLElement | null = null

const currentDisplayName = computed(() => authStore.currentUser?.displayName ?? '')

async function saveDisplayName(): Promise<void> {
  displayNameError.value = ''
  displayNameMessage.value = ''
  const validation = validateDisplayNameForm({ displayName: displayName.value })
  if (validation.data === null) {
    displayNameError.value = validation.fieldErrors.displayName ?? '표시 이름을 확인해 주세요.'
    return
  }
  savingDisplayName.value = true
  try {
    await authStore.updateDisplayName(validation.data)
    displayName.value = authStore.currentUser?.displayName ?? validation.data.displayName
    displayNameMessage.value = '표시 이름을 변경했어요.'
  } catch (error) {
    const normalized = normalizeApiError(error)
    displayNameError.value =
      fieldErrorsToRecord(normalized.fieldErrors).displayName ?? '표시 이름을 변경하지 못했어요.'
  } finally {
    savingDisplayName.value = false
  }
}

async function changePassword(): Promise<void> {
  passwordErrors.value = {}
  passwordMessage.value = ''
  const candidate = {
    currentPassword: passwordForm.currentPassword,
    newPassword: passwordForm.newPassword,
  }
  const parsed = passwordChangeRequestSchema.safeParse(candidate)
  if (!parsed.success) {
    for (const issue of parsed.error.issues) {
      const field = String(issue.path[0] ?? 'newPassword')
      passwordErrors.value[field] ??= issue.message
    }
  }
  if (passwordForm.newPassword !== passwordForm.newPasswordConfirm) {
    passwordErrors.value.newPasswordConfirm = '새 비밀번호가 서로 달라요.'
  }
  if (Object.keys(passwordErrors.value).length > 0 || !parsed.success) return

  changingPassword.value = true
  try {
    await changeAccountPassword(parsed.data)
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.newPasswordConfirm = ''
    passwordMessage.value = '비밀번호를 변경하고 다른 모든 세션을 종료했어요.'
  } catch (error) {
    const normalized = normalizeApiError(error)
    const fields = fieldErrorsToRecord(normalized.fieldErrors)
    passwordErrors.value = fields
    if (Object.keys(fields).length === 0) {
      passwordErrors.value.currentPassword =
        normalized.code === 'PASSWORD_REUSE_NOT_ALLOWED'
          ? '현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.'
          : '현재 비밀번호를 확인해 주세요.'
    }
  } finally {
    changingPassword.value = false
  }
}

async function logout(): Promise<void> {
  logoutError.value = ''
  loggingOut.value = true
  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = normalizeApiError(error).message
  } finally {
    loggingOut.value = false
  }
}

async function openDeletionDialog(event: Event): Promise<void> {
  dialogTrigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  deletionDialogOpen.value = true
  deletePassword.value = ''
  permanentDeletionConfirmed.value = false
  deletionError.value = ''
  await nextTick()
  deletionPasswordInput.value?.focus()
}

async function closeDeletionDialog(): Promise<void> {
  if (deleting.value) return
  deletionDialogOpen.value = false
  await nextTick()
  dialogTrigger?.focus()
}

async function submitDeletion(): Promise<void> {
  deletionError.value = ''
  if (deletePassword.value === '' || !permanentDeletionConfirmed.value) {
    deletionError.value = '현재 비밀번호와 영구 삭제 확인을 모두 완료해 주세요.'
    return
  }
  deleting.value = true
  try {
    const accepted = await deleteAccount({
      currentPassword: deletePassword.value,
      permanentDeletionConfirmed: true,
    })
    await authStore.completeAccountDeletion()
    deletionDialogOpen.value = false
    await router.replace({
      name: 'login',
      state: { accountDeletionAccepted: true, purgeBy: accepted.purgeBy },
    })
  } catch (error) {
    const normalized = normalizeApiError(error)
    deletionError.value =
      normalized.code === 'INVALID_CREDENTIALS'
        ? '현재 비밀번호가 올바르지 않아요.'
        : '회원 탈퇴를 접수하지 못했어요. 다시 시도해 주세요.'
  } finally {
    deleting.value = false
  }
}

function handleDialogKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    void closeDeletionDialog()
    return
  }
  if (event.key === 'Tab' && deletionDialog.value !== null) {
    const focusable = [
      ...deletionDialog.value.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      ),
    ]
    const first = focusable[0]
    const last = focusable.at(-1)
    if (first === undefined || last === undefined) return
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

onBeforeUnmount(() => {
  dialogTrigger = null
})
</script>

<template>
  <section class="settings-page app-page" aria-labelledby="settings-heading">
    <PageHeader
      title="계정 관리"
      description="표시 이름과 비밀번호를 관리하거나 계정을 안전하게 삭제할 수 있어요."
    />

    <section class="settings-card section-surface" aria-labelledby="settings-profile-heading">
      <div>
        <p class="section-kicker">내 계정</p>
        <h2 id="settings-profile-heading" class="section-title">표시 이름</h2>
        <p class="settings-card__meta">
          {{ currentDisplayName }} · {{ authStore.currentUser?.email }}
        </p>
      </div>
      <form class="settings-form" novalidate @submit.prevent="saveDisplayName">
        <label class="field" for="settings-display-name">
          <span class="field__label">표시 이름</span>
          <input
            id="settings-display-name"
            v-model="displayName"
            class="control"
            autocomplete="nickname"
            maxlength="100"
            :aria-invalid="Boolean(displayNameError)"
          />
        </label>
        <p v-if="displayNameError" class="inline-error" role="alert">{{ displayNameError }}</p>
        <p v-if="displayNameMessage" class="alert alert--success" role="status">
          {{ displayNameMessage }}
        </p>
        <button type="submit" class="button button--secondary" :disabled="savingDisplayName">
          {{ savingDisplayName ? '저장 중…' : '표시 이름 저장' }}
        </button>
      </form>
    </section>

    <section class="settings-card section-surface" aria-labelledby="settings-password-heading">
      <div>
        <p class="section-kicker">보안</p>
        <h2 id="settings-password-heading" class="section-title">비밀번호 변경</h2>
        <p class="settings-card__meta">변경하면 현재 브라우저를 제외한 모든 세션이 종료됩니다.</p>
      </div>
      <form class="settings-form" novalidate @submit.prevent="changePassword">
        <label class="field" for="settings-current-password">
          <span class="field__label">현재 비밀번호</span>
          <input
            id="settings-current-password"
            v-model="passwordForm.currentPassword"
            class="control"
            type="password"
            autocomplete="current-password"
          />
          <span v-if="passwordErrors.currentPassword" class="inline-error">{{
            passwordErrors.currentPassword
          }}</span>
        </label>
        <label class="field" for="settings-new-password">
          <span class="field__label">새 비밀번호</span>
          <input
            id="settings-new-password"
            v-model="passwordForm.newPassword"
            class="control"
            type="password"
            autocomplete="new-password"
          />
          <span v-if="passwordErrors.newPassword" class="inline-error">{{
            passwordErrors.newPassword
          }}</span>
        </label>
        <label class="field" for="settings-new-password-confirm">
          <span class="field__label">새 비밀번호 확인</span>
          <input
            id="settings-new-password-confirm"
            v-model="passwordForm.newPasswordConfirm"
            class="control"
            type="password"
            autocomplete="new-password"
          />
          <span v-if="passwordErrors.newPasswordConfirm" class="inline-error">{{
            passwordErrors.newPasswordConfirm
          }}</span>
        </label>
        <p v-if="passwordMessage" class="alert alert--success" role="status">
          {{ passwordMessage }}
        </p>
        <button type="submit" class="button button--primary" :disabled="changingPassword">
          {{ changingPassword ? '변경 중…' : '비밀번호 변경' }}
        </button>
      </form>
    </section>

    <section class="settings-card section-surface" aria-labelledby="settings-session-heading">
      <div>
        <p class="section-kicker">Session</p>
        <h2 id="settings-session-heading" class="section-title">로그아웃</h2>
      </div>
      <p v-if="logoutError" class="alert alert--danger" role="alert">{{ logoutError }}</p>
      <button type="button" class="button button--secondary" :disabled="loggingOut" @click="logout">
        {{ loggingOut ? '로그아웃 중…' : '로그아웃' }}
      </button>
    </section>

    <section
      class="settings-card settings-card--danger section-surface"
      aria-labelledby="settings-delete-heading"
    >
      <div>
        <p class="section-kicker">Danger zone</p>
        <h2 id="settings-delete-heading" class="section-title">회원 탈퇴</h2>
        <p class="settings-card__meta">
          모든 세션을 종료하고 GitHub App을 uninstall하며 private snapshot과 Career Artifact 파일을
          정리합니다. 삭제 목표 시간은 접수 후 24시간 이내입니다.
        </p>
      </div>
      <button type="button" class="button button--danger" @click="openDeletionDialog">
        회원 탈퇴
      </button>
    </section>

    <Teleport to="body">
      <div v-if="deletionDialogOpen" class="deletion-dialog-layer" @keydown="handleDialogKeydown">
        <button
          type="button"
          class="deletion-dialog-overlay"
          aria-label="회원 탈퇴 창 닫기"
          :disabled="deleting"
          @click="closeDeletionDialog"
        />
        <section
          ref="deletionDialog"
          class="deletion-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="deletion-dialog-title"
          aria-describedby="deletion-dialog-description"
        >
          <h2 id="deletion-dialog-title">계정을 영구 삭제할까요?</h2>
          <p id="deletion-dialog-description">
            이 작업은 되돌릴 수 없습니다. 모든 세션 종료, GitHub App uninstall, private snapshot 및
            Resume·Portfolio 파일 삭제를 접수하며 24시간 안에 terminal purge를 목표로 합니다.
          </p>
          <form class="settings-form" novalidate @submit.prevent="submitDeletion">
            <label class="field" for="delete-current-password">
              <span class="field__label">현재 비밀번호</span>
              <input
                id="delete-current-password"
                ref="deletionPasswordInput"
                v-model="deletePassword"
                class="control"
                type="password"
                autocomplete="current-password"
              />
            </label>
            <label class="deletion-dialog__confirmation">
              <input
                v-model="permanentDeletionConfirmed"
                class="checkbox-control"
                type="checkbox"
              />
              <span>삭제 범위와 복구 불가 안내를 이해했으며 계정 영구 삭제를 다시 확인합니다.</span>
            </label>
            <p v-if="deletionError" class="alert alert--danger" role="alert">{{ deletionError }}</p>
            <div class="deletion-dialog__actions">
              <button
                type="button"
                class="button button--secondary"
                :disabled="deleting"
                @click="closeDeletionDialog"
              >
                취소
              </button>
              <button type="submit" class="button button--danger" :disabled="deleting">
                {{ deleting ? '삭제 접수 중…' : '계정 영구 삭제' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.settings-page,
.settings-card,
.settings-form {
  display: grid;
  gap: var(--space-5);
}

.settings-card {
  margin-top: var(--space-5);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.settings-card__meta {
  margin-top: var(--space-2);
  color: var(--color-muted-strong);
}

.settings-card .button {
  justify-self: start;
}

.settings-card--danger {
  border-color: color-mix(in srgb, var(--color-danger) 32%, var(--color-border));
}

.deletion-dialog-layer {
  position: fixed;
  z-index: 100;
  inset: 0;
}

.deletion-dialog-overlay {
  position: absolute;
  border: 0;
  background: rgb(16 24 40 / 58%);
  inset: 0;
}

.deletion-dialog {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(35rem, calc(100% - 2rem));
  max-height: calc(100dvh - 2rem);
  overflow-y: auto;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
  padding: clamp(var(--space-5), 4vw, var(--space-7));
  transform: translate(-50%, -50%);
}

.deletion-dialog > p {
  margin-block: var(--space-3) var(--space-5);
  color: var(--color-muted-strong);
  line-height: 1.65;
}

.deletion-dialog__confirmation {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.deletion-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

@media (max-width: 40rem) {
  .deletion-dialog {
    top: auto;
    right: 0;
    bottom: 0;
    left: 0;
    width: auto;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    transform: none;
  }

  .deletion-dialog__actions .button {
    flex: 1 1 0;
  }
}
</style>
