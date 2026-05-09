import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { uploadDocument } from './documents'

describe('uploadDocument', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('posts FormData with file part and documentType as query param', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: {
        id: 'd1',
        applicationId: 'a1',
        documentType: 'PAN_CARD',
        fileName: 'x.pdf',
        fileSize: 4,
      },
    })
    const file = new File(['abc'], 'test.pdf', { type: 'application/pdf' })
    await uploadDocument('6ba7b810-9dad-11d1-80b4-00c04fd430c8', file, 'PAN_CARD')

    expect(post).toHaveBeenCalledTimes(1)
    const [url, body] = post.mock.calls[0] as [string, unknown]
    expect(url).toContain('/documents/6ba7b810-9dad-11d1-80b4-00c04fd430c8/upload')
    expect(url).toContain('documentType=PAN_CARD')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
  })
})
