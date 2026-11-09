import { Link } from 'react-router-dom';
import type { Policy } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import Card, { CardHeader } from '@/shared/ui/Card';

export default function ActivePolicyCard({policy}: Readonly<{ policy: Policy | null | undefined }>) {
  const rows = policy
    ? [
      ['Per-payment cap', money(policy.perTxCapMinor)],
      ['Ask me above', money(policy.approvalThresholdMinor)],
      ['Daily budget', money(policy.dailyBudgetMinor)],
      ['Trusted payees', String(policy.payeeAllowlist.length)],
      ['New payees', policy.newPayeeRequiresApproval ? 'Need approval' : 'Allowed'],
    ]
    : [];
  return (
    <Card>
      <CardHeader
        title="Active policy"
        subtitle={policy ? `Version ${policy.version}` : 'No active policy'}
        action={
          <Link to="/policy" className="text-sm font-semibold">
            Edit
          </Link>
        }
      />
      {policy ? (
        <dl className="grid gap-2 text-sm">
          {rows.map(([k, v]) => (
            <div key={k}
                 className="flex items-center justify-between border-b border-line pb-2 last:border-0 last:pb-0">
              <dt className="text-muted">{k}</dt>
              <dd className="font-semibold">{v}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="text-sm text-muted">Nothing is allowed until a policy is confirmed.</p>
      )}
    </Card>
  );
}
