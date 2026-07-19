import { QueryClient } from '@tanstack/vue-query'

import { isApiClientError } from '@/shared/api/errors'

export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry(failureCount, error) {
          if (isApiClientError(error) && error.status >= 400 && error.status < 500) {
            return false
          }

          return failureCount < 1
        },
      },
      mutations: {
        retry: false,
      },
    },
  })
}

export const queryClient = createAppQueryClient()
