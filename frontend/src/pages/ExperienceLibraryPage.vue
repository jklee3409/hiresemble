<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { featureFlags } from '@/app/featureFlags'
import { safeGitHubRepositoryUrl } from '@/features/github/presentation'
import ProfileTabs from '@/features/profile/ProfileTabs.vue'
import { profileQueryKeys } from '@/features/profile/queryKeys'
import type {
  EvidenceSourceType,
  EvidenceVerificationStatus,
  ExperienceItemDetailDto,
  ExperienceItemDto,
  ExperienceMatchKind,
  ExperienceMatchResolution,
  ExperienceSourceDto,
} from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageHeader from '@/shared/ui/PageHeader.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'
import { useAuthStore } from '@/stores/auth'

type ReviewableMatchKind = Exclude<ExperienceMatchKind, 'SAME_EXPERIENCE'>
type VerificationFilter = '' | EvidenceVerificationStatus
type MatchFilter = '' | ReviewableMatchKind

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cache = useQueryClient()
const notifications = useNotifications()
const userId = computed(() => authStore.currentUser?.id ?? '')
const page = ref(0)
const verificationFilter = ref<VerificationFilter>('')
const matchFilter = ref<MatchFilter>('')
const selectedId = ref(typeof route.query.selected === 'string' ? route.query.selected : '')
const detailPanel = ref<HTMLElement | null>(null)
/* 카드 안에서 바로 고친다. 별도 편집 영역을 열지 않으므로 어느 카드를 고치는 중인지만 들고 있다. */
const editingId = ref('')
const actionError = ref('')
const edit = reactive({ title: '', content: '' })

const listParams = computed<profileApi.ExperienceListParams>(() => ({
  ...(verificationFilter.value ? { verificationStatus: verificationFilter.value } : {}),
  ...(matchFilter.value ? { matchKind: matchFilter.value } : {}),
  page: page.value,
  size: 5,
  sort: 'updatedAt,desc',
}))

const experiences = useQuery({
  queryKey: computed(() => profileQueryKeys.experiences(userId.value, listParams.value)),
  queryFn: () => profileApi.listExperiences(listParams.value),
  enabled: computed(() => userId.value !== ''),
})

const detail = useQuery({
  queryKey: computed(() => profileQueryKeys.experience(userId.value, selectedId.value)),
  queryFn: () => profileApi.getExperience(selectedId.value),
  enabled: computed(() => userId.value !== '' && selectedId.value !== ''),
})

const targetId = computed(() => detail.data.value?.item.matchedExperienceItemId ?? '')
const targetDetail = useQuery({
  queryKey: computed(() => profileQueryKeys.experience(userId.value, targetId.value)),
  queryFn: () => profileApi.getExperience(targetId.value),
  enabled: computed(() => userId.value !== '' && targetId.value !== ''),
})

const updateMutation = useMutation({
  mutationFn: (input: { id: string; title: string; content: string; version: number }) =>
    profileApi.updateExperience(input.id, {
      title: input.title,
      content: input.content,
      version: input.version,
    }),
})
const verificationMutation = useMutation({
  mutationFn: (input: { item: ExperienceItemDto; status: 'PENDING' | 'VERIFIED' | 'REJECTED' }) =>
    profileApi.verifyExperience(input.item.id, {
      status: input.status,
      version: input.item.version,
    }),
})
const resolutionMutation = useMutation({
  mutationFn: (input: { item: ExperienceItemDto; resolution: ExperienceMatchResolution }) =>
    profileApi.resolveExperienceMatch(input.item.id, {
      resolution: input.resolution,
      targetExperienceItemId:
        input.resolution === 'MERGE_WITH_TARGET' ? input.item.matchedExperienceItemId : null,
      version: input.item.version,
    }),
})
const busy = computed(
  () =>
    updateMutation.isPending.value ||
    verificationMutation.isPending.value ||
    resolutionMutation.isPending.value,
)

watch(
  () => route.query.selected,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (selectedId.value !== next) selectedId.value = next
  },
)

watch([verificationFilter, matchFilter], () => {
  page.value = 0
})

