import type { ReactNode } from 'react';
import type { Decision } from '../api/types';

export type Tone = 'allow' | 'approve' | 'deny' | 'info' | 'brand' | 'neutral';

const tones: Record<Tone, string> = {
  allow: 'bg-allow-bg text-allow-fg',
  approve: 'bg-approve-bg text-approve-fg',
  deny: 'bg-deny-bg text-deny-fg',
  info: 'bg-info-bg text-info-fg',
  brand: 'bg-chip text-brand-deep',
  neutral: 'bg-soft text-muted',
};

export default function Pill({tone = 'neutral', children, className = '', title}: Readonly<{
  tone?: Tone;
  children: ReactNode;
  className?: string;
  title?: string
}>) {
  return (
    <span title={title}
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${tones[tone]} ${className}`}>
      {children}
    </span>
  );
}

export function getAppropriateDecision(decision: Decision): Tone {
  return decision === 'deny' ? 'deny' : 'approve';
}

export function decisionTone(decision: Decision): Tone {
  return decision === 'allow' ? 'allow' : getAppropriateDecision(decision);
}

export function decisionLabel(decision: Decision, status: string): string {
  if (decision === 'deny') return 'Denied';
  if (decision === 'needs_approval') {
    if (status === 'executed') return 'Approved';
    if (status === 'denied') return 'Denied by you';
    if (status === 'expired') return 'Expired';
    return 'Waiting for you';
  }
  if (status === 'failed') return 'Failed';
  if (status === 'executing') return 'Executing';
  return 'Executed';
}
