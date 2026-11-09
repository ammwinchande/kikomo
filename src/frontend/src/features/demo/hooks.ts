import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/client';
import { keys } from '@/shared/api/keys';
import type { DemoRunView, DemoScenario } from '@/shared/api/types';
import { demoApi } from './api';
import { useDemoStore } from './store';

export function useStartRun() {
  const qc = useQueryClient();
  const setRun = useDemoStore((s) => s.setRun);
  return useMutation({
    mutationFn: (scenario: DemoScenario) => demoApi.start(scenario),
    onSuccess: (run) => {
      qc.setQueryData(keys.demoRun(run.id), run);
      setRun(run.id);
    },
  });
}

/** Polls every second while the scripted agent is still running. */
export function useRun(id: string | null) {
  return useQuery({
    queryKey: keys.demoRun(id ?? ''),
    queryFn: () => demoApi.run(id!),
    enabled: !!id,
    refetchInterval: (q) => (q.state.data?.status === 'running' ? 1_000 : false),
  });
}

export function useConnectInfo() {
  return useQuery({
    queryKey: keys.demoConnect,
    queryFn: demoApi.connect,
    staleTime: Infinity,
    retry: false,
    meta: { silent: true },
  });
}

export function demoUnavailable(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

export function pendingApprovalId(run: DemoRunView | undefined): string | null {
  if (!run) return null;
  const labels = run.steps.map((s) => s.label);
  if (labels.some((l) => l === 'approved' || l === 'denied' || l === 'expired' || l === 'executed' && labels.includes('pending_approval') && labels.lastIndexOf('executed') > labels.lastIndexOf('pending_approval'))) return null;
  const waiting = [...run.steps].reverse().find((s) => s.label === 'pending_approval' && s.approval_id);
  return waiting?.approval_id ?? null;
}
