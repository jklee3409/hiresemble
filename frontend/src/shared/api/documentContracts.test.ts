import { describe, expect, it } from 'vitest'

import {
  DOCUMENT_PARSE_STATUSES,
  DOCUMENT_TYPES,
  EVIDENCE_EXTRACTION_STATUSES,
  documentDetailSchema,
  documentTextSchema,
  documentUploadAcceptedSchema,
} from './documentContracts'

describe('P4 document contracts', () => {
  it('keeps exact canonical document enums', () => {
    expect(DOCUMENT_TYPES).toEqual([
      'RESUME',
      'PORTFOLIO',
      'CAREER_DESCRIPTION',
      'CERTIFICATE',
      'TRANSCRIPT',
      'OTHER',
    ])
    expect(DOCUMENT_PARSE_STATUSES).toEqual([
      'UPLOADED',
      'PARSING',
      'PARSED',
      'NEEDS_MANUAL_TEXT',
      'FAILED',
    ])
    expect(EVIDENCE_EXTRACTION_STATUSES).toEqual([
      'NOT_STARTED',
      'QUEUED',
      'EXTRACTING',
      'SUCCEEDED',
      'FAILED',
    ])
  })

  it('parses complete upload, detail and text DTOs without internal storage fields', () => {
    expect(
      documentUploadAcceptedSchema.parse({
        documentId: uuid(1),
        parseStatus: 'UPLOADED',
        evidenceExtractionStatus: 'NOT_STARTED',
        agentRunId: uuid(2),
        status: 'QUEUED',
      }),
    ).toEqual(expect.objectContaining({ documentId: uuid(1), status: 'QUEUED' }))

    const detail = documentDetailSchema.parse({
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
      version: 3,
      uploadedAt: '2026-07-19T00:00:00Z',
      updatedAt: '2026-07-19T00:01:00Z',
      pageCount: 1,
      characterCount: 120,
      parsedAt: '2026-07-19T00:01:00Z',
      storageKey: 'must-not-escape',
      checksumSha256: 'secret',
    })
    expect(detail).not.toHaveProperty('storageKey')
    expect(detail).not.toHaveProperty('checksumSha256')
    expect(
      documentTextSchema.parse({
        documentId: uuid(1),
        text: 'safe preview',
        characterCount: 12,
        manualTextProvided: false,
        version: 1,
        updatedAt: '2026-07-19T00:00:00Z',
      }),
    ).toHaveProperty('text', 'safe preview')
  })
})

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
