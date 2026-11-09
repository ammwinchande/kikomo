import EmptyState from '@/shared/ui/EmptyState';
import Spinner from '@/shared/ui/Spinner';
import ChainStatus from './components/ChainStatus';
import EntriesTable from './components/EntriesTable';
import { useAuditEntries, useVerifyChain } from './hooks';
import { useAuditStore } from './store';

export default function AuditPage() {
  const entries = useAuditEntries();
  const list = entries.data ?? [];
  const {tamperedSeq, check, simulate, repair} = useAuditStore();
  const verify = useVerifyChain(list.length);
  const middle = list.length ? list[Math.floor(list.length / 2)].seq : null;

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-lg font-semibold">Tamper-evident audit log</h1>
        <p className="text-sm text-muted">
          Each entry carries the fingerprint of the one before it. Change any past entry and every fingerprint after it
          stops matching.
        </p>
      </div>
      <ChainStatus
        check={check}
        tampered={tamperedSeq !== null}
        verifying={verify.isPending}
        canSimulate={middle !== null}
        onVerify={() => verify.mutate()}
        onSimulate={() => middle !== null && simulate(middle)}
        onRepair={repair}
      />
      <section aria-label="Audit log entries in order">
        {entries.isPending ? (
          <Spinner label="Loading audit log"/>
        ) : list.length === 0 ? (
          <EmptyState title="The audit log is empty"
                      text="Every decision, approval and kill-switch press will be recorded here."
                      icon={<span className="mb-3 text-2xl" aria-hidden="true">⛓</span>}/>
        ) : (
          <EntriesTable entries={list} tamperedSeq={tamperedSeq} check={check}/>
        )}
      </section>
    </div>
  );
}
