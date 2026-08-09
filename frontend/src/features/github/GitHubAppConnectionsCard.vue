<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  useDisconnectGitHubAppConnectionMutation,
  useGitHubAppCapabilityQuery,
  useGitHubAppConnectionListQuery,
  useRefreshGitHubAppConnectionMutation,
} from '@/features/github/appConnectionQueries'
import {
  safeGitHubInstallationManageUrl,
  safeGitHubInstallationUrl,
} from '@/features/github/appNavigation'
import { useCreateGitHubSourceMutation } from '@/features/github/queries'
import { parsePublicGitHubUrl } from '@/features/github/presentation'
import { startGitHubAppConnection } from '@/shared/api/githubAppConnectionApi'
import type { GitHubAppConnectionDto } from '@/shared/api/githubAppConnectionContracts'
import { normalizeApiError } from '@/shared/api/errors'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'

const props = defineProps<{ userId: string }>()
const emit = defineEmits<{ sourceCreated: [sourceId: string] }>()

const route = useRoute()
const router = useRouter()
const notifications = useNotifications()
const enabled = computed(() => props.userId !== '')
const capability = useGitHubAppCapabilityQuery(() => props.userId, enabled)
const connections = useGitHubAppConnectionListQuery(() => props.userId, enabled)
const refreshMutation = useRefreshGitHubAppConnectionMutation(() => props.userId)
const disconnectMutation = useDisconnectGitHubAppConnectionMutation(() => props.userId)
const sourceMutation = useCreateGitHubSourceMutation(() => props.userId)
const externalAnchor = ref<HTMLAnchorElement | null>(null)
const externalHref = ref('')
const connecting = ref(false)
const feedback = ref('')
const errorMessage = ref('')
const callbackResult = ref<CallbackResult | null>(null)
const privateUrl = ref('')
const connectionId = ref('')
const participationConfirmed = ref(false)
const privateFormError = ref('')

const activeConnections = computed(
  () => connections.data.value?.items.filter((connection) => connection.status === 'ACTIVE') ?? [],
)
const available = computed(
  () => capability.data.value?.enabled === true && capability.data.value.configured === true,
)

watch(
  activeConnections,
  (items) => {
    if (!items.some((connection) => connection.id === connectionId.value)) {
      connectionId.value = items[0]?.id ?? ''
    }
  },
  { immediate: true },
)

onMounted(() => {
  const sensitiveKeys = [
    'githubAppResult',
    'state',
    'code',
    'installation_id',
    'setup_action',
    'error',
  ]
  const raw = route.query.githubAppResult
  callbackResult.value = callbackValue(typeof raw === 'string' ? raw : null)
  if (sensitiveKeys.some((key) => route.query[key] !== undefined)) {
    const query = { ...route.query }
    for (const key of sensitiveKeys) delete query[key]
    void router.replace({ query })
  }
})

async function connect(): Promise<void> {
  connecting.value = true
  feedback.value = ''
  errorMessage.value = ''
  try {
    const started = await startGitHubAppConnection()
    const safe = safeGitHubInstallationUrl(started.installationUrl)
    if (safe === null) throw new Error('unsafe GitHub installation URL')
    externalHref.value = safe
    await nextTick()
    externalAnchor.value?.click()
  } catch (error) {
    const normalized = normalizeApiError(error)
    errorMessage.value =
      normalized.code === 'GITHUB_APP_NOT_CONFIGURED'
        ? 'GitHub App 설정이 아직 완료되지 않았어요.'
        : 'GitHub App 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.'
  } finally {
    connecting.value = false
  }
}

async function refresh(connection: GitHubAppConnectionDto): Promise<void> {
  feedback.value = ''
  errorMessage.value = ''
  try {
    await refreshMutation.mutateAsync({
      connectionId: connection.id,
      version: connection.version,
    })
    feedback.value = 'GitHub App 권한과 저장소 범위를 다시 확인했어요.'
  } catch (error) {
    const normalized = normalizeApiError(error)
    errorMessage.value = connectionError(normalized.code)
  }
}

async function disconnect(connection: GitHubAppConnectionDto): Promise<void> {
  const confirmed = await notifications.confirm({
    title: `${connection.targetAccountLogin} GitHub App 연결을 해제할까요?`,
    message:
      'GitHub App을 uninstall하고 새 private 저장소 refresh를 즉시 중단합니다. private snapshot과 raw evidence는 삭제하지만, 이미 승인한 경험과 생성된 Resume·Portfolio version은 유지합니다.',
    confirmLabel: '연결 해제 및 uninstall',
    tone: 'danger',
  })
  if (!confirmed) return
  feedback.value = ''
  errorMessage.value = ''
  try {
    await disconnectMutation.mutateAsync({
      connectionId: connection.id,
      version: connection.version,
    })
    feedback.value = '권한 해제와 private snapshot 정리를 접수했어요.'
  } catch (error) {
    errorMessage.value = connectionError(normalizeApiError(error).code)
  }
}

