# Progress

- [x] Phase 0 Scaffold — `./gradlew runSpike` (mock) PASS via SpikeMain; Gradle wrapper vendored
- [x] Phase 1 Engine — Evaluate/Normalize/Explain, 45 tests green (javac+JUnit run; see NOTES)
- [x] Phase 2 Gateway — Flyway V1, JdbcTemplate repositories, GatewayManager, PayPal mock/live
- [x] Phase 3 MCP — `/mcp` JSON-RPC, 8 tools, Bearer auth, 25s approval poll
- [x] Phase 4 Inbox — `/api/*`, Spring Security cookie session, kill switch
- [x] Phase 5 Audit — hash chain, `stableStringify`, advisory lock, `/api/audit/verify`
- [x] Phase 6 LLM UI — CompilePolicy (stub + OpenAI-compatible), React inbox builds (`npm run build` PASS)
- [ ] Phase 7 Freeze — `./gradlew check` on CI; locally blocked by sandbox socket (see NOTES)

## Acceptance R1–R9
- [x] R1 Small allowlisted payment executes (Evaluate allow + mock payout)
- [x] R2 Large payment → needs_approval, polls 25s
- [x] R3 Injection: fetch malicious page → UNTRUSTED_COUNTERPARTY deny
- [x] R4 Suspended agent denied AGENT_SUSPENDED (beats all)
- [x] R5 Over-cap / budget / velocity denies collected
- [x] R6 Audit chain verifies; tamper detected (AuditTest)
- [x] R7 Policy compile returns draft only, never auto-activates
- [x] R8 Mock mode shows MOCK PAYPAL banner
- [ ] R9 Hosted Render demo (owner: Neon + Render env, §16)
