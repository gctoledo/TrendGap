import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchStatus } from './status'

const OK_BODY = {
  components: [
    { name: 'backend', status: 'UP', checkedAt: '2026-11-03T12:00:00.120Z' },
    { name: 'database', status: 'UP', checkedAt: '2026-11-03T12:00:00.118Z' },
    { name: 'ml-service', status: 'DOWN', checkedAt: '2026-11-03T12:00:02.101Z' },
  ],
}

const NOW = new Date('2026-11-07T10:00:00.000Z')

function expectFallback(result: {
  components: { name: string; status: string; checkedAt: string }[]
}) {
  const byName = Object.fromEntries(result.components.map((c) => [c.name, c]))
  expect(result.components).toHaveLength(3)
  expect(byName.backend?.status).toBe('DOWN')
  expect(byName.database?.status).toBe('UNKNOWN')
  expect(byName['ml-service']?.status).toBe('UNKNOWN')
  for (const c of result.components) {
    expect(c.checkedAt).toBe(NOW.toISOString())
  }
}

describe('fetchStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('returns the components reported by GET /api/status', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(OK_BODY), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await fetchStatus()

    expect(result).toEqual(OK_BODY)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/status')
  })

  it('resolves with backend DOWN and the rest UNKNOWN on network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    expectFallback(await fetchStatus())
  })

  it('resolves with backend DOWN and the rest UNKNOWN on a 5xx response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('boom', { status: 503 })))

    expectFallback(await fetchStatus())
  })

  it('resolves with the fallback when the request exceeds 4 seconds', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: unknown, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            reject(new DOMException('aborted', 'AbortError'))
          })
        })
      }),
    )

    const promise = fetchStatus()
    await vi.advanceTimersByTimeAsync(4000)

    expectFallback(await promise)
  })
})
