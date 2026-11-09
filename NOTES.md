# Notes

## ⚠ Before the next deploy / restart (2026-11-08 hardening)

1. **Live mode now fails fast on weak secrets.** With `PAYPAL_MODE=live` the app refuses to start unless
   `ADMIN_PASSWORD` is set and not `admin`, `SESSION_SECRET` is ≥ 32 chars, `DEMO_AGENT_TOKEN` is ≥ 32 chars and not
   the published default, and `PAYPAL_CLIENT_ID`/`PAYPAL_CLIENT_SECRET` are set (`StartupValidator`). In mock mode the
   same problems are only logged as warnings.
   **Current `.env.local` is live mode without `SESSION_SECRET` and `DEMO_AGENT_TOKEN`; add both before restarting.**
   Render: `SESSION_SECRET` is generated (`render.yaml`); set `DEMO_AGENT_TOKEN` and `ADMIN_PASSWORD` in the dashboard.
2. **Flyway V2 runs on first start** (`V2__executing_status_and_indexes.sql`, additive): adds the `executing` call
   status, a unique spend-ledger row per call, and indexes. It replaces the constraint named `calls_status_check`
   (the name Postgres generated for V1). Optional pre-check on Neon:
   `select conname from pg_constraint where conrelid = 'calls'::regclass and contype = 'c';`
3. **`V1__init.sql` must stay byte-identical** to what Neon already applied. It had been reformatted (whitespace
   only) in the working tree, which would have failed startup with a Flyway checksum mismatch; it was restored to
   the committed version. Put schema changes in new `V<n>__*.sql` files only.
4. **MCP clients must send `Accept: application/json, text/event-stream`** (MCP Streamable HTTP spec). Requests
   without it now get HTTP 400; `GET /mcp` returns 405 (stateless server). Standard MCP clients already do this.

## Choices (SPEC §0.1)
- Spring Boot **4.1.2** + **Spring AI 2.0.2** (SPEC §2). Verified compatible before migrating (scratch project +
  prototype of the stateless MCP server with header-based auth). Brings Jackson 3 (`tools.jackson.*`),
  Spring Security 7, Flyway 12, Testcontainers 2.
- MCP: Spring AI MCP server, **stateless Streamable HTTP at `/mcp`** (`McpServerConfig`, `KikomoMcpTools`). Tool
  input schemas are generated from the typed `@McpTool` methods. Bearer auth is a servlet filter
  (`AgentTokenFilter`, SPEC §7) that answers 401 before the MCP server; the agent id reaches tools through
  `McpTransportContext`. stdio transport is not enabled (one HTTP endpoint for judges).
- LLM: Spring AI OpenAI starter (`spring-ai-starter-model-openai`) pointed at `LLM_BASE_URL`; structured output
  `.entity(Policy.class)` (SPEC §12). The starter refuses to start without an API key, so `spring.ai.openai.api-key`
  defaults to the placeholder `unset`; the model is only called when `LLM_PROVIDER != stub` **and** `LLM_API_KEY` is
  set. Unused models (embedding, image, audio, moderation) are disabled. `/api/policies/compile` now also returns
  `compiler: "llm" | "stub"`.
- Frontend build: Gradle `Exec` tasks locally; the Docker image builds the SPA in a `node:22-alpine` stage because
  `src/main/resources/static/assets` is git-ignored (Render builds from git). node-gradle plugin not used: its
  downloaded Node binaries need glibc and the builder image is Alpine.
- Gradle wrapper regenerated with `gradle wrapper --gradle-version 8.14.3`: the vendored jar had no `Main-Class`,
  so `./gradlew` only worked where a system Gradle existed (it would have failed inside the Docker builder).
