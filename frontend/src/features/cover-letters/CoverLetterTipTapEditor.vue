<script setup lang="ts">
import StarterKit from '@tiptap/starter-kit'
import { Editor, EditorContent } from '@tiptap/vue-3'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import {
  canonicalizeEditorContent,
  sameTipTapContent,
} from '@/features/cover-letters/editorContent'
import type { TipTapDocumentDto } from '@/shared/api/coverLetterContracts'

interface EditorJsonContent {
  type?: string
  text?: string
  marks?: Array<{ type: string }>
  content?: EditorJsonContent[]
}

const props = defineProps<{
  content: TipTapDocumentDto
  readonly?: boolean
  maxLength?: number | null
  serverCharacterCount?: number | null
}>()
const emit = defineEmits<{
  update: [content: TipTapDocumentDto, characterCount: number]
}>()
const previewCount = ref(canonicalizeEditorContent(props.content).characterCount)

const editor = new Editor({
  content: toEditorContent(props.content),
  editable: !props.readonly,
  extensions: [
    StarterKit.configure({
      heading: false,
      blockquote: false,
      code: false,
      codeBlock: false,
      horizontalRule: false,
      strike: false,
    }),
  ],
  editorProps: {
    attributes: {
      role: 'textbox',
      'aria-label': '자기소개서 답변',
      'aria-multiline': 'true',
      class: 'cover-tiptap__content',
    },
  },
  onUpdate: ({ editor: current }) => {
    try {
      const canonical = canonicalizeEditorContent(current.getJSON())
      previewCount.value = canonical.characterCount
      emit('update', canonical.document, canonical.characterCount)
    } catch {
      // Only the configured allowlist is emitted. An invalid transient document is not persisted.
    }
  },
})

watch(
  () => props.readonly,
  (readonly) => editor.setEditable(!readonly),
)

watch(
  () => props.content,
  (content) => {
    try {
      const current = canonicalizeEditorContent(editor.getJSON()).document
      if (!sameTipTapContent(current, content)) {
        editor.commands.setContent(toEditorContent(content), { emitUpdate: false })
      }
      previewCount.value = canonicalizeEditorContent(content).characterCount
    } catch {
      editor.commands.setContent(toEditorContent(content), { emitUpdate: false })
      previewCount.value = 0
    }
  },
  { deep: true },
)

onBeforeUnmount(() => editor.destroy())

const effectiveCount = computed(() => props.serverCharacterCount ?? previewCount.value)
const overLimit = computed(
  () =>
    props.maxLength !== null &&
    props.maxLength !== undefined &&
    effectiveCount.value > props.maxLength,
)

function insertSuggestion(suggestion: string): void {
  if (props.readonly || suggestion.trim().length === 0) return
  editor.chain().focus().insertContent(suggestion.trim()).run()
}

defineExpose({ insertSuggestion })

function toEditorContent(value: TipTapDocumentDto): EditorJsonContent {
  return {
    type: 'doc',
    content: value.content.map((node) => ({
      type: node.type,
      ...(node.text === null ? {} : { text: node.text }),
      ...(node.marks.length === 0
        ? {}
        : { marks: node.marks.map((mark) => ({ type: mark.type })) }),
      ...(node.content.length === 0
        ? {}
        : {
            content: node.content.map((child) =>
              toEditorContent({ type: 'doc', content: [child] }).content!.at(0)!,
            ),
          }),
    })),
  }
}
</script>

<template>
  <div class="cover-tiptap" :class="{ 'cover-tiptap--readonly': readonly }">
    <div class="cover-tiptap__toolbar" role="toolbar" aria-label="답변 서식">
      <button
        type="button"
        :aria-pressed="editor.isActive('bold')"
        :disabled="readonly"
        @click="editor.chain().focus().toggleBold().run()"
      >
        굵게
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('italic')"
        :disabled="readonly"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        기울임
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('bulletList')"
        :disabled="readonly"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        글머리 목록
      </button>
      <button
        type="button"
        :aria-pressed="editor.isActive('orderedList')"
        :disabled="readonly"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        번호 목록
      </button>
    </div>
    <EditorContent :editor="editor" />
    <div class="cover-tiptap__footer">
      <span v-if="readonly">읽기 전용</span>
      <span
        :class="{ 'cover-tiptap__count--over': overLimit }"
        aria-live="polite"
        aria-label="답변 글자 수"
      >
        {{ effectiveCount }}{{ maxLength ? ` / ${maxLength}` : '' }}자
      </span>
    </div>
  </div>
</template>

<style scoped>
.cover-tiptap {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.cover-tiptap--readonly {
  background: var(--color-surface-subtle);
}

.cover-tiptap__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-subtle);
  padding: var(--space-2);
}

.cover-tiptap__toolbar button {
  min-height: 2.25rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  padding: 0.375rem 0.625rem;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.cover-tiptap__toolbar button:hover:not(:disabled),
.cover-tiptap__toolbar button[aria-pressed='true'] {
  border-color: var(--color-brand-border);
  background: var(--color-brand-soft);
  color: var(--color-brand-strong);
}

.cover-tiptap__toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/*
 * 본문 높이는 화면에 맞춘다. 답변이 길어져도 페이지 전체가 아니라 이 영역만 스크롤해
 * 문항·글자 수·저장 button이 한 화면에 남아 있게 한다.
 */
:deep(.cover-tiptap__content) {
  box-sizing: border-box;
  width: 100%;
  height: var(--cover-editor-height, clamp(12rem, calc(100dvh - 28rem), 40rem));
  overflow-y: auto;
  padding: var(--space-4) var(--space-5);
  outline: none;
  font-size: 0.8125rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

:deep(.cover-tiptap__content:focus-visible) {
  background: var(--color-surface-subtle);
}

:deep(.cover-tiptap__content p + p),
:deep(.cover-tiptap__content ul + p),
:deep(.cover-tiptap__content ol + p) {
  margin-top: var(--space-3);
}

:deep(.cover-tiptap__content ul),
:deep(.cover-tiptap__content ol) {
  margin: var(--space-3) 0;
  padding-left: var(--space-6);
}

:deep(.cover-tiptap__content ul) {
  list-style: disc;
}

:deep(.cover-tiptap__content ol) {
  list-style: decimal;
}

.cover-tiptap__footer {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.cover-tiptap__count--over {
  color: var(--color-danger-strong);
  font-weight: 750;
}

@media (max-width: 40rem) {
  :deep(.cover-tiptap__content) {
    height: var(--cover-editor-height, clamp(12rem, calc(100dvh - 24rem), 32rem));
    padding: var(--space-4);
  }

  .cover-tiptap__toolbar button {
    flex: 1 1 auto;
  }
}
</style>
