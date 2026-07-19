import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './http'
import * as documentApi from './documentApi'

describe('P4 document API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uploads multipart with document fields and Idempotency-Key', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue(uploadAccepted())
    const file = new File(['long document'], 'candidate.txt', { type: 'text/plain' })
    await documentApi.uploadDocument(file, 'RESUME', '지원 이력서', 'document-upload:key-1234')
    expect(post).toHaveBeenCalledTimes(1)
    const [url, body, config] = post.mock.calls[0]
    expect(url).toBe('/documents')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    expect((body as FormData).get('documentType')).toBe('RESUME')
    expect((body as FormData).get('displayName')).toBe('지원 이력서')
    expect(config?.headers).toEqual({ 'Idempotency-Key': 'document-upload:key-1234' })
  })

  it('maps list, detail, text, manual, reparse, presign and delete exactly', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      .mockResolvedValueOnce(detail())
      .mockResolvedValueOnce({
        documentId: uuid(1),
        text: 'preview',
        characterCount: 7,
        manualTextProvided: false,
        version: 2,
        updatedAt: now,
      })
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue(runAccepted())
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValueOnce(runAccepted())
      .mockResolvedValueOnce({ url: 'http://storage.test/object', expiresAt: now })
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)
    const params = {
      documentType: 'RESUME' as const,
      page: 0,
      size: 20,
      sort: 'uploadedAt,desc' as const,
    }
    await documentApi.listDocuments(params)
    await documentApi.getDocument(uuid(1))
    await documentApi.getDocumentText(uuid(1))
    await documentApi.provideDocumentManualText(
      uuid(1),
      { text: 'x'.repeat(100), version: 2 },
      'manual-key-1234',
    )
    await documentApi.reparseDocument(uuid(1), { version: 3 }, 'reparse-key-1234')
    await documentApi.createDocumentDownloadUrl(uuid(1))
    await documentApi.deleteDocument(uuid(1), 4)
    expect(get).toHaveBeenNthCalledWith(1, '/documents', { params })
    expect(get).toHaveBeenNthCalledWith(2, `/documents/${uuid(1)}`)
    expect(get).toHaveBeenNthCalledWith(3, `/documents/${uuid(1)}/text`)
    expect(put).toHaveBeenCalledWith(
      `/documents/${uuid(1)}/manual-text`,
      { text: 'x'.repeat(100), version: 2 },
      { headers: { 'Idempotency-Key': 'manual-key-1234' } },
    )
    expect(post).toHaveBeenNthCalledWith(
      1,
      `/documents/${uuid(1)}/reparse`,
      { version: 3 },
      { headers: { 'Idempotency-Key': 'reparse-key-1234' } },
    )
    expect(post).toHaveBeenNthCalledWith(2, `/documents/${uuid(1)}/download-url`)
    expect(remove).toHaveBeenCalledWith(`/documents/${uuid(1)}`, { params: { version: 4 } })
  })
})

const now = '2026-07-19T00:00:00Z'
function uploadAccepted() {
  return {
    documentId: uuid(1),
    parseStatus: 'UPLOADED',
    evidenceExtractionStatus: 'NOT_STARTED',
    agentRunId: uuid(2),
    status: 'QUEUED',
  }
}
function runAccepted() {
  return {
    agentRunId: uuid(2),
    status: 'QUEUED',
    resourceType: 'DOCUMENT',
    resourceId: uuid(1),
    replayed: false,
  }
}
function detail() {
  return {
    id: uuid(1),
    documentType: 'RESUME',
    displayName: 'resume.txt',
    mimeType: 'text/plain',
    fileSizeBytes: 120,
    parseStatus: 'PARSED',
    evidenceExtractionStatus: 'SUCCEEDED',
    manualTextProvided: false,
    safeError: null,
    latestAgentRunId: uuid(2),
    version: 2,
    uploadedAt: now,
    updatedAt: now,
    pageCount: 1,
    characterCount: 120,
    parsedAt: now,
  }
}
function uuid(value: number) {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
