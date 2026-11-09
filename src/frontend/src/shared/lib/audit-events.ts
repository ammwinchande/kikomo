import type { AuditEntryView } from '../api/types';
import { money } from './money';
import { reasonCopy } from './reasons';

export interface AuditLabel {
  event: string;
  detail: string;
}

const str = (v: unknown) => (v === null || v === undefined ? '' : String(v));

/** Human labels for an audit_log row, from its event_type and payload (see co.tz.kikomo.audit.AuditEvent). */
export function auditLabel(entry: Pick<AuditEntryView, 'event_type' | 'payload'>): AuditLabel {
  const p = entry.payload ?? {};
  const tool = str(p.tool);
  const amount = typeof p.amount_minor === 'number' ? money(p.amount_minor) : '';
  const codes = Array.isArray(p.reason_codes) ? (p.reason_codes as unknown[]).map(str) : [];
  switch (entry.event_type) {
    case 'call_evaluated': {
      const decision = str(p.decision);
      const event =
        decision === 'allow' ? 'Call decided: allowed' : decision === 'deny' ? 'Call decided: denied' : 'Call decided: needs approval';
      const why = decision === 'deny' && codes[0] ? reasonCopy(codes[0]).label : '';
      return {event, detail: [tool, amount, why].filter(Boolean).join(' · ') || 'Policy evaluated'};
    }
    case 'call_executed':
      return {event: 'Payment executed', detail: [tool, amount].filter(Boolean).join(' ') || 'PayPal call accepted'};
    case 'call_failed':
      return {event: 'Payment failed', detail: str(p.error) || tool || 'PayPal call failed'};
    case 'call_recording_failed':
      return {event: 'Recording failed', detail: 'The result could not be stored'};
    case 'approval_approved':
      return {event: 'Approval granted', detail: `By ${str(p.decided_by) || 'admin'}`};
    case 'approval_denied':
      return {event: 'Approval denied', detail: `By ${str(p.decided_by) || 'admin'}`};
    case 'approval_expired':
      return {event: 'Approval expired', detail: 'Nobody decided in time'};
    case 'agent_suspended':
      return {event: 'Agent suspended', detail: 'Kill switch pressed'};
    case 'agent_activated':
      return {event: 'Agent resumed', detail: 'Kill switch released'};
    case 'policy_activated': {
      const cap = typeof p.per_tx_cap_minor === 'number' ? `cap ${money(p.per_tx_cap_minor)}` : '';
      return {event: 'Policy activated', detail: cap || 'New policy version'};
    }
    default:
      return {event: entry.event_type.replaceAll("_", ' '), detail: ''};
  }
}

/** First 8 hex characters, the way the design shows fingerprints. */
export function shortHash(hash: string): string {
  return hash.slice(0, 8);
}

export const GENESIS_HASH = '0'.repeat(64);
