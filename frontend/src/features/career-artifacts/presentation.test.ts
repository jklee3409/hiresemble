import { describe, expect, it } from 'vitest'

import {
  ARTIFACT_GENERATION_LABELS,
  ARTIFACT_LIFECYCLE_LABELS,
  PORTFOLIO_SLIDE_LABELS,
  PORTFOLIO_VISUAL_LABELS,
  hasCareerArtifactQualityWarning,
} from './presentation'

describe('Career Artifact presentation', () => {
  it('never exposes lifecycle, generation, slide, or visual raw enums', () => {
    expect(ARTIFACT_LIFECYCLE_LABELS.ARCHIVED).not.toContain('ARCHIVED')
    expect(ARTIFACT_GENERATION_LABELS.INTERRUPTED).not.toContain('INTERRUPTED')
    expect(PORTFOLIO_SLIDE_LABELS.TECHNICAL_DECISION).not.toContain('_')
    expect(PORTFOLIO_VISUAL_LABELS.IMPACT_METRICS).not.toContain('_')
  })

  it('warns without blocking when fewer than two projects/careers or no strength is selected', () => {
    expect(hasCareerArtifactQualityWarning(['PROJECT'])).toBe(true)
    expect(hasCareerArtifactQualityWarning(['PROJECT', 'CAREER'])).toBe(true)
    expect(hasCareerArtifactQualityWarning(['PROJECT', 'CAREER', 'STRENGTH'])).toBe(false)
  })
})
