import { api } from '@/shared/api/client';
import type { Health, OkResponse } from '@/shared/api/types';

export const authApi = {
  login: (password: string) => api.post<OkResponse>('/api/auth/login', {password}, {silentAuth: true}),
  logout: () => api.post<OkResponse>('/api/auth/logout'),
  session: () => api.get<OkResponse>('/api/auth/session', {silentAuth: true}),
  health: () => api.get<Health>('/api/health'),
};
