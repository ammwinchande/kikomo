import { create } from 'zustand';

interface DemoUiState {
  runId: string | null;
  setRun: (id: string) => void;
  clear: () => void;
}

export const useDemoStore = create<DemoUiState>((set) => ({
  runId: null,
  setRun: (runId) => set({ runId }),
  clear: () => set({ runId: null }),
}));
