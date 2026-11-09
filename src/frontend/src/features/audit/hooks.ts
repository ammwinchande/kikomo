import { useMutation, useQuery } from '@tanstack/react-query';
import { keys } from '@/shared/api/keys';
import { auditApi } from './api';
import { useAuditStore } from './store';

export function useAuditEntries(limit = 200, refetchInterval: number | false = 10_000) {
  return useQuery({queryKey: keys.auditEntries(limit), queryFn: () => auditApi.entries(limit), refetchInterval});
}

/**
 * Runs the real server-side verification on click. If the UI is simulating tampering, the (locally) broken entry is
 * reported instead, because that is what the verifier would say if the row really had changed.
 */
export function useVerifyChain(entryCount: number) {
  const tamperedSeq = useAuditStore((s) => s.tamperedSeq);
  const setCheck = useAuditStore((s) => s.setCheck);
  return useMutation({
    mutationFn: auditApi.verify,
    onSuccess: (r) => {
      if (tamperedSeq !== null) setCheck({kind: 'bad', atSeq: tamperedSeq, count: entryCount});
      else if (r.valid) setCheck({kind: 'ok', count: entryCount});
      else setCheck({kind: 'bad', atSeq: 0, count: entryCount});
    },
  });
}
