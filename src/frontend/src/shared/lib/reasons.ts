import type { Policy, ReasonCode } from '../api/types';
import { money } from './money';

/** Facts about the call that the copy can refer to. */
export interface ReasonContext {
  amountMinor?: number | null;
  currency?: string | null;
  counterparty?: string | null;
  category?: string | null;
  tool?: string;
  policy?: Policy | null;
}

export interface ReasonCopy {
  /** Short label, e.g. "Over the approval threshold". */
  label: string;
  /** One or two plain sentences. */
  text: string;
}

/** Which policy fields each code involves; drives the "Policy values involved" list. */
export type PolicyField =
  | 'perTxCap'
  | 'approvalThreshold'
  | 'dailyBudget'
  | 'allowlist'
  | 'newPayees'
  | 'categories'
  | 'velocity'
  | 'refunds'
  | 'currency'
  | 'agentStatus'
  | 'toolType'
  | 'hostAllowed'
  | 'sourceOfPayee'
  | 'denyWins';

const amt = (c: ReasonContext) => money(c.amountMinor ?? null, c.currency ?? 'USD');
const threshold = (c: ReasonContext) => money(c.policy?.approvalThresholdMinor ?? null);
const cap = (c: ReasonContext) => money(c.policy?.perTxCapMinor ?? null);
const budget = (c: ReasonContext) => money(c.policy?.dailyBudgetMinor ?? null);
const payee = (c: ReasonContext) => c.counterparty || 'This payee';

export const REASONS: Record<ReasonCode, {
  label: string;
  text: (c: ReasonContext) => string;
  fields: PolicyField[]
}> = {
  OK_READ_ONLY: {
    label: 'Read only',
    text: () => 'Reading cannot move money. Anything found this way is marked untrusted from here on.',
    fields: ['toolType', 'hostAllowed'],
  },
  OK_WITHIN_POLICY: {
    label: 'Within policy',
    text: (c) => `This ${amt(c)} payment is inside every limit and goes to a trusted payee.`,
    fields: ['perTxCap', 'approvalThreshold', 'allowlist'],
  },
  AGENT_SUSPENDED: {
    label: 'Agent suspended',
    text: () => 'You switched this agent off with the kill switch, so every call is refused.',
    fields: ['agentStatus', 'denyWins'],
  },
  NO_ACTIVE_POLICY: {
    label: 'No active policy',
    text: () => 'This agent has no confirmed policy yet, so nothing is allowed.',
    fields: ['denyWins'],
  },
  INVALID_AMOUNT_FORMAT: {
    label: 'Amount not readable',
    text: () => 'The amount the agent sent is not a valid number of dollars and cents.',
    fields: ['denyWins'],
  },
  INVALID_CURRENCY: {
    label: 'Wrong currency',
    text: (c) => `The policy is in ${c.policy?.currency || 'USD'}; the agent asked for something else.`,
    fields: ['currency', 'denyWins'],
  },
  AMOUNT_OVER_CAP: {
    label: 'Over the per-payment cap',
    text: (c) => `${amt(c)} is more than the ${cap(c)} any single payment may be.`,
    fields: ['perTxCap', 'denyWins'],
  },
  DAILY_BUDGET_EXCEEDED: {
    label: 'Daily budget used up',
    text: (c) => `This payment would push today's spend past the ${budget(c)} daily budget.`,
    fields: ['dailyBudget', 'denyWins'],
  },
  VELOCITY_EXCEEDED: {
    label: 'Too many calls',
    text: (c) =>
      c.policy
        ? `More than ${c.policy.velocity.maxCalls} calls in ${c.policy.velocity.windowSeconds} seconds. The agent has to slow down.`
        : 'The agent made too many calls in a short time.',
    fields: ['velocity', 'denyWins'],
  },
  UNTRUSTED_COUNTERPARTY: {
    label: 'Payee came from a web page',
    text: () =>
      'This payee address first appeared inside a web page the agent read, not in anything you approved. A page cannot add a payee.',
    fields: ['sourceOfPayee', 'allowlist', 'denyWins'],
  },
  CATEGORY_NOT_ALLOWED: {
    label: 'Category not allowed',
    text: (c) => `"${c.category || 'this category'}" is not one of the categories the policy allows.`,
    fields: ['categories', 'denyWins'],
  },
  INVALID_COUNTERPARTY_FORMAT: {
    label: 'Payee not an e-mail',
    text: () => 'The payee the agent sent is not a valid e-mail address.',
    fields: ['denyWins'],
  },
  SSRF_BLOCKED: {
    label: 'Host not allowed',
    text: () => 'The agent tried to read a page on a host that is not on the allowed list.',
    fields: ['hostAllowed', 'denyWins'],
  },
  NEW_PAYEE_REQUIRES_APPROVAL: {
    label: 'New payee',
    text: (c) => `${payee(c)} is not on your trusted list yet.`,
    fields: ['newPayees', 'allowlist'],
  },
  AMOUNT_REQUIRES_APPROVAL: {
    label: 'Over the approval threshold',
    text: (c) => `This payment of ${amt(c)} is above the ${threshold(c)} level where you asked to be consulted.`,
    fields: ['approvalThreshold', 'perTxCap'],
  },
  REFUND_REQUIRES_APPROVAL: {
    label: 'Refund needs approval',
    text: () => 'Your policy asks a person to approve every refund.',
    fields: ['refunds'],
  },
};

