import { create } from 'zustand';
import type { Policy } from '@/shared/api/types';

interface PolicyUiState {
  sentence: string;
  draft: Policy | null;
  compiler: 'llm' | 'stub' | null;
  liveVersion: number | null;
  setSentence: (s: string) => void;
  setDraft: (draft: Policy, compiler: 'llm' | 'stub') => void;
  discard: () => void;
  activated: (version: number) => void;
  dismissLive: () => void;
}

export const DEFAULT_SENTENCE = 'Allow dev tools up to $500, approve over $25';

export const usePolicyStore = create<PolicyUiState>((set) => ({
  sentence: DEFAULT_SENTENCE,
  draft: null,
  compiler: null,
  liveVersion: null,
  setSentence: (sentence) => set({sentence}),
  setDraft: (draft, compiler) => set({draft, compiler, liveVersion: null}),
  discard: () => set({draft: null, compiler: null}),
  activated: (version) => set({draft: null, compiler: null, liveVersion: version}),
  dismissLive: () => set({liveVersion: null}),
}));
