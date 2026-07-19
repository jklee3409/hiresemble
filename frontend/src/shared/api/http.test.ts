import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { describe, expect, it, vi } from 'vitest'

import type { ErrorResponseDto } from './contracts'
import { ApiClientError, fieldErrorsToRecord } from './errors'
import { HttpApiClient } from './http'

describe('HttpApiClient', () => {
  it('uses the P1 base URL, sends cookies, and applies the current dynamic CSRF header', async () => {
    const requests: Array<{ url: string; csrf: unknown; customCsrf: unknown }> = []
    const adapter: AxiosAdapter = async (config) => {
      requests.push({
        url: config.url ?? '',
        csrf: config.headers.get('X-CSRF-TOKEN'),
        customCsrf: config.headers.get('X-CUSTOM-CSRF'),
      })

      if (config.url === '/auth/csrf') {
        return ok(config, {
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'first-token',
        })
      }

      return ok(config, undefined)
    }
    const client = new HttpApiClient({ adapter })

    expect(client.client.defaults.baseURL).toBe('/api/v1')
    expect(client.client.defaults.withCredentials).toBe(true)

    await client.ensureCsrf()
    await client.post('/auth/logout')
    client.setCsrfToken({
      headerName: 'X-CUSTOM-CSRF',
      parameterName: '_csrf',
      token: 'rotated-token',
    })
    await client.post('/auth/logout')

    expect(requests).toEqual([
      { url: '/auth/csrf', csrf: undefined, customCsrf: undefined },
      { url: '/auth/logout', csrf: 'first-token', customCsrf: undefined },
      { url: '/auth/logout', csrf: undefined, customCsrf: 'rotated-token' },
    ])
  })

  it('refreshes an invalid CSRF token and retries a mutation only once', async () => {
    let csrfRequests = 0
    let mutationRequests = 0
    const mutationTokens: unknown[] = []
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === '/auth/csrf') {
        csrfRequests += 1
        return ok(config, {
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: `token-${csrfRequests}`,
        })
      }

      mutationRequests += 1
      mutationTokens.push(config.headers.get('X-CSRF-TOKEN'))
      throw responseError(config, csrfError())
    }
    const client = new HttpApiClient({ adapter })

    await client.ensureCsrf()

    await expect(client.post('/auth/logout')).rejects.toMatchObject({
      status: 403,
      code: 'CSRF_INVALID',
    })
    expect(csrfRequests).toBe(2)
    expect(mutationRequests).toBe(2)
    expect(mutationTokens).toEqual(['token-1', 'token-2'])
  })

  it('normalizes the common error contract, maps field errors, and invokes the 401 reset', async () => {
    const unauthorized = vi.fn()
    const response = errorResponse({
      status: 401,
      code: 'AUTHENTICATION_REQUIRED',
      fieldErrors: [{ field: 'email', reason: 'INVALID_FORMAT' }],
    })
    const adapter: AxiosAdapter = async (config) => {
      throw responseError(config, response)
    }
    const client = new HttpApiClient({ adapter })
    client.setUnauthorizedHandler(unauthorized)

    const failure = await client.get('/auth/me').catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ApiClientError)
    expect(failure).toMatchObject({
      status: 401,
      code: 'AUTHENTICATION_REQUIRED',
      requestId: response.requestId,
    })
    expect(fieldErrorsToRecord((failure as ApiClientError).fieldErrors)).toEqual({
      email: '입력 형식을 확인해 주세요.',
    })
    expect(unauthorized).toHaveBeenCalledTimes(1)
  })

  it('does not retry a 409 mutation', async () => {
    let mutationRequests = 0
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === '/auth/csrf') {
        return ok(config, {
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'token',
        })
      }

      mutationRequests += 1
      throw responseError(config, errorResponse({ status: 409, code: 'EMAIL_ALREADY_REGISTERED' }))
    }
    const client = new HttpApiClient({ adapter })

    await client.ensureCsrf()
    await expect(client.post('/auth/signup', {})).rejects.toMatchObject({
      status: 409,
      code: 'EMAIL_ALREADY_REGISTERED',
    })
    expect(mutationRequests).toBe(1)
  })
})

function ok<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<T> {
  return {
    config,
    data,
    headers: {},
    status: 200,
    statusText: 'OK',
  }
}

function responseError(config: InternalAxiosRequestConfig, data: ErrorResponseDto): AxiosError {
  return new AxiosError('Request failed', AxiosError.ERR_BAD_RESPONSE, config, undefined, {
    config,
    data,
    headers: {},
    status: data.status,
    statusText: String(data.status),
  })
}

function csrfError(): ErrorResponseDto {
  return errorResponse({ status: 403, code: 'CSRF_INVALID' })
}

function errorResponse(
  input: Partial<ErrorResponseDto> & Pick<ErrorResponseDto, 'status' | 'code'>,
): ErrorResponseDto {
  return {
    timestamp: '2026-07-19T00:00:00Z',
    message: '요청을 처리할 수 없습니다.',
    fieldErrors: [],
    requestId: '00000000-0000-0000-0000-000000000001',
    ...input,
  }
}
