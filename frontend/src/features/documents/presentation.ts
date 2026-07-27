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
  UPLOADED: '문서 읽기 전',
  PARSING: '읽는 중',
  PARSED: '읽기 완료',
  NEEDS_MANUAL_TEXT: '내용 직접 입력 필요',
  FAILED: '읽지 못함',
}

export const EVIDENCE_EXTRACTION_STATUS_LABELS: Record<EvidenceExtractionStatus, string> = {
  NOT_STARTED: '경력 정보 정리 전',
  QUEUED: '기다리는 중',
  EXTRACTING: '정리 중',
  SUCCEEDED: '정리 완료',
  FAILED: '정리하지 못함',
}

export function documentStateMessage(
  parseStatus: DocumentParseStatus,
  evidenceStatus: EvidenceExtractionStatus,
): string {
  if (parseStatus === 'UPLOADED' || parseStatus === 'PARSING') {
    return '자료의 내용을 읽고 있어요.'
  }
  if (parseStatus === 'NEEDS_MANUAL_TEXT') {
    return '자료에서 내용을 충분히 읽지 못했어요. 아래에서 내용을 직접 입력해 주세요.'
  }
  if (parseStatus === 'FAILED') {
    return '자료를 읽지 못했어요. 문제 내용을 확인한 뒤 다시 처리할 수 있어요.'
  }
  if (evidenceStatus === 'FAILED') {
    return '자료 내용은 안전하게 남아 있어요. 경력 정보만 정리하지 못했으니 다시 시도할 수 있어요.'
  }
  if (evidenceStatus === 'SUCCEEDED') {
    return '자료 내용과 경력 정보가 준비됐어요. 정리된 내용을 확인해 주세요.'
  }
  return '자료 내용은 준비됐고 경력 정보를 정리하고 있어요.'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}
