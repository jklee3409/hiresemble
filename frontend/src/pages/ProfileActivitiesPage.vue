<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref } from 'vue'
import { z } from 'zod'

import ProfileSectionActions from '@/features/profile/ProfileSectionActions.vue'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import {
  ACTIVITY_TYPES,
  type ActivityCreateRequest,
  type ActivityDto,
  type ActivityType,
} from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import PageHeader from '@/shared/ui/PageHeader.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { focusFirstInvalidControl } from '@/shared/ui/formFocus'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

const TYPE_LABELS: Record<ActivityType, string> = {
  CLUB: '동아리',
  VOLUNTEERING: '봉사활동',
  CONTEST: '공모전',
  SUPPORTERS: '서포터즈',
  PRESS_CORPS: '기자단',
  STUDENT_COUNCIL: '학생회',
  EDUCATION_PROGRAM: '교육 프로그램',
  INTERNATIONAL: '해외 경험',
  OTHER: '기타',
}

const activitySchema = z
  .object({
    title: z.string().trim().min(1, '활동 제목을 입력해 주세요.').max(200),
    activityType: z.enum(ACTIVITY_TYPES),
    organizer: z.string().trim().min(1, '진행 주체를 입력해 주세요.').max(200),
    startedAt: z.string(),
    endedAt: z.string(),
    ongoing: z.boolean(),
    role: z.string().trim().max(200),
    description: z.string().trim().min(1, '맡은 일과 활동 내용을 입력해 주세요.').max(10_000),
    achievements: z.string().trim().max(10_000),
    relatedUrl: z.union([
      z.literal(''),
      z.url('http:// 또는 https://로 시작하는 링크를 입력해 주세요.'),
    ]),
    useAsMaterial: z.boolean(),
  })
  .superRefine((value, context) => {
    if (value.ongoing && value.endedAt !== '') {
      context.addIssue({
        code: 'custom',
        path: ['endedAt'],
        message: '진행 중인 활동은 종료일을 비워 주세요.',
      })
    }
    if (value.startedAt && value.endedAt && value.startedAt > value.endedAt) {
      context.addIssue({
        code: 'custom',
        path: ['endedAt'],
        message: '종료일은 시작일보다 빠를 수 없어요.',
      })
    }
  })

type ActivityForm = z.input<typeof activitySchema>

const authStore = useAuthStore()
const cache = useQueryClient()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const queryKey = computed(() => ['user', userId.value, 'activities'] as const)
const activities = useQuery({
  queryKey,
  queryFn: () => profileApi.listActivities({ page: 0, size: 100, sort: 'startedAt,desc' }),
  enabled: computed(() => userId.value !== ''),
})

const editorOpen = ref(false)
const editingId = ref('')
const fieldErrors = ref<Record<string, string>>({})
const actionError = ref('')
const form = reactive<ActivityForm>(emptyForm())

const saveMutation = useMutation({
  mutationFn: (input: { id: string; request: ActivityCreateRequest; version: number }) =>
    input.id === ''
      ? profileApi.createActivity(input.request)
      : profileApi.updateActivity(input.id, { ...input.request, version: input.version }),
})
const deleteMutation = useMutation({
  mutationFn: (item: ActivityDto) => profileApi.deleteActivity(item.id, item.version),
})

function emptyForm(): ActivityForm {
  return {
    title: '',
    activityType: 'CLUB',
    organizer: '',
    startedAt: '',
    endedAt: '',
    ongoing: false,
    role: '',
    description: '',
    achievements: '',
    relatedUrl: '',
    useAsMaterial: false,
  }
}

function openCreate(): void {
  editingId.value = ''
  Object.assign(form, emptyForm())
  fieldErrors.value = {}
  actionError.value = ''
  editorOpen.value = true
}

function openEdit(item: ActivityDto): void {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title,
    activityType: item.activityType,
    organizer: item.organizer,
    startedAt: item.startedAt ?? '',
    endedAt: item.endedAt ?? '',
    ongoing: item.ongoing,
    role: item.role ?? '',
    description: item.description,
    achievements: item.achievements ?? '',
    relatedUrl: item.relatedUrl ?? '',
    useAsMaterial: item.useAsMaterial,
  })
  fieldErrors.value = {}
  actionError.value = ''
  editorOpen.value = true
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeEditor(): void {
  editorOpen.value = false
  editingId.value = ''
  fieldErrors.value = {}
  actionError.value = ''
}

