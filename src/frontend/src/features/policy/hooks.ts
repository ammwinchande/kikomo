import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/client';
import { keys } from '@/shared/api/keys';
import type { CompileRequest, Policy } from '@/shared/api/types';
import { toast } from '@/shared/store/ui-store';
import { policyApi } from './api';
import { usePolicyStore } from './store';

export function useActivePolicy(agentId: string | undefined) {
  return useQuery({
    queryKey: keys.policy(agentId ?? ''),
    queryFn: () => policyApi.active(agentId!),
    enabled: !!agentId,
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1,
    meta: {silent: true},
  });
}

export function useCompileDraft() {
  const setDraft = usePolicyStore((s) => s.setDraft);
  return useMutation({
    mutationFn: (body: CompileRequest) => policyApi.compile(body),
    onSuccess: (r) => setDraft(r.draft, r.compiler),
  });
}

export function useActivatePolicy(agentId: string | undefined) {
  const qc = useQueryClient();
  const activated = usePolicyStore((s) => s.activated);
  return useMutation({
    mutationFn: (policy: Policy) => policyApi.activate(agentId!, policy),
    meta: {silent: true},
    onSuccess: async (_r, policy) => {
      await qc.invalidateQueries({queryKey: keys.policyAll});
      await qc.invalidateQueries({queryKey: keys.agents});
      await qc.invalidateQueries({queryKey: keys.auditAll});
      const fresh = agentId ? qc.getQueryData<Policy>(keys.policy(agentId)) : undefined;
      activated(fresh?.version ?? policy.version + 1);
      toast('New policy is live.', 'success');
    },
    onError: (error) => {
      const errors = error instanceof ApiError ? error.problem?.errors : undefined;
      toast(errors?.length ? `Policy rejected: ${errors.join('; ')}` : `Could not activate: ${error instanceof Error ? error.message : 'unknown error'}`, 'error');
    },
  });
}
