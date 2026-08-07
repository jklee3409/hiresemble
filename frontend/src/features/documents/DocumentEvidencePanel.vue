<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { profileQueryKeys } from '@/features/profile/queryKeys'
import type {
  EvidenceDto,
  EvidenceUpdateRequest,
  EvidenceVerificationStatus,
} from '@/shared/api/contracts'
import { normalizeApiError } from '@/shared/api/errors'
import * as profileApi from '@/shared/api/profileApi'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PaginationNav from '@/shared/ui/PaginationNav.vue'
import StatePanel from '@/shared/ui/StatePanel.vue'
import StatusBadge from '@/shared/ui/StatusBadge.vue'
import { useNotifications } from '@/shared/ui/notifications'

/* 한 화면에서 검토에 집중할 수 있도록 목록만 나눠 보여 준다. 요약 수치와 일괄 작업은 전체 소재 기준이다. */
const EVIDENCE_PAGE_SIZE = 5

const props = defineProps<{ userId: string; documentId: string; documentName?: string }>()
const notifications = useNotifications()
const selectedIds = ref<string[]>([])
const page = ref(0)
const editingId = ref('')
const actionError = ref('')
const edit = reactive({ title: '', content: '' })
const filters = computed<profileApi.EvidenceListParams>(() => ({
  documentId: props.documentId,
  page: 0,
  size: 100,
  sort: 'updatedAt,desc',
}))
const evidence = useQuery({
  queryKey: computed(() => profileQueryKeys.evidence(props.userId, filters.value)),
  queryFn: () => profileApi.listEvidence(filters.value),
})
const items = computed(() => evidence.data.value?.items ?? [])
const reviewableItems = computed(() =>
  items.value.filter(
    (item) =>
      item.verificationStatus !== 'SOURCE_DELETED' && item.experienceLinkKind !== 'CORROBORATING',
  ),
)
const pendingCount = computed(
  () => reviewableItems.value.filter((item) => item.verificationStatus === 'PENDING').length,
)
const approvedCount = computed(
  () => reviewableItems.value.filter((item) => item.verificationStatus === 'VERIFIED').length,
)
const excludedCount = computed(
  () => reviewableItems.value.filter((item) => item.verificationStatus === 'REJECTED').length,
)
const corroboratingCount = computed(
  () => items.value.filter((item) => item.experienceLinkKind === 'CORROBORATING').length,
)
const selectedItems = computed(() =>
  reviewableItems.value.filter((item) => selectedIds.value.includes(item.id)),
)
const allSelected = computed(
  () =>
    reviewableItems.value.length > 0 && selectedItems.value.length === reviewableItems.value.length,
)
const totalPages = computed(() => Math.max(1, Math.ceil(items.value.length / EVIDENCE_PAGE_SIZE)))
const pageStart = computed(() => page.value * EVIDENCE_PAGE_SIZE)
const visibleItems = computed(() =>
  items.value.slice(pageStart.value, pageStart.value + EVIDENCE_PAGE_SIZE),
)
const rangeLabel = computed(() =>
  items.value.length === 0
    ? '0개 소재'
    : `${items.value.length}개 소재 중 ${pageStart.value + 1}–${pageStart.value + visibleItems.value.length}번째`,
)

watch(items, (next) => {
  const valid = new Set(next.map((item) => item.id))
  selectedIds.value = selectedIds.value.filter((id) => valid.has(id))
})

/* 소재가 줄어 마지막 쪽이 사라지면 빈 목록이 남지 않도록 마지막 쪽으로 당긴다. */
watch(totalPages, (next) => {
  if (page.value > next - 1) page.value = next - 1
})

