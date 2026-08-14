import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'
import {
  ApiError,
  SERVICE_UNAVAILABLE_MESSAGE,
  looksLikeHtmlErrorBody,
  parseApiErrorResponse,
  isTransportUnavailable,
  mapAxiosErrorToApiError,
} from '@/api/http'
import { userFriendlyMessage } from '@/lib/userFriendlyError'

const NGINX_502 =
  '<html>\r\n<head><title>502 Bad Gateway</title></head>\r\n<body>\r\n<center><h1>502 Bad Gateway</h1></center>\r\n<hr><center>nginx/1.28.3 (Ubuntu)</center>\r\n</body>\r\n</html>\r\n'

function axiosErr(partial: Partial<AxiosError<unknown>> & { status?: number; data?: unknown }): AxiosError<unknown> {
  const status = partial.status
  const data = partial.data
  return {
    name: 'AxiosError',
    message: partial.message || 'Request failed with status code ' + String(status ?? ''),
    isAxiosError: true,
    toJSON: () => ({}),
    code: partial.code,
    response:
      status != null
        ? {
            data,
            status,
            statusText: String(status),
            headers: {},
            config: {} as never,
          }
        : undefined,
    ...partial,
  } as AxiosError<unknown>
}

describe('transport error pure helpers', () => {
  it('detects nginx HTML and suppresses parse message', () => {
    expect(looksLikeHtmlErrorBody(NGINX_502)).toBe(true)
    expect(parseApiErrorResponse(NGINX_502).message).toBe('')
  })

  it('does not treat structured JSON as HTML', () => {
    expect(looksLikeHtmlErrorBody('{"message":"Policy name is required"}')).toBe(false)
    expect(parseApiErrorResponse({ message: 'Policy name is required', reason: 'VALIDATION' }).message).toBe(
      'Policy name is required',
    )
  })

  it('flags 502/503/504 and HTML as transport unavailable', () => {
    expect(isTransportUnavailable(502, axiosErr({ status: 502, data: NGINX_502 }))).toBe(true)
    expect(isTransportUnavailable(503, axiosErr({ status: 503, data: '<html>503</html>' }))).toBe(true)
    expect(isTransportUnavailable(504, axiosErr({ status: 504, data: 'gateway' }))).toBe(true)
    expect(isTransportUnavailable(400, axiosErr({ status: 400, data: { message: 'x' } }))).toBe(false)
  })
})

describe('mapAxiosErrorToApiError goldens', () => {
  it('502 HTML → safe message, no nginx leak', () => {
    const e = mapAxiosErrorToApiError(axiosErr({ status: 502, data: NGINX_502 }))
    expect(e).toBeInstanceOf(ApiError)
    expect(e.message).toBe(SERVICE_UNAVAILABLE_MESSAGE)
    expect(e.message).not.toMatch(/nginx|<!DOCTYPE|<html|502 Bad Gateway/i)
    expect(e.reason).toBe('SERVICE_UNAVAILABLE')
    expect(userFriendlyMessage(e, 'fallback')).toBe(SERVICE_UNAVAILABLE_MESSAGE)
  })

  it('503 HTML → safe message', () => {
    const e = mapAxiosErrorToApiError(
      axiosErr({ status: 503, data: '<html>503 Service Temporarily Unavailable</html>' }),
    )
    expect(e.message).toBe(SERVICE_UNAVAILABLE_MESSAGE)
  })

  it('structured 400 validation preserved', () => {
    const e = mapAxiosErrorToApiError(
      axiosErr({ status: 400, data: { message: 'Policy name is required', reason: 'VALIDATION' } }),
    )
    expect(e.message).toBe('Policy name is required')
    expect(e.message).not.toBe(SERVICE_UNAVAILABLE_MESSAGE)
    expect(userFriendlyMessage(e, 'fallback')).toBe('Policy name is required')
  })

  it('network/reset without response → safe message', () => {
    const e = mapAxiosErrorToApiError(
      axiosErr({ message: 'Network Error', code: 'ERR_NETWORK', status: undefined, data: undefined }),
    )
    expect(e.message).toBe(SERVICE_UNAVAILABLE_MESSAGE)
  })
})