async function save(): Promise<void> {
  const parsed = activitySchema.safeParse(form)
  if (!parsed.success) {
    fieldErrors.value = Object.fromEntries(
      parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message]),
    )
    await nextTick()
    focusFirstInvalidControl()
    return
  }
  fieldErrors.value = {}
  actionError.value = ''
  const current = activities.data.value?.items.find((item) => item.id === editingId.value)
  const request: ActivityCreateRequest = {
    ...parsed.data,
    startedAt: parsed.data.startedAt || null,
    endedAt: parsed.data.ongoing ? null : parsed.data.endedAt || null,
    role: parsed.data.role || null,
    achievements: parsed.data.achievements || null,
    relatedUrl: parsed.data.relatedUrl || null,
  }
  try {
    await saveMutation.mutateAsync({
      id: editingId.value,
      request,
      version: current?.version ?? 0,
    })
    await cache.invalidateQueries({ queryKey: queryKey.value })
    await cache.invalidateQueries({ queryKey: ['user', userId.value, 'evidence'] })
    notifications.toast(
      editingId.value ? '대외활동을 수정했어요.' : '대외활동을 등록했어요.',
      'success',
    )
    closeEditor()
  } catch (error) {
    const normalized = normalizeApiError(error)
    actionError.value =
      normalized.code === 'RESOURCE_VERSION_CONFLICT'
        ? '다른 곳에서 내용이 변경됐어요. 목록을 새로 확인한 뒤 다시 수정해 주세요.'
        : normalized.message
    await activities.refetch()
  }
}

async function remove(item: ActivityDto): Promise<void> {
  const confirmed = await notifications.confirm({
    title: '이 대외활동을 삭제할까요?',
    message:
      '직접 등록한 활동과 연결된 소재 후보가 함께 삭제됩니다. 이력서·포트폴리오 원본에는 영향을 주지 않아요.',
    confirmLabel: '대외활동 삭제',
  })
  if (!confirmed) return
  try {
    await deleteMutation.mutateAsync(item)
    await cache.invalidateQueries({ queryKey: queryKey.value })
    await cache.invalidateQueries({ queryKey: ['user', userId.value, 'evidence'] })
    notifications.toast('대외활동을 삭제했어요.', 'success')
  } catch (error) {
    notifications.toast(normalizeApiError(error).message, 'error')
    await activities.refetch()
  }
}

function period(item: ActivityDto): string {
  const start = item.startedAt?.replaceAll('-', '.') ?? '시작일 미입력'
  const end = item.ongoing ? '현재' : (item.endedAt?.replaceAll('-', '.') ?? '종료일 미입력')
  return `${start} ~ ${end}`
}
</script>

