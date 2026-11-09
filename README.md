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
./gradlew bootRun
```

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

## Tests

```bash
./gradlew check      # unit tests + Postgres integration tests (Testcontainers: needs Docker running)
./gradlew runSpike   # PayPal sandbox spike: mock by default; PAYPAL_MODE=live hits the sandbox
```

## Hosting (Render + Neon)

`render.yaml` (Docker, Starter) + Neon Postgres. Set env from `.env.example`; set `PUBLIC_URL` after first deploy. See SPEC.md §16.
With `PAYPAL_MODE=live` the app refuses to start unless `ADMIN_PASSWORD` (non-default), `SESSION_SECRET` (32+ chars),
`DEMO_AGENT_TOKEN` (32+ chars) and the PayPal credentials are set.
