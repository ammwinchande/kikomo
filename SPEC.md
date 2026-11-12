# Kikomo: build spec (single source of truth)

Kikomo is an MCP gateway that sits between an AI agent and PayPal. Every payment tool call passes a deterministic policy engine before PayPal is touched. Risky calls pause for human approval, bad calls are denied with a reason, a kill switch suspends the agent, and everything lands in a hash-chained audit log.

This file is written to be executed by a coding agent without further input.

## 0. Agent contract (read first)

1. **Every decision in this file is final.** Do not ask the user which library, host, port, color or approach to use. If something here is ambiguous, pick the simplest option that satisfies the acceptance test, and write the choice in `NOTES.md`.

2. **Work in the phases of section 17, in order.** After each phase run `./gradlew check` and the phase's "done when" command. Fix failures before moving on. Commit after each phase.

3. **Keep `PROGRESS.md`** as a checklist of phases and acceptance tests (R1 to R9). Tick items only when their command passes.

4. **Never commit secrets.** `.env` is git-ignored. `.env.example` lists every variable with a safe placeholder.

5. **Never put a language model in the enforcement path.** `Evaluate.java` is a pure function. Models only compile policy text (section 12) and write optional explanations.

6. **If a PayPal capability is unavailable in the sandbox**, follow the fallback in section 9 and record it in `NOTES.md`. Do not stop.

7. **Do not ask the user questions.** If blocked on a missing secret, run in mock mode (`PAYPAL_MODE=mock`), make the UI show a persistent "MOCK PAYPAL" banner, and list what is missing in `NOTES.md` under "Needs the owner".

8. The user is entering the PayPal AI Hackathon. Requirements that matter for judging: public repo with a visible open-source license, hosted demo or full run instructions, demo video under 3 minutes (user records it), PayPal sandbox integration that is central to the project, AI used meaningfully.

## 1. Changes from the earlier plan (already decided)

| Earlier plan | Now | Why | 
| ----- | ----- | ----- | 
| Next.js inbox | Spring Boot 4.1 REST API + Vite React SPA in one fat JAR | Unified deployment via Docker on Render; demonstrates enterprise Java capability. | 
| Five tools | Eight tools: `send_payment`, `create_order`, `get_order`, `create_invoice`, `create_refund`, `list_transactions`, `check_approval`, `kikomo_fetch_url` | `create_order` alone cannot complete without a buyer approving, so `send_payment` (Payouts API) is the primary "agent pays" tool and runs fully server-side. `kikomo_fetch_url` powers the injection demo. `check_approval` lets an agent poll | 
| Webhooks required | Webhooks optional, polling is the baseline | Fewer moving parts; webhooks are phase 7 if time remains | 

## 2. Decisions

| Topic | Decision | 
| ----- | ----- | 
| Language and runtime | Java 21, JVM | 
| Build tool | Gradle (using Gradle Wrapper), `sourceCompatibility = '21'` | 
| Server | Spring Boot 4.1.x (Embedded Tomcat) | 
| MCP | Official Spring AI MCP support (or lightweight Java MCP SDK), stdio entry and Streamable HTTP at `/mcp` | 
| Validation | Jakarta Bean Validation (Hibernate Validator) everywhere | 
| Database | PostgreSQL via `spring-boot-starter-jdbc` (plain `JdbcTemplate`, **strictly NO JPA/Hibernate ORM**), migrations via Flyway | 
| Frontend | React 18, Vite, Tailwind CSS, built via `node-gradle` plugin and served from `src/main/resources/static/` | 
| Tests | JUnit 5, MockMvc for integration, Testcontainers for database tests | 
| Money | `long` minor units (cents), USD only, parse decimal strings with strict regex | 
| PayPal | Direct REST calls using Java `HttpClient` (`java.net.http.HttpClient`), sandbox base URL `https://api-m.sandbox.paypal.com` | 
| LLM | Provider-agnostic via Spring AI (`spring-ai-openai-spring-boot-starter`) pointed at any OpenAI-compatible endpoint. Default is **Meta Model API with Muse Spark** (`https://api.meta.ai/v1`); `LLM_PROVIDER=stub` for tests. | 
| License | MIT | 
| Hosting | **Render** web service (Starter plan) via Docker container, **Neon** free Postgres for the database | 
| Admin auth for the inbox | One shared password, Spring Security cookie session; agents authenticate with bearer tokens | 

