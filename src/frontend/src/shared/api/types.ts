/** DTOs exactly as the Spring backend emits them (snake_case). Keep in sync with co.tz.kikomo.web.dto. */

export type Decision = 'allow' | 'deny' | 'needs_approval';
export type CallStatus = 'executing' | 'executed' | 'denied' | 'pending_approval' | 'failed' | 'expired';
export type ApprovalStatus = 'pending' | 'approved' | 'denied' | 'expired';
export type AgentStatus = 'active' | 'suspended';

export type ReasonCode =
  | 'OK_READ_ONLY'
  | 'OK_WITHIN_POLICY'
  | 'AGENT_SUSPENDED'
  | 'NO_ACTIVE_POLICY'
  | 'INVALID_AMOUNT_FORMAT'
  | 'INVALID_CURRENCY'
  | 'AMOUNT_OVER_CAP'
  | 'DAILY_BUDGET_EXCEEDED'
  | 'VELOCITY_EXCEEDED'
  | 'UNTRUSTED_COUNTERPARTY'
  | 'CATEGORY_NOT_ALLOWED'
  | 'INVALID_COUNTERPARTY_FORMAT'
  | 'SSRF_BLOCKED'
  | 'NEW_PAYEE_REQUIRES_APPROVAL'
  | 'AMOUNT_REQUIRES_APPROVAL'
  | 'REFUND_REQUIRES_APPROVAL';

export interface Health {
  status: string;
  paypal_mode: 'mock' | 'live';
  mock: boolean;
}

export interface OkResponse {
  ok: boolean;
}

export interface CallView {
  id: string;
  agent_id: string;
  tool: string;
  decision: Decision;
  status: CallStatus;
  counterparty: string | null;
  amount_minor: number | null;
  currency: string | null;
  category: string | null;
  reason_codes: string[];
  explanation: string;
  args: Record<string, unknown> | null;
  result: Record<string, unknown> | null;
  approval_id: string | null;
  created_at: string | null;
}

export interface ApprovalView {
  id: string;
  call_id: string;
  agent_id: string;
  status: ApprovalStatus;
  decided_by: string | null;
  decided_at: string | null;
  expires_at: string;
  created_at: string | null;
  call: CallView | null;
}

export interface AgentView {
  id: string;
  name: string;
  status: AgentStatus;
  spent_today_minor: number;
  daily_budget_minor: number | null;
  policy_version: number | null;
  created_at: string | null;
}

export interface AgentStatusResponse {
  ok: boolean;
  status: AgentStatus;
}

export interface StatsView {
  pending_approvals: number;
  allowed_today: number;
  denied_today: number;
  needs_approval_today: number;
  audit_valid: boolean;
}

export interface AuditVerifyResponse {
  ok: boolean;
  valid: boolean;
}

export interface AuditEntryView {
  seq: number;
  ts: string | null;
  agent_id: string | null;
  event_type: string;
  payload: Record<string, unknown>;
  prev_hash: string;
  hash: string;
}

/** co.tz.kikomo.shared.Policy (camelCase: it is the stored policy document itself). */
export interface Policy {
  version: number;
  currency: string | null;
  perTxCapMinor: number;
  dailyBudgetMinor: number;
  approvalThresholdMinor: number;
  payeeAllowlist: string[];
  newPayeeRequiresApproval: boolean;
  allowedCategories: string[];
  velocity: { maxCalls: number; windowSeconds: number };
  refundsRequireApproval: boolean;
  approvalTtlSeconds: number;
}

export interface CompileRequest {
  text: string;
  allowlist: string[];
}

export interface CompileResponse {
  draft: Policy;
  source_text: string;
  compiler: 'llm' | 'stub';
}

export type DemoScenario = 'small' | 'large' | 'injection' | 'all';
export type DemoStepKind = 'info' | 'allow' | 'approve' | 'deny';
export type DemoRunStatus = 'running' | 'done' | 'failed';

export interface DemoStep {
  id: string;
  kind: DemoStepKind;
  title: string;
  label: string;
  detail: string;
  call_id: string | null;
  approval_id: string | null;
}

export interface DemoRunView {
  id: string;
  scenario: DemoScenario;
  status: DemoRunStatus;
  started_at: string;
  steps: DemoStep[];
}

export interface DemoConnectInfo {
  mcp_url: string;
  malicious_page_url: string;
}

/** RFC 9457 problem body produced by ApiExceptionHandler. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  errors?: string[];
  reference?: string;
}
