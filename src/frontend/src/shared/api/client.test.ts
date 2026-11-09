import { http, HttpResponse } from 'msw';
import { server } from '@/test/setup';
import { state } from '@/test/msw/handlers';
import { api, ApiError, onUnauthorized, xsrfToken } from './client';

describe('api client', () => {
  it('reads the XSRF cookie', () => {
    document.cookie = 'XSRF-TOKEN=abc%20def';
    expect(xsrfToken()).toBe('abc def');
  });

  it('sends the XSRF header and JSON body on POST', async () => {
    await api.post('/api/approvals/a1/approve');
    const call = state.received.find((r) => r.url === '/api/approvals/a1/approve');
    expect(call?.xsrf).toBe('test-xsrf-token');
    expect(call?.method).toBe('POST');
  });

  it('turns a ProblemDetail into an ApiError', async () => {
    await expect(api.post('/api/approvals/nope/approve')).rejects.toMatchObject({
      status: 404,
      message: 'approval not found'
    });
    const err = (await api.post('/api/approvals/nope/approve').catch((e: unknown) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.problem?.detail).toBe('approval not found');
  });

  it('notifies unauthorized listeners on 401 unless silenced', async () => {
    state.loggedIn = false;
    const seen: number[] = [];
    const off = onUnauthorized((s) => seen.push(s));
    await expect(api.get('/api/stats')).rejects.toBeInstanceOf(ApiError);
    await expect(api.get('/api/auth/session', {silentAuth: true})).rejects.toBeInstanceOf(ApiError);
    off();
    expect(seen).toEqual([401]);
  });

  it('handles empty bodies', async () => {
    server.use(http.get('/api/empty', () => new HttpResponse(null, {status: 204})));
    await expect(api.get('/api/empty')).resolves.toBeUndefined();
  });
});
