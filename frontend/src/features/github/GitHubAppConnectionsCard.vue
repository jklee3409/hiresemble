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
import {
  GITHUB_CONNECTION_STATUS_LABELS,
  parsePublicGitHubUrl,
} from '@/features/github/presentation'
import { startGitHubAppConnection } from '@/shared/api/githubAppConnectionApi'
import type { GitHubAppConnectionDto } from '@/shared/api/githubAppConnectionContracts'
import { normalizeApiError } from '@/shared/api/errors'
import InlineNotice from '@/shared/ui/InlineNotice.vue'
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
        ? '아직 private 저장소 연결을 준비하는 중이에요. 조금 뒤에 다시 시도해 주세요.'
        : 'GitHub 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.'
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
    feedback.value = '권한과 저장소 범위를 다시 확인했어요.'
  } catch (error) {
    const normalized = normalizeApiError(error)
    errorMessage.value = connectionError(normalized.code)
  }
}

async function disconnect(connection: GitHubAppConnectionDto): Promise<void> {
  const confirmed = await notifications.confirm({
    title: `${connection.targetAccountLogin} 연결을 해제할까요?`,
    message:
      'GitHub에서 이 앱을 지우고 private 저장소를 더 이상 읽지 않아요. 저장해 둔 private 원본 기록도 함께 지워져요. 이미 승인한 경험과 만들어 둔 이력서·포트폴리오는 그대로 남아요.',
    confirmLabel: '연결 해제하기',
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
    feedback.value = '연결을 해제하고 저장해 둔 private 기록을 지우고 있어요.'
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
    privateFormError.value = '연결된 GitHub 계정을 고르고, 직접 참여한 저장소인지 확인해 주세요.'
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
    feedback.value = 'private 저장소 목록을 불러오고 있어요.'
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
    expired: '연결 요청 시간이 지났어요. 다시 시작해 주세요.',
    permission: 'GitHub에서 저장소 정보와 파일 읽기 권한을 모두 허용해 주세요.',
    unavailable: 'GitHub App 연결을 확인하지 못했어요.',
  }[value]
}

function connectionError(code: string): string {
  if (code === 'GITHUB_APP_PERMISSION_MISMATCH')
    return '저장소 정보와 파일 읽기 권한이 모두 필요해요. GitHub에서 권한을 다시 확인해 주세요.'
  if (code === 'GITHUB_INSTALLATION_SUSPENDED')
    return 'GitHub에서 이 앱이 일시 중지됐어요. GitHub 설정에서 다시 켜 주세요.'
  if (code === 'GITHUB_INSTALLATION_REVOKED')
    return 'GitHub에서 이 앱의 권한이 해제됐어요. 다시 연결해 주세요.'
  return 'GitHub 연결 상태를 확인하지 못했어요.'
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
      GitHub로 이동해 저장소 정보 읽기(<strong>Metadata read</strong>)와 파일 읽기(<strong
        >Contents read</strong
      >)만 허용해 주세요. 그중에서도 Hiresemble에서 다시 고른 1~10개 저장소만 읽어요.
    </p>
    <p class="github-app-card__notice">
      개인 access token은 붙여 넣지 마세요. 연결에 쓰는 값은 브라우저에 저장하지 않아요.
    </p>

    <a
      v-if="externalHref"
      ref="externalAnchor"
      class="sr-only"
      :href="externalHref"
      rel="noopener noreferrer"
      >GitHub 설치 화면으로 이동</a
    >

    <InlineNotice
      v-if="callbackResult"
      :tone="callbackResult === 'connected' ? 'info' : 'warning'"
      :title="callbackMessage(callbackResult)"
    />
    <InlineNotice v-if="feedback" tone="info" :title="feedback" />
    <InlineNotice v-if="errorMessage" tone="danger" role="alert" :title="errorMessage" />

    <StatePanel
      v-if="capability.isPending.value"
      kind="loading"
      title="private 저장소 연결을 쓸 수 있는지 확인하는 중…"
    />
    <StatePanel
      v-else-if="capability.isError.value || !available"
      kind="error"
      title="private 저장소 연결은 아직 쓸 수 없어요."
      description="공개 저장소 연결은 아래에서 그대로 사용할 수 있어요."
    />

    <template v-else>
      <StatePanel
        v-if="connections.isPending.value"
        kind="loading"
        title="연결한 GitHub 계정을 불러오는 중…"
      />
      <StatePanel
        v-else-if="connections.isError.value"
        kind="error"
        title="연결한 GitHub 계정을 불러오지 못했어요."
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
            <StatusBadge
              :label="GITHUB_CONNECTION_STATUS_LABELS[connection.status]"
              :tone="statusTone(connection.status)"
            />
          </div>
          <dl>
            <div>
              <dt>GitHub에서 허용한 범위</dt>
              <dd>
                {{
                  connection.repositorySelection === 'ALL'
                    ? '모든 저장소'
                    : 'GitHub에서 고른 저장소만'
                }}
              </dd>
            </div>
            <div>
              <dt>마지막으로 확인한 때</dt>
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
              >GitHub에서 범위 바꾸기</a
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
      <p v-else class="github-app-card__empty">아직 연결한 GitHub 계정이 없어요.</p>

      <form
        v-if="activeConnections.length"
        class="private-source-form"
        novalidate
        @submit.prevent="registerPrivateSource"
      >
        <div>
          <p class="section-kicker">Private 저장소 등록</p>
          <h3>연결한 계정에서 저장소 고르기</h3>
        </div>
        <label class="field">
          <span class="field__label">연결한 GitHub 계정</span>
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
          <span>제가 직접 참여했고, GitHub에서 이 앱에 허용한 저장소입니다.</span>
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
