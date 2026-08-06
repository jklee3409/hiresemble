<script setup lang="ts">
import { computed } from 'vue'

import { evidenceCurrentState } from '@/features/cover-letters/presentation'
import type { EvidenceDto } from '@/shared/api/contracts'
import type { EvidenceRefDto } from '@/shared/api/jobContracts'
import AppIcon from '@/shared/ui/AppIcon.vue'

/*
 * 답변에 사용할 소재 고르기.
 * 편집기 아래 button에서 펼쳐지는 영역이라 다른 내용을 아래로 밀지 않는다.
 * 이미 이 답변의 근거가 된 소재와 아직 쓰지 않은 소재를 구분해 보여 준다.
 */

const props = withDefaults(
  defineProps<{
    usedEvidence: readonly EvidenceRefDto[]
    evidenceItems: readonly EvidenceDto[]
    recommendedEvidenceIds: ReadonlySet<string>
    selectedEvidenceIds: ReadonlySet<string>
    loading?: boolean
    error?: boolean
    readOnly?: boolean
  }>(),
  { loading: false, error: false, readOnly: false },
)
const emit = defineEmits<{ toggle: [evidenceId: string]; clear: []; close: [] }>()

const usedEvidenceIds = computed(() => new Set(props.usedEvidence.map((item) => item.id)))
const unusedEvidence = computed(() =>
  props.evidenceItems.filter((item) => !usedEvidenceIds.value.has(item.id)),
)
const selectedCount = computed(
  () => unusedEvidence.value.filter((item) => props.selectedEvidenceIds.has(item.id)).length,
)

function snippet(item: EvidenceDto): string {
  const content = (item.content ?? '').replace(/\s+/g, ' ').trim()
  return content.length > 80 ? `${content.slice(0, 80)}…` : content
}
</script>

<template>
  <div class="material-picker">
    <header class="material-picker__head">
      <p>
        여기서 고른 소재를 다음 <strong>AI 초안</strong>에 먼저 써요. 고르지 않으면 확인해 둔 경험
        전체에서 알맞은 것을 골라 써요.
      </p>
      <button
        v-if="!readOnly && selectedCount > 0"
        type="button"
        class="button button--ghost button--compact"
        @click="emit('clear')"
      >
        모두 해제
      </button>
    </header>

    <p v-if="loading" class="material-picker__note">확인해 둔 경험을 불러오는 중이에요…</p>
    <p v-else-if="error" class="material-picker__note material-picker__note--warn">
      경험 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
    </p>
    <ul v-else-if="unusedEvidence.length" class="assist__evidence material-picker__list">
      <li v-for="item in unusedEvidence" :key="item.id">
        <label :class="{ 'assist__evidence--on': selectedEvidenceIds.has(item.id) }">
          <input
            type="checkbox"
            class="checkbox-control"
            :checked="selectedEvidenceIds.has(item.id)"
            :disabled="readOnly"
            @change="emit('toggle', item.id)"
          />
          <span>
            <strong>{{ item.title }}</strong>
            <em v-if="recommendedEvidenceIds.has(item.id)">이 공고와 잘 맞아요</em>
            <small v-if="snippet(item)">{{ snippet(item) }}</small>
          </span>
        </label>
      </li>
    </ul>
    <p v-else-if="evidenceItems.length" class="material-picker__note">
      확인해 둔 경험을 이 답변에 모두 썼어요.
    </p>
    <template v-else>
      <p class="material-picker__note">
        아직 확인해 둔 경험이 없어요. 이력서·자료를 올리고 경험을 확인하면 소재로 쓸 수 있어요.
      </p>
      <RouterLink :to="{ name: 'documents' }" class="text-link">이력서·자료 올리러 가기</RouterLink>
    </template>

    <section v-if="usedEvidence.length" class="material-picker__used">
      <h3>이 답변에 이미 쓴 소재</h3>
      <ul>
        <li v-for="reference in usedEvidence" :key="reference.id">
          <AppIcon name="evidence" />
          <span>
            {{ reference.title }}
            <small>{{ evidenceCurrentState(reference).label }}</small>
            <small v-if="evidenceCurrentState(reference).excludedFromNewContext">
              새 초안·검토에서는 쓰지 않아요
            </small>
          </span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.material-picker {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.material-picker__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.material-picker__head p {
  flex: 1 1 16rem;
  min-width: 0;
}

.material-picker__head strong {
  color: var(--color-ink-title);
  font-weight: 750;
}

.material-picker__note {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.material-picker__note--warn {
  color: var(--color-warning-strong);
}

.material-picker__list {
  display: grid;
  gap: var(--space-1);
}

.material-picker__list label {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  font-size: var(--font-size-sm);
}

.material-picker__list label:hover {
  background: var(--color-fill);
}

.material-picker__list strong {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.material-picker__list em {
  margin-left: var(--space-2);
  color: var(--color-brand-strong);
  font-size: var(--font-size-xs);
  font-style: normal;
  font-weight: 700;
}

.material-picker__list small {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.material-picker__used {
  display: grid;
  gap: var(--space-2);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
}

.material-picker__used h3 {
  color: var(--color-ink-title);
  font-size: var(--font-size-sm);
  font-weight: 780;
}

.material-picker__used ul {
  display: grid;
  gap: var(--space-2);
}

.material-picker__used li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.55;
}

.material-picker__used :deep(.icon) {
  width: 1rem;
  height: 1rem;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.material-picker__used small {
  display: block;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}
</style>
