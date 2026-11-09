import type { ProblemDetail } from './types';

/** Thrown for every non-2xx response. `problem` is the parsed RFC 9457 body when the server sent one. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, fallback: string) {
    super(problem?.detail || problem?.title || fallback);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/** Reads the double-submit CSRF cookie that Spring Security sets on every response. */
export function xsrfToken(): string {
  const m = /(?:^|; )XSRF-TOKEN=([^;]*)/.exec(document.cookie);
  return m ? decodeURIComponent(m[1]) : '';
}

type Listener = (status: number) => void;
const unauthorizedListeners = new Set<Listener>();

/** Lets the auth store learn about 401/403 answers without importing it here (avoids a cycle). */
export function onUnauthorized(listener: Listener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  /** When true a 401/403 does not notify the auth store (used by the session probe itself). */
  silentAuth?: boolean;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET';
  const headers: Record<string, string> = {Accept: 'application/json'};
  if (method !== 'GET') {
    headers['Content-Type'] = 'application/json';
    headers['X-XSRF-TOKEN'] = xsrfToken();
  }
  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    signal: opts.signal,
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => null) as ProblemDetail | null;
    if ((res.status === 401 || res.status === 403) && !opts.silentAuth) {
      unauthorizedListeners.forEach((l) => l(res.status));
    }
    throw new ApiError(res.status, problem, `${method} ${path} failed with ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, opts?: Omit<RequestOptions, 'method' | 'body'>) => request<T>(path, {...opts, method: 'GET'}),
  post: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, {...opts, method: 'POST', body}),
};
