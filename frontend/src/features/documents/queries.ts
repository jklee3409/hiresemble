import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import {
  getDocument,
  getDocumentText,
  listDocuments,
  type DocumentListParams,
} from '@/shared/api/documentApi'

export const documentQueryKeys = {
  root(userId: string) {
    return ['user', userId, 'documents'] as const
  },
  list(userId: string, filters: DocumentListParams) {
    return ['user', userId, 'documents', filters] as const
  },
  detail(userId: string, documentId: string) {
    return ['user', userId, 'document', documentId] as const
  },
  text(userId: string, documentId: string) {
    return ['user', userId, 'documentText', documentId] as const
  },
}

export function useDocumentListQuery(
  userId: MaybeRefOrGetter<string>,
  filters: MaybeRefOrGetter<DocumentListParams>,
  enabled: MaybeRefOrGetter<boolean> = true,
) {
  return useQuery({
    queryKey: computed(() => documentQueryKeys.list(toValue(userId), toValue(filters))),
    queryFn: () => listDocuments(toValue(filters)),
    enabled: computed(() => toValue(enabled) && toValue(userId) !== ''),
  })
}

export function useDocumentDetailQuery(
  userId: MaybeRefOrGetter<string>,
  documentId: MaybeRefOrGetter<string>,
) {
  return useQuery({
    queryKey: computed(() => documentQueryKeys.detail(toValue(userId), toValue(documentId))),
    queryFn: () => getDocument(toValue(documentId)),
    enabled: computed(() => toValue(userId) !== '' && toValue(documentId) !== ''),
  })
}

export function useDocumentTextQuery(
  userId: MaybeRefOrGetter<string>,
  documentId: MaybeRefOrGetter<string>,
  enabled: MaybeRefOrGetter<boolean>,
) {
  return useQuery({
    queryKey: computed(() => documentQueryKeys.text(toValue(userId), toValue(documentId))),
    queryFn: () => getDocumentText(toValue(documentId)),
    enabled: computed(
      () => toValue(enabled) && toValue(userId) !== '' && toValue(documentId) !== '',
    ),
  })
}
