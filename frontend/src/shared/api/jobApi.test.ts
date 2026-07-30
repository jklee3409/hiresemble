import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from './errors'
import { apiClient } from './http'
import * as jobApi from './jobApi'

describe('P5 Job API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('preserves the exact 201 manual and 202 asynchronous create branches', async () => {
    const post = vi
      .spyOn(apiClient.client, 'post')
      .mockResolvedValueOnce({
        status: 201,
        data: accepted('MANUAL_INPUT_PROVIDED', null),
      })
      .mockResolvedValueOnce({
        status: 202,
        data: accepted('QUEUED', uuid(2)),
      })
    const request = {
      sourceUrl: 'https://jobs.example.com/1',
      descriptionText: 'manual body',
    }

    await expect(jobApi.createJob(request, 'job-create:key-1234')).resolves.toMatchObject({
      httpStatus: 201,
      job: { extractionStatus: 'MANUAL_INPUT_PROVIDED', agentRunId: null },
    })
    await expect(
      jobApi.createJob({ sourceUrl: request.sourceUrl }, 'job-create:key-5678'),
    ).resolves.toMatchObject({
      httpStatus: 202,
      job: { extractionStatus: 'QUEUED', agentRunId: uuid(2) },
    })
    expect(post).toHaveBeenNthCalledWith(1, '/jobs', request, {
      headers: { 'Idempotency-Key': 'job-create:key-1234' },
    })
  })

  it('rejects a mismatched status/body branch as an invalid server response', async () => {
    vi.spyOn(apiClient.client, 'post').mockResolvedValue({
      status: 200,
      data: accepted('MANUAL_INPUT_PROVIDED', null),
    })
    await expect(
      jobApi.createJob({ sourceUrl: 'https://jobs.example.com/1' }, 'job-create:key-1234'),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' } satisfies Partial<ApiClientError>)
  })

  it('maps list, detail, update, status, retry and delete to the seven P5 endpoints', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      .mockResolvedValueOnce(detail())
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue(detail())
    const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      ...detail(),
      status: 'SUBMITTED',
      submittedAt: now,
    })
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      agentRunId: uuid(2),
      status: 'QUEUED',
      resourceType: 'JOB',
      resourceId: uuid(1),
      replayed: false,
    })
    const remove = vi.spyOn(apiClient, 'delete').mockResolvedValue(undefined)
    const params = { status: 'IN_PROGRESS' as const, page: 0, size: 20 }

    await jobApi.listJobs(params)
    await jobApi.getJob(uuid(1))
    await jobApi.updateJob(uuid(1), { companyName: 'New', version: 1 })
    await jobApi.updateJobStatus(uuid(1), { status: 'SUBMITTED', version: 2 })
    await jobApi.retryJobExtraction(uuid(1), { version: 3 }, 'job-retry-extraction:key-1')
    await jobApi.deleteJob(uuid(1), 4)

    expect(get).toHaveBeenNthCalledWith(1, '/jobs', { params })
    expect(get).toHaveBeenNthCalledWith(2, `/jobs/${uuid(1)}`)
    expect(put).toHaveBeenCalledWith(`/jobs/${uuid(1)}`, { companyName: 'New', version: 1 })
    expect(patch).toHaveBeenCalledWith(`/jobs/${uuid(1)}/status`, {
      status: 'SUBMITTED',
      version: 2,
    })
    expect(post).toHaveBeenCalledWith(
      `/jobs/${uuid(1)}/retry-extraction`,
      { version: 3 },
      { headers: { 'Idempotency-Key': 'job-retry-extraction:key-1' } },
    )
    expect(remove).toHaveBeenCalledWith(`/jobs/${uuid(1)}`, { params: { version: 4 } })
  })

  it('rejects invalid payloads returned by every typed read endpoint', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ items: [{ id: 'not-a-uuid' }] })
    await expect(jobApi.listJobs()).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' })
  })

  it('maps the three P6 analysis operations with exact allowlisted request and sort values', async () => {
    const post = vi.spyOn(apiClient.client, 'post').mockResolvedValue({
      status: 202,
      data: {
        agentRunId: uuid(20),
        status: 'QUEUED',
        resourceType: 'JOB',
        resourceId: uuid(1),
        replayed: false,
      },
    })
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce({
        items: [analysisSummary()],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      })
      .mockResolvedValueOnce(analysisDetail())

    await expect(
      jobApi.analyzeJob(
        uuid(1),
        { qualityMode: 'BALANCED', forceReanalyze: false, jobVersion: 3 },
        'job-analysis:key-1234',
      ),
    ).resolves.toMatchObject({ resourceType: 'JOB', resourceId: uuid(1) })
    await jobApi.listJobAnalyses(uuid(1), {
      page: 0,
      size: 20,
      sort: 'analysisVersion,desc',
    })
    await jobApi.getLatestJobAnalysis(uuid(1))

    expect(post).toHaveBeenCalledWith(
      `/jobs/${uuid(1)}/analysis`,
      { qualityMode: 'BALANCED', forceReanalyze: false, jobVersion: 3 },
      { headers: { 'Idempotency-Key': 'job-analysis:key-1234' } },
    )
    expect(get).toHaveBeenNthCalledWith(1, `/jobs/${uuid(1)}/analyses`, {
      params: { page: 0, size: 20, sort: 'analysisVersion,desc' },
    })
    expect(get).toHaveBeenNthCalledWith(2, `/jobs/${uuid(1)}/analyses/latest`)
  })

  it('rejects a P6 accepted response that links a different resource', async () => {
    vi.spyOn(apiClient.client, 'post').mockResolvedValue({
      status: 202,
      data: {
        agentRunId: uuid(20),
        status: 'QUEUED',
        resourceType: 'JOB',
        resourceId: uuid(2),
        replayed: false,
      },
    })

    await expect(
      jobApi.analyzeJob(
        uuid(1),
        { qualityMode: 'ECONOMY', forceReanalyze: true, jobVersion: 3 },
        'job-analysis:key-1234',
      ),
    ).rejects.toMatchObject({ code: 'INVALID_SERVER_RESPONSE' })
  })
})