async function selectExperience(item: ExperienceItemDto): Promise<void> {
  selectedId.value = item.id
  actionError.value = ''
  await router.replace({ query: { ...route.query, selected: item.id } })
  await nextTick()
  detailPanel.value?.focus({ preventScroll: false })
}

async function closeDetail(): Promise<void> {
  const query = { ...route.query }
  delete query.selected
  selectedId.value = ''
  actionError.value = ''
  await router.replace({ query })
}

function openEdit(item: ExperienceItemDto): void {
  edit.title = item.title
  edit.content = item.content
  actionError.value = ''
  editingId.value = item.id
}

function cancelEdit(): void {
  editingId.value = ''
  actionError.value = ''
}

async function refreshAfterMutation(value: ExperienceItemDetailDto): Promise<void> {
  cache.setQueryData(profileQueryKeys.experience(userId.value, value.item.id), value)
  await cache.invalidateQueries({ queryKey: profileQueryKeys.experiencesRoot(userId.value) })
  await cache.invalidateQueries({ queryKey: profileQueryKeys.evidenceRoot(userId.value) })
}

async function save(item: ExperienceItemDto): Promise<void> {
  const title = edit.title.trim()
  const content = edit.content.trim()
  if (!title || !content) {
    actionError.value = '제목과 핵심 내용은 비워 둘 수 없어요.'
    return
  }
  actionError.value = ''
  try {
    const saved = await updateMutation.mutateAsync({
      id: item.id,
      title,
      content,
      version: item.version,
    })
    await refreshAfterMutation(saved)
    editingId.value = ''
    notifications.toast('경험 내용을 수정했어요.', 'success')
  } catch (error) {
    actionError.value = conflictMessage(error)
    await experiences.refetch()
  }
}

async function verify(
  item: ExperienceItemDto,
  status: 'PENDING' | 'VERIFIED' | 'REJECTED',
): Promise<void> {
  actionError.value = ''
  try {
    const saved = await verificationMutation.mutateAsync({ item, status })
    await refreshAfterMutation(saved)
    notifications.toast(verificationMessage(status), 'success')
  } catch (error) {
    actionError.value = conflictMessage(error)
    await experiences.refetch()
  }
}

async function resolveMatch(
  item: ExperienceItemDto,
  resolution: ExperienceMatchResolution,
): Promise<void> {
  if (resolution === 'MERGE_WITH_TARGET') {
    const confirmed = await notifications.confirm({
      title: '기존 경험에 합칠까요?',
      message:
        '이 경험 카드는 목록에서 사라지고, 연결된 출처는 제안된 기존 경험에 합쳐져요. 내용이 같은 경험인지 다시 확인해 주세요.',
      confirmLabel: '기존 경험에 합치기',
      tone: 'danger',
    })
    if (!confirmed) return
  }
  actionError.value = ''
  try {
    const saved = await resolutionMutation.mutateAsync({ item, resolution })
    await refreshAfterMutation(saved)
    notifications.toast(
      resolution === 'KEEP_SEPARATE' ? '별도 경험으로 보관했어요.' : '기존 경험에 출처를 합쳤어요.',
      'success',
    )
    if (saved.item.id !== selectedId.value) {
      await selectExperience(saved.item)
    }
  } catch (error) {
    actionError.value = conflictMessage(error)
    await detail.refetch()
  }
}

function conflictMessage(error: unknown): string {
  const normalized = normalizeApiError(error)
  return normalized.status === 409
    ? '다른 곳에서 경험이 변경됐어요. 최신 내용을 다시 확인해 주세요.'
    : normalized.message
}

function verificationMessage(status: 'PENDING' | 'VERIFIED' | 'REJECTED'): string {
  if (status === 'VERIFIED') return '이 경험을 활용 승인했어요.'
  if (status === 'REJECTED') return '이 경험을 활용에서 제외했어요.'
  return '이 경험을 다시 검토할 수 있게 바꿨어요.'
}

function verificationLabel(status: EvidenceVerificationStatus): string {
  return {
    PENDING: '확인 필요',
    VERIFIED: '활용 승인',
    REJECTED: '활용 제외',
    SOURCE_DELETED: '원본 삭제됨',
  }[status]
}

