import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import DocumentDetailPage from '@/pages/DocumentDetailPage.vue'
import DocumentListPage from '@/pages/DocumentListPage.vue'
import * as documentApi from '@/shared/api/documentApi'
import * as profileApi from '@/shared/api/profileApi'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api/documentApi', () => ({
  DOCUMENT_SORTS: ['uploadedAt,desc', 'updatedAt,desc'],
  listDocuments: vi.fn(),
  getDocument: vi.fn(),
  getDocumentText: vi.fn(),
  uploadDocument: vi.fn(),
  provideDocumentManualText: vi.fn(),
  reparseDocument: vi.fn(),
  createDocumentDownloadUrl: vi.fn(),
  deleteDocument: vi.fn(),
  createDocumentIdempotencyKey: vi.fn((operation: string) => `${operation}-key-1234`),
}))

vi.mock('@/shared/api/profileApi', () => ({
  listEvidence: vi.fn(),
  updateEvidence: vi.fn(),
  verifyEvidence: vi.fn(),
}))

describe('P4 document pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(documentApi.listDocuments).mockResolvedValue(page([]))
    vi.mocked(profileApi.listEvidence).mockResolvedValue(page([]))
  })

  it('renders both state axes and preserves text when evidence extraction fails', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue(
      detail({
        evidenceExtractionStatus: 'FAILED',
        safeError: { code: 'EVIDENCE_FAILED', message: '근거 추출을 다시 시도해 주세요.' },
      }),
    )
    vi.mocked(documentApi.getDocumentText).mockResolvedValue({
      documentId,
      text: '보존된 추출 텍스트',
      characterCount: 10,
      manualTextProvided: false,
      version: 2,
      updatedAt: now,
    })
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('텍스트 준비 완료')
    expect(wrapper.text()).toContain('근거 추출 실패')
    expect(wrapper.text()).toContain('문서 업로드 실패가 아닙니다')
    expect(wrapper.text()).toContain('보존된 추출 텍스트')
  })

  it('validates manual text and resumes the accepted run', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue(
      detail({
        parseStatus: 'NEEDS_MANUAL_TEXT',
        evidenceExtractionStatus: 'NOT_STARTED',
        latestAgentRunId: runId,
      }),
    )
    vi.mocked(documentApi.provideDocumentManualText).mockResolvedValue({
      agentRunId: runId,
      status: 'QUEUED',
      resourceType: 'DOCUMENT',
      resourceId: documentId,
      replayed: false,
    })
    const { wrapper } = await mountDetail()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('가'.repeat(99))
    await wrapper.get('form').trigger('submit')
    expect(documentApi.provideDocumentManualText).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('100자 이상')

    await textarea.setValue('가'.repeat(100))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(documentApi.provideDocumentManualText).toHaveBeenCalledWith(
      documentId,
      { text: '가'.repeat(100), version: 2 },
      'manual-text-key-1234',
    )
    expect(wrapper.text()).toContain('같은 작업을 다시 시작했습니다')
  })

  it('purges detail/text caches and navigates to the list after immediate delete', async () => {
    vi.mocked(documentApi.getDocument).mockResolvedValue(detail())
    vi.mocked(documentApi.getDocumentText).mockResolvedValue({
      documentId,
      text: 'text',
      characterCount: 4,
      manualTextProvided: false,
      version: 2,
      updatedAt: now,
    })
    vi.mocked(documentApi.deleteDocument).mockResolvedValue()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, router, cache } = await mountDetail()
    cache.setQueryData(['user', 'user-1', 'documentText', documentId], { text: 'sensitive' })
    cache.setQueryData(['user', 'user-1', 'evidence', { documentId, page: 0, size: 100 }], {
      items: [{ content: 'sensitive evidence' }],
    })
    const deleteButton = wrapper.findAll('button').find((button) => button.text() === '삭제')
    await deleteButton?.trigger('click')
    await flushPromises()

    expect(documentApi.deleteDocument).toHaveBeenCalledWith(documentId, 2)
    expect(cache.getQueryData(['user', 'user-1', 'document', documentId])).toBeUndefined()
    expect(cache.getQueryData(['user', 'user-1', 'documentText', documentId])).toBeUndefined()
    expect(
      cache.getQueryData(['user', 'user-1', 'evidence', { documentId, page: 0, size: 100 }]),
    ).toBeUndefined()
    expect(router.currentRoute.value.name).toBe('documents')
  })

  it('shows list filters and two independent statuses', async () => {
    vi.mocked(documentApi.listDocuments).mockResolvedValue(
      page([{ ...summary(), parseStatus: 'PARSING', evidenceExtractionStatus: 'QUEUED' }]),
    )
    const { wrapper } = await mountList('/documents?documentType=RESUME&sort=updatedAt,desc')
    expect(wrapper.text()).toContain('텍스트 처리 중')
    expect(wrapper.text()).toContain('근거 추출 대기')
    expect(wrapper.findAll('select')).toHaveLength(5)
  })
})

async function mountDetail() {
  const pinia = authenticatedPinia()
  const router = testRouter()
  await router.push(`/documents/${documentId}`)
  await router.isReady()
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(DocumentDetailPage, {
    global: {
      plugins: [pinia, router, [VueQueryPlugin, { queryClient: cache }]],
      stubs: { DocumentRunMonitor: true },
    },
  })
  await flushPromises()
  return { wrapper, router, cache }
}

async function mountList(path: string) {
  const pinia = authenticatedPinia()
  const router = testRouter()
  await router.push(path)
  await router.isReady()
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(DocumentListPage, {
    global: { plugins: [pinia, router, [VueQueryPlugin, { queryClient: cache }]] },
  })
  await flushPromises()
  return { wrapper, router, cache }
}

function authenticatedPinia() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore(pinia)
  auth.status = 'authenticated'
  auth.currentUser = { id: 'user-1', email: 'user-1@example.com', displayName: 'User One' }
  return pinia
}

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/documents', name: 'documents', component: { template: '<div>documents</div>' } },
      { path: '/documents/:documentId', name: 'document-detail', component: DocumentDetailPage },
      {
        path: '/agent-runs/:agentRunId',
        name: 'agent-run-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

const now = '2026-07-19T00:00:00Z'
const documentId = '00000000-0000-4000-8000-000000000001'
const runId = '00000000-0000-4000-8000-000000000002'
function summary() {
  return {
    id: documentId,
    documentType: 'RESUME' as const,
    displayName: '이력서.txt',
    mimeType: 'text/plain',
    fileSizeBytes: 120,
    parseStatus: 'PARSED' as const,
    evidenceExtractionStatus: 'SUCCEEDED' as const,
    manualTextProvided: false,
    safeError: null,
    latestAgentRunId: runId,
    version: 2,
    uploadedAt: now,
    updatedAt: now,
  }
}
function detail(overrides = {}) {
  return { ...summary(), pageCount: 1, characterCount: 120, parsedAt: now, ...overrides }
}
function page<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
  }
}
