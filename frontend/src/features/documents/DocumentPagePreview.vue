<script setup lang="ts">
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist'
import { nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'

import { createDocumentDownloadUrl } from '@/shared/api/documentApi'
import { normalizeApiError } from '@/shared/api/errors'
import PaginationNav from '@/shared/ui/PaginationNav.vue'

/*
 * 원본 PDF를 브라우저에서 직접 그려 한 번에 한 페이지만 보여 준다.
 * 페이지를 넘길 때마다 다시 내려받지 않도록 원본은 처음 한 번만 받아 두고,
 * pdf.js는 실제로 미리보기를 여는 화면에서만 필요하므로 동적 import로 분리한다.
 */
const MIN_RENDER_WIDTH = 280
const MAX_RENDER_WIDTH = 900
const MIN_RENDER_HEIGHT = 320
const MAX_RENDER_HEIGHT = 880
const VIEWPORT_HEIGHT_RATIO = 0.74
const MAX_PIXEL_RATIO = 2
const RESIZE_DEBOUNCE_MS = 180

const props = defineProps<{ documentId: string }>()
const emit = defineEmits<{ unavailable: [reason: string] }>()

const canvas = ref<HTMLCanvasElement | null>(null)
const frame = ref<HTMLElement | null>(null)
const pdf = shallowRef<PDFDocumentProxy | null>(null)
const pageIndex = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loadError = ref('')
const renderError = ref('')
const canvasWidth = ref(0)
const canvasHeight = ref(0)

let renderToken = 0
let activeRender: RenderTask | null = null
let resizeObserver: ResizeObserver | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null

watch(() => props.documentId, load, { immediate: true })
watch(pageIndex, () => void renderCurrentPage())

onBeforeUnmount(teardown)

async function load(): Promise<void> {
  teardown()
  if (props.documentId === '') return
  loading.value = true
  loadError.value = ''
  renderError.value = ''
  try {
    const [pdfjs, workerUrl, download] = await Promise.all([
      import('pdfjs-dist'),
      import('pdfjs-dist/build/pdf.worker.min.mjs?url').then((module) => module.default),
      createDocumentDownloadUrl(props.documentId),
    ])
    pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
    const response = await fetch(download.url)
    if (!response.ok) throw new Error(`원본을 내려받지 못했어요. (HTTP ${response.status})`)
    const loaded = await pdfjs.getDocument({ data: await response.arrayBuffer() }).promise
    pdf.value = loaded
    totalPages.value = loaded.numPages
    pageIndex.value = 0
    // canvas는 불러오는 동안 DOM에 없다. 상태를 먼저 내려 붙인 뒤에 첫 페이지를 그린다.
    loading.value = false
    await nextTick()
    await renderCurrentPage()
    observeResize()
  } catch (error) {
    loadError.value = messageOf(error)
    emit('unavailable', loadError.value)
  } finally {
    loading.value = false
  }
}

async function renderCurrentPage(): Promise<void> {
  const loaded = pdf.value
  const target = canvas.value
  if (loaded === null || target === null) return
  const token = (renderToken += 1)
  activeRender?.cancel()
  activeRender = null
  renderError.value = ''
  try {
    const page = await loaded.getPage(pageIndex.value + 1)
    if (token !== renderToken) return
    /* 스크롤 없이 한 페이지가 통째로 보이도록 프레임 폭과 화면 높이 중 더 빡빡한 쪽에 맞춘다. */
    const base = page.getViewport({ scale: 1 })
    const boxWidth = clamp(frameWidth(), MIN_RENDER_WIDTH, MAX_RENDER_WIDTH)
    const boxHeight = clamp(
      Math.round(window.innerHeight * VIEWPORT_HEIGHT_RATIO),
      MIN_RENDER_HEIGHT,
      MAX_RENDER_HEIGHT,
    )
    const scale = Math.min(boxWidth / base.width, boxHeight / base.height)
    const ratio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO)
    const viewport = page.getViewport({ scale: scale * ratio })
    target.width = Math.floor(viewport.width)
    target.height = Math.floor(viewport.height)
    canvasWidth.value = Math.round(base.width * scale)
    canvasHeight.value = Math.round(base.height * scale)
    const task = page.render({ canvas: target, viewport })
    activeRender = task
    await task.promise
    if (token === renderToken) page.cleanup()
  } catch (error) {
    // 페이지를 빠르게 넘기면 앞선 render가 취소된다. 취소는 오류로 알리지 않는다.
    if (token !== renderToken || isRenderCancelled(error)) return
    renderError.value = messageOf(error)
  } finally {
    if (token === renderToken) activeRender = null
  }
}

