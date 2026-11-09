import { type ReasonContext, reasonCopy } from '@/shared/lib/reasons';

export default function ReasonList({codes, ctx, tone = 'approve'}: Readonly<{
  codes: string[];
  ctx: ReasonContext;
  tone?: 'approve' | 'deny' | 'allow'
}>) {
  const color = {
    approve: 'border-approve-fg/20 bg-approve-bg text-approve-fg',
    deny: 'border-deny-fg/20 bg-deny-bg text-deny-fg',
    allow: 'border-allow-fg/20 bg-allow-bg text-allow-fg'
  }[tone];
  if (codes.length === 0) return null;
  return (
    <ul className="grid gap-2">
      {codes.map((code) => {
        const {label, text} = reasonCopy(code, ctx);
        return (
          <li key={code} className={`rounded-lg border px-3 py-2 text-sm ${color}`}>
            <span className="font-semibold">{label}.</span> {text}
          </li>
        );
      })}
    </ul>
  );
}