const updateMutation = useMutation({
  mutationFn: ({ item, request }: { item: EvidenceDto; request: EvidenceUpdateRequest }) =>
    profileApi.updateEvidence(item.id, request),
})
const verifyMutation = useMutation({
  mutationFn: ({
    item,
    status,
  }: {
    item: EvidenceDto
    status: 'PENDING' | 'VERIFIED' | 'REJECTED'
  }) => profileApi.verifyEvidence(item.id, { status, version: item.version }),
})
const batchMutation = useMutation({
  mutationFn: ({
    targets,
    status,
  }: {
    targets: EvidenceDto[]
    status: 'PENDING' | 'VERIFIED' | 'REJECTED'
  }) =>
    profileApi.verifyEvidenceBatch({
      items: targets.map((item) => ({ id: item.id, version: item.version })),
      status,
    }),
})
const busy = computed(
  () =>
    updateMutation.isPending.value ||
    verifyMutation.isPending.value ||
    batchMutation.isPending.value,
)

function toggleAll(): void {
  selectedIds.value = allSelected.value ? [] : reviewableItems.value.map((item) => item.id)
}

function openEdit(item: EvidenceDto): void {
  editingId.value = item.id
  edit.title = item.title
  edit.content = item.content
  actionError.value = ''
}

async function save(item: EvidenceDto): Promise<void> {
  if (!edit.title.trim() || !edit.content.trim()) {
    actionError.value = '제목과 핵심 내용은 비워 둘 수 없어요.'
    return
  }
  try {
    await updateMutation.mutateAsync({
      item,
      request: {
        title: edit.title.trim(),
        content: edit.content.trim(),
        metadata: item.metadata,
        version: item.version,
      },
    })
    editingId.value = ''
    notifications.toast('소재 내용을 수정했어요.', 'success')
    await evidence.refetch()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
    await evidence.refetch()
  }
}

async function changeStatus(
  item: EvidenceDto,
  status: 'PENDING' | 'VERIFIED' | 'REJECTED',
): Promise<void> {
  if (item.verificationStatus === 'SOURCE_DELETED') return
  if (status === 'PENDING') {
    const confirmed = await notifications.confirm({
      title: '이 소재를 다시 검토할까요?',
      message:
        '활용 상태가 검토 전으로 돌아가며, 다시 승인하기 전까지 자소서와 면접 준비에 사용되지 않아요.',
      confirmLabel: '다시 검토',
    })
    if (!confirmed) return
  }
  try {
    await verifyMutation.mutateAsync({ item, status })
    notifications.toast(statusMessage(status, 1), 'success')
    await evidence.refetch()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
    await evidence.refetch()
  }
}

async function changeSelected(
  status: 'VERIFIED' | 'REJECTED',
  targets = selectedItems.value,
): Promise<void> {
  if (targets.length === 0) return
  try {
    await batchMutation.mutateAsync({ targets, status })
    selectedIds.value = []
    notifications.toast(statusMessage(status, targets.length), 'success')
    await evidence.refetch()
  } catch (error) {
    actionError.value = normalizeApiError(error).message
    await evidence.refetch()
  }
}

function statusMessage(status: 'PENDING' | 'VERIFIED' | 'REJECTED', count: number): string {
  if (status === 'VERIFIED') return `${count}개 소재를 활용 승인했어요.`
  if (status === 'REJECTED') return `${count}개 소재를 활용에서 제외했어요.`
  return '소재를 다시 검토할 수 있게 바꿨어요.'
}

function statusLabel(status: EvidenceVerificationStatus): string {
  return {
    PENDING: '검토 전',
    VERIFIED: '활용 승인',
    REJECTED: '활용 제외',
    SOURCE_DELETED: '원본 삭제됨',
  }[status]
}

