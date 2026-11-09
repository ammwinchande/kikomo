import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/client';
import { toast } from '@/shared/store/ui-store';

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return 'Your session ended. Please sign in again.';
    return error.message;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 5_000, retry: 1, refetchOnWindowFocus: true },
      mutations: { retry: 0 },
    },
    queryCache: new QueryCache({
      onError: (error, query) => {
        // Background refetch failures of auth-gated data are expected right after logout; stay quiet for those.
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) return;
        if (query.meta?.silent) return;
        toast(describe(error), 'error');
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _vars, _ctx, mutation) => {
        if (mutation.meta?.silent) return;
        toast(describe(error), 'error');
      },
    }),
  });
}

export const queryClient = createQueryClient();