export function isReasonCode(code: string): code is ReasonCode {
  return Object.hasOwn(REASONS, code);
}

export function reasonCopy(code: string, ctx: ReasonContext = {}): ReasonCopy {
  if (isReasonCode(code)) return {label: REASONS[code].label, text: REASONS[code].text(ctx)};
  return {label: code.replaceAll('_', ' ').toLowerCase(), text: 'The policy engine reported this code.'};
}

export interface PolicyValue {
  k: string;
  v: string;
}

/** The policy values the Why panel should show for a set of reason codes. */
export function policyValuesFor(codes: string[], ctx: ReasonContext, agentSuspended = false): PolicyValue[] {
  const fields = new Set<PolicyField>();
  for (const code of codes) if (isReasonCode(code)) REASONS[code].fields.forEach((f) => fields.add(f));
  const p = ctx.policy;
  const out: PolicyValue[] = [];
  const onList = !!ctx.counterparty && !!p?.payeeAllowlist?.some((e) => e.toLowerCase() === ctx.counterparty!.toLowerCase());
  for (const f of fields) {
    switch (f) {
      case 'perTxCap':
        if (p) out.push({k: 'Per-payment cap', v: money(p.perTxCapMinor)});
        break;
      case 'approvalThreshold':
        if (p) out.push({k: 'Approval threshold', v: money(p.approvalThresholdMinor)});
        break;
      case 'dailyBudget':
        if (p) out.push({k: 'Daily budget', v: money(p.dailyBudgetMinor)});
        break;
      case 'allowlist':
        if (p) out.push({k: 'Payee on allowlist', v: onList ? 'Yes' : 'No'});
        break;
      case 'newPayees':
        if (p) out.push({k: 'New payees', v: p.newPayeeRequiresApproval ? 'Need approval' : 'Allowed'});
        break;
      case 'categories':
        if (p) out.push({k: 'Allowed categories', v: p.allowedCategories.join(', ') || 'none'});
        break;
      case 'velocity':
        if (p) out.push({k: 'Speed limit', v: `${p.velocity.maxCalls} calls / ${p.velocity.windowSeconds} s`});
        break;
      case 'refunds':
        if (p) out.push({k: 'Refunds', v: p.refundsRequireApproval ? 'Need approval' : 'Allowed'});
        break;
      case 'currency':
        out.push({k: 'Policy currency', v: p?.currency || 'USD'});
        break;
      case 'agentStatus':
        out.push({k: 'Agent status', v: agentSuspended ? 'Suspended' : 'Active'});
        break;
      case 'toolType':
        out.push({k: 'Tool type', v: 'Read only'});
        break;
      case 'hostAllowed':
        out.push({k: 'Host allowed', v: codes.includes('SSRF_BLOCKED') ? 'No' : 'Yes'});
        break;
      case 'sourceOfPayee':
        out.push({k: 'Source of payee', v: 'kikomo_fetch_url'});
        break;
      case 'denyWins':
        out.push({k: 'Rule that decided', v: 'Deny wins'});
        break;
    }
  }
  return out;
}
