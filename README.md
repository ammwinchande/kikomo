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

Endpoint `POST /mcp` (Streamable HTTP JSON-RPC), header `Authorization: Bearer <DEMO_AGENT_TOKEN>`.
Tools: `send_payment`, `create_order`, `get_order`, `create_invoice`, `create_refund`, `list_transactions`, `check_approval`, `kikomo_fetch_url`.

Demo scenarios: small payment (allow), large payment (approval in inbox), injection — fetch `/demo/malicious-page` via `kikomo_fetch_url`, then try paying the attacker payee (denied `UNTRUSTED_COUNTERPARTY`).

## Tests

```bash
./gradlew check
./gradlew runSpike   # PayPal sandbox spike (mock-safe)
```

## Hosting (Render + Neon)

`render.yaml` (Docker, Starter) + Neon Postgres. Set env from `.env.example`; set `PUBLIC_URL` after first deploy. See SPEC.md §16.
