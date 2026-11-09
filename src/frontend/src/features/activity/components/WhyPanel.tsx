import type { CallView, Policy } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import { policyValuesFor, reasonCopy } from '@/shared/lib/reasons';
import Button from '@/shared/ui/Button';
import Card, { CardHeader } from '@/shared/ui/Card';
import CodeBlock from '@/shared/ui/CodeBlock';
import Pill, { decisionLabel, decisionTone } from '@/shared/ui/Pill';

interface Props {
  call: CallView | null;
  policy: Policy | null | undefined;
  agentSuspended: boolean;
  explainOpen: boolean;
  onToggleExplain: () => void;
}

export default function WhyPanel({call, policy, agentSuspended, explainOpen, onToggleExplain}: Readonly<Props>) {
  if (!call) {
    return (
      <Card>
        <CardHeader title="Why panel" subtitle="Select a row to see the reasoning"/>
      </Card>
    );
  }
  const ctx = {
    amountMinor: call.amount_minor,
    currency: call.currency,
    counterparty: call.counterparty,
    category: call.category,
    tool: call.tool,
    policy
  };
  const tone = decisionTone(call.decision);
  const values = policyValuesFor(call.reason_codes, ctx, agentSuspended);
  const target = call.counterparty ?? (call.args && typeof call.args.url === 'string' ? call.args.url : '');
  return (
    <Card aria-live="polite">
      <CardHeader
        title="Why panel"
        action={<Pill tone={tone}>{decisionLabel(call.decision, call.status)}</Pill>}
      />
      <div className="text-2xl font-bold tracking-tight">{money(call.amount_minor, call.currency)}</div>
      <div className="text-sm text-muted">
        <span className="font-semibold text-ink">{call.tool}</span>
        {target && <> · {target}</>}
      </div>

      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-muted">What the policy said</h3>
      <ul className="mt-2 grid gap-2">
        {call.reason_codes.map((code) => {
          const copy = reasonCopy(code, ctx);
          return (
            <li key={code}
                className={`rounded-lg px-3 py-2 text-sm ${tone === 'allow' ? 'bg-allow-bg text-allow-fg' : tone === 'deny' ? 'bg-deny-bg text-deny-fg' : 'bg-approve-bg text-approve-fg'}`}>
              <div className="font-mono text-xs font-semibold">{code}</div>
              <div className="mt-0.5">{copy.text}</div>
            </li>
          );
        })}
        {call.reason_codes.length === 0 && <li className="text-sm text-muted">No reason codes were recorded.</li>}
      </ul>

      {values.length > 0 && (
        <>
          <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-muted">Policy values involved</h3>
          <dl className="mt-2 grid gap-1.5 text-sm">
            {values.map((v) => (
              <div key={v.k}
                   className="flex items-center justify-between gap-4 border-b border-line pb-1.5 last:border-0">
                <dt className="text-muted">{v.k}</dt>
                <dd className="font-semibold">{v.v}</dd>
              </div>
            ))}
          </dl>
        </>
      )}

      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-muted">What the agent sent</h3>
      <div className="mt-2">
        <CodeBlock code={JSON.stringify(call.args ?? {}, null, 2)} copy={false}/>
      </div>

      <div className="mt-5">
        <Button variant="ghost" onClick={onToggleExplain} aria-expanded={explainOpen}>
          {explainOpen ? 'Hide plain words' : 'Explain in plain words'}
        </Button>
        {explainOpen && (
          <p className="mt-3 rounded-lg bg-bg px-3 py-2 text-sm" role="note">
            {call.explanation}
          </p>
        )}
        <p className="mt-2 text-xs text-muted">The decision above is rule-based and never uses AI. The plain-words
          version is a written summary only.</p>
      </div>
    </Card>
  );
}