- Admin session: HMAC-SHA256-signed, 12h `KIKOMO_ADMIN` cookie (HttpOnly, SameSite=Strict, Secure on https) +
  double-submit CSRF (`XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header). Stateless: logout clears the cookie but a stolen
  cookie stays valid until it expires.
- `src/frontend/package-lock.json` re-synced with npm 10 (the version in `node:22-alpine`); `npm ci`
  failed with the previous lock file.

## Behaviour changes in the 2026-11-08 review (all covered by tests)
- **Audit log works** (A1): the INSERT had 7 placeholders for 6 columns, so every append failed silently and
  `/api/audit/verify` was "valid" only because the table was empty. `call_evaluated` is now written in the same
  transaction as the call (fail closed); post-execution events are best-effort and logged on failure.
- **Amount overflow** (A2): amounts are limited to 13 whole digits and parsed with exact arithmetic;
  `100000000000000000` used to wrap to a negative amount, be allowed, and *reduce* the day's spend.
- **PayPal errors are failures** (A3): non-2xx responses used to be recorded as `executed` and counted as spend.
- Amounts sent to PayPal are locale independent (A4); a database error after a successful PayPal call no longer
  marks the call `failed` (A5); `order_id`/`capture_id` must match `^[A-Za-z0-9-]{1,64}$` (A6); unknown tools are
  JSON-RPC errors (A7); expired approvals cannot be approved and are swept to `expired` every 30 s (A8); payee
  normalisation uses `Locale.ROOT` (A9); policy activation is transactional (A10); `HARD_MAX_TX_MINOR` is enforced
  (A11).
- **Approvals execute on approve**: the approver's request runs the call (exactly once, compare-and-set on
  `calls.status`). A payment approved after the agent's 25 s wait still executes; the agent sees the result with
  `check_approval` (which now also returns `call_id`, `call_status` and `result`). Deny marks the call `denied`
  immediately.
- **Concurrency**: evaluation runs under a per-agent advisory lock and allowed spending calls are recorded as
  `executing` (reserved budget) before execution, so parallel calls cannot overspend the daily budget or velocity.
  Calls approved later are not re-checked against the budget (a human approved them).
- **kikomo_fetch_url goes through the gateway** (SPEC §8): it is recorded in `calls`, audited, and denied for a
  suspended agent or an agent without a policy. Disallowed hosts are `denied` with `SSRF_BLOCKED`. Only http(s), no
  redirects, 10 s timeout, 1 MB body limit; `localhost`/`127.0.0.1` are no longer force-added (they are in the
  default `FETCH_ALLOWED_HOSTS`; the `PUBLIC_URL` host is always added). Envelope: `fetched_url`, `emails_found`
  (no `paypal` key).
- Money tools without an `amount` now fail (`amount is required`) instead of calling PayPal with 0.
- PayPal: OAuth token cached until expiry, `PayPal-Request-Id` = call id on POSTs (idempotent retries),
  `sender_batch_id = kikomo-<call id>`, 10 s connect / 30 s request timeouts, `list_transactions` = last 30 days.
- MCP tool arguments are the declared parameters only; the legacy `counterparty` alias for `payee_email` is still
  accepted by the gateway but not advertised.
- Admin API error codes: 401 when not logged in (was 403), 404 unknown call/approval/agent/policy, 409 approval
  already decided or expired, 400 invalid policy (body lists `errors`) or malformed JSON. Errors are RFC 9457
  problem details; unexpected errors return a `reference` id that is logged with the stack trace.
- `/api/approvals` `expires_at` is ISO-8601 (`2026-11-08T07:15:00Z`); was `2026-11-08 07:15:00.0`.

## Frontend follow-ups (not done yet; backend changes above are compatible with the current SPA)
- [ ] `api()` should check `r.ok`: on **401** return to the login screen (session expired / server restart),
      on 4xx/5xx show `detail` from the problem-details body instead of rendering it as data (e.g. `calls.map` on an
      error object).
- [ ] Approve/Deny: show **409** "already decided / expired" and **404** messages to the admin.
- [ ] Persist login across page reloads: probe an authenticated endpoint on load instead of `loggedIn=false`.
- [ ] Show approval context (tool, amount, payee, reasons, `expires_at` countdown) in the inbox; today only the id.
- [ ] Policy compiler: show `compiler: "stub"` (offline draft / LLM fallback) and validation `errors` on activate;
      add an "Activate" button (`POST /api/policies/{agentId}/activate`).
- [ ] Logout button (`POST /api/auth/logout`).
- [ ] Replace `any` types with interfaces for the API DTOs (`CallView`, `ApprovalView`, `AgentView`, ...).

## Remaining SPEC gaps (deliberately not done)
- §2 node-gradle plugin (see Choices), §2 MCP stdio entry, §4 `DEMO_MODE`-gated `/api/demo/*` endpoints and §14
  `DemoController` + Java scripted demo agent (the E2E script used for verification lives outside the repo).
- §10 tamper demo endpoints.

## Needs the owner
- PayPal sandbox `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` (else `PAYPAL_MODE=mock` + MOCK banner).
- `DEMO_PAYEE_DEVTOOLS`, `DEMO_PAYEE_CONF` sandbox personal emails.
- Render deploy (`PUBLIC_URL`), `ADMIN_PASSWORD`, `DEMO_AGENT_TOKEN` (32+ chars); `SESSION_SECRET` is generated by
  `render.yaml`.
- Demo video (<3 min) — owner records.

## History
- Phase 7 evidence (2026-11-07): live E2E against Neon Postgres + live PayPal sandbox: login ok, small payout
  executed (HTTP 201, payout_batch_id), $100 payment -> AMOUNT_REQUIRES_APPROVAL -> approved in inbox -> executed,
  malicious-page fetch -> UNTRUSTED_COUNTERPARTY deny, suspend -> AGENT_SUSPENDED deny. (The `/api/audit/verify
  valid:true` in that run was on an empty audit table, see A1.)
- Neon (2026-11-07): `neon init` + `neon deploy`; `KikomoApplication.applyNeonEnv()` translates
  `DATABASE_URL_UNPOOLED` (direct connection, preferred for Flyway/advisory locks) into Spring's JDBC URL.
- 2026-11-08 verification: `./gradlew test` (unit + Testcontainers Postgres integration) green; Docker image built
  and run with `docker compose`; scripted MCP + admin API E2E against the container (mock PayPal) passed. See
  PROGRESS.md.