const now = '2026-07-27T00:00:00Z'

function accepted(extractionStatus: 'QUEUED' | 'MANUAL_INPUT_PROVIDED', agentRunId: string | null) {
  return { jobId: uuid(1), status: 'IN_PROGRESS', extractionStatus, agentRunId }
}

function detail() {
  return {
    id: uuid(1),
    companyName: 'Hiresemble',
    title: 'Backend Engineer',
    positionName: '백엔드 개발자',
    status: 'IN_PROGRESS',
    extractionStatus: 'EXTRACTED',
    submittedAt: null,
    deadlineAt: null,
    deadlineSource: 'UNKNOWN',
    latestFitScore: null,
    analysisOutdated: false,
    outdatedReasons: [],
    coverLetterStatus: null,
    interviewPreparationCount: 0,
    version: 1,
    createdAt: now,
    updatedAt: now,
    sourceUrl: 'https://jobs.example.com/openings/1',
    canonicalUrl: 'https://jobs.example.com/openings/1',
    roleCategory: null,
    employmentType: null,
    location: null,
    descriptionText: '공고 본문',
    descriptionSource: 'AUTO_EXTRACTED',
    extractionError: null,
    closedAt: null,
    closedReason: null,
    latestAnalysis: null,
    coverLetterId: null,
    latestQuestionSetId: null,
    latestMockSessionId: null,
  }
}

function analysisSummary() {
  return {
    id: uuid(10),
    analysisVersion: 1,
    eligibility: 'CONDITIONAL',
    fitScore: 72.5,
    analysisOutdated: false,
    outdatedReasons: [],
    createdAt: now,
    agentRunId: uuid(20),
  }
}

function analysisDetail() {
  return {
    ...analysisSummary(),
    scoreBreakdown: [],
    requiredQualifications: [],
    preferredQualifications: [],
    responsibilities: [],
    strengths: [],
    gaps: [],
    matchedEvidenceRefs: [],
    analysisSummary: null,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
