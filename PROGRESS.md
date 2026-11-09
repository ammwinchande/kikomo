# Progress

- [x] Phase 0 Scaffold — `./gradlew runSpike` (mock) PASS via SpikeMain; Gradle wrapper vendored
- [x] Phase 1 Engine — Evaluate/Normalize/Explain, 45 tests green (javac+JUnit run; see NOTES)
- [x] Phase 2 Gateway — Flyway V1, JdbcTemplate repositories, GatewayManager, PayPal mock/live
- [x] Phase 3 MCP — Spring AI stateless Streamable HTTP `/mcp`, 8 typed tools with schemas, Bearer auth filter, 25s approval wait
- [x] Phase 4 Inbox — `/api/*`, Spring Security cookie session, kill switch
- [x] Phase 5 Audit — hash chain, `stableStringify`, advisory lock, `/api/audit/verify`
- [x] Phase 6 LLM UI — CompilePolicy (Spring AI structured output + stub fallback), React inbox builds (`npm run build` PASS)
- [x] Phase 7 Freeze — `./gradlew check` BUILD SUCCESSFUL (2026-11-07, escalated run) + live HTTP E2E on Neon (see NOTES)

## Acceptance R1–R9
- [x] R1 Small allowlisted payment executes (Evaluate allow + mock payout)
- [x] R2 Large payment → needs_approval, polls 25s
- [x] R3 Injection: fetch malicious page → UNTRUSTED_COUNTERPARTY deny
- [x] R4 Suspended agent denied AGENT_SUSPENDED (beats all)
- [x] R5 Over-cap / budget / velocity denies collected
- [x] R6 Audit chain verifies; tamper detected (AuditTest)
- [x] R7 Policy compile returns draft only, never auto-activates
- [x] R8 Mock mode shows MOCK PAYPAL banner
- [ ] R9 Hosted Render demo (Neon DB live + verified; Render deploy still owner step, §16)

## 2026-11-08 review: code standards, bug fixes, hardening, SPEC gaps

Re-tested with `./gradlew test` (JUnit 5 unit tests + Testcontainers Postgres integration tests over real HTTP) and a
Docker E2E (`docker compose up --build`, scripted MCP + admin API calls, mock PayPal). Details in NOTES.md.

### Bugs (A)
- [x] A1 audit INSERT (6 columns / 7 placeholders) — `AuditIntTest`, `GatewayIntTest.auditLogRecordsEveryCallAndVerifies`
- [x] A2 amount overflow → negative spend — `EvaluateTest.overflowingAmount…`, `GatewayIntTest.overflowingAmountIsRejectedNotAllowed`
- [x] A3 PayPal non-2xx treated as success — `LivePayPalClientTest.non2xxIsAnErrorNotASuccess`
- [x] A4 locale-dependent amounts — `LivePayPalClientTest`, `NormalizeTest.moneyFormatIsLocaleIndependent`
- [x] A5 DB failure after PayPal success marked `failed` — `CallExecutor` (only the side effect is in the failure path)
- [x] A6 path injection via order/capture id — `GatewayIntTest.refundWithPathTraversalCaptureIdIsRejected`
- [x] A7 unknown tool executed — `GatewayIntTest.unknownToolIsRejected`
- [x] A8 expired approvals approvable / never expired — `ApiIntTest.expiredApprovalCannotBeApproved`, `ApprovalLifecycleIntTest.overdueApprovalsAreSweptToExpired`
- [x] A9 locale-dependent payee lower-casing — `NormalizeTest.lowercasingIgnoresDefaultLocale`
- [x] A10 non-transactional policy activation — `ApiIntTest.invalidPolicyIsBadRequest` (previous policy stays active)
- [x] A11 `HARD_MAX_TX_MINOR` not enforced — `PolicyValidator`
- [x] Audit append self-invocation bypassed the transaction (found during this work) — `AuditIntTest.concurrentAppendsKeepTheChainValid`

