import { describe, expect, it } from 'vitest'

import { metadataFieldsToRecord, metadataToFields } from './evidenceMetadata'

describe('evidence metadata field editor', () => {
  it('round-trips every primitive value without changing existing keys', () => {
    const metadata = {
      ' spaced key ': '원문',
      '': null,
      count: 12.5,
      enabled: true,
      disabled: false,
    }

    const result = metadataFieldsToRecord(metadataToFields(metadata))

    expect(result).toEqual({ data: metadata, error: '' })
  })

  it('rejects duplicate keys and unselected number or boolean values', () => {
    expect(
      metadataFieldsToRecord([
        { key: '역할', type: 'text', value: '기획' },
        { key: '역할', type: 'text', value: '개발' },
      ]).error,
    ).toContain('같은 이름')
    expect(metadataFieldsToRecord([{ key: '인원', type: 'number', value: '' }]).error).toContain(
      '숫자',
    )
    expect(metadataFieldsToRecord([{ key: '재직', type: 'boolean', value: '' }]).error).toContain(
      '예 또는 아니요',
    )
  })

  it('keeps the existing encoded-size boundary', () => {
    expect(
      metadataFieldsToRecord([{ key: '내용', type: 'text', value: '가'.repeat(6_000) }]).error,
    ).toContain('너무 많아요')
  })
})
