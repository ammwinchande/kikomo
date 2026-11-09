import type {
  AgentView,
  ApprovalView,
  AuditEntryView,
  CallView,
  DemoRunView,
  Policy,
  StatsView
} from '@/shared/api/types';

export const AGENT_ID = '11111111-1111-1111-1111-111111111111';
const NOW = new Date('2026-11-09T09:45:00Z');
export const now = () => new Date(NOW);
const iso = (secondsAgo: number) => new Date(NOW.getTime() - secondsAgo * 1000).toISOString();

export const policy: Policy = {
  version: 1,
  currency: 'USD',
  perTxCapMinor: 50000,
  dailyBudgetMinor: 60000,
  approvalThresholdMinor: 2500,
  payeeAllowlist: ['devtools@example.com', 'events@example.com'],
  newPayeeRequiresApproval: true,
  allowedCategories: ['developer_tools', 'events'],
  velocity: {maxCalls: 5, windowSeconds: 60},
  refundsRequireApproval: true,
  approvalTtlSeconds: 900,
};

export const agent: AgentView = {
  id: AGENT_ID,
  name: 'demo-agent',
  status: 'active',
  spent_today_minor: 1200,
  daily_budget_minor: 60000,
  policy_version: 1,
  created_at: iso(86400),
};


function call(partial: Partial<CallView> & Pick<CallView, 'id' | 'decision' | 'status' | 'reason_codes'>): CallView {
  return {
    agent_id: AGENT_ID,
    tool: 'send_payment',
    counterparty: 'devtools@example.com',
    amount_minor: 1200,
    currency: 'USD',
    category: 'developer_tools',
    explanation: 'Within policy limits.',
    args: {amount: '12.00', currency: 'USD', payee_email: 'devtools@example.com', category: 'developer_tools'},
    result: null,
    approval_id: null,
    created_at: iso(240),
    ...partial,
  };
}

export const calls: CallView[] = [
  call({
    id: 'c6',
    decision: 'deny',
    status: 'denied',
    reason_codes: ['AGENT_SUSPENDED'],
    explanation: 'Agent is suspended by kill switch.',
    created_at: iso(10)
  }),
  call({
    id: 'c5',
    decision: 'needs_approval',
    status: 'pending_approval',
    reason_codes: ['NEW_PAYEE_REQUIRES_APPROVAL', 'AMOUNT_REQUIRES_APPROVAL'],
    counterparty: 'newvendor@example.org',
    amount_minor: 6000,
    approval_id: 'a2',
    created_at: iso(60),
    explanation: 'New payee requires human approval. Amount is at or above approval threshold.'
  }),
  call({
    id: 'c3',
    decision: 'deny',
    status: 'denied',
    reason_codes: ['UNTRUSTED_COUNTERPARTY'],
    counterparty: 'pay-me@evil-payments.example',
    amount_minor: 9000,
    explanation: 'Counterparty came from untrusted fetched content.',
    created_at: iso(100),
    args: {amount: '90.00', currency: 'USD', payee_email: 'pay-me@evil-payments.example', category: 'developer_tools'}
  }),
  call({
    id: 'c4',
    tool: 'kikomo_fetch_url',
    decision: 'allow',
    status: 'executed',
    reason_codes: ['OK_READ_ONLY'],
    counterparty: null,
    amount_minor: null,
    currency: null,
    category: null,
    explanation: 'Read-only tool allowed.',
    created_at: iso(104),
    args: {url: 'http://localhost:8080/demo/malicious-page'}
  }),
  call({
    id: 'c2',
    decision: 'needs_approval',
    status: 'pending_approval',
    reason_codes: ['AMOUNT_REQUIRES_APPROVAL'],
    counterparty: 'events@example.com',
    category: 'events',
    amount_minor: 48000,
    approval_id: 'a1',
    created_at: iso(120),
    explanation: 'Amount is at or above approval threshold.'
  }),
  call({
    id: 'c1',
    decision: 'allow',
    status: 'executed',
    reason_codes: ['OK_WITHIN_POLICY'],
    result: {id: 'MOCK-PAYOUT-1'},
    created_at: iso(300)
  }),
];

