import type { DocumentType } from '@/shared/api/documentContracts'

export const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024
const ALLOWED_EXTENSIONS = new Set(['pdf', 'docx', 'txt'])

export interface UploadFormInput {
  file: File | null
  documentType: DocumentType
  displayName: string
}

export function validateUpload(input: UploadFormInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (input.file === null) {
    errors.file = 'PDF, DOCX 또는 TXT 파일을 선택해 주세요.'
  } else {
    const extension = input.file.name.split('.').pop()?.toLocaleLowerCase() ?? ''
    if (!ALLOWED_EXTENSIONS.has(extension)) {
      errors.file = 'PDF, DOCX, TXT 파일만 업로드할 수 있습니다.'
    } else if (input.file.size === 0) {
      errors.file = '빈 파일은 업로드할 수 없습니다.'
    } else if (input.file.size > MAX_DOCUMENT_BYTES) {
      errors.file = '파일 크기는 20MB 이하여야 합니다.'
    }
  }
  const displayName = input.displayName.trim()
  if (displayName.length > 255) errors.displayName = '표시 이름은 255자 이하여야 합니다.'
  return errors
}

export function validateManualText(text: string): string | null {
  const normalized = text.normalize('NFC').replace(/\r\n?/g, '\n')
  const codePointCount = Array.from(normalized).length
  const nonWhitespaceCount = Array.from(normalized).filter((value) => !/\s/u.test(value)).length
  if (nonWhitespaceCount < 100) return '공백을 제외한 텍스트를 100자 이상 입력해 주세요.'
  if (codePointCount > 500_000) return '텍스트는 최대 500,000자까지 입력할 수 있습니다.'
  return null
}