function statusTone(status: EvidenceVerificationStatus): 'neutral' | 'success' | 'warning' {
  if (status === 'VERIFIED') return 'success'
  if (status === 'REJECTED' || status === 'SOURCE_DELETED') return 'warning'
  return 'neutral'
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
</script>

<template>
  <section class="evidence-review section-surface" aria-labelledby="evidence-review-heading">
    <header class="evidence-review__header">
      <div>
        <p class="section-kicker">3. 자소서 소재 검토</p>
        <h3 id="evidence-review-heading" class="section-title">AI가 찾은 경험을 확인해 주세요</h3>
        <p class="evidence-review__description">
          승인한 내용만 자소서와 면접 준비의 소재 후보로 활용해요. 제외해도 업로드한 원본과 분석
          기록은 삭제되지 않으며 언제든 다시 검토할 수 있어요.
        </p>
      </div>
      <div class="evidence-review__summary" aria-label="소재 검토 현황">
        <strong>{{ pendingCount > 0 ? `${pendingCount}개 확인 필요` : '검토 완료' }}</strong>
        <span>
          승인 {{ approvedCount }} · 제외 {{ excludedCount }}
          <template v-if="corroboratingCount"> · 기존 경험 출처 {{ corroboratingCount }}</template>
        </span>
      </div>
    </header>

    <div v-if="reviewableItems.length" class="review-progress">
      <span :class="{ 'review-progress__step--active': pendingCount > 0 }">내용 확인</span>
      <span aria-hidden="true">→</span>
      <span :class="{ 'review-progress__step--complete': pendingCount === 0 }">활용 여부 결정</span>
      <span aria-hidden="true">→</span>
      <span>자소서·면접에서 선택</span>
    </div>

    <p v-if="actionError" class="alert alert--danger evidence-review__message" role="alert">
      {{ actionError }}
    </p>
    <StatePanel
      v-if="evidence.isPending.value"
      class="evidence-review__state"
      kind="loading"
      title="추출한 소재를 불러오는 중이에요"
    />
    <StatePanel
      v-else-if="evidence.isError.value"
      class="evidence-review__state"
      kind="error"
      title="소재를 불러오지 못했어요"
      description="잠시 후 다시 시도해 주세요."
    />
    <StatePanel
      v-else-if="items.length === 0"
      class="evidence-review__state"
      kind="empty"
      title="아직 검토할 소재가 없어요"
      description="분석이 끝나면 문서에서 찾은 경험이 이곳에 표시돼요."
    />

    <template v-else>
      <div v-if="reviewableItems.length" class="review-toolbar" aria-label="소재 일괄 작업">
        <label class="review-toolbar__selection">
          <input type="checkbox" :checked="allSelected" @change="toggleAll" />
          전체 선택
        </label>
        <span>{{ selectedItems.length ? `${selectedItems.length}개 선택` : rangeLabel }}</span>
        <div class="review-toolbar__actions">
          <button
            type="button"
            class="button button--secondary button--compact"
            :disabled="busy || selectedItems.length === 0"
            @click="changeSelected('VERIFIED')"
          >
            선택 승인
          </button>
          <button
            type="button"
            class="button button--ghost button--compact"
            :disabled="busy || selectedItems.length === 0"
            @click="changeSelected('REJECTED')"
          >
            선택 제외
          </button>
          <button
            v-if="pendingCount"
            type="button"
            class="button button--primary button--compact"
            :disabled="busy"
            @click="
              changeSelected(
                'VERIFIED',
                reviewableItems.filter((item) => item.verificationStatus === 'PENDING'),
              )
            "
          >
            검토 전 모두 승인
          </button>
        </div>
      </div>

      <ul class="evidence-review__list">
        <li
          v-for="item in visibleItems"
          :key="item.id"
          class="evidence-card"
          :class="`evidence-card--${item.verificationStatus.toLowerCase()}`"
        >
          <template v-if="item.experienceLinkKind === 'CORROBORATING' && item.experienceItemId">
            <div class="evidence-card__existing-header">
              <span class="icon-tile icon-tile--success" aria-hidden="true">
                <AppIcon name="check" />
              </span>
              <div>
                <div class="evidence-card__title">
                  <h4>{{ item.title }}</h4>
                  <StatusBadge label="기존 경험에 출처 추가됨" tone="info" />
                </div>
                <p>
                  {{ categoryLabel(item.evidenceCategory) }} · {{ documentName ?? '업로드 자료' }}
                </p>
              </div>
            </div>
            <p class="evidence-card__content">{{ item.content }}</p>
            <div class="evidence-card__existing-note">
              <p>
                이미 보관한 경험과 같은 내용으로 확인되어 새 카드로 만들지 않았어요. 이 자료는 기존
                경험의 보강 출처로 연결됐어요.
              </p>
              <RouterLink
                class="button button--secondary button--compact"
                :to="{ path: '/profile/experiences', query: { selected: item.experienceItemId } }"
              >
                경험 보관함에서 보기
              </RouterLink>
            </div>
          </template>
          <template v-else-if="editingId === item.id">
            <form class="evidence-editor" @submit.prevent="save(item)">
              <label class="field"
                ><span class="field__label">소재 제목</span
                ><input v-model="edit.title" class="control" maxlength="250"
              /></label>
              <label class="field"
                ><span class="field__label">핵심 내용</span
                ><textarea
                  v-model="edit.content"
                  class="control evidence-editor__content"
                  maxlength="20000"
                />
              </label>
              <div class="form-actions">
                <button class="button button--primary button--compact" type="submit">
                  수정 저장</button
                ><button
                  class="button button--secondary button--compact"
                  type="button"
                  @click="editingId = ''"
                >
                  취소
                </button>
              </div>
            </form>
          </template>
          <template v-else>
            <div class="evidence-card__header">
              <label
                v-if="item.verificationStatus !== 'SOURCE_DELETED'"
                class="evidence-card__check"
                ><input v-model="selectedIds" type="checkbox" :value="item.id" /><span
                  class="sr-only"
                  >{{ item.title }} 선택</span
                ></label
              >
              <div class="evidence-card__identity">
                <div class="evidence-card__title">
                  <h4>{{ item.title }}</h4>
                  <StatusBadge
                    :label="statusLabel(item.verificationStatus)"
                    :tone="statusTone(item.verificationStatus)"
                  />
                </div>
                <p>
                  {{ categoryLabel(item.evidenceCategory) }} · 출처
                  {{ documentName ?? '업로드 자료' }}
                </p>
              </div>
            </div>
            <p class="evidence-card__content">{{ item.content }}</p>
            <details v-if="Object.keys(item.metadata).length" class="evidence-card__details">
              <summary>AI가 정리한 참고 정보</summary>
              <dl>
                <template v-for="(value, key) in item.metadata" :key="key"
                  ><dt>{{ key }}</dt>
                  <dd>{{ value ?? '-' }}</dd></template
                >
              </dl>
            </details>
            <div v-if="item.verificationStatus !== 'SOURCE_DELETED'" class="evidence-card__actions">
              <button
                type="button"
                class="button button--ghost button--compact"
                @click="openEdit(item)"
              >
                내용 다듬기
              </button>
              <template v-if="item.verificationStatus === 'PENDING'">
                <button
                  type="button"
                  class="button button--primary button--compact"
                  :disabled="busy"
                  @click="changeStatus(item, 'VERIFIED')"
                >
                  활용 승인
                </button>
                <button
                  type="button"
                  class="button button--secondary button--compact"
                  :disabled="busy"
                  @click="changeStatus(item, 'REJECTED')"
                >
                  활용 제외
                </button>
              </template>
              <button
                v-else
                type="button"
                class="button button--secondary button--compact"
                :disabled="busy"
                @click="changeStatus(item, 'PENDING')"
              >
                다시 검토
              </button>
            </div>
            <p v-else class="alert alert--warning evidence-card__readonly">
              원본이 삭제되어 기록만 확인할 수 있어요. 내용과 활용 상태는 변경할 수 없어요.
            </p>
          </template>
        </li>
      </ul>

      <PaginationNav
        v-if="totalPages > 1"
        :page="page"
        :total-pages="totalPages"
        label="찾은 경험 페이지"
        @change="page = $event"
      />
    </template>
  </section>
</template>

<style scoped>
.evidence-review {
  margin-top: var(--space-5);
  padding: clamp(var(--space-5), 3vw, var(--space-7));
}
.evidence-review__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
}
.evidence-review__description {
  max-width: 48rem;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}