async function registerPrivateSource(): Promise<void> {
  privateFormError.value = ''
  const parsed = parsePublicGitHubUrl(privateUrl.value)
  if (parsed === null) {
    privateFormError.value = 'https://github.com/계정 또는 계정/저장소 주소를 입력해 주세요.'
    return
  }
  if (connectionId.value === '' || !participationConfirmed.value) {
    privateFormError.value = 'ACTIVE 연결을 고르고 직접 참여한 저장소임을 확인해 주세요.'
    return
  }
  try {
    const accepted = await sourceMutation.mutateAsync({
      url: parsed.canonicalUrl,
      participationConfirmed: true,
      accessMode: 'GITHUB_APP',
      connectionId: connectionId.value,
    })
    privateUrl.value = ''
    participationConfirmed.value = false
    feedback.value = 'private 저장소 목록을 확인하고 있어요.'
    emit('sourceCreated', accepted.resourceId!)
  } catch (error) {
    errorMessage.value = connectionError(normalizeApiError(error).code)
  }
}

function callbackValue(value: string | null): CallbackResult | null {
  return ['connected', 'cancelled', 'expired', 'permission', 'unavailable'].includes(value ?? '')
    ? (value as CallbackResult)
    : null
}

function callbackMessage(value: CallbackResult): string {
  return {
    connected: 'GitHub App 연결을 확인했어요.',
    cancelled: 'GitHub App 연결이 취소됐어요.',
    expired: '연결 요청이 만료됐어요. 다시 시작해 주세요.',
    permission: 'Metadata read와 Contents read 권한을 확인해 주세요.',
    unavailable: 'GitHub App 연결을 확인하지 못했어요.',
  }[value]
}

function connectionError(code: string): string {
  if (code === 'GITHUB_APP_PERMISSION_MISMATCH')
    return 'Metadata read와 Contents read 권한이 필요해요.'
  if (code === 'GITHUB_INSTALLATION_SUSPENDED') return 'GitHub installation이 일시 중지됐어요.'
  if (code === 'GITHUB_INSTALLATION_REVOKED') return 'GitHub installation 권한이 해제됐어요.'
  return 'GitHub App 연결 상태를 확인하지 못했어요.'
}

function statusTone(status: GitHubAppConnectionDto['status']) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'SUSPENDED' || status === 'DISCONNECTING') return 'warning' as const
  return 'danger' as const
}