export const approvals: ApprovalView[] = [
  {
    id: 'a1',
    call_id: 'c2',
    agent_id: AGENT_ID,
    status: 'pending',
    decided_by: null,
    decided_at: null,
    expires_at: iso(-761),
    created_at: iso(120),
    call: calls.find((c) => c.id === 'c2')!
  },
  {
    id: 'a2',
    call_id: 'c5',
    agent_id: AGENT_ID,
    status: 'pending',
    decided_by: null,
    decided_at: null,
    expires_at: iso(-598),
    created_at: iso(60),
    call: calls.find((c) => c.id === 'c5')!
  },
];

export const stats: StatsView = {
  pending_approvals: 2,
  allowed_today: 2,
  denied_today: 2,
  needs_approval_today: 2,
  audit_valid: true
};

const h = (n: number) => (n === 0 ? '0'.repeat(64) : `${n.toString(16)}f`.padStart(8, n.toString(16)).repeat(8));
export const auditEntries: AuditEntryView[] = [
  {
    seq: 1,
    ts: iso(400),
    agent_id: AGENT_ID,
    event_type: 'policy_activated',
    payload: {agent_id: AGENT_ID, per_tx_cap_minor: 50000, daily_budget_minor: 60000},
    prev_hash: h(0),
    hash: h(1)
  },
  {
    seq: 2,
    ts: iso(300),
    agent_id: AGENT_ID,
    event_type: 'call_evaluated',
    payload: {call_id: 'c1', tool: 'send_payment', decision: 'allow', reason_codes: ['OK_WITHIN_POLICY']},
    prev_hash: h(1),
    hash: h(2)
  },
  {
    seq: 3,
    ts: iso(299),
    agent_id: AGENT_ID,
    event_type: 'call_executed',
    payload: {call_id: 'c1', tool: 'send_payment'},
    prev_hash: h(2),
    hash: h(3)
  },
  {
    seq: 4,
    ts: iso(120),
    agent_id: AGENT_ID,
    event_type: 'call_evaluated',
    payload: {
      call_id: 'c2',
      tool: 'send_payment',
      decision: 'needs_approval',
      reason_codes: ['AMOUNT_REQUIRES_APPROVAL']
    },
    prev_hash: h(3),
    hash: h(4)
  },
  {
    seq: 5,
    ts: iso(100),
    agent_id: AGENT_ID,
    event_type: 'call_evaluated',
    payload: {call_id: 'c3', tool: 'send_payment', decision: 'deny', reason_codes: ['UNTRUSTED_COUNTERPARTY']},
    prev_hash: h(4),
    hash: h(5)
  },
  {
    seq: 6,
    ts: iso(30),
    agent_id: AGENT_ID,
    event_type: 'agent_suspended',
    payload: {agent_id: AGENT_ID, status: 'suspended'},
    prev_hash: h(5),
    hash: h(6)
  },
];

export const demoRunDone: DemoRunView = {
  id: 'run-1',
  scenario: 'small',
  status: 'done',
  started_at: iso(5),
  steps: [
    {
      id: 's1',
      kind: 'info',
      title: 'Agent asks to pay $12.00',
      label: 'send_payment',
      detail: 'To devtools@example.com, category developer_tools.',
      call_id: null,
      approval_id: null
    },
    {
      id: 's2',
      kind: 'allow',
      title: 'Allowed',
      label: 'OK_WITHIN_POLICY',
      detail: 'Within policy limits.',
      call_id: 'c1',
      approval_id: null
    },
    {
      id: 's3',
      kind: 'allow',
      title: 'Executed on PayPal sandbox',
      label: 'executed',
      detail: 'Payout accepted. Recorded in the audit log.',
      call_id: 'c1',
      approval_id: null
    },
  ],
};

export const demoRunWaiting: DemoRunView = {
  id: 'run-2',
  scenario: 'large',
  status: 'running',
  started_at: iso(3),
  steps: [
    {
      id: 's1',
      kind: 'info',
      title: 'Agent asks to pay $480.00',
      label: 'send_payment',
      detail: 'To events@example.com, category events.',
      call_id: null,
      approval_id: null
    },
    {
      id: 's2',
      kind: 'approve',
      title: 'Needs approval',
      label: 'AMOUNT_REQUIRES_APPROVAL',
      detail: 'This payment is above the approval threshold.',
      call_id: 'c2',
      approval_id: 'a1'
    },
    {
      id: 's3',
      kind: 'approve',
      title: 'Waiting for a human',
      label: 'pending_approval',
      detail: 'The agent is paused. Approve it in the inbox, or here.',
      call_id: 'c2',
      approval_id: 'a1'
    },
  ],
};