<template>
  <section
    class="activities-page app-page profile-workspace-shell"
    aria-labelledby="activities-heading"
  >
    <ProfileTabs />
    <div class="profile-workspace-shell__content">
      <PageHeader
        heading-id="activities-heading"
        title="대외활동"
        description="동아리, 봉사, 공모전처럼 직접 참여한 경험을 기록하고 필요한 활동만 자소서·면접 소재 후보로 선택하세요."
        variant="compact"
      >
        <template #actions>
          <button
            v-if="!editorOpen"
            type="button"
            class="button button--primary"
            @click="openCreate"
          >
            대외활동 등록
          </button>
        </template>
      </PageHeader>

      <aside class="activity-policy" aria-label="대외활동 소재 활용 안내">
        <strong>문서 분석 결과와 별도로 관리해요.</strong>
        <p>
          여기는 직접 입력한 활동만 표시됩니다. ‘소재 후보로 사용’을 켠 활동만 관련 자소서나 면접
          준비에서 AI가 제안할 수 있어요.
        </p>
      </aside>

      <form
        v-if="editorOpen"
        class="activity-editor section-surface"
        novalidate
        @submit.prevent="save"
      >
        <header class="section-header">
          <div>
            <p class="section-kicker">{{ editingId ? '활동 수정' : '새 활동' }}</p>
            <h2 class="section-title">
              {{
                editingId ? '기록한 대외활동을 다듬어 주세요.' : '어떤 경험이었는지 알려 주세요.'
              }}
            </h2>
            <p class="section-description">
              필수 항목은 제목, 종류, 진행 주체와 활동 내용뿐이에요.
            </p>
          </div>
          <button type="button" class="button button--ghost button--compact" @click="closeEditor">
            닫기
          </button>
        </header>

        <div class="activity-form-grid">
          <label class="field activity-form-grid__wide">
            <span class="field__label">활동 제목</span>
            <input
              v-model="form.title"
              class="control"
              placeholder="예: 교내 IT 동아리 운영진"
              maxlength="200"
              :aria-invalid="Boolean(fieldErrors.title)"
            />
            <span v-if="fieldErrors.title" class="inline-error">{{ fieldErrors.title }}</span>
          </label>
          <label class="field">
            <span class="field__label">활동 종류</span>
            <select v-model="form.activityType" class="control">
              <option v-for="type in ACTIVITY_TYPES" :key="type" :value="type">
                {{ TYPE_LABELS[type] }}
              </option>
            </select>
          </label>
          <label class="field">
            <span class="field__label">진행 주체·주관 기관</span>
            <input
              v-model="form.organizer"
              class="control"
              placeholder="예: OO대학교 총학생회"
              maxlength="200"
              :aria-invalid="Boolean(fieldErrors.organizer)"
            />
            <span v-if="fieldErrors.organizer" class="inline-error">{{
              fieldErrors.organizer
            }}</span>
          </label>
          <label class="field">
            <span class="field__label">시작일 <span class="field__optional">(선택)</span></span>
            <input v-model="form.startedAt" class="control" type="date" />
          </label>
          <label class="field">
            <span class="field__label">종료일 <span class="field__optional">(선택)</span></span>
            <input
              v-model="form.endedAt"
              class="control"
              type="date"
              :disabled="form.ongoing"
              :aria-invalid="Boolean(fieldErrors.endedAt)"
            />
            <span v-if="fieldErrors.endedAt" class="inline-error">{{ fieldErrors.endedAt }}</span>
          </label>
          <label class="check-row activity-form-grid__wide">
            <input
              v-model="form.ongoing"
              class="checkbox-control"
              type="checkbox"
              @change="form.endedAt = ''"
            />
            현재 진행 중인 활동이에요
          </label>
          <label class="field activity-form-grid__wide">
            <span class="field__label">역할 <span class="field__optional">(선택)</span></span>
            <input
              v-model="form.role"
              class="control"
              placeholder="예: 운영진, 콘텐츠 기획 담당"
              maxlength="200"
            />
          </label>
          <label class="field activity-form-grid__wide">
            <span class="field__label">활동 내용</span>
            <textarea
              v-model="form.description"
              class="control activity-editor__textarea"
              placeholder="무엇을 목표로 어떤 일을 맡았는지 구체적으로 적어 주세요."
              maxlength="10000"
              :aria-invalid="Boolean(fieldErrors.description)"
            />
            <span v-if="fieldErrors.description" class="inline-error">{{
              fieldErrors.description
            }}</span>
          </label>
          <label class="field activity-form-grid__wide">
            <span class="field__label">주요 성과 <span class="field__optional">(선택)</span></span>
            <textarea
              v-model="form.achievements"
              class="control"
              placeholder="예: 참가자 30명을 모집하고 만족도 4.7점을 달성"
              maxlength="10000"
            />
          </label>
          <label class="field activity-form-grid__wide">
            <span class="field__label">관련 링크 <span class="field__optional">(선택)</span></span>
            <input
              v-model="form.relatedUrl"
              class="control"
              type="url"
              placeholder="https://example.com"
              maxlength="1000"
              :aria-invalid="Boolean(fieldErrors.relatedUrl)"
            />
            <span v-if="fieldErrors.relatedUrl" class="inline-error">{{
              fieldErrors.relatedUrl
            }}</span>
          </label>
        </div>

        <label class="material-choice">
          <input v-model="form.useAsMaterial" class="switch-control" type="checkbox" />
          <span>
            <strong>자소서·면접 소재 후보로 사용</strong>
            <small
              >켜 두면 관련 공고에서 AI가 이 활동을 후보로 제안할 수 있어요. 자동으로 모든 글에
              사용되지는 않습니다.</small
            >
          </span>
        </label>
        <p v-if="actionError" class="alert alert--danger" role="alert">{{ actionError }}</p>
        <div class="form-actions activity-editor__actions">
          <button type="button" class="button button--secondary" @click="closeEditor">취소</button>
          <button
            type="submit"
            class="button button--primary"
            :disabled="saveMutation.isPending.value"
          >
            {{
              saveMutation.isPending.value
                ? '저장하는 중…'
                : editingId
                  ? '수정 내용 저장'
                  : '대외활동 저장'
            }}
          </button>
        </div>
      </form>

      <StatePanel
        v-if="activities.isPending.value"
        class="activities-page__state"
        kind="loading"
        title="대외활동을 불러오는 중…"
      />
      <StatePanel
        v-else-if="activities.isError.value"
        class="activities-page__state"
        kind="error"
        title="대외활동을 불러오지 못했어요."
        description="잠시 후 다시 시도해 주세요."
      >
        <template #actions
          ><button type="button" class="button button--secondary" @click="activities.refetch()">
            다시 시도
          </button></template
        >
      </StatePanel>
      <StatePanel
        v-else-if="activities.data.value?.items.length === 0"
        class="activities-page__state"
        kind="empty"
        title="아직 등록한 대외활동이 없어요."
        description="직접 참여한 활동을 기록해 두면 자소서 소재를 고르거나 면접 답변을 준비할 때 다시 활용할 수 있어요."
      >
        <template #actions
          ><button type="button" class="button button--primary" @click="openCreate">
            첫 대외활동 등록
          </button></template
        >
      </StatePanel>
      <ul v-else class="activity-list data-list" aria-label="등록한 대외활동">
        <li
          v-for="item in activities.data.value?.items"
          :key="item.id"
          class="activity-card data-card"
        >
          <div class="activity-card__top">
            <div>
              <div class="activity-card__title">
                <h2>{{ item.title }}</h2>
                <StatusBadge
                  :label="item.useAsMaterial ? '소재 후보 사용' : '기록만 보관'"
                  :tone="item.useAsMaterial ? 'success' : 'neutral'"
                />
              </div>
              <p>{{ TYPE_LABELS[item.activityType] }} · {{ item.organizer }}</p>
            </div>
            <div class="activity-card__actions">
              <button
                type="button"
                class="button button--secondary button--compact"
                @click="openEdit(item)"
              >
                수정
              </button>
              <button
                type="button"
                class="button button--danger button--compact"
                @click="remove(item)"
              >
                삭제
              </button>
            </div>
          </div>
          <dl class="activity-card__meta">
            <div>
              <dt>활동 기간</dt>
              <dd>{{ period(item) }}</dd>
            </div>
            <div v-if="item.role">
              <dt>역할</dt>
              <dd>{{ item.role }}</dd>
            </div>
          </dl>
          <p class="activity-card__description">{{ item.description }}</p>
          <p v-if="item.achievements" class="activity-card__achievement">
            <strong>주요 성과</strong>{{ item.achievements }}
          </p>
          <a
            v-if="item.relatedUrl"
            class="text-link"
            :href="item.relatedUrl"
            target="_blank"
            rel="noopener noreferrer"
            >관련 링크 열기</a
          >
        </li>
      </ul>
      <ProfileSectionActions />
    </div>
  </section>
