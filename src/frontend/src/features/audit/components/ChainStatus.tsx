import Button from '@/shared/ui/Button';
import Card from '@/shared/ui/Card';
import type { ChainCheck } from '../store';

interface Props {
  check: ChainCheck;
  tampered: boolean;
  verifying: boolean;
  canSimulate: boolean;
  onVerify: () => void;
  onSimulate: () => void;
  onRepair: () => void;
}

export default function ChainStatus({
                                      check,
                                      tampered,
                                      verifying,
                                      canSimulate,
                                      onVerify,
                                      onSimulate,
                                      onRepair
                                    }: Readonly<Props>) {
  const getTone = (check: { kind: "idle" } | { kind: "bad"; atSeq: number; count: number }) => {
    return check.kind === 'bad' ? 'bg-deny-bg text-deny-fg' : 'bg-bg text-muted'
  };
  const tone = check.kind === 'ok' ? 'bg-allow-bg text-allow-fg' : getTone(check);
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {tampered ? (
            <Button variant="ghost" onClick={onRepair}>
              Repair
            </Button>
          ) : (
            <Button variant="ghost" onClick={onSimulate} disabled={!canSimulate}>
              Simulate tampering
            </Button>
          )}
        </div>
        <Button onClick={onVerify} loading={verifying}>
          Verify chain
        </Button>
      </div>
      <output className={`mt-4 rounded-lg px-4 py-3 text-sm font-medium ${tone}`}>
        {check.kind === 'idle' && 'Press Verify chain to re-check every fingerprint from the first entry.'}
        {check.kind === 'ok' && `Chain intact. ${check.count} ${check.count === 1 ? 'entry' : 'entries'} checked.`}
        {check.kind === 'bad' &&
          (check.atSeq > 0
            ? `Tampering detected at entry ${check.atSeq}. Entries ${check.atSeq} to ${check.count} can no longer be trusted.`
            : 'Tampering detected. The server could not verify the chain.')}
      </output>
      {tampered &&
        <p className="mt-2 text-xs text-muted">Simulation only: the real log is append-only and unchanged. Verify to see
          what detection looks like.</p>}
    </Card>
  );
}