### Hosting rationale and exact setup

* **One Render web service** runs everything via Docker container: it serves the built SPA, the inbox API, the demo pages, and the MCP endpoint at `/mcp`. One URL for judges.

* **Use the Starter plan, not free.** Free web services spin down when idle and the cold start can make a judge think the demo is broken.

* **Use Neon for Postgres.** Render's free Postgres has an expiry window.

* Public URL pattern: `https://kikomo-<suffix>.onrender.com`. Set `PUBLIC_URL` to the real value after the first deploy.

* Deploy a **walking skeleton on day 3** (section 17, phase 3) so hosting problems surface early.

## 3. Repository layout

```
kikomo/
  SPEC.md  PROGRESS.md  NOTES.md  README.md  LICENSE  .env.example
  build.gradle  settings.gradle  gradlew  gradlew.bat
  Dockerfile  docker-compose.yml  render.yaml
  src/
    frontend/              # React SPA (Vite, Tailwind)
      package.json  vite.config.ts  tailwind.config.ts
      index.html  src/main.tsx  src/App.tsx
      src/components/  src/pages/  src/styles.css  src/logo.tsx
      public/logo.png      # the owner's logo, see section 15
    main/
      java/co/tz/kikomo/
        KikomoApplication.java
        config/            # Security, AppConfig, Env validation
        mcp/               # Spring AI MCP tools and server config
        engine/
          Evaluate.java    # pure policy engine
          Normalize.java   # payee normalization
          Explain.java     # deterministic reason-code explanations
        gateway/           # orchestrates: auth, evaluate, approve, execute, log
        paypal/            # types, live HttpClient, mock
        audit/             # hash chain append and verify
        db/                # JdbcTemplate repositories
        llm/               # CompilePolicy, ExplainPolicy
        demo/              # Scenarios, Mock malicious page controller
        web/               # Inbox REST API Controllers
        shared/            # Policy, Tools, Money, Reason record/classes
      resources/
        application.yml
        db/migration/V1__init.sql  # Flyway SQL migrations
        static/            # Generated by gradle frontend build task
    test/
      java/co/tz/kikomo/
        engine/EvaluateTest.java, NormalizeTest.java
        audit/AuditTest.java
        integration/GatewayIntTest.java, McpIntTest.java, ApiIntTest.java
        e2e/E2eRunner.java

```

## 4. Environment variables (Validated on startup via `@ConfigurationProperties`)

| Variable | Required | Example or default | Purpose | 
| ----- | ----- | ----- | ----- | 
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://localhost:5432/kikomo` | Postgres (Neon string with `sslmode=require` in production) | 
| `PUBLIC_URL` | yes in prod | `http://localhost:8080` | used in demo page links and README output | 
| `PORT` | no | `8080` | HTTP port | 
| `PAYPAL_MODE` | no | `live` or `mock` (default `live`) | `mock` is for tests and offline work only | 
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | if live | from developer dashboard, sandbox app | OAuth client credentials | 
| `PAYPAL_BASE_URL` | no | `https://api-m.sandbox.paypal.com` | never point at production | 
| `ADMIN_PASSWORD` | yes |  | inbox login | 
| `SESSION_SECRET` | yes | 32+ random chars | cookie signing / Spring session | 
| `DEMO_AGENT_TOKEN` | yes | random 32+ chars | bearer token of the seeded demo agent | 
| `DEMO_PAYEE_DEVTOOLS` | for demo | sandbox personal account email | allowlisted payee 1 | 
| `DEMO_PAYEE_CONF` | for demo | sandbox personal account email | allowlisted payee 2 | 
| `DEMO_PAYEE_ATTACKER` | for demo | `pay-me@evil-payments.example` | not allowlisted | 
| `LLM_PROVIDER` | no | `meta`, `openrouter` or `stub` (default `stub`) | policy compiler and explanations | 
| `LLM_API_KEY` | if not stub |  | key for the chosen provider | 
| `LLM_BASE_URL` | no | `https://api.meta.ai/v1` | OpenAI-compatible base URL | 
| `LLM_MODEL` | no | `muse-spark-1.2-contributor` | model id | 
| `HARD_MAX_TX_MINOR` | no | `100000` | absolute ceiling any compiled policy may set (USD 1,000) | 
| `FETCH_ALLOWED_HOSTS` | no | `localhost,127.0.0.1,<host of PUBLIC_URL>` | SSRF guard | 
| `DEMO_MODE` | no | `true` | enables `/api/demo/*` endpoints | 

