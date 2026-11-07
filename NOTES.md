# Notes

## Choices (SPEC §0.1)
- Spring Boot **3.5.7** instead of 4.1.x: 4.1 line was not resolvable with a stable Spring AI / MCP pairing at build time; 3.5.7 is the supported LTS pairing for Java 21. Recorded here per §0.1.
- MCP: lightweight Streamable-HTTP JSON-RPC at `/mcp` implemented in `McpController` (SPEC §2 allows "lightweight Java MCP SDK"). No LLM in enforcement path; `Evaluate` is pure.
- LLM compiler uses direct OpenAI-compatible `HttpClient` calls (same endpoint/model config as Spring AI starter would use) with `LLM_PROVIDER=stub` fallback, so tests run offline.
- Frontend build is a Gradle `Exec` task (`frontendBuild`/`copyFrontend`) instead of node-gradle plugin — simpler, same output to `src/main/resources/static/`.
- Admin session: signed-value cookie `KIKOMO_ADMIN` set on `/api/auth/login`; agents use Bearer tokens (SHA-256 hashed in `agents.token_hash`).

## Needs the owner
- PayPal sandbox `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` (else `PAYPAL_MODE=mock` + MOCK banner).
- `DEMO_PAYEE_DEVTOOLS`, `DEMO_PAYEE_CONF` sandbox personal emails.
- Neon Postgres URL + Render deploy (`PUBLIC_URL`), `ADMIN_PASSWORD`, `SESSION_SECRET`, `DEMO_AGENT_TOKEN` (32+ chars).
- Owner logo at `src/frontend/public/logo.png` (placeholder README there now).
- Demo video (<3 min) — owner records.

## PayPal fallback (§9/§0.6)
- No live credentials in this environment: running in mock mode. `LivePayPalClient` implements Payouts/Orders/Invoicing/Refunds via sandbox REST; set `PAYPAL_MODE=live` + credentials to use it.
- Gradle wrapper jar in this sandbox could not be generated (`gradle wrapper` needs a loopback socket the sandbox blocks, and the escalated run was declined). `gradlew` falls back to system Gradle; on a normal machine run `gradle wrapper --gradle-version 8.14.3` once to regenerate the canonical wrapper jar. `./gradlew check` should be run on CI / owner machine.
- Phase 7 evidence (2026-11-07, updated): `./gradlew check` BUILD SUCCESSFUL (escalated, GRADLE_USER_HOME=/tmp/gh). Live E2E against Neon Postgres + live PayPal sandbox: login ok, small payout executed (HTTP 201, payout_batch_id), $100 payment -> AMOUNT_REQUIRES_APPROVAL -> approved in inbox -> executed, malicious-page fetch -> UNTRUSTED_COUNTERPARTY deny, suspend -> AGENT_SUSPENDED deny, /api/audit/verify valid:true. Bugs fixed during E2E: calls INSERT placeholder order, PgArray serialization in /api/calls, explicit @PathVariable/@RequestParam names + `-parameters`. Docker daemon still unavailable in sandbox; app ran via java with Neon DATABASE_URL_UNPOOLED.
- Neon (2026-11-07): `neon init` (org-spring-lake-67286120 / crimson-flower-14919522 / production) + `neon deploy` — "No changes — branch production already matches the policy. Utilized services: Postgres. Pulled 5 Neon variables into .env.local" (DATABASE_URL, DATABASE_URL_UNPOOLED, NEON_BRANCH, NEON_BRANCH_ID, NEON_FUNCTION_HOST_SUFFIX). App wiring: `KikomoApplication.applyNeonEnv()` translates DATABASE_URL_UNPOOLED (direct, preferred for Flyway/advisory locks) into Spring's jdbc URL + credentials. Driver stays PostgreSQL JDBC via JdbcTemplate — correct for a long-lived JVM service (Neon HTTP driver targets serverless JS, not Java).
