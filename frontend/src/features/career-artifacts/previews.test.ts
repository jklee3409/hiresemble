import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PortfolioArtifactPreview from './PortfolioArtifactPreview.vue'
import ResumeArtifactPreview from './ResumeArtifactPreview.vue'

describe('Career Artifact structured previews', () => {
  it('renders a readable single-column Resume projection with grounded evidence and warnings', () => {
    const wrapper = mount(ResumeArtifactPreview, { props: { preview: resumePreview() } })
    expect(wrapper.text()).toContain('백엔드 개발자')
    expect(wrapper.text()).toContain('응답 시간을 줄였습니다')
    expect(wrapper.text()).toContain('근거 1개')
    expect(wrapper.text()).toContain('수치를 다시 확인하세요')
    expect(wrapper.html()).not.toContain('iframe')
  })

  it('moves Portfolio slides with Arrow, Home, and End while exposing text labels', async () => {
    const wrapper = mount(PortfolioArtifactPreview, { props: { preview: portfolioPreview() } })
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(6)
    expect(wrapper.text()).toContain('표지')
    await tabs[0]?.trigger('keydown', { key: 'End' })
    expect(wrapper.find('[role="tabpanel"]').text()).toContain('마무리')
    await wrapper.findAll('[role="tab"]')[5]?.trigger('keydown', { key: 'Home' })
    expect(wrapper.find('[role="tabpanel"]').text()).toContain('슬라이드 1')
    await wrapper.findAll('[role="tab"]')[0]?.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.find('[role="tabpanel"]').text()).toContain('슬라이드 2')
  })
})

function evidenceRef() {
  return {
    experienceItemId: '00000000-0000-4000-8000-000000000001',
    evidenceId: '00000000-0000-4000-8000-000000000002',
    usageType: 'PRIMARY_EXPERIENCE' as const,
    title: '성능 개선 경험',
  }
}

function resumePreview() {
  return {
    headline: '백엔드 개발자',
    summary: '검증된 경험 요약',
    sections: [
      {
        type: 'CAREER',
        title: '주요 경험',
        items: [
          {
            heading: 'API 성능 개선',
            subheading: '플랫폼 팀',
            period: '2025',
            bullets: ['응답 시간을 줄였습니다'],
            evidenceRefs: [evidenceRef()],
          },
        ],
      },
    ],
    warnings: ['수치를 다시 확인하세요'],
  }
}

function portfolioPreview() {
  return {
    slides: Array.from({ length: 6 }, (_, index) => ({
      slideNo: index + 1,
      slideType:
        index === 0
          ? ('COVER' as const)
          : index === 5
            ? ('CLOSING' as const)
            : ('PROJECT_CASE_STUDY' as const),
      title: `슬라이드 ${index + 1}`,
      subtitle: null,
      items: index === 0 || index === 5 ? [] : ['문제와 해결'],
      visualType: index === 0 ? ('NONE' as const) : ('PROCESS' as const),
      evidenceRefs: index === 0 || index === 5 ? [] : [evidenceRef()],
    })),
    warnings: [],
  }
}
