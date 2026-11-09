import type { DemoRunView, DemoStep } from '@/shared/api/types';
import Button from '@/shared/ui/Button';
import Card, { CardHeader } from '@/shared/ui/Card';
import Pill, { type Tone } from '@/shared/ui/Pill';
import Spinner from '@/shared/ui/Spinner';

const kindTone: Record<DemoStep['kind'], Tone> = {info: 'info', allow: 'allow', approve: 'approve', deny: 'deny'};

interface Props {
  run: DemoRunView | undefined;
  loading: boolean;
  approvalId: string | null;
  approving: boolean;
  onApprove: (approvalId: string) => void;
  onClear: () => void;
}

export default function StepFeed({run, loading, approvalId, approving, onApprove, onClear}: Readonly<Props>) {
  return (
    <Card>
      <CardHeader
        title="What just happened"
        action={
          run && (
            <Button variant="link" size="sm" onClick={onClear}>
              Clear
            </Button>
          )
        }
      />
      {!run && !loading ? (
        <div className="rounded-xl border border-dashed border-line px-6 py-10 text-center">
          <div className="font-semibold">Nothing has run yet</div>
          <p className="mt-1 text-sm text-muted">Choose a scenario above and each step will appear here as it
            happens.</p>
        </div>
      ) : (
        <ol className="grid gap-3" aria-live="polite" aria-label="Demo steps">
          {run?.steps.map((s) => (
            <li key={s.id} className="flex items-start gap-3 rounded-lg border border-line p-3">
              <Pill tone={kindTone[s.kind]} className="mt-0.5 font-mono">
                {s.label}
              </Pill>
              <div className="min-w-0 flex-1">
                <div className="font-semibold">{s.title}</div>
                <div className="text-sm text-muted">{s.detail}</div>
                {s.label === 'pending_approval' && s.approval_id && approvalId === s.approval_id && (
                  <Button size="sm" className="mt-2" onClick={() => onApprove(s.approval_id!)} loading={approving}>
                    Approve it now
                  </Button>
                )}
              </div>
            </li>
          ))}
          {(loading || run?.status === 'running') && (
            <li>
              <Spinner label={run?.steps.length ? 'Waiting for the next step' : 'Starting the scripted agent'}/>
            </li>
          )}
          {run?.status === 'failed' &&
            <li className="text-sm font-semibold text-deny-fg">The run stopped early. See the last step for the
              error.</li>}
        </ol>
      )}
    </Card>
  );
}
