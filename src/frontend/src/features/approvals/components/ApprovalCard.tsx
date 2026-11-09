import { useEffect, useState } from 'react';
import type { ApprovalView, Policy } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import { countdown, isPast, relativeTime } from '@/shared/lib/time';
import Button from '@/shared/ui/Button';
import Card from '@/shared/ui/Card';
import Pill from '@/shared/ui/Pill';
import ReasonList from './ReasonList';
import type { LocalDecision } from '../store';

interface Props {
  approval: ApprovalView;
  policy: Policy | null | undefined;
  localDecision: LocalDecision | undefined;
  busy: boolean;
  onApprove: () => void;
  onDeny: () => void;
  onReset: () => void;
}

function useTick(enabled: boolean) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (!enabled) return;
    const t = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(t);
  }, [enabled]);
  return now;
}

export default function ApprovalCard({
                                       approval,
                                       policy,
                                       localDecision,
                                       busy,
                                       onApprove,
                                       onDeny,
                                       onReset
                                     }: Readonly<Props>) {
  const call = approval.call;
  const pending = approval.status === 'pending' && !localDecision;
  const now = useTick(pending);
  const expired = isPast(approval.expires_at, now);
  const amount = money(call?.amount_minor ?? null, call?.currency ?? 'USD');
  const ctx = {
    amountMinor: call?.amount_minor,
    currency: call?.currency,
    counterparty: call?.counterparty,
    category: call?.category,
    policy
  };

  const getApprovalStatus = (approval: ApprovalView) => {
    return approval.status === 'denied' ? 'denied' : undefined
  };

  const decided = localDecision ?? (approval.status === 'approved' ? 'approved' : getApprovalStatus(approval));

  return (
    <Card className="transition-shadow hover:shadow-md" data-testid={`approval-${approval.id}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="text-2xl font-bold tracking-tight">{amount}</div>
          <div className="text-xs text-muted">
            {call?.currency ?? 'USD'} · {call?.tool ?? 'call'}
          </div>
        </div>
        <div className="text-right text-xs text-muted">
          {pending && !expired && (
            <div className="font-semibold text-ink">
              Expires in <span className="tabular-nums">{countdown(approval.expires_at, now)}</span>
            </div>
          )}
          {pending && expired && <div className="font-semibold text-deny-fg">Expired</div>}
          <div>Requested {relativeTime(approval.created_at, now)}</div>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted">to</span>
        <span className="font-semibold">{call?.counterparty ?? 'unknown payee'}</span>
        {call?.category && <Pill tone="neutral">{call.category.replaceAll('_', ' ')}</Pill>}
      </div>

      <div className="mt-4">
        <ReasonList codes={call?.reason_codes ?? []} ctx={ctx}/>
      </div>

      {decided === undefined ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <span className="text-xs text-muted">Approving sends the payment to PayPal.</span>
          <div className="flex gap-2">
            <Button variant="danger" onClick={onDeny} disabled={busy || expired}>
              Deny
            </Button>
            <Button onClick={onApprove} loading={busy} disabled={expired}>
              Approve and send
            </Button>
          </div>
        </div>
      ) : (
        <output
          className={`mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg px-3 py-2 text-sm font-semibold ${
            decided === 'approved' ? 'bg-allow-bg text-allow-fg' : 'bg-deny-bg text-deny-fg'
          }`}
        >
          <span>{decided === 'approved' ? 'Approved and executed' : 'Denied by you. The agent was told no.'}</span>
          {localDecision && (
            <Button variant="link" size="sm" onClick={onReset} className="text-current">
              Dismiss
            </Button>
          )}
        </output>
      )}
    </Card>
  );
}
