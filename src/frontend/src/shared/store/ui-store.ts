import { create } from 'zustand';

export type ToastKind = 'info' | 'success' | 'error';

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}

interface UiState {
  toasts: Toast[];
  toast: (text: string, kind?: ToastKind) => void;
  dismiss: (id: number) => void;
}

let nextId = 1;

export const useUiStore = create<UiState>((set) => ({
  toasts: [],
  toast: (text, kind = 'info') => {
    const id = nextId++;
    set((s) => ({toasts: [...s.toasts, {id, kind, text}]}));
    if (typeof window !== 'undefined') window.setTimeout(() => set((s) => ({toasts: s.toasts.filter((t) => t.id !== id)})), 5000);
  },
  dismiss: (id) => set((s) => ({toasts: s.toasts.filter((t) => t.id !== id)})),
}));

export const toast = (text: string, kind?: ToastKind) => useUiStore.getState().toast(text, kind);
