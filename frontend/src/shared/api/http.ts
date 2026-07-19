import axios, {
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

import type { CsrfDto } from './contracts'
import { ApiClientError, normalizeApiError } from './errors'

type UnauthorizedHandler = () => Promise<void> | void
type RetriableRequestConfig = InternalAxiosRequestConfig & {
  hiresembleCsrfRetried?: boolean
}

export interface HttpApiClientOptions {
  adapter?: AxiosAdapter
  baseURL?: string
}

export class HttpApiClient {
  readonly client: AxiosInstance

  private csrf: CsrfDto | null = null
  private csrfBootstrap: Promise<CsrfDto> | null = null
  private unauthorizedHandler: UnauthorizedHandler | null = null

  constructor(options: HttpApiClientOptions = {}) {
    this.client = axios.create({
      baseURL: options.baseURL ?? import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
      withCredentials: true,
      adapter: options.adapter,
    })

    this.client.interceptors.request.use((config) => {
      if (this.csrf !== null && isMutation(config.method)) {
        config.headers.set(this.csrf.headerName, this.csrf.token)
      }

      return config
    })

    this.client.interceptors.response.use(
      (response) => response,
      async (error: unknown) => {
        const apiError = normalizeApiError(error)
        const requestConfig = axios.isAxiosError(error)
          ? (error.config as RetriableRequestConfig | undefined)
          : undefined

        if (
          apiError.status === 403 &&
          apiError.code === 'CSRF_INVALID' &&
          requestConfig !== undefined &&
          isMutation(requestConfig.method) &&
          requestConfig.hiresembleCsrfRetried !== true
        ) {
          requestConfig.hiresembleCsrfRetried = true
          await this.ensureCsrf(true)
          return this.client.request(requestConfig)
        }

        if (apiError.status === 401 && this.unauthorizedHandler !== null) {
          await this.unauthorizedHandler()
        }

        return Promise.reject(apiError)
      },
    )
  }

  setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
    this.unauthorizedHandler = handler

    return () => {
      if (this.unauthorizedHandler === handler) {
        this.unauthorizedHandler = null
      }
    }
  }

  setCsrfToken(csrf: CsrfDto): void {
    this.csrf = csrf
  }

  clearCsrfToken(): void {
    this.csrf = null
    this.csrfBootstrap = null
  }

  async ensureCsrf(force = false): Promise<CsrfDto> {
    if (!force && this.csrf !== null) {
      return this.csrf
    }

    if (!force && this.csrfBootstrap !== null) {
      return this.csrfBootstrap
    }

    if (force) {
      this.csrf = null
    }

    const bootstrap = this.get<CsrfDto>('/auth/csrf').then((csrf) => {
      if (!isCsrfDto(csrf)) {
        throw new ApiClientError({
          message: 'CSRF 응답 형식이 올바르지 않습니다.',
          status: 0,
          code: 'INVALID_SERVER_RESPONSE',
        })
      }

      this.csrf = csrf
      return csrf
    })

    this.csrfBootstrap = bootstrap

    try {
      return await bootstrap
    } finally {
      if (this.csrfBootstrap === bootstrap) {
        this.csrfBootstrap = null
      }
    }
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.get<T>(url, config)
    return response.data
  }

  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.post<T>(url, data, config)
    return response.data
  }
}

export const apiClient = new HttpApiClient()

function isMutation(method: string | undefined): boolean {
  return method !== undefined && !['get', 'head', 'options'].includes(method.toLowerCase())
}

function isCsrfDto(value: unknown): value is CsrfDto {
  return (
    isRecord(value) &&
    typeof value.headerName === 'string' &&
    value.headerName.length > 0 &&
    typeof value.parameterName === 'string' &&
    value.parameterName.length > 0 &&
    typeof value.token === 'string' &&
    value.token.length > 0
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