function frameWidth(): number {
  const element = frame.value
  if (element === null) return MAX_RENDER_WIDTH
  const style = window.getComputedStyle(element)
  const padding = Number.parseFloat(style.paddingLeft) + Number.parseFloat(style.paddingRight)
  return element.clientWidth - (Number.isFinite(padding) ? padding : 0)
}

function observeResize(): void {
  window.addEventListener('resize', scheduleRerender)
  if (typeof ResizeObserver !== 'function' || frame.value === null) return
  resizeObserver = new ResizeObserver(scheduleRerender)
  resizeObserver.observe(frame.value)
}

function scheduleRerender(): void {
  if (resizeTimer !== null) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => void renderCurrentPage(), RESIZE_DEBOUNCE_MS)
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function teardown(): void {
  renderToken += 1
  activeRender?.cancel()
  activeRender = null
  window.removeEventListener('resize', scheduleRerender)
  resizeObserver?.disconnect()
  resizeObserver = null
  if (resizeTimer !== null) clearTimeout(resizeTimer)
  resizeTimer = null
  void pdf.value?.loadingTask.destroy()
  pdf.value = null
  totalPages.value = 0
  pageIndex.value = 0
  canvasWidth.value = 0
  canvasHeight.value = 0
}

function isRenderCancelled(error: unknown): boolean {
  return error instanceof Error && error.name === 'RenderingCancelledException'
}

function messageOf(error: unknown): string {
  if (error instanceof Error && error.name === 'PasswordException') {
    return '암호가 걸린 PDF는 미리보기를 만들 수 없어요. 원본 파일 열기로 확인해 주세요.'
  }
  if (error instanceof Error && error.name === 'InvalidPDFException') {
    return '원본을 PDF로 읽지 못했어요. 원본 파일 열기로 확인해 주세요.'
  }
  if (error instanceof TypeError) {
    return '원본 저장소에 접근하지 못했어요. 원본 파일 열기로 확인해 주세요.'
  }
  return normalizeApiError(error).message
}
</script>

<template>
  <div class="page-preview">
    <p v-if="loading" class="page-preview__status" role="status">원본을 불러오는 중…</p>
    <p v-else-if="loadError" class="alert alert--warning page-preview__status" role="alert">
      {{ loadError }}
    </p>
    <template v-else>
      <div ref="frame" class="page-preview__frame">
        <canvas
          ref="canvas"
          class="page-preview__canvas"
          :style="
            canvasWidth ? { width: `${canvasWidth}px`, height: `${canvasHeight}px` } : undefined
          "
          role="img"
          :aria-label="`원본 ${totalPages}쪽 중 ${pageIndex + 1}쪽`"
        />
      </div>
      <p v-if="renderError" class="alert alert--warning page-preview__status" role="alert">
        {{ renderError }}
      </p>
      <PaginationNav
        v-if="totalPages > 0"
        :page="pageIndex"
        :total-pages="totalPages"
        label="원본 페이지"
        @change="pageIndex = $event"
      />
    </template>
  </div>
</template>

<style scoped>
.page-preview {
  margin-top: var(--space-4);
}

/* 한 페이지가 프레임 폭에 딱 맞게 들어오도록 안쪽에 스크롤을 만들지 않는다. */
.page-preview__frame {
  display: grid;
  place-items: center;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-fill);
}

.page-preview__canvas {
  display: block;
  max-width: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.page-preview__status {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
</style>