function verificationTone(
  status: EvidenceVerificationStatus,
): 'neutral' | 'success' | 'notice' | 'warning' {
  if (status === 'VERIFIED') return 'success'
  if (status === 'PENDING') return 'notice'
  if (status === 'SOURCE_DELETED') return 'warning'
  return 'neutral'
}

function matchLabel(kind: ExperienceMatchKind): string {
  return {
    NEW: '새 경험',
    SAME_EXPERIENCE: '같은 경험',
    RELATED_DIFFERENT: '비슷한 경험 확인',
    CONFLICT: '내용 차이 확인',
  }[kind]
}

function matchTone(kind: ExperienceMatchKind): 'brand' | 'notice' | 'warning' | 'danger' {
  if (kind === 'CONFLICT') return 'danger'
  if (kind === 'RELATED_DIFFERENT') return 'warning'
  if (kind === 'SAME_EXPERIENCE') return 'notice'
  return 'brand'
}

function categoryLabel(value: string): string {
  return (
    {
      CAREER: '경력',
      PROJECT: '프로젝트',
      EDUCATION: '교육',
      AWARD: '수상',
      CERTIFICATION: '자격',
      ACTIVITY: '대외활동',
    }[value] ?? '경험'
  )
}

function sourceTypeLabel(value: EvidenceSourceType): string {
  return (
    {
      EDUCATION: '학력',
      CERTIFICATION: '자격증',
      LANGUAGE_SCORE: '어학',
      AWARD: '수상',
      CAREER: '경력',
      ACTIVITY: '대외활동',
      DOCUMENT_CHUNK: '업로드 자료',
      GITHUB_REPOSITORY: 'GitHub',
      EXPERIENCE: '경험 보관함',
      MANUAL: '직접 입력',
    }[value] ?? '출처'
  )
}

function sourceLabel(source: ExperienceSourceDto): string {
  if (source.sourceDeletedAt) return `${sourceTypeLabel(source.sourceType)} · 원본 삭제됨`
  if (source.relationKind === 'CORROBORATING')
    return `${sourceTypeLabel(source.sourceType)} · 보강 출처`
  return `${sourceTypeLabel(source.sourceType)} · 최초 출처`
}

/*
 * 개수 대신 실제 문서 이름을 보여 준다. 같은 경험이 여러 문서에서 나왔다면
 * 서버가 가장 먼저 추출한 문서 이름을 주고, 나머지는 "외 N곳"으로만 센다.
 */
function documentSourceLabel(item: ExperienceItemDto): string {
  if (!item.primaryDocumentName) {
    return item.documentSourceCount > 0 ? '원본이 삭제된 문서' : '문서 출처 없음'
  }
  if (item.documentSourceCount > 1) {
    return `${item.primaryDocumentName} 외 ${item.documentSourceCount - 1}곳`
  }
  return item.primaryDocumentName
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(new Date(value))
}

function similarityLabel(value: number | null): string {
  return value === null ? '유사도 미제공' : `유사도 ${Math.round(value * 100)}%`
}
</script>

