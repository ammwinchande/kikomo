# Kikomo

MCP gateway between an AI agent and PayPal. Every payment tool call passes a deterministic policy engine before PayPal is touched. Risky calls pause for human approval, bad calls are denied with a reason, a kill switch suspends the agent, and everything lands in a hash-chained audit log.

License: MIT (see LICENSE).

## Run locally (one command)

```bash
docker-compose up --build
# open http://localhost:8080 — login with ADMIN_PASSWORD (default `admin` in compose)
```

Without Docker (needs Postgres on localhost:5432):

```bash
cp .env.example .env   # fill in values
# Export the values in .env into your shell; bootRun does not load that file automatically.
./gradlew bootRun
```

Java 21 and Node.js 22.12+ (including npm) are required for Gradle builds. `./gradlew bootJar` installs the locked
frontend dependencies, builds the SPA, and syncs its HTML, JS, CSS and public files into the JAR. `bootRun` uses
the same resource pipeline. Docker builds the SPA in its Node stage and reuses that output during Gradle packaging.

Compose mounts Postgres 18 data at `/var/lib/postgresql`. If an existing volume contains a database created by
an older PostgreSQL major version, migrate that database before reusing the volume with Postgres 18.

## MCP

Endpoint `POST /mcp` (MCP Streamable HTTP, stateless JSON-RPC). Headers:
`Authorization: Bearer <DEMO_AGENT_TOKEN>` and `Accept: application/json, text/event-stream`.
`tools/list` returns an input schema for every tool.

```bash
curl -s http://localhost:8080/mcp -H "Authorization: Bearer $DEMO_AGENT_TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"send_payment","arguments":{"payee_email":"devtools@example.com","amount":"10.00","category":"developer_tools"}}}'
```

Calls that need approval wait up to 25 s for the inbox; afterwards the agent gets `pending_approval` + `approval_id`
and can poll `check_approval`. Approving in the inbox executes the payment even if the agent stopped waiting.
Tools: `send_payment`, `create_order`, `get_order`, `create_invoice`, `create_refund`, `list_transactions`, `check_approval`, `kikomo_fetch_url`.

Demo scenarios: small payment (allow), large payment (approval in inbox), injection — fetch `/demo/malicious-page` via `kikomo_fetch_url`, then try paying the attacker payee (denied `UNTRUSTED_COUNTERPARTY`).

## Admin portal

`http://localhost:8080` serves the React admin portal (sign in with `ADMIN_PASSWORD`). Pages: **Approvals** (inbox with
countdowns, reasons, kill switch, policy summary), **Activity** (every call and a Why panel with the reason codes, policy
values and the agent's arguments), **Audit** (hash-chained log, Verify chain, client-side tamper simulation), **Policy**
(sentence → AI/stub draft → diff → confirm) and **Demo** (scripted agent that runs the small / large / injection scenarios
over real MCP; off when `DEMO_MODE=false`). Source in `src/frontend` (feature folders, TanStack Query for server state,
Zustand for UI state). Dev loop: `npm --prefix src/frontend run dev` (proxies `/api`, `/mcp`, `/demo` to :8080).

## Tests

```bash
./gradlew check      # unit tests + Postgres integration tests (Testcontainers: needs Docker running) + frontend typecheck/Vitest
npm --prefix src/frontend test   # frontend tests only (Vitest + Testing Library + MSW)
./gradlew runSpike   # PayPal sandbox spike: mock by default; PAYPAL_MODE=live hits the sandbox
```

## Hosting (Render + Neon)

Create a Render Blueprint from `render.yaml` (Docker, Starter) and supply the prompted settings:

| Settings | Value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Neon **direct**, non-pooler JDBC URL: `jdbc:postgresql://<host>/<database>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Credentials for that Neon database |
| `PUBLIC_URL` | The service's real `https://…onrender.com` URL; verify it after the first deploy |
| `ADMIN_PASSWORD` | A strong, non-default password for the admin portal |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | PayPal **sandbox** application credentials |
| `DEMO_PAYEE_DEVTOOLS`, `DEMO_PAYEE_CONF` | Sandbox personal-account emails receiving the demo payments |
| `LLM_API_KEY` | Key for the configured Meta provider; change provider/base URL/model together for another provider |

Render generates `SESSION_SECRET` and `DEMO_AGENT_TOKEN`; retrieve the token from the service's Environment page
when configuring an external MCP client. Keep both values stable across restarts. The blueprint enables the demo,
live **sandbox** PayPal, and the Meta policy compiler using the defaults in SPEC.md §4. No Render database is created.

For an existing Render service, add new prompted variables manually: Blueprint `sync: false` entries only prompt
during initial creation ([Render docs](https://render.com/docs/blueprint-spec#prompting-for-secret-values)).
With `PAYPAL_MODE=live`, missing or default auth secrets and missing PayPal credentials prevent startup.
For offline verification, use `PAYPAL_MODE=mock` and `LLM_PROVIDER=stub`; the UI shows a persistent MOCK PAYPAL banner.

After deployment, check `/api/health`, sign in, reload `/approvals`, and run the small-payment, approval and injection
demo scenarios. Verify the nonempty audit chain. R9 in `PROGRESS.md` remains pending until the hosted checks pass.
