import type { Policy } from '@/shared/api/types';
import Card, { CardHeader } from '@/shared/ui/Card';
import Pill from '@/shared/ui/Pill';
import { policyRows } from '../lib/policy-rows';

export default function ActivePolicyCard({policy, loading}: Readonly<{
  policy: Policy | null | undefined;
  loading: boolean
}>) {
  function renderPolicies(policy: Policy | null | undefined) {
    if (!policy) {
      return <p className="text-sm text-muted">No active policy. Nothing is allowed until one is confirmed.</p>;
    }
    return (
      <>
        <dl className="grid gap-2 text-sm">
          {policyRows(policy).map((r) => (
            <div key={r.k}
                 className="flex items-center justify-between gap-4 border-b border-line pb-2 last:border-0 last:pb-0">
              <dt className="text-muted">{r.k}</dt>
              <dd className="text-right font-semibold">{r.v}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 text-xs text-muted">Payees on the allowlist: {policy.payeeAllowlist.join(', ') || 'none'}</p>
      </>
    )
  }

  return (
    <Card>
      <CardHeader title="Active policy"
                  action={policy ? <Pill tone="brand">Version {policy.version}</Pill> : undefined}/>
      {loading ?
        <p className="text-sm text-muted">Loading…</p>
        : renderPolicies(policy)
      }
    </Card>
  );
}