<template>
  <section
    class="experience-page app-page profile-workspace-shell"
    aria-labelledby="experience-heading"
  >
    <ProfileTabs />

    <div class="profile-workspace-shell__content">
      <PageHeader
        heading-id="experience-heading"
        eyebrow="내 지원 정보"
        title="경험 보관함"
        description="이력서와 포트폴리오에서 찾은 강점과 경험을 한곳에서 관리해요. 같은 경험은 카드 하나로 모으고, 상세에서 보강된 출처를 확인할 수 있어요."
        variant="list"
      />

      <details class="filter-disclosure experience-filters" open>
        <summary>경험 필터</summary>
        <form class="filter-toolbar experience-filters__form" @submit.prevent>
          <label class="field">
            <span class="field__label">활용 상태</span>
            <select v-model="verificationFilter" class="control">
              <option value="">전체</option>
              <option value="PENDING">확인 필요</option>
              <option value="VERIFIED">활용 승인</option>
              <option value="REJECTED">활용 제외</option>
            </select>
          </label>
          <label class="field">
            <span class="field__label">유사 경험 검토</span>
            <select v-model="matchFilter" class="control">
              <option value="">전체</option>
              <option value="RELATED_DIFFERENT">비슷한 경험 확인</option>
              <option value="CONFLICT">내용 차이 확인</option>
              <option value="NEW">검토 완료된 별도 경험</option>
            </select>
          </label>
        </form>
      </details>

      <p v-if="actionError" class="alert alert--danger experience-message" role="alert">
        {{ actionError }}
      </p>

      <StatePanel
        v-if="experiences.isPending.value"
        class="experience-state"
        kind="loading"
        title="경험 보관함을 불러오는 중이에요"
      />
      <StatePanel
        v-else-if="experiences.isError.value"
        class="experience-state"
        kind="error"
        title="경험 보관함을 불러오지 못했어요"
        description="잠시 후 다시 시도해 주세요."
      >
        <template #actions>
          <button type="button" class="button button--secondary" @click="experiences.refetch()">
            다시 시도
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="!experiences.data.value?.items.length"
        class="experience-state"
        kind="empty"
        title="조건에 맞는 경험이 없어요"
        description="이력서나 포트폴리오 분석이 끝나면 새 경험이 이곳에 모여요."
      />

      <template v-else>
        <div class="experience-list-heading">
          <div>
            <p class="section-kicker">정리된 경험</p>
            <h2 class="section-title">경험 {{ experiences.data.value.totalElements }}개</h2>
          </div>
          <span>최근 수정한 순서</span>
        </div>

        <ul class="experience-list data-list">
          <li
            v-for="item in experiences.data.value.items"
            :key="item.id"
            class="experience-card data-card"
            :class="{ 'experience-card--review': item.reviewRequired }"
            :data-testid="`experience-card-${item.id}`"
          >
            <div class="experience-card__header">
              <div class="experience-card__identity">
                <div class="experience-card__badges">
                  <StatusBadge
                    :label="verificationLabel(item.verificationStatus)"
                    :tone="verificationTone(item.verificationStatus)"
                  />
                  <StatusBadge
                    v-if="item.reviewRequired"
                    :label="matchLabel(item.matchKind)"
                    :tone="matchTone(item.matchKind)"
                  />
                  <StatusBadge
                    v-if="item.githubRepositorySourceCount > 0"
                    label="GitHub 출처"
                    tone="info"
                  />
                </div>
                <h3 v-if="editingId !== item.id">{{ item.title }}</h3>
              </div>
              <div v-if="editingId !== item.id" class="experience-card__actions">
                <button
                  v-if="item.verificationStatus !== 'VERIFIED'"
                  type="button"
                  class="button button--primary button--compact"
                  :disabled="busy"
                  @click="verify(item, 'VERIFIED')"
                >
                  활용 승인
                </button>
                <button
                  v-if="item.verificationStatus !== 'REJECTED'"
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="busy"
                  @click="verify(item, 'REJECTED')"
                >
                  활용 제외
                </button>
                <button
                  v-else
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="busy"
                  @click="verify(item, 'PENDING')"
                >
                  다시 검토
                </button>
                <button
                  type="button"
                  class="button button--ghost button--compact"
                  :disabled="busy"
                  @click="openEdit(item)"
                >
                  수정
                </button>
              </div>
            </div>

            <form
              v-if="editingId === item.id"
              class="experience-card__editor"
              @submit.prevent="save(item)"
            >
              <label class="field">
                <span class="field__label">경험 제목</span>
                <input v-model="edit.title" class="control" maxlength="250" />
              </label>
              <label class="field">
                <span class="field__label">핵심 내용</span>
                <textarea v-model="edit.content" class="control" maxlength="20000" />
              </label>
              <div class="form-actions">
                <button type="submit" class="button button--primary" :disabled="busy">
                  수정 저장
                </button>
                <button
                  type="button"
                  class="button button--secondary"
                  :disabled="busy"
                  @click="cancelEdit"
                >
                  취소
                </button>
              </div>
            </form>

            <template v-else>
              <p class="experience-card__content">{{ item.content }}</p>
              <dl class="experience-card__meta">
                <div>
                  <dt>분류</dt>
                  <dd>{{ categoryLabel(item.evidenceCategory) }}</dd>
                </div>
                <div class="experience-card__meta-wide">
                  <dt>문서 출처</dt>
                  <dd :title="documentSourceLabel(item)">{{ documentSourceLabel(item) }}</dd>
                </div>
                <div>
                  <dt>출처</dt>
                  <dd>{{ item.sourceCount }}개</dd>
                </div>
                <div v-if="item.githubRepositorySourceCount > 0">
                  <dt>GitHub 저장소</dt>
                  <dd>{{ item.githubRepositorySourceCount }}곳</dd>
                </div>
                <div>
                  <dt>최근 수정</dt>
                  <dd>{{ formatDate(item.updatedAt) }}</dd>
                </div>
              </dl>
            </template>

            <p v-if="item.reviewRequired" class="experience-card__review-note">
              <AppIcon name="alert" />
              <span>
                {{ similarityLabel(item.matchSimilarity) }} · 기존 경험과 비교한 뒤 합칠지 선택해
                주세요.
              </span>
              <button type="button" class="text-link" @click="selectExperience(item)">
                비교해서 확인
              </button>
            </p>
          </li>
        </ul>

        <PaginationNav
          v-if="experiences.data.value.totalPages > 1"
          :page="experiences.data.value.page"
          :total-pages="experiences.data.value.totalPages"
          label="경험 보관함 페이지"
          @change="page = $event"
        />
      </template>

      <section
        v-if="selectedId"
        ref="detailPanel"
        class="experience-detail section-surface"
        aria-labelledby="experience-detail-heading"
        tabindex="-1"
      >
        <div class="experience-detail__top">
          <div>
            <p class="section-kicker">비슷한 경험 검토</p>
            <h2 id="experience-detail-heading" class="section-title">내용과 출처 확인</h2>
          </div>
          <button
            type="button"
            class="button button--ghost button--icon"
            aria-label="경험 상세 닫기"
            @click="closeDetail"
          >
            <AppIcon name="close" />
          </button>
        </div>

        <StatePanel
          v-if="detail.isPending.value"
          kind="loading"
          title="경험 상세를 불러오는 중이에요"
        />
        <StatePanel
          v-else-if="detail.isError.value"
          kind="error"
          title="경험 상세를 불러오지 못했어요"
          description="목록을 새로 확인한 뒤 다시 열어 주세요."
        />

        <template v-else-if="detail.data.value">
          <div
            v-if="detail.data.value.item.reviewRequired"
            class="experience-comparison"
            aria-labelledby="experience-comparison-heading"
          >
            <div class="experience-comparison__heading">
              <div>
                <p class="section-kicker">비슷한 경험 확인</p>
                <h3 id="experience-comparison-heading">두 경험이 같은 사건인지 확인해 주세요</h3>
              </div>
              <StatusBadge
                :label="matchLabel(detail.data.value.item.matchKind)"
                :tone="matchTone(detail.data.value.item.matchKind)"
              />
            </div>
            <div class="experience-comparison__grid">
              <article>
                <small>새로 찾은 경험</small>
                <h4>{{ detail.data.value.item.title }}</h4>
                <p>{{ detail.data.value.item.content }}</p>
              </article>
              <article v-if="targetDetail.data.value">
                <small>기존 경험</small>
                <h4>{{ targetDetail.data.value.item.title }}</h4>
                <p>{{ targetDetail.data.value.item.content }}</p>
              </article>
              <article v-else class="experience-comparison__loading">
                <small>기존 경험</small>
                <p>비교할 기존 경험을 불러오는 중이에요.</p>
              </article>
            </div>
            <div class="experience-comparison__actions">
              <button
                type="button"
                class="button button--secondary"
                :disabled="busy"
                @click="resolveMatch(detail.data.value.item, 'KEEP_SEPARATE')"
              >
                별도 경험으로 유지
              </button>
              <button
                type="button"
                class="button button--primary"
                :disabled="busy || !detail.data.value.item.matchedExperienceItemId"
                @click="resolveMatch(detail.data.value.item, 'MERGE_WITH_TARGET')"
              >
                기존 경험에 합치기
              </button>
            </div>
          </div>

          <article class="experience-detail__content">
            <div class="experience-detail__title">
              <h3>{{ detail.data.value.item.title }}</h3>
              <StatusBadge
                :label="verificationLabel(detail.data.value.item.verificationStatus)"
                :tone="verificationTone(detail.data.value.item.verificationStatus)"
              />
            </div>
            <p>{{ detail.data.value.item.content }}</p>
          </article>

          <section class="experience-sources" aria-labelledby="experience-sources-heading">
            <div>
              <p class="section-kicker">출처</p>
              <h3 id="experience-sources-heading">
                이 경험을 확인한 곳 {{ detail.data.value.sources.length }}개
              </h3>
            </div>
            <ul>
              <li v-for="source in detail.data.value.sources" :key="source.evidenceId">
                <span
                  class="icon-tile icon-tile--sm"
                  :class="{ 'icon-tile--success': source.relationKind === 'CORROBORATING' }"
                >
                  <AppIcon
                    :name="source.relationKind === 'CORROBORATING' ? 'check' : 'documents'"
                  />
                </span>
                <div>
                  <strong>{{ sourceLabel(source) }}</strong>
                  <small
                    >{{ formatDate(source.createdAt) }} ·
                    {{ similarityLabel(source.similarity) }}</small
                  >
                  <template v-if="source.sourceType === 'GITHUB_REPOSITORY'">
                    <p v-if="source.sourceDeletedAt" class="experience-source__tombstone">
                      GitHub 연결은 삭제됐지만, 이 경험에 남은 provenance 기록은 보관됩니다.
                    </p>
                    <dl class="experience-source__github">
                      <div v-if="source.repositoryName">
                        <dt>저장소</dt>
                        <dd>{{ source.repositoryName }}</dd>
                      </div>
                      <div v-if="source.commitShaShort">
                        <dt>기준 commit</dt>
                        <dd>
                          <code>{{ source.commitShaShort }}</code>
                        </dd>
                      </div>
                      <div v-if="source.capturedAt">
                        <dt>확인 시각</dt>
                        <dd>{{ formatDate(source.capturedAt) }}</dd>
                      </div>
                    </dl>
                    <p v-if="source.sourceExcerpt" class="experience-source__excerpt">
                      {{ source.sourceExcerpt }}
                    </p>
                  </template>
                </div>
                <RouterLink
                  v-if="source.documentId"
                  class="button button--ghost button--compact"
                  :to="`/documents/${source.documentId}`"
                >
                  자료 보기
                </RouterLink>
                <div
                  v-else-if="source.sourceType === 'GITHUB_REPOSITORY'"
                  class="experience-source__actions"
                >
                  <RouterLink
                    v-if="featureFlags.githubSourceEnabled && source.githubSourceId"
                    class="button button--ghost button--compact"
                    :to="`/profile/github?source=${encodeURIComponent(source.githubSourceId)}`"
                  >
                    GitHub 연결 보기
                  </RouterLink>
                  <a
                    v-if="safeGitHubRepositoryUrl(source.repositoryUrl)"
                    class="button button--ghost button--compact"
                    :href="safeGitHubRepositoryUrl(source.repositoryUrl)!"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    공개 저장소 열기
                  </a>
                </div>
              </li>
            </ul>
          </section>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
