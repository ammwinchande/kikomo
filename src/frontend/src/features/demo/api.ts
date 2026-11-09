import { api } from '@/shared/api/client';
import type { DemoConnectInfo, DemoRunView, DemoScenario } from '@/shared/api/types';

export const demoApi = {
  start: (scenario: DemoScenario) => api.post<DemoRunView>('/api/demo/runs', { scenario }),
  run: (id: string) => api.get<DemoRunView>(`/api/demo/runs/${id}`),
  connect: () => api.get<DemoConnectInfo>('/api/demo/connect'),
};