.evidence-review__summary {
  flex: 0 0 auto;
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-brand-soft);
  text-align: right;
}
.evidence-review__summary strong {
  color: var(--color-brand-strong);
}
.evidence-review__summary span,
.review-toolbar > span {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.review-progress {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-5);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.review-progress__step--active {
  color: var(--color-warning-strong);
  font-weight: 750;
}
.review-progress__step--complete {
  color: var(--color-success-strong);
  font-weight: 750;
}
.evidence-review__message,
.evidence-review__state {
  margin-top: var(--space-4);
}
.review-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding: var(--space-3);
  border-block: 1px solid var(--color-border);
}
.review-toolbar__selection {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: 700;
}
.review-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-left: auto;
}
.evidence-review__list {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
/*
 * 경험 소재 카드. 왼쪽 색 띠 없이 흰 면과 옅은 그림자만으로 세운다.
 * 확인 여부는 제목 옆 상태 badge가 이미 글자로 알려 준다.
 */
.evidence-card {
  padding: var(--space-6);
  border: 0;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  transition:
    box-shadow var(--motion-base),
    transform var(--motion-base);
}
.evidence-card:hover {
  box-shadow: var(--shadow-lift);
  transform: translateY(-1px);
}
/* 제외한 소재는 앞으로 나오지 않도록 면을 눌러 둔다. */
.evidence-card--rejected {
  background: var(--color-surface-subtle);
  box-shadow: none;
}
.evidence-card--rejected:hover {
  box-shadow: var(--shadow-sm);
  transform: none;
}
.evidence-card__header,
.evidence-card__existing-header,
.evidence-card__title,
.evidence-card__actions {
  display: flex;
  align-items: center;
}
.evidence-card__header {
  gap: var(--space-3);
}
.evidence-card__existing-header {
  gap: var(--space-3);
}
.evidence-card__existing-header > div {
  min-width: 0;
}
.evidence-card__existing-header p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.evidence-card__check {
  align-self: flex-start;
  padding-top: 0.2rem;
}
.evidence-card__identity {
  min-width: 0;
}
.evidence-card__title {
  flex-wrap: wrap;
  gap: var(--space-2);
}
.evidence-card__title h4 {
  color: var(--color-ink-title);
  font-size: var(--font-size-md);
  font-weight: 780;
  letter-spacing: -0.015em;
}
.evidence-card__identity > p {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
.evidence-card__content {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.75;
  white-space: pre-wrap;
}
.evidence-card__details {
  margin-top: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}
.evidence-card__details summary {
  cursor: pointer;
  font-weight: 700;
}
.evidence-card__details dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-1) var(--space-3);
  margin-top: var(--space-2);
}
.evidence-card__details dt {
  color: var(--color-text-muted);
}
.evidence-card__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}
.evidence-card__readonly {
  margin-top: var(--space-3);
}
.evidence-card__existing-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-info-soft);
  color: var(--color-info-strong);
  padding: var(--space-3) var(--space-4);
}
.evidence-card__existing-note p {
  margin: 0;
  font-size: var(--font-size-xs);
  line-height: 1.6;
}
.evidence-editor {
  display: grid;
  gap: var(--space-3);
}
.evidence-editor__content {
  min-height: 9rem;
}
@media (max-width: 48rem) {
  .evidence-review__header {
    flex-direction: column;
  }
  .evidence-review__summary {
    width: 100%;
    text-align: left;
  }
  .review-toolbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .review-toolbar__actions {
    width: 100%;
    margin-left: 0;
  }
  .review-toolbar__actions .button {
    flex: 1 1 auto;
  }
  .evidence-card__existing-note {
    align-items: stretch;
    flex-direction: column;
  }
  .evidence-card__existing-note .button {
    width: 100%;
  }
}
</style>