</template>

<style scoped>
.activity-policy,
.activity-editor,
.activities-page__state,
.activity-list {
  margin-top: var(--space-5);
}

.activity-policy {
  border-left: 3px solid var(--color-brand);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-brand-soft);
  padding: 0.875rem 1rem;
}
.activity-policy strong {
  color: var(--color-brand-strong);
}
.activity-policy p {
  margin: 0.25rem 0 0;
  color: var(--color-ink-soft);
  font-size: 0.875rem;
}
.activity-editor {
  padding: clamp(1rem, 3vw, 1.5rem);
}
.activity-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1.25rem;
}
.activity-form-grid__wide {
  grid-column: 1 / -1;
}
.activity-editor__textarea {
  min-height: 8rem;
}
.material-choice {
  display: flex;
  align-items: flex-start;
  gap: 0.875rem;
  margin-top: 1.25rem;
  border: 0;
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  padding: 1rem 1.125rem;
}
.material-choice span {
  display: grid;
  gap: 0.25rem;
}
.material-choice small {
  color: var(--color-muted-strong);
  line-height: 1.5;
}
.activity-editor__actions {
  justify-content: flex-end;
  margin-top: 1.25rem;
}
.activity-card {
  display: grid;
  gap: 1rem;
}
.activity-card__top,
.activity-card__title,
.activity-card__actions {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.activity-card__top {
  justify-content: space-between;
}
.activity-card__title {
  align-items: center;
}
.activity-card h2 {
  margin: 0;
  font-size: 1.05rem;
}
.activity-card__top p {
  margin: 0.35rem 0 0;
  color: var(--color-muted);
  font-size: 0.875rem;
}
.activity-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin: 0;
}
.activity-card__meta div {
  display: flex;
  gap: 0.5rem;
}
.activity-card__meta dt {
  color: var(--color-muted);
  font-size: 0.8125rem;
}
.activity-card__meta dd {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 0.8125rem;
  font-weight: 650;
}
.activity-card__description {
  margin: 0;
  white-space: pre-wrap;
}
.activity-card__achievement {
  display: grid;
  gap: 0.25rem;
  margin: 0;
  border-radius: var(--radius-sm);
  background: var(--color-success-soft);
  color: var(--color-success-strong);
  padding: 0.75rem;
  white-space: pre-wrap;
}

@media (max-width: 639px) {
  .activity-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .activity-form-grid__wide {
    grid-column: auto;
  }
  .activity-card__top {
    display: grid;
  }
  .activity-card__actions {
    width: 100%;
  }
  .activity-card__actions .button {
    flex: 1;
  }
}
</style>
