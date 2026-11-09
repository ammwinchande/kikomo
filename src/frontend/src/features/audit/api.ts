import { api } from '@/shared/api/client';
import type { AuditEntryView, AuditVerifyResponse } from '@/shared/api/types';

export const auditApi = {
  entries: (limit = 200) => api.get<AuditEntryView[]>(`/api/audit/entries?limit=${limit}`),
  verify: () => api.get<AuditVerifyResponse>('/api/audit/verify'),
};
