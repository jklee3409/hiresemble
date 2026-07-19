import type {
  DocumentParseStatus,
  DocumentType,
  EvidenceExtractionStatus,
} from '@/shared/api/documentContracts'

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  RESUME: '이력서',
  PORTFOLIO: '포트폴리오',
  CAREER_DESCRIPTION: '경력기술서',
  CERTIFICATE: '자격·증명서',
  TRANSCRIPT: '성적표',
  OTHER: '기타',
}

export const DOCUMENT_PARSE_STATUS_LABELS: Record<DocumentParseStatus, string> = {
  UPLOADED: '처리 대기',
  PARSING: '텍스트 처리 중',
  PARSED: '텍스트 준비 완료',
  NEEDS_MANUAL_TEXT: '텍스트 입력 필요',
  FAILED: '파싱 실패',
}

export const EVIDENCE_EXTRACTION_STATUS_LABELS: Record<EvidenceExtractionStatus, string> = {
  NOT_STARTED: '근거 추출 대기 전',
  QUEUED: '근거 추출 대기',
  EXTRACTING: '근거 추출 중',
  SUCCEEDED: '근거 추출 완료',
  FAILED: '근거 추출 실패',
}

export function documentStateMessage(
  parseStatus: DocumentParseStatus,
  evidenceStatus: EvidenceExtractionStatus,
): string {
  if (parseStatus === 'UPLOADED' || parseStatus === 'PARSING') {
    return '파일의 텍스트를 처리하고 있습니다.'
  }
  if (parseStatus === 'NEEDS_MANUAL_TEXT') {
    return '읽을 수 있는 텍스트가 부족합니다. 아래에서 텍스트를 직접 입력해 주세요.'
  }
  if (parseStatus === 'FAILED') {
    return '파일 파싱에 실패했습니다. 안전한 오류를 확인한 뒤 재처리할 수 있습니다.'
  }
  if (evidenceStatus === 'FAILED') {
    return '텍스트는 보존됐지만 근거 추출에 실패했습니다. 문서 업로드 실패가 아닙니다.'
  }
  if (evidenceStatus === 'SUCCEEDED') {
    return '텍스트와 근거 후보가 준비됐습니다. 근거를 검토해 주세요.'
  }
  return '텍스트는 준비됐고 근거 추출이 진행 중입니다.'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`
}
