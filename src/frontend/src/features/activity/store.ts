import { create } from 'zustand';

interface ActivityUiState {
  selectedCallId: string | null;
  explainOpen: boolean;
  select: (id: string) => void;
  toggleExplain: () => void;
}

export const useActivityStore = create<ActivityUiState>((set) => ({
  selectedCallId: null,
  explainOpen: false,
  select: (id) => set((s) => ({ selectedCallId: id, explainOpen: s.selectedCallId === id ? s.explainOpen : false })),
  toggleExplain: () => set((s) => ({ explainOpen: !s.explainOpen })),
}));
