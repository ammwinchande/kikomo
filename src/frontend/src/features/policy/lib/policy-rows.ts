import type { Policy } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import { humanDuration } from '@/shared/lib/time';

export interface PolicyRow {
  k: string;
  v: string;
}

export function policyRows(p: Policy): PolicyRow[] {
  return [
    {k: 'Currency', v: p.currency || 'USD'},
    {k: 'Per-payment cap', v: money(p.perTxCapMinor)},
    {k: 'Ask me above', v: money(p.approvalThresholdMinor)},
    {k: 'Daily budget', v: money(p.dailyBudgetMinor)},
    {k: 'Categories', v: p.allowedCategories.join(', ') || 'none'},
    {k: 'New payees', v: p.newPayeeRequiresApproval ? 'Need approval' : 'Allowed'},
    {k: 'Refunds', v: p.refundsRequireApproval ? 'Need approval' : 'Allowed'},
    {k: 'Speed limit', v: `${p.velocity.maxCalls} calls / ${p.velocity.windowSeconds} s`},
    {k: 'Approval expires after', v: humanDuration(p.approvalTtlSeconds)},
  ];
}

export interface DiffRow extends PolicyRow {
  now: string;
  next: string;
  changed: boolean;
}

export interface PolicyDiff {
  rows: DiffRow[];
  changeCount: number;
  warnings: string[];
}

/** Compares the draft against the active policy, row by row, and explains the risky changes in plain words. */
export function diffPolicies(current: Policy | null | undefined, draft: Policy): PolicyDiff {
  const nextRows = policyRows(draft);
  const nowRows = current ? policyRows(current) : nextRows.map((r) => ({k: r.k, v: '—'}));
  const rows: DiffRow[] = nextRows.map((r, i) => {
    const now = nowRows[i]?.v ?? '—';
    return {k: r.k, v: r.v, now, next: r.v, changed: !current || now !== r.v};
  });
  const warnings: string[] = [];
  if (current) {
    const removed = current.allowedCategories.filter((c) => !draft.allowedCategories.includes(c));
    if (removed.length) {
      warnings.push(
        `Removes the ${removed.join(', ')} ${removed.length === 1 ? 'category' : 'categories'}. The active policy allows ${
          removed.length === 1 ? 'it' : 'them'
        }, so calls in ${removed.join(', ')} will be denied with CATEGORY_NOT_ALLOWED.`,
      );
    }
    if (draft.perTxCapMinor > current.perTxCapMinor) warnings.push(`Raises the per-payment cap from ${money(current.perTxCapMinor)} to ${money(draft.perTxCapMinor)}.`);
    if (draft.approvalThresholdMinor > current.approvalThresholdMinor) {
      warnings.push(`Payments between ${money(current.approvalThresholdMinor)} and ${money(draft.approvalThresholdMinor)} will no longer wait for you.`);
    }
    if (current.newPayeeRequiresApproval && !draft.newPayeeRequiresApproval) warnings.push('New payees would no longer need your approval.');
    if (current.refundsRequireApproval && !draft.refundsRequireApproval) warnings.push('Refunds would no longer need your approval.');
    const draftPayees = new Set(draft.payeeAllowlist.map((d) => d.toLowerCase()));
    const droppedPayees = current.payeeAllowlist.filter((p) => !draftPayees.has(p.toLowerCase()));
    if (droppedPayees.length) warnings.push(`Drops ${droppedPayees.join(', ')} from the trusted payees.`);
    const kept: string[] = [];
    if (draft.dailyBudgetMinor === current.dailyBudgetMinor) kept.push('a daily budget');
    if (draft.velocity.maxCalls === current.velocity.maxCalls && draft.velocity.windowSeconds === current.velocity.windowSeconds) kept.push('a speed limit');
    if (draft.refundsRequireApproval === current.refundsRequireApproval) kept.push('refunds');
    if (kept.length) warnings.push(`Your sentence did not change ${joinWords(kept)}, so the active values were kept.`);
  }
  return {rows, changeCount: rows.filter((r) => r.changed).length, warnings};
}

function joinWords(items: string[]): string {
  if (items.length <= 1) return items.join('');
  return `${items.slice(0, -1).join(', ')} or ${items.at(-1)}`;
}

/** The server bumps the stored version itself; the document we send just needs sane defaults. */
export function prepareForActivation(draft: Policy, current: Policy | null | undefined): Policy {
  return {
    ...draft,
    version: (current?.version ?? 0) + 1,
    currency: draft.currency || current?.currency || 'USD',
    payeeAllowlist: draft.payeeAllowlist?.length ? draft.payeeAllowlist : (current?.payeeAllowlist ?? []),
  };
}
