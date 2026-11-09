import { api } from '@/shared/api/client';
import type { CompileRequest, CompileResponse, OkResponse, Policy } from '@/shared/api/types';

export const policyApi = {
  active: (agentId: string) => api.get<Policy>(`/api/policies/${agentId}`),
  compile: (body: CompileRequest) => api.post<CompileResponse>('/api/policies/compile', body),
  activate: (agentId: string, policy: Policy) => api.post<OkResponse>(`/api/policies/${agentId}/activate`, policy),
};
