import type { AuditEntryView } from '@/shared/api/types';
import { auditLabel, GENESIS_HASH, shortHash } from '@/shared/lib/audit-events';
import Pill from '@/shared/ui/Pill';
import { Table, Td, Th } from '@/shared/ui/Table';
import { type ChainCheck, tamperHash } from '../store';

interface Props {
  entries: AuditEntryView[];
  tamperedSeq: number | null;
  check: ChainCheck;
}

function linkStatus(seq: number, check: ChainCheck): { label: string; tone: 'allow' | 'deny' | 'neutral' | 'approve' } {
  if (check.kind === 'idle') return {label: 'Not checked yet', tone: 'neutral'};
  if (check.kind === 'ok') return {label: 'Linked', tone: 'allow'};
  if (check.atSeq === 0) return {label: 'Untrusted', tone: 'deny'};
  if (seq < check.atSeq) return {label: 'Linked', tone: 'allow'};
  if (seq === check.atSeq) return {label: 'Fingerprint mismatch', tone: 'deny'};
  return {label: 'Untrusted', tone: 'approve'};
}

export default function EntriesTable({entries, tamperedSeq, check}: Readonly<Props>) {
  return (
    <Table ariaLabel="Audit log entries in order">
      <thead>
      <tr>
        <Th>#</Th>
        <Th>Event</Th>
        <Th>Fingerprint</Th>
        <Th>Follows</Th>
        <Th>Link</Th>
      </tr>
      </thead>
      <tbody>
      {entries.map((e) => {
        const {event, detail} = auditLabel(e);
        const hash = e.seq === tamperedSeq ? tamperHash(e.hash) : e.hash;
        const status = linkStatus(e.seq, check);
        const broken = check.kind === 'bad' && check.atSeq > 0 && e.seq === check.atSeq;
        return (
          <tr key={e.seq} className={broken ? 'bg-deny-bg/40' : 'bg-white'} data-testid={`audit-${e.seq}`}>
            <Td className="tabular-nums text-muted">{e.seq}</Td>
            <Td>
              <div className="font-semibold">{event}</div>
              {detail && <div className="text-xs text-muted">{detail}</div>}
            </Td>
            <Td className="font-mono text-xs" title={hash}>
              {shortHash(hash)}
            </Td>
            <Td className="font-mono text-xs text-muted" title={e.prev_hash}>
              {e.prev_hash === GENESIS_HASH ? 'start' : shortHash(e.prev_hash)}
            </Td>
            <Td>
              <Pill tone={status.tone}>{status.label}</Pill>
            </Td>
          </tr>
        );
      })}
      </tbody>
    </Table>
  );
}
