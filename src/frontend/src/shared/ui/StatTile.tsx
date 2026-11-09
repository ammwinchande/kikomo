import type { ReactNode } from 'react';
import Card from './Card';

export default function StatTile({label, value, hint, tone = 'ink'}: Readonly<{
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: 'ink' | 'allow' | 'deny' | 'brand'
}>) {
  const color = {ink: 'text-ink', allow: 'text-allow-fg', deny: 'text-deny-fg', brand: 'text-brand'}[tone];
  return (
    <Card className="min-w-0">
      <div className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</div>
      <div className={`mt-1 text-2xl font-bold ${color}`}>{value}</div>
      {hint && <div className="mt-0.5 text-xs text-muted">{hint}</div>}
    </Card>
  );
}
