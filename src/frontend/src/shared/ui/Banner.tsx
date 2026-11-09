import type { ReactNode } from 'react';
import type { Tone } from './Pill';

const tones: Record<Tone, string> = {
  allow: 'border-allow-fg/20 bg-allow-bg text-allow-fg',
  approve: 'border-approve-fg/20 bg-approve-bg text-approve-fg',
  deny: 'border-deny-fg/20 bg-deny-bg text-deny-fg',
  info: 'border-line bg-info-bg text-info-fg',
  brand: 'border-brand/20 bg-chip text-brand-deep',
  neutral: 'border-line bg-soft text-muted',
};

export default function Banner({tone = 'info', children, action, role = 'status'}: Readonly<{
  tone?: Tone;
  children: ReactNode;
  action?: ReactNode;
  role?: 'status' | 'alert'
}>) {
  return (
    <div role={role}
         className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border px-4 py-3 text-sm font-medium ${tones[tone]}`}>
      <div>{children}</div>
      {action}
    </div>
  );
}
