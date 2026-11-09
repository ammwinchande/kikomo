import { api } from '@/shared/api/client';
import type { CallView } from '@/shared/api/types';

export const callsApi = {
  list: (agentId?: string) => api.get<CallView[]>(agentId ? `/api/calls?agentId=${agentId}` : '/api/calls'),
  get: (id: string) => api.get<CallView>(`/api/calls/${id}`),
};
