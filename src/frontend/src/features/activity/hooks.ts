import { useQuery } from '@tanstack/react-query';
import { keys } from '@/shared/api/keys';
import { callsApi } from './api';

export function useCalls(agentId?: string, refetchInterval: number | false = 5_000) {
  return useQuery({ queryKey: keys.calls(agentId), queryFn: () => callsApi.list(agentId), refetchInterval });
}
