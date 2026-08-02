import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './http'
import * as dashboardApi from './dashboardApi'

describe('Dashboard API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('maps the selected month and published guide read endpoints', async () => {
    const get = vi
      .spyOn(apiClient, 'get')
      .mockResolvedValueOnce(dashboard())
      .mockResolvedValueOnce([guide()])

    await expect(dashboardApi.getDashboard('2026-08')).resolves.toMatchObject({
      month: '2026-08',
      jobs: { preparingCount: 2 },
    })
    await expect(dashboardApi.listCareerGuides()).resolves.toHaveLength(1)
    expect(get).toHaveBeenNthCalledWith(1, '/dashboard', { params: { month: '2026-08' } })
    expect(get).toHaveBeenNthCalledWith(2, '/career-guides')
  })

  it('rejects mismatched deadline counts and non-published guide states', async () => {
    vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({
        ...dashboard(),
        deadlineDays: [{ ...dashboard().deadlineDays[0], count: 2 }],
      })
      .mockResolvedValueOnce([{ ...guide(), status: 'DRAFT' }])

    await expect(dashboardApi.getDashboard('2026-08')).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
    await expect(dashboardApi.listCareerGuides()).rejects.toMatchObject({
      code: 'INVALID_SERVER_RESPONSE',
    })
  })
})

function dashboard() {
  return {
    generatedAt: '2026-08-02T03:00:00Z',
    month: '2026-08',
    profile: {
      displayName: '이종규',
      legalName: '이종규',
      desiredRoles: ['백엔드 개발자'],
      desiredLocations: ['서울'],
      completed: true,
      completionPercent: 100,
      missingItems: [],
      primaryEducation: {
        schoolName: '한국대학교',
        major: '컴퓨터공학',
        degree: '학사',
        educationLevel: 'BACHELOR',
        educationStatus: 'GRADUATED',
      },
    },
    documents: { registeredCount: 3, processingCount: 1, needsActionCount: 0 },
    jobs: { registeredCount: 4, preparingCount: 2, submittedCount: 1 },
    agentRuns: { activeCount: 1 },
    deadlineDays: [
      {
        date: '2026-08-08',
        count: 1,
        items: [
          {
            id: uuid(1),
            companyName: 'Hiresemble',
            title: '백엔드 개발자',
            positionName: '백엔드 개발자',
            status: 'IN_PROGRESS',
            deadlineAt: '2026-08-08T14:59:59Z',
          },
        ],
      },
    ],
  }
}

function guide() {
  return {
    id: uuid(2),
    status: 'PUBLISHED',
    displayOrder: 10,
    category: '공고 분석',
    title: '공고 분석 전에 확인할 항목',
    summary: '업무와 필수 조건을 확인하세요.',
    body: '담당 업무와 필수 조건을 나눠 확인하세요.',
    publishedAt: '2026-08-02T00:00:00Z',
    version: 1,
  }
}

function uuid(value: number): string {
  return `00000000-0000-4000-8000-${String(value).padStart(12, '0')}`
}
