import Button from '@/shared/ui/Button';
import Card, { CardHeader } from '@/shared/ui/Card';
import Pill from '@/shared/ui/Pill';
import type { PolicyDiff } from '../lib/policy-rows';

interface Props {
  diff: PolicyDiff;
  compiler: 'llm' | 'stub' | null;
  onDiscard: () => void;
  onActivate: () => void;
  activating: boolean;
}

export default function DraftDiffCard({diff, compiler, onDiscard, onActivate, activating}: Readonly<Props>) {
  return (
    <Card>
      <CardHeader
        title="Draft compared with the active policy"
        subtitle={compiler === 'stub' ? 'Drafted by the offline rule compiler (no AI key configured).' : undefined}
        action={<Pill
          tone={diff.changeCount ? 'approve' : 'neutral'}>{diff.changeCount} {diff.changeCount === 1 ? 'change' : 'changes'}</Pill>}
      />
      {diff.warnings.length > 0 && (
        <ul className="mb-4 grid gap-2" aria-label="What to know">
          {diff.warnings.map((w) => (
            <li key={w}
                className="rounded-lg border border-approve-fg/20 bg-approve-bg px-3 py-2 text-sm text-approve-fg">
              {w}
            </li>
          ))}
        </ul>
      )}
      <table className="w-full text-sm" aria-label="Draft policy compared with the active policy">
        <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-muted">
          <th className="pb-2 font-semibold">Setting</th>
          <th className="pb-2 font-semibold">Now</th>
          <th className="pb-2 font-semibold">Draft</th>
        </tr>
        </thead>
        <tbody>
        {diff.rows.map((r) => (
          <tr key={r.k} className={r.changed ? 'bg-selected' : ''}>
            <td className="border-t border-line px-2 py-2 text-muted">{r.k}</td>
            <td className={`border-t border-line px-2 py-2 ${r.changed ? 'line-through text-muted' : ''}`}>{r.now}</td>
            <td className="border-t border-line px-2 py-2 font-semibold">
              {r.changed && <Pill tone="brand" className="mr-2">Changed</Pill>}
              {r.next}
            </td>
          </tr>
        ))}
        </tbody>
      </table>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span className="text-xs text-muted">Nothing changes until you confirm.</span>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onDiscard} disabled={activating}>
            Discard
          </Button>
          <Button onClick={onActivate} loading={activating}>
            Confirm and activate
          </Button>
        </div>
      </div>
    </Card>
  );
}
