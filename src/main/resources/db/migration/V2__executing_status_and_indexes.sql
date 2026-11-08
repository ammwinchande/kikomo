-- V2: production hardening. Additive only; safe to apply to an existing V1 database.

-- 'executing' = allowed/approved and in flight to PayPal. Lets the gateway reserve budget before execution
-- and claim approved calls exactly once (compare-and-set on status).
alter table calls drop constraint if exists calls_status_check;
alter table calls add constraint calls_status_check
    check (status in ('executing', 'executed', 'denied', 'pending_approval', 'failed', 'expired'));

-- A call is counted in the spend ledger at most once.
create unique index if not exists spend_ledger_call_id_uq on spend_ledger (call_id);

-- Hot paths: per-agent velocity/budget checks, approval inbox and expiry sweep.
create index if not exists calls_agent_created_idx on calls (agent_id, created_at desc);
create index if not exists calls_created_idx on calls (created_at desc);
create index if not exists spend_ledger_agent_created_idx on spend_ledger (agent_id, created_at);
create index if not exists approvals_status_expires_idx on approvals (status, expires_at);
create index if not exists approvals_call_id_idx on approvals (call_id);