## 5. Data model (`src/main/resources/db/migration/V1__init.sql`)

```
create extension if not exists pgcrypto;

create table agents (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  token_hash text not null unique,
  status text not null default 'active' check (status in ('active','suspended')),
  created_at timestamptz not null default now()
);

create table policies (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references agents(id),
  version int not null,
  doc jsonb not null,
  source_text text,
  status text not null check (status in ('draft','active','archived')),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz,
  unique (agent_id, version)
);
create unique index one_active_policy on policies(agent_id) where status = 'active';

create table calls (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references agents(id),
  tool text not null,
  args jsonb not null,
  amount_minor bigint,
  currency text,
  counterparty text,
  category text,
  decision text not null check (decision in ('allow','deny','needs_approval')),
  reason_codes text[] not null default '{}',
  status text not null check (status in ('executed','denied','pending_approval','failed','expired')),
  approval_id uuid,
  result jsonb,
  created_at timestamptz not null default now()
);

create table approvals (
  id uuid primary key default gen_random_uuid(),
  call_id uuid not null references calls(id),
  agent_id uuid not null references agents(id),
  status text not null default 'pending' check (status in ('pending','approved','denied','expired')),
  decided_by text,
  decided_at timestamptz,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table spend_ledger (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references agents(id),
  call_id uuid not null references calls(id),
  amount_minor bigint not null,
  currency text not null,
  counterparty text not null,
  created_at timestamptz not null default now()
);

create table untrusted_strings (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references agents(id),
  value_norm text not null,
  source_url text not null,
  created_at timestamptz not null default now(),
  unique (agent_id, value_norm)
);

create table audit_log (
  seq bigint primary key,
  ts timestamptz not null default now(),
  agent_id uuid,
  event_type text not null,
  payload jsonb not null,
  prev_hash char(64) not null,
  hash char(64) not null
);

create function audit_immutable() returns trigger as $$
begin raise exception 'audit_log is append-only'; end $$ language plpgsql;
create trigger audit_immutable before update or delete on audit_log
  for each row execute function audit_immutable();

```

## 6. Policy document and engine

### 6.1 Policy schema (`Policy.java` Java Record)

```
public record Policy(
    int version,
    String currency, // Default "USD"
    @Min(1) @Max(HARD_MAX_TX_MINOR) long perTxCapMinor,
    long dailyBudgetMinor, // Must be >= perTxCapMinor
    @Min(1) long approvalThresholdMinor, // Must be <= perTxCapMinor
    List<String> payeeAllowlist, // max 50
    boolean newPayeeRequiresApproval,
    List<String> allowedCategories, // null means any
    Velocity velocity,
    boolean refundsRequireApproval,
    @Min(60) @Max(86400) int approvalTtlSeconds
) {}

public record Velocity(int maxCalls, int windowSeconds) {}

```

Demo policy: currency USD, per transaction cap 50000 (USD 500), daily budget 60000, approval threshold 2500 (USD 25), allowlist `[DEMO_PAYEE_DEVTOOLS, DEMO_PAYEE_CONF]`, `newPayeeRequiresApproval: true`, categories `["developer_tools","events"]`, velocity 5 calls per 60 seconds, refunds require approval, approval TTL 900.

### 6.3 Evaluation (`Evaluate.java`)

Signature, pure, no I/O:

```
public EvaluateResult evaluate(EvaluationInput input);

```

Algorithm, exact order:

1. `agentStatus.equals("suspended")` → deny `AGENT_SUSPENDED`.

2. `policy == null` → deny `NO_ACTIVE_POLICY`.

3. Tool is read-only → allow `OK_READ_ONLY`.

4. Collect **deny** codes (do not stop at first): format, currency, amount > cap, spend > budget, velocity exceeded, counterparty in untrusted set, category not allowed. Return deny if any.

5. Collect **approval** codes: counterparty not in allowlist + requires approval, amount >= threshold, refund + requires approval. Return needs_approval if any.

