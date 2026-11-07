import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => {
  vi.unstubAllGlobals()
})

test('renders the status page heading', () => {
  vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => undefined)))
  render(
    <QueryClientProvider client={new QueryClient()}>
      <App />
    </QueryClientProvider>,
  )
  expect(screen.getByRole('heading', { name: 'Status do sistema' })).toBeDefined()
})
