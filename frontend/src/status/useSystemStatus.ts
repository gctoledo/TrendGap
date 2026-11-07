import { useQuery } from '@tanstack/react-query'
import { fetchStatus } from '../api/status'

export function useSystemStatus() {
  return useQuery({
    queryKey: ['system-status'],
    queryFn: fetchStatus,
    refetchInterval: 5000,
    refetchIntervalInBackground: true,
    retry: false,
  })
}
