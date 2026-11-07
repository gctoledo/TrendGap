export type ComponentName = 'backend' | 'database' | 'ml-service'
export type ComponentState = 'UP' | 'DOWN' | 'UNKNOWN'

export interface ComponentStatus {
  name: ComponentName
  status: ComponentState
  checkedAt: string
}

export interface SystemStatus {
  components: ComponentStatus[]
}

const TIMEOUT_MS = 4000

function unreachable(checkedAt: string): SystemStatus {
  return {
    components: [
      { name: 'backend', status: 'DOWN', checkedAt },
      { name: 'database', status: 'UNKNOWN', checkedAt },
      { name: 'ml-service', status: 'UNKNOWN', checkedAt },
    ],
  }
}

export async function fetchStatus(): Promise<SystemStatus> {
  const attemptedAt = new Date().toISOString()
  try {
    const response = await fetch('/api/status', { signal: AbortSignal.timeout(TIMEOUT_MS) })
    if (!response.ok) return unreachable(attemptedAt)
    return (await response.json()) as SystemStatus
  } catch {
    return unreachable(attemptedAt)
  }
}
