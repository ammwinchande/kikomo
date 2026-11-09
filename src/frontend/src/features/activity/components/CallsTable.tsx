import type { CallView } from '@/shared/api/types';
import { money } from '@/shared/lib/money';
import { clockTime } from '@/shared/lib/time';
import Pill, { decisionLabel, decisionTone } from '@/shared/ui/Pill';
import { Table, Td, Th } from '@/shared/ui/Table';

function target(c: CallView): string {
  if (c.counterparty) return c.counterparty;
  const url = c.args && typeof c.args.url === 'string' ? c.args.url : null;
  if (url) {
    try {
      const u = new URL(url);
      return u.pathname + u.search;
    } catch {
      return url;
    }
  }
  return '—';
}

export default function CallsTable({calls, selectedId, onSelect}: Readonly<{
  calls: CallView[];
  selectedId: string | null;
  onSelect: (id: string) => void
}>) {
  return (
    <Table ariaLabel="Agent calls, newest first">
      <thead>
      <tr>
        <Th>Time</Th>
        <Th>Call</Th>
        <Th className="text-right">Amount</Th>
        <Th>Decision</Th>
      </tr>
      </thead>
      <tbody>
      {calls.map((c) => {
        const selected = c.id === selectedId;
        return (
          <tr
            key={c.id}
            onClick={() => onSelect(c.id)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onSelect(c.id);
              }
            }}
            tabIndex={0}
            role="row"
            aria-selected={selected}
            className={`cursor-pointer transition-colors ${selected ? 'bg-selected' : 'bg-white hover:bg-bg'}`}
          >
            <Td className="whitespace-nowrap tabular-nums text-muted">{clockTime(c.created_at)}</Td>
            <Td>
              <div className="font-semibold">{c.tool}</div>
              <div className="truncate text-xs text-muted" title={target(c)}>
                {target(c)}
              </div>
            </Td>
            <Td
              className="whitespace-nowrap text-right font-semibold tabular-nums">{money(c.amount_minor, c.currency)}</Td>
            <Td>
              <Pill tone={decisionTone(c.decision)}>{decisionLabel(c.decision, c.status)}</Pill>
            </Td>
          </tr>
        );
      })}
      </tbody>
    </Table>
  );
}
