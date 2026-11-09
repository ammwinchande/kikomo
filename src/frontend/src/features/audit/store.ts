import { create } from 'zustand';

export type ChainCheck = { kind: 'idle' } | { kind: 'ok'; count: number } | {
  kind: 'bad';
  atSeq: number;
  count: number
};

/**
 * The "Simulate tampering" control is a client-side demonstration only: the audit_log table is append-only in the
 * database, so the UI pretends one past entry changed and shows what the verifier would report.
 */
interface AuditUiState {
  tamperedSeq: number | null;
  check: ChainCheck;
  simulate: (seq: number) => void;
  repair: () => void;
  setCheck: (check: ChainCheck) => void;
}

export const useAuditStore = create<AuditUiState>((set) => ({
  tamperedSeq: null,
  check: {kind: 'idle'},
  simulate: (seq) => set({tamperedSeq: seq, check: {kind: 'idle'}}),
  repair: () => set({tamperedSeq: null, check: {kind: 'idle'}}),
  setCheck: (check) => set({check}),
}));

/** Flips the last hex digit so the fingerprint visibly differs. */
export function tamperHash(hash: string): string {
  const last = hash.slice(-1);
  const flipped = last === 'f' ? '0' : (Number.parseInt(last, 16) + 1).toString(16);
  return hash.slice(0, -1) + flipped;
}
