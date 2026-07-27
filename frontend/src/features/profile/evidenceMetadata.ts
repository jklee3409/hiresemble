import type { EvidenceMetadataValue } from '@/shared/api/contracts'

export type EvidenceMetadataFieldType = 'text' | 'number' | 'boolean' | 'empty'

export interface EvidenceMetadataField {
  key: string
  type: EvidenceMetadataFieldType
  value: string
}

export function metadataToFields(
  metadata: Record<string, EvidenceMetadataValue>,
): EvidenceMetadataField[] {
  return Object.entries(metadata).map(([key, value]) => ({
    key,
    type:
      value === null
        ? 'empty'
        : typeof value === 'boolean'
          ? 'boolean'
          : typeof value === 'number'
            ? 'number'
            : 'text',
    value: value === null ? '' : String(value),
  }))
}

export function metadataFieldsToRecord(fields: EvidenceMetadataField[]): {
  data: Record<string, EvidenceMetadataValue> | null
  error: string
} {
  const keys = new Set<string>()
  const entries: Array<[string, EvidenceMetadataValue]> = []

  for (const field of fields) {
    if (keys.has(field.key)) {
      return { data: null, error: '같은 이름의 추가 정보가 있어요. 하나만 남겨 주세요.' }
    }
    keys.add(field.key)

    if (field.type === 'number') {
      if (field.value.trim() === '') {
        return { data: null, error: `${field.key || '추가 정보'}에 숫자를 입력해 주세요.` }
      }
      const numberValue = Number(field.value)
      if (!Number.isFinite(numberValue)) {
        return { data: null, error: `${field.key || '추가 정보'}에 숫자를 입력해 주세요.` }
      }
      entries.push([field.key, numberValue])
    } else if (field.type === 'boolean') {
      if (!['true', 'false'].includes(field.value)) {
        return {
          data: null,
          error: `${field.key || '추가 정보'}에서 예 또는 아니요를 골라 주세요.`,
        }
      }
      entries.push([field.key, field.value === 'true'])
    } else if (field.type === 'empty') {
      entries.push([field.key, null])
    } else {
      entries.push([field.key, field.value])
    }
  }

  const data = Object.fromEntries(entries)
  if (new TextEncoder().encode(JSON.stringify(data)).byteLength > 16 * 1024) {
    return { data: null, error: '추가 정보가 너무 많아요. 항목이나 내용을 줄여 주세요.' }
  }
  return { data, error: '' }
}