### Code standards (B/C/D)
- [x] Sonar findings from the review (S3776, S1192, S108, S2095, S2142, S8786, S107, S1118, S1659, S1481/S1854, S3358, S135, S9391, S6863, S3330/S4502 justified) and App.tsx (S6594, S7744, floating promises)
- [x] Enums for decision / call / approval / agent status; typed records + RowMappers; repositories split per table
- [x] One injected Jackson 3 `JsonMapper`; one shared `HttpClient` bean with timeouts; one `Hashing` utility
- [x] Validated `@ConfigurationProperties` (`AppConfig`) + `StartupValidator` (SPEC §4)
- [x] RFC 9457 problem details: 400/401/404/409, reference id for 500s; no silent catches; decision logging

### Production hardening (E)
- [x] Timeouts, shared HttpClient, OAuth token cache, `PayPal-Request-Id` idempotency
- [x] SSRF: no forced localhost, http(s) only, no redirects, 1 MB limit, linear e-mail extraction
- [x] Per-agent advisory lock + reserved spend — `ApprovalLifecycleIntTest.concurrentPaymentsCannotOverspendTheDailyBudget`
- [x] `executing` status before PayPal (Flyway V2) + unique ledger row per call
- [x] `list_transactions` uses a valid 30-day window

### SPEC gaps (F)
- [x] Spring Boot 4.1.2 + Spring AI 2.0.2 (MCP server + structured-output policy compiler)
- [x] Real MCP input schemas — `McpIntTest.toolsListExposesAllEightToolsWithSchemas`, `sendPaymentSchemaDescribesItsArguments`
- [x] `kikomo_fetch_url` through the gateway (calls + audit) — `GatewayIntTest.injectedPayeeFromFetchedPageIsDenied`, `fetchOfDisallowedHostIsBlocked`
- [x] Approval after the 25s wait still executes — `ApprovalLifecycleIntTest.approvalAfterAgentStoppedWaitingStillExecutesExactlyOnce`
- [x] 40+ Evaluate cases (42) — `EvaluateTest`
- [x] Testcontainers DB tests — `support/IntegrationTestBase` + `integration/*`
- [x] `runSpike` hits the sandbox in live mode (OAuth + create/get order)
- [x] Docker image builds the SPA; Gradle wrapper fixed; compose waits for a healthy DB
- [x] Frontend follow-ups for the new error codes — done in the 2026-11-09 admin portal redesign (below)

## 2026-11-09 admin portal redesign (tmp/Kikomo Admin Portal.html, 6 boards)
- [x] Backend: `GET /api/auth/session`, richer `CallView` (`currency`, `category`, `args`, `result`, `approval_id`,
      `created_at`, `explanation`), `ApprovalView` embeds its `call`, `AgentView` has `spent_today_minor` /
      `daily_budget_minor` / `policy_version`, `GET /api/stats`, `GET /api/audit/entries`, `DemoController` +
      `DemoRunService` (SPEC §14 scripted agent over loopback MCP, 404 when `DEMO_MODE=false`), `SpaController`
      forwards the SPA routes — `ApiIntTest` (17), `DemoIntTest` (8), `DemoModeOffIntTest` (1)
- [x] `application.yml` `HARD_MAX_TX_MINOR` default restored to 100000 (SPEC §4); 10000 made the $500 demo policy
      un-activatable (`ApiIntTest.activatingValidPolicyWorks` was failing)
- [x] `ApiExceptionHandler` now covers every controller package (demo endpoints returned 500 instead of 404/400)
- [x] Frontend rewritten: feature folders, TanStack Query (server state), Zustand (UI state), react-router,
      Tailwind design tokens from the boards; 6 pages (sign in, approvals, activity + why panel, audit chain,
      policy compiler, demo runner); Vitest + Testing Library + MSW, 53 tests, run by `./gradlew check`