/* 안내 문구는 페이지 설명 한 줄로 충분하다. 필터가 곧바로 이어지도록 위 여백만 남긴다. */
.experience-filters,
.experience-message,
.experience-state,
.experience-list-heading,
.experience-list,
.experience-detail {
  margin-top: var(--space-5);
}

/* 설명 줄이 아래 섹션과 같은 폭까지 늘어나 좁은 단으로 접히지 않게 한다. */
.experience-page :deep(.page-header__body),
.experience-page :deep(.page-description) {
  max-width: none;
}

.experience-filters__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 14rem));
  gap: var(--space-4);
}

.experience-list-heading,
.experience-card__header,
.experience-detail__top,
.experience-detail__title,
.experience-comparison__heading,
.experience-comparison__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.experience-list-heading > span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.experience-list {
  margin-top: var(--space-4);
}

.experience-card--review {
  background: color-mix(in srgb, var(--color-warning-soft) 32%, var(--color-surface));
}

.experience-card__identity {
  min-width: 0;
}

.experience-card__identity h3,
.experience-detail__title h3,
.experience-comparison h3,
.experience-comparison h4,
.experience-sources h3 {
  margin: 0;
  color: var(--color-ink-title);
  letter-spacing: -0.02em;
}

.experience-card__identity h3 {
  margin-top: var(--space-2);
  font-size: var(--font-size-lg);
}

