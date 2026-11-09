import { http, HttpResponse } from 'msw';
import * as f from '../fixtures';

/** Mutable server state for a test; reset by `resetServerState()` in setup. */
export const state = {
  loggedIn: true,
  approvals: [...f.approvals],
  agents: [{ ...f.agent }],
  calls: [...f.calls],
  demoRuns: new Map<string, typeof f.demoRunDone>(),
  demoMode: true,
  auditValid: true,
  received: [] as { method: string; url: string; xsrf: string | null; body: unknown }[],
};

export function resetServerState() {
  state.loggedIn = true;
  state.approvals = f.approvals.map((a) => ({ ...a }));
  state.agents = [{ ...f.agent }];
  state.calls = [...f.calls];
  state.demoRuns = new Map();
  state.demoMode = true;
  state.auditValid = true;
  state.received = [];
}

const unauthorized = () => HttpResponse.json({ status: 401, title: 'Unauthorized' }, { status: 401 });
const problem = (status: number, detail: string, extra: Record<string, unknown> = {}) => HttpResponse.json({ status, title: 'Error', detail, ...extra }, { status });

async function record(request: Request) {
  const body = request.method === 'GET' ? null : await request.clone().text().then((t) => (t ? JSON.parse(t) : null)).catch(() => null);
  state.received.push({ method: request.method, url: new URL(request.url).pathname + new URL(request.url).search, xsrf: request.headers.get('X-XSRF-TOKEN'), body });
}

const guard = (_request: Request) => (state.loggedIn ? null : unauthorized());

export const handlers = [
  http.get('/api/health', () => HttpResponse.json({ status: 'ok', paypal_mode: 'mock', mock: true })),
  http.get('/api/auth/session', ({ request }) => guard(request) ?? HttpResponse.json({ ok: true })),
  http.post('/api/auth/login', async ({ request }) => {
    await record(request);
    const body = (await request.json()) as { password?: string };
    if (body.password === 'admin') {
      state.loggedIn = true;
      return HttpResponse.json({ ok: true });
    }
    return problem(401, 'bad password');
  }),
  http.post('/api/auth/logout', async ({ request }) => {
    await record(request);
    state.loggedIn = false;
    return HttpResponse.json({ ok: true });
  }),

  http.get('/api/stats', ({ request }) => {
    const pending = state.approvals.filter((a) => a.status === 'pending').length;
    return guard(request) ?? HttpResponse.json({ ...f.stats, pending_approvals: pending, audit_valid: state.auditValid });
  }),
  http.get('/api/approvals', ({ request }) => {
    const status = new URL(request.url).searchParams.get('status');
    return guard(request) ?? HttpResponse.json(status ? state.approvals.filter((a) => a.status === status) : state.approvals);
  }),
  http.post('/api/approvals/:id/:decision', async ({ request, params }) => {
    await record(request);
    const a = state.approvals.find((x) => x.id === params.id);
    if (!a) return problem(404, 'approval not found');
    if (a.status !== 'pending') return problem(409, 'already decided');
    a.status = params.decision === 'approve' ? 'approved' : 'denied';
    return HttpResponse.json({ ok: true });
  }),

  http.get('/api/agents', ({ request }) => guard(request) ?? HttpResponse.json(state.agents)),
  http.post('/api/agents/:id/:action', async ({ request, params }) => {
    await record(request);
    const a = state.agents.find((x) => x.id === params.id);
    if (!a) return problem(404, 'agent not found');
    a.status = params.action === 'suspend' ? 'suspended' : 'active';
    return HttpResponse.json({ ok: true, status: a.status });
  }),

  http.get('/api/calls', ({ request }) => guard(request) ?? HttpResponse.json(state.calls)),
  http.get('/api/calls/:id', ({ request, params }) => {
    const c = state.calls.find((x) => x.id === params.id);
    return guard(request) ?? (c ? HttpResponse.json(c) : problem(404, 'call not found'));
  }),

  http.get('/api/policies/compile', () => problem(405, 'method')),
  http.get('/api/policies/:agentId', ({ request }) => guard(request) ?? HttpResponse.json(f.policy)),
  http.post('/api/policies/compile', async ({ request }) => {
    await record(request);
    const body = (await request.json()) as { text: string; allowlist: string[] };
    const draft = { ...f.policy, allowedCategories: ['developer_tools'], payeeAllowlist: body.allowlist };
    return HttpResponse.json({ draft, source_text: body.text, compiler: 'stub' });
  }),
  http.post('/api/policies/:agentId/activate', async ({ request }) => {
    await record(request);
    const body = (await request.json()) as { perTxCapMinor: number };
    if (body.perTxCapMinor > 100000) return problem(400, 'invalid policy', { errors: ['perTxCapMinor must be <= HARD_MAX_TX_MINOR (100000)'] });
    return HttpResponse.json({ ok: true });
  }),

  http.get('/api/audit/entries', ({ request }) => {
    const limit = Number(new URL(request.url).searchParams.get('limit') ?? 200);
    return guard(request) ?? HttpResponse.json(f.auditEntries.slice(-limit));
  }),
  http.get('/api/audit/verify', async ({ request }) => {
    await record(request);
    return guard(request) ?? HttpResponse.json({ ok: state.auditValid, valid: state.auditValid });
  }),

  http.get('/api/demo/connect', ({ request }) =>
    guard(request) ?? (state.demoMode ? HttpResponse.json({ mcp_url: 'http://localhost:8080/mcp', malicious_page_url: 'http://localhost:8080/demo/malicious-page' }) : problem(404, 'demo mode is off')),
  ),
  http.post('/api/demo/runs', async ({ request }) => {
    await record(request);
    if (!state.demoMode) return problem(404, 'demo mode is off');
    const { scenario } = (await request.json()) as { scenario: string };
    const run = scenario === 'large' ? { ...f.demoRunWaiting } : { ...f.demoRunDone, status: 'running' as const, steps: f.demoRunDone.steps.slice(0, 1) };
    state.demoRuns.set(run.id, run);
    return HttpResponse.json(run);
  }),
  http.get('/api/demo/runs/:id', ({ request, params }) => {
    const run = state.demoRuns.get(String(params.id));
    if (!run) return guard(request) ?? problem(404, 'demo run not found');
    // Each poll advances the small run until it is done.
    if (run.scenario === 'small' && run.status === 'running') {
      const next = f.demoRunDone.steps.slice(0, run.steps.length + 1);
      Object.assign(run, { steps: next, status: next.length === f.demoRunDone.steps.length ? 'done' : 'running' });
    }
    return HttpResponse.json(run);
  }),
];
