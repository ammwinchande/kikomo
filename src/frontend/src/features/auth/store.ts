import { create } from 'zustand';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

interface AuthState {
  status: AuthStatus;
  setAuthenticated: () => void;
  setAnonymous: () => void;
  reset: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  setAuthenticated: () => set({status: 'authenticated'}),
  setAnonymous: () => set({status: 'anonymous'}),
  reset: () => set({status: 'unknown'}),
}));
