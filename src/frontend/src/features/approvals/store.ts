import { create } from 'zustand';

export type LocalDecision = 'approved' | 'denied';

/**
 * UI-only memory of what the admin just decided, so a card can show "Approved and executed" / "Denied by you"
 * (with a Reset) instead of vanishing the moment the server drops it from the pending list.
 */
interface ApprovalsUiState {
  decided: Record<string, LocalDecision>;
  markDecided: (id: string, decision: LocalDecision) => void;
  reset: (id: string) => void;
  clearAll: () => void;
}

export const useApprovalsStore = create<ApprovalsUiState>((set) => ({
  decided: {},
  markDecided: (id, decision) => set((s) => ({decided: {...s.decided, [id]: decision}})),
  reset: (id) =>
    set((s) => {
      const next = {...s.decided};
      delete next[id];
      return {decided: next};
    }),
  clearAll: () => set({decided: {}}),
}));
