import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/client';
import { keys } from '@/shared/api/keys';
import type { ApprovalStatus, ApprovalView } from '@/shared/api/types';
import { toast } from '@/shared/store/ui-store';
import { approvalsApi } from './api';
import { useApprovalsStore } from './store';

/** All recent approvals when `status` is omitted (the inbox filters client-side so decided cards can linger). */
export function useApprovals(status?: ApprovalStatus, refetchInterval: number | false = 5_000) {
  return useQuery({queryKey: keys.approvals(status), queryFn: () => approvalsApi.list(status), refetchInterval});
}

export function useStats(refetchInterval: number | false = 5_000) {
  return useQuery({queryKey: keys.stats, queryFn: approvalsApi.stats, refetchInterval});
}

export type Decide = { id: string; decision: 'approve' | 'deny' };

/** Approve executes the payment on the server; deny tells the agent no. Both refresh everything they touch. */
export function useDecideApproval() {
  const qc = useQueryClient();
  const markDecided = useApprovalsStore((s) => s.markDecided);
  const resetDecided = useApprovalsStore((s) => s.reset);
  return useMutation({
    mutationFn: ({id, decision}: Decide) => (decision === 'approve' ? approvalsApi.approve(id) : approvalsApi.deny(id)),
    meta: {silent: true},
    onMutate: async ({id, decision}) => {
      await qc.cancelQueries({queryKey: keys.approvalsAll});
      // Remember the decision locally first so the card never flickers out of the list while the server answers.
      markDecided(id, decision === 'approve' ? 'approved' : 'denied');
      const previous = qc.getQueriesData<ApprovalView[]>({queryKey: keys.approvalsAll});
      qc.setQueriesData<ApprovalView[]>({queryKey: keys.approvalsAll}, (old) =>
        old?.map((a) => (a.id === id ? {...a, status: decision === 'approve' ? 'approved' : 'denied'} : a)),
      );
      return {previous};
    },
    onSuccess: (_data, {decision}) => {
      toast(decision === 'approve' ? 'Approved. The payment was sent to PayPal.' : 'Denied. The agent was told no.', 'success');
    },
    onError: (error, {id, decision}, ctx) => {
      resetDecided(id);
      ctx?.previous.forEach(([key, data]) => qc.setQueryData(key, data));
      if (error instanceof ApiError && error.status === 409) {
        toast('This request was already decided or has expired.', 'error');
      } else {
        toast(`Could not ${decision}: ${error instanceof Error ? error.message : 'unknown error'}`, 'error');
      }
    },
    onSettled: () => {
      void qc.invalidateQueries({queryKey: keys.approvalsAll});
      void qc.invalidateQueries({queryKey: keys.stats});
      void qc.invalidateQueries({queryKey: keys.callsAll});
      void qc.invalidateQueries({queryKey: keys.agents});
      void qc.invalidateQueries({queryKey: keys.auditAll});
      void qc.invalidateQueries({queryKey: ['demo']});
    },
  });
}