.experience-card__badges,
.experience-detail__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.experience-card__content,
.experience-detail__content > p,
.experience-comparison article p {
  margin: var(--space-4) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.75;
  white-space: pre-wrap;
}

.experience-card__content {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

/* 문서 이름이 들어가는 칸만 두 배 폭을 준다. 파일명은 길어도 한 줄로 줄인다. */
.experience-card__meta {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.8fr) minmax(0, 0.8fr) minmax(0, 1fr);
  gap: var(--space-3);
  margin: var(--space-5) 0 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.experience-card__meta-wide dd {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.experience-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.experience-card__editor {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.experience-card__editor textarea {
  min-height: 9rem;
  resize: vertical;
}

.experience-card__meta dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.experience-card__meta dd {
  margin: var(--space-1) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 680;
}

.experience-card__review-note {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-4) 0 0;
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-strong);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-xs);
}

.experience-card__review-note > span {
  flex: 1 1 12rem;
}

.experience-card__review-note .text-link {
  border: 0;
  background: none;
  color: var(--color-brand-strong);
  font: inherit;
  font-weight: 750;
  text-decoration: underline;
  text-underline-offset: 0.18rem;
}

.experience-detail {
  scroll-margin-top: calc(var(--global-header-height) + var(--space-4));
  padding: clamp(var(--space-5), 3vw, var(--space-7));
  outline: none;
}