6. Otherwise allow `OK_WITHIN_POLICY`.

### 6.4 Normalization (`Normalize.java`)

Trim, NFKC normalize, lowercase. Regex `^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$`.

### 6.5 Unit tests (`EvaluateTest.java`, 40+ cases)

Cover boundaries, suspended beats all, empty lists, lookalike chars.

## 7. MCP surface

Use Spring AI MCP or a Java MCP SDK. Endpoint `/mcp`. Authentication intercepts HTTP and extracts the Bearer token, hashes it, checks `agents` table.

All money tools return JSON:

```
{ "status": "executed | denied | pending_approval | failed",
  "call_id": "uuid", "decision": "allow", "reason_codes": [],
  "explanation": "...", "paypal": { } }

```

**Approval behavior.** The gateway threads sleep or use asynchronous wait (e.g. `CompletableFuture` / Spring `@Async`) polling the `approvals` table for up to **25 seconds**.

## 8. Gateway pipeline (`GatewayManager.java`)

For every tool: (1) authenticate, (2) Validate with Jakarta `@Valid`, (3) normalize, (4) load state, (5) `evaluate`, (6) insert `calls` row, (7) append audit, (8) branch, (9) append audit executed/failed, (10) return envelope.

## 9. PayPal adapter (`co.tz.kikomo.paypal.PayPalClient`)

Use Java 21 `HttpClient` for REST requests to `https://api-m.sandbox.paypal.com`.

Phase 0 spikes mapped to Gradle: Create a Spring Boot `CommandLineRunner` or standalone Java script mapped to a Gradle task `./gradlew runSpike` that hits the live Sandbox endpoints to verify credentials.

## 10. Audit log (`AuditService.java`)

* Implement `stableStringify` for JSON.

* Genesis `prev_hash` is 64 zeros.

* Compute SHA-256 in Java. Store in DB using `JdbcTemplate` inside `@Transactional`. Use `SELECT pg_advisory_xact_lock(424242)` to serialize append access.

* `verify()`: streams rows, recomputes, checks hashes.

## 11. Provenance (untrusted content)

`kikomo_fetch_url` uses Java 21 `HttpClient` to fetch text, extracts emails, stores in `untrusted_strings`. The Evaluate engine checks this table.

## 12. Natural-language policy compiler (`CompilePolicy.java`)

Uses `spring-ai-openai-spring-boot-starter`. Set `spring.ai.openai.base-url=${LLM_BASE_URL}`, `spring.ai.openai.api-key=${LLM_API_KEY}`. Map the expected output to the `Policy` Java Record using Spring AI's structured output converters.

## 13. Inbox HTTP API (Spring Web MVC)

Standard `@RestController` endpoints mapped to `/api/*`.

## 14. Demo scripts

Implement `DemoController` and a Java-based scripted agent that uses the MCP client to simulate the small, large, and injection payloads.

## 15. UI spec and brand

The Vite React frontend calls the `/api` endpoints exactly as specified.

## 16. Running, hosting and judge access

**Local one-command run:** `docker-compose up --build` starts Postgres and the app.
Without Docker: `./gradlew bootRun`.

**Hosted (Render):**

* `render.yaml` must use `runtime: docker` (the current Render field; it replaces the deprecated `env: docker`).

* `Dockerfile`:

```
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

```

## 17. Phases and "done when" commands

| Phase | Target | Work | Done when | 
| ----- | ----- | ----- | ----- | 
| 0 | Scaffold | Spring Boot Initializr, `build.gradle`, Dockerfile | `./gradlew runSpike` passes | 
| 1 | Engine | `Evaluate.java`, `Normalize.java` | `./gradlew test --tests "*EvaluateTest*"` green | 
| 2 | Gateway | Migrations (Flyway), `JdbcTemplate`, Gateway service | `./gradlew test --tests "*Gateway*"` green | 
| 3 | MCP | Spring AI MCP, Render walking skeleton | Deployment successful on Render | 
| 4 | Inbox | Web MVC, Security | REST tests pass | 
| 5 | Audit | AuditService, Tamper endpoints | `./gradlew test --tests "*Audit*"` green | 
| 6 | LLM UI | Policy compiler, Vite build | UI checklist | 
| 7 | Freeze | README, CI workflow | `./gradlew build` clean | 
