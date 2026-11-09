import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { keys } from '@/shared/api/keys';
import type { AgentView } from '@/shared/api/types';
import { toast } from '@/shared/store/ui-store';
import { agentsApi } from './api';

export function useAgents(refetchInterval: number | false = 5_000) {
  return useQuery({ queryKey: keys.agents, queryFn: agentsApi.list, refetchInterval });
}

/** The agent the portal is about: the seeded demo agent, else the first one. */
export function pickPrimaryAgent(agents: AgentView[] | undefined): AgentView | undefined {
  if (!agents || agents.length === 0) return undefined;
  return agents.find((a) => a.name === 'demo-agent') ?? agents[0];
}

export function useSetAgentStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, suspend }: { id: string; suspend: boolean }) => (suspend ? agentsApi.suspend(id) : agentsApi.activate(id)),
    onMutate: async ({ id, suspend }) => {
      await qc.cancelQueries({ queryKey: keys.agents });
      const previous = qc.getQueryData<AgentView[]>(keys.agents);
      qc.setQueryData<AgentView[]>(keys.agents, (old) => old?.map((a) => (a.id === id ? { ...a, status: suspend ? 'suspended' : 'active' } : a)));
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(keys.agents, ctx.previous);
    },
    onSuccess: (_d, { suspend }) => toast(suspend ? 'Agent suspended. Every call is now refused.' : 'Agent resumed.', 'success'),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: keys.agents });
      void qc.invalidateQueries({ queryKey: keys.auditAll });
    },
  });
}