.experience-detail > .state-panel,
.experience-detail__content,
.experience-sources,
.experience-comparison {
  margin-top: var(--space-5);
}

.experience-detail__content,
.experience-sources,
.experience-comparison {
  border-radius: var(--radius-lg);
  background: var(--color-surface-subtle);
  padding: var(--space-5);
}

.experience-comparison__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.experience-comparison {
  background: var(--color-warning-soft);
}

.experience-comparison__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.experience-comparison article {
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-4);
  box-shadow: var(--shadow-xs);
}

.experience-comparison article small {
  color: var(--color-text-muted);
  font-weight: 700;
}

.experience-comparison article h4 {
  margin-top: var(--space-2);
}

.experience-sources > div h3 {
  font-size: var(--font-size-md);
}

.experience-sources ul {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.experience-sources li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  padding: var(--space-3);
}

.experience-sources strong,
.experience-sources small {
  display: block;
}

.experience-source__github {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin-top: var(--space-2);
  font-size: var(--font-size-xs);
}

.experience-source__github div {
  display: flex;
  gap: var(--space-1);
}

.experience-source__github dt,
.experience-source__tombstone {
  color: var(--color-muted);
}

.experience-source__excerpt {
  margin-top: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--color-fill);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
}

.experience-source__tombstone {
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
}

.experience-source__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.experience-sources strong {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.experience-sources small {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

@media (max-width: 48rem) {
  .experience-card__header,
  .experience-detail__title,
  .experience-comparison__heading {
    align-items: stretch;
    flex-direction: column;
  }

  .experience-card__actions {
    align-self: flex-start;
  }

  .experience-card__meta,
  .experience-comparison__grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 35rem) {
  .experience-filters__form,
  .experience-card__meta,
  .experience-comparison__grid {
    grid-template-columns: 1fr;
  }

  .experience-card__actions {
    width: 100%;
  }

  .experience-card__actions .button,
  .experience-comparison__actions .button,
  .experience-sources li .button {
    flex: 1 1 8rem;
    width: 100%;
  }

  .experience-sources li {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .experience-sources li .button {
    grid-column: 1 / -1;
  }
}
</style>
