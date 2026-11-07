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
