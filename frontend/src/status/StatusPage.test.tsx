import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { StatusPage } from './StatusPage'

interface Body {
  components: { name: string; status: string; checkedAt: string }[]
}

function body(
  backend: string,
  database: string,
  ml: string,
  at = '2026-11-03T12:00:00.000Z',
): Body {
  return {
    components: [
      { name: 'backend', status: backend, checkedAt: at },
      { name: 'database', status: database, checkedAt: at },
      { name: 'ml-service', status: ml, checkedAt: at },
    ],
  }
}

function respond(b: Body) {
  return new Response(JSON.stringify(b), { status: 200 })
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <StatusPage />
    </QueryClientProvider>,
  )
}

describe('StatusPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('shows the three components with state label and check time', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(respond(body('UP', 'DOWN', 'UNKNOWN', '2026-11-03T12:00:00.000Z'))),
    )

    renderPage()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(screen.getByText(/backend/i)).toBeTruthy()
    expect(screen.getByText(/database/i)).toBeTruthy()
    expect(screen.getByText(/ml-service/i)).toBeTruthy()
    expect(screen.getByText('Saudável')).toBeTruthy()
    expect(screen.getByText('Fora do ar')).toBeTruthy()
    expect(screen.getByText('Desconhecido')).toBeTruthy()
    expect(document.body.querySelectorAll('time').length).toBe(3)
  })

  it('shows the fallback states when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    renderPage()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(screen.getAllByText('Fora do ar')).toHaveLength(1)
    expect(screen.getAllByText('Desconhecido')).toHaveLength(2)
  })

  it('refetches every 5 seconds and updates the displayed state', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(respond(body('UP', 'UP', 'DOWN')))
      .mockImplementation(() =>
        Promise.resolve(respond(body('UP', 'UP', 'UP', '2026-11-03T12:00:05.000Z'))),
      )
    vi.stubGlobal('fetch', fetchMock)

    renderPage()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.getAllByText('Saudável')).toHaveLength(2)
    expect(screen.getAllByText('Fora do ar')).toHaveLength(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000)
      await vi.advanceTimersByTimeAsync(10)
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(screen.getAllByText('Saudável')).toHaveLength(3)
    expect(screen.queryByText('Fora do ar')).toBeNull()
  })
})
