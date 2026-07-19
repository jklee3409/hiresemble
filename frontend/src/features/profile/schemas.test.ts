import { describe, expect, it } from 'vitest'

import {
  validateCareerForm,
  validateCertificationForm,
  validateEducationForm,
  validateLanguageScoreForm,
  validateProfileForm,
} from './schemas'

describe('P2 profile Zod schemas', () => {
  it('trims desired items, rejects canonical duplicates, and enforces the ten item limit', () => {
    const valid = validateProfileForm({
      legalName: '  User  ',
      introduction: '',
      desiredRoles: [' Backend Engineer '],
      desiredIndustries: ['IT'],
      desiredLocations: ['Seoul'],
      expectedGraduationDate: '',
      version: 0,
    })
    expect(valid.data).toMatchObject({ legalName: 'User', desiredRoles: ['Backend Engineer'] })

    const duplicate = validateProfileForm({
      legalName: '',
      introduction: '',
      desiredRoles: ['Backend', ' backend '],
      desiredIndustries: [],
      desiredLocations: [],
      expectedGraduationDate: '',
      version: 0,
    })
    expect(duplicate.fieldErrors.desiredRoles).toContain('중복')

    const overflow = validateProfileForm({
      legalName: '',
      introduction: '',
      desiredRoles: Array.from({ length: 11 }, (_, index) => `role-${index}`),
      desiredIndustries: [],
      desiredLocations: [],
      expectedGraduationDate: '',
      version: 0,
    })
    expect(overflow.fieldErrors.desiredRoles).toContain('10개')
  })

  it('validates education date order and the GPA pair and range', () => {
    const base = {
      schoolName: 'School',
      major: '',
      degree: '',
      educationStatus: 'GRADUATED' as const,
      admissionDate: '2025-03-01',
      graduationDate: '2024-02-01',
      gpa: '4.2',
      gpaScale: '4.0',
      isPrimary: true,
      description: '',
    }
    const invalid = validateEducationForm(base)
    expect(invalid.fieldErrors.graduationDate).toBeDefined()
    expect(invalid.fieldErrors.gpa).toBeDefined()

    expect(
      validateEducationForm({ ...base, graduationDate: '', gpaScale: '' }).fieldErrors.gpa,
    ).toContain('함께')
    expect(
      validateEducationForm({
        ...base,
        graduationDate: '2026-02-01',
        gpa: '3.8',
        gpaScale: '4.5',
      }).data,
    ).toMatchObject({ gpa: 3.8, gpaScale: 4.5, isPrimary: true })
  })

  it('validates certification and language expiry dates', () => {
    expect(
      validateCertificationForm({
        name: 'Cert',
        issuer: '',
        credentialNumber: '',
        acquiredDate: '2026-02-01',
        expiresAt: '2026-01-01',
        description: '',
      }).fieldErrors.expiresAt,
    ).toBeDefined()
    expect(
      validateLanguageScoreForm({
        testName: 'TOEIC',
        score: '900',
        grade: '',
        testedAt: '2026-02-01',
        expiresAt: '2026-01-01',
      }).fieldErrors.expiresAt,
    ).toBeDefined()
  })

  it('requires a current career to have no end date', () => {
    const result = validateCareerForm({
      organization: 'Company',
      position: '',
      employmentType: '',
      startedAt: '2025-01-01',
      endedAt: '2026-01-01',
      isCurrent: true,
      responsibilities: '',
      achievements: '',
    })
    expect(result.fieldErrors.endedAt).toContain('재직 중')
  })
})