function formatInstant(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

type CallbackResult = 'connected' | 'cancelled' | 'expired' | 'permission' | 'unavailable'
</script>

<template>
  <section class="github-app-card section-surface" aria-labelledby="github-app-heading">
    <div class="section-header github-app-card__heading">
      <div>
        <p class="section-kicker">Private repository</p>
        <h2 id="github-app-heading" class="section-title">GitHub App 연결</h2>
      </div>
      <button
        v-if="available"
        type="button"
        class="button button--primary"
        :disabled="connecting"
        @click="connect"
      >
        {{ connecting ? '연결 준비 중…' : 'GitHub App 연결' }}
      </button>
    </div>

    <p>
      GitHub로 이동해 <strong>Metadata read</strong>와 <strong>Contents read</strong>만 허용합니다.
      GitHub에서 고른 저장소 중 Hiresemble에서 다시 선택한 1~10개만 읽습니다.
    </p>
    <p class="github-app-card__notice">
      PAT나 token을 붙여 넣지 마세요. 연결 과정의 code와 state는 브라우저 저장소에 보관하지
      않습니다.
    </p>

    <a
      v-if="externalHref"
      ref="externalAnchor"
      class="sr-only"
      :href="externalHref"
      rel="noopener noreferrer"
      >GitHub 설치 화면으로 이동</a
    >

    <p
      v-if="callbackResult"
      class="alert"
      :class="callbackResult === 'connected' ? 'alert--success' : 'alert--warning'"
      role="status"
    >
      {{ callbackMessage(callbackResult) }}
    </p>
    <p v-if="feedback" class="alert alert--success" role="status">{{ feedback }}</p>
    <p v-if="errorMessage" class="alert alert--danger" role="alert">{{ errorMessage }}</p>

    <StatePanel
      v-if="capability.isPending.value"
      kind="loading"
      title="GitHub App 제공 상태를 확인하는 중…"
    />
    <StatePanel
      v-else-if="capability.isError.value || !available"
      kind="error"
      title="Private GitHub 연결을 지금 사용할 수 없어요."
      description="Backend capability와 GitHub App 환경 변수 설정을 확인해 주세요. 공개 GitHub 흐름은 계속 사용할 수 있습니다."
    />

    <template v-else>
      <StatePanel
        v-if="connections.isPending.value"
        kind="loading"
        title="연결된 installation을 불러오는 중…"
      />
      <StatePanel
        v-else-if="connections.isError.value"
        kind="error"
        title="GitHub App 연결 목록을 불러오지 못했어요."
      />
      <ul v-else-if="connections.data.value?.items.length" class="github-app-list">
        <li v-for="connection in connections.data.value.items" :key="connection.id">
          <div class="github-app-list__summary">
            <div>
              <strong>{{ connection.targetAccountLogin }}</strong>
              <span>{{
                connection.targetAccountType === 'ORGANIZATION' ? 'Organization' : 'Personal'
              }}</span>
            </div>
            <StatusBadge :label="connection.status" :tone="statusTone(connection.status)" />
          </div>
          <dl>
            <div>
              <dt>GitHub repository 설정</dt>
              <dd>
                {{
                  connection.repositorySelection === 'ALL'
                    ? 'All repositories'
                    : 'Selected repositories'
                }}
              </dd>
            </div>
            <div>
              <dt>마지막 확인</dt>
              <dd>{{ formatInstant(connection.lastCheckedAt) }}</dd>
            </div>
          </dl>
          <div class="github-app-list__actions">
            <a
              v-if="safeGitHubInstallationManageUrl(connection.manageUrl)"
              class="button button--ghost button--compact"
              :href="safeGitHubInstallationManageUrl(connection.manageUrl)!"
              target="_blank"
              rel="noopener noreferrer"
              >GitHub 저장소 설정 관리</a
            >
            <button
              type="button"
              class="button button--secondary button--compact"
              :disabled="refreshMutation.isPending.value || connection.status === 'DISCONNECTING'"
              @click="refresh(connection)"
            >
              권한 다시 확인
            </button>
            <button
              type="button"
              class="button button--danger button--compact"
              :disabled="disconnectMutation.isPending.value || connection.status !== 'ACTIVE'"
              @click="disconnect(connection)"
            >
              연결 해제
            </button>
          </div>
        </li>
      </ul>
      <p v-else class="github-app-card__empty">아직 연결된 GitHub App installation이 없어요.</p>

      <form
        v-if="activeConnections.length"
        class="private-source-form"
        novalidate
        @submit.prevent="registerPrivateSource"
      >
        <div>
          <p class="section-kicker">Private source 등록</p>
          <h3>연결한 계정의 저장소 선택 시작</h3>
        </div>
        <label class="field">
          <span class="field__label">ACTIVE GitHub App 연결</span>
          <select v-model="connectionId" class="control">
            <option
              v-for="connection in activeConnections"
              :key="connection.id"
              :value="connection.id"
            >
              {{ connection.targetAccountLogin }} · {{ connection.targetAccountType }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">GitHub 계정 또는 private 저장소 URL</span>
          <input
            v-model="privateUrl"
            class="control"
            type="url"
            maxlength="500"
            placeholder="https://github.com/owner 또는 https://github.com/owner/repository"
          />
        </label>
        <label class="github-app-card__confirmation">
          <input v-model="participationConfirmed" class="checkbox-control" type="checkbox" />
          <span>제가 직접 참여했고, 연결된 installation에서 허용한 저장소입니다.</span>
        </label>
        <p v-if="privateFormError" class="inline-error" role="alert">{{ privateFormError }}</p>
        <button
          type="submit"
          class="button button--primary"
          :disabled="sourceMutation.isPending.value"
        >
          {{ sourceMutation.isPending.value ? '확인하는 중…' : 'Private 저장소 불러오기' }}
        </button>
      </form>
    </template>
  </section>
</template>

<style scoped>
.github-app-card {
  display: grid;
  gap: var(--space-4);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}

.github-app-card__heading,
.github-app-list__summary,
.github-app-list__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.github-app-card__notice,
.github-app-card__empty,
.github-app-list span,
.github-app-list dt {
  color: var(--color-muted-strong);
  font-size: var(--font-size-sm);
}

.github-app-list,
.private-source-form {
  display: grid;
  gap: var(--space-4);
}

.github-app-list > li {
  display: grid;
  gap: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
  padding: var(--space-4);
}

.github-app-list__summary > div,
.github-app-list dl,
.github-app-list dl > div {
  display: grid;
  gap: var(--space-1);
}

.github-app-list dl {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.github-app-list__actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.private-source-form {
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.github-app-card__confirmation {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

@media (max-width: 40rem) {
  .github-app-card__heading,
  .github-app-list__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .github-app-card__heading .button,
  .github-app-list__actions .button {
    width: 100%;
  }

  .github-app-list dl {
    grid-template-columns: 1fr;
  }
}
</style>
