<script setup lang="ts">
import { computed, ref } from 'vue'

import { parseJobDescription, parseJobDescriptionInline } from './descriptionParser'

const props = defineProps<{ source: string }>()
const expanded = ref(false)
const nodes = computed(() => parseJobDescription(props.source))
const isLong = computed(() => props.source.length > 4_500 || nodes.value.length > 14)
</script>

<template>
  <div class="job-document">
    <div
      class="job-document__content"
      :class="{ 'job-document__content--collapsed': isLong && !expanded }"
      :aria-label="isLong && !expanded ? '일부만 펼친 공고 본문' : '공고 본문 전체'"
    >
      <template v-for="(node, index) in nodes" :key="`${node.type}-${index}`">
        <h3 v-if="node.type === 'heading'" class="job-document__heading">{{ node.text }}</h3>
        <p v-else-if="node.type === 'paragraph'" class="job-document__paragraph">
          <template
            v-for="(inline, inlineIndex) in parseJobDescriptionInline(node.text)"
            :key="inlineIndex"
          >
            <a
              v-if="inline.type === 'link'"
              :href="inline.href"
              target="_blank"
              rel="noopener noreferrer"
              >{{ inline.value }}</a
            >
            <template v-else>{{ inline.value }}</template>
          </template>
        </p>
        <component
          :is="node.type === 'ordered-list' ? 'ol' : 'ul'"
          v-else
          class="job-document__list"
        >
          <li v-for="(item, itemIndex) in node.items" :key="itemIndex">
            <template
              v-for="(inline, inlineIndex) in parseJobDescriptionInline(item)"
              :key="inlineIndex"
            >
              <a
                v-if="inline.type === 'link'"
                :href="inline.href"
                target="_blank"
                rel="noopener noreferrer"
                >{{ inline.value }}</a
              >
              <template v-else>{{ inline.value }}</template>
            </template>
          </li>
        </component>
      </template>
    </div>
    <button
      v-if="isLong"
      type="button"
      class="button button--ghost job-document__toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      {{ expanded ? '본문 접기' : '공고 본문 전체 보기' }}
    </button>
  </div>
</template>

<style scoped>
.job-document {
  position: relative;
  margin-top: var(--space-6);
}

.job-document__content {
  max-width: 45rem;
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  line-height: 1.82;
}

.job-document__content--collapsed {
  position: relative;
  max-height: 58rem;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, #000 88%, transparent);
}

.job-document__heading {
  margin-top: var(--space-8);
  color: var(--color-text);
  font-size: 1.125rem;
  font-weight: 760;
  line-height: 1.45;
}

.job-document__heading:first-child {
  margin-top: 0;
}

.job-document__paragraph,
.job-document__list {
  margin-top: var(--space-3);
  overflow-wrap: anywhere;
}

.job-document__list {
  display: grid;
  gap: var(--space-2);
  padding-left: 1.4rem;
}

.job-document__list:is(ul) {
  list-style: disc;
}

.job-document__list:is(ol) {
  list-style: decimal;
}

.job-document a {
  color: var(--color-brand-strong);
  text-decoration: underline;
  text-underline-offset: 0.18em;
}

.job-document__toggle {
  margin-top: var(--space-5);
}
</style>
