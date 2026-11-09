import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, onUnauthorized } from '@/shared/api/client';
import { keys } from '@/shared/api/keys';
import { authApi } from './api';
import { useAuthStore } from './store';

/** Probes the server once (and after any 401) and keeps the auth store in sync. */
export function useSession() {
  const status = useAuthStore((s) => s.status);
  const setAuthenticated = useAuthStore((s) => s.setAuthenticated);
  const setAnonymous = useAuthStore((s) => s.setAnonymous);
  const query = useQuery({
    queryKey: keys.session,
    queryFn: authApi.session,
    retry: false,
    staleTime: Infinity,
    meta: {silent: true},
  });
  useEffect(() => {
    if (query.isSuccess) setAuthenticated();
    else if (query.isError) setAnonymous();
  }, [query.isSuccess, query.isError, setAuthenticated, setAnonymous]);
  useEffect(() => onUnauthorized(() => setAnonymous()), [setAnonymous]);
  return {
    status: query.isPending && status === 'unknown' ? 'unknown' : status,
    isLoading: query.isPending && status === 'unknown'
  };
}

export function useLogin() {
  const qc = useQueryClient();
  const setAuthenticated = useAuthStore((s) => s.setAuthenticated);
  return useMutation({
    mutationFn: (password: string) => authApi.login(password),
    meta: {silent: true},
    onSuccess: () => {
      setAuthenticated();
      qc.setQueryData(keys.session, {ok: true});
      void qc.invalidateQueries();
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  const setAnonymous = useAuthStore((s) => s.setAnonymous);
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      setAnonymous();
      qc.clear();
    },
  });
}

export function useHealth() {
  return useQuery({queryKey: keys.health, queryFn: authApi.health, staleTime: 60_000, meta: {silent: true}});
}

export function loginErrorText(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) return 'That password is not right. Try again.';
  if (error instanceof ApiError) return error.message;
  return 'Could not reach the server.';
}
