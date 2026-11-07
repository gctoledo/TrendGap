import type { ComponentState } from '../api/status'
import { useSystemStatus } from './useSystemStatus'

const LABELS: Record<ComponentState, string> = {
  UP: 'Saudável',
  DOWN: 'Fora do ar',
  UNKNOWN: 'Desconhecido',
}

export function StatusPage() {
  const { data } = useSystemStatus()

  return (
    <main>
      <h1>Status do sistema</h1>
      {data === undefined ? (
        <p>Carregando…</p>
      ) : (
        <ul>
          {data.components.map((component) => (
            <li key={component.name}>
              <strong>{component.name}</strong>: <span>{LABELS[component.status]}</span>{' '}
              <time dateTime={component.checkedAt}>
                {new Date(component.checkedAt).toLocaleTimeString('pt-BR')}
              </time>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
