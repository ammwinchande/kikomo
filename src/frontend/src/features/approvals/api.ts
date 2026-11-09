import { api } from '@/shared/api/client';
import type { ApprovalStatus, ApprovalView, OkResponse, StatsView } from '@/shared/api/types';

export const approvalsApi = {
  list: (status?: ApprovalStatus) => api.get<ApprovalView[]>(status ? `/api/approvals?status=${status}` : '/api/approvals'),
  approve: (id: string) => api.post<OkResponse>(`/api/approvals/${id}/approve`),
  deny: (id: string) => api.post<OkResponse>(`/api/approvals/${id}/deny`),
  stats: () => api.get<StatsView>('/api/stats'),
};
