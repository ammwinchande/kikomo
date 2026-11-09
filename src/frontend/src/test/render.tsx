import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store';
import { useApprovalsStore } from '@/features/approvals/store';
import { useActivityStore } from '@/features/activity/store';
import { useAuditStore } from '@/features/audit/store';
import { DEFAULT_SENTENCE, usePolicyStore } from '@/features/policy/store';
import { useDemoStore } from '@/features/demo/store';
import { useUiStore } from '@/shared/store/ui-store';

export function resetStores() {
  useAuthStore.setState({status: 'unknown'});
  useApprovalsStore.setState({decided: {}});
  useActivityStore.setState({selectedCallId: null, explainOpen: false});
  useAuditStore.setState({tamperedSeq: null, check: {kind: 'idle'}});
  usePolicyStore.setState({sentence: DEFAULT_SENTENCE, draft: null, compiler: null, liveVersion: null});
  useDemoStore.setState({runId: null});
  useUiStore.setState({toasts: []});
}

export function testQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {retry: false, staleTime: 0, gcTime: 0},
      mutations: {retry: false}
    }
  });
}

interface Options extends Omit<RenderOptions, 'wrapper'> {
  route?: string;
  path?: string;
  client?: QueryClient;
  /** Additional <Route> elements (e.g. navigation targets the page links to). */
  extraRoutes?: ReactNode;
}

/** Renders with a fresh QueryClient and a memory router at `route` (the element is mounted at `path`, default `*`). */
export function renderWithProviders(ui: ReactElement, {
  route = '/',
  path = '*',
  client = testQueryClient(),
  extraRoutes,
  ...options
}: Options = {}) {
  const result = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]} future={{v7_startTransition: true, v7_relativeSplatPath: true}}>
        <Routes>
          <Route path={path} element={ui}/>
          {extraRoutes}
          {path !== '/login' && <Route path="/login" element={<div>Login route</div>}/>}
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
    options,
  );
  return {...result, client};
}

export * from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';
