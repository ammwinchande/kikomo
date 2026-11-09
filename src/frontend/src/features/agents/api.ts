import { api } from '@/shared/api/client';
import type { AgentStatusResponse, AgentView } from '@/shared/api/types';

export const agentsApi = {
  list: () => api.get<AgentView[]>('/api/agents'),
  suspend: (id: string) => api.post<AgentStatusResponse>(`/api/agents/${id}/suspend`),
  activate: (id: string) => api.post<AgentStatusResponse>(`/api/agents/${id}/activate`),
};
