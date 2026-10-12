# AGENTS.md

This file provides guidance to coding agents working in this repository (same content as CLAUDE.md).

## Project Overview

**QBit Webhooks**: signed, SSRF-safe outbound webhooks for QQQ applications. It covers:
- table, process, ESB and custom events
- subscriptions with filters, and an outbox with replica-safe claims
- retries, dead letters, endpoint health, and an app home

Design: `docs/DESIGN.md` (from QRun-IO/qqq#1105). Inbound webhooks are phase 2 (DESIGN section 10).

## Build Commands

```bash
./mvnw compile          # always use the wrapper: Maven 3.10 cannot read the parent POM
./mvnw test
./mvnw verify           # tests + Checkstyle + JaCoCo gate (70% instructions, 90% classes)
```

## Architecture

- **Entry point:** `WebhooksQBitProducer implements QBitMetaDataProducer<WebhooksQBitConfig>`. Optional: `WebhooksEsbSource.configure(qInstance)` for ESB topics.
- **Packages:**
  - `metadata` (tables, enums, validating customizers), `registry` (event types)
  - `events`:
    - record and process listeners
    - `DeliveryOutbox`, `SubscriptionMatcher`, `PayloadBuilder`
  - `delivery`:
    - `DeliveryEngine` (claim, send, outcome), `EndpointHealth`
    - `WebhooksAlerts` (reflective Alerts SPI)
  - `secrets` (AES-GCM, env references, rotation), `processes`, `app` (home, secret widget), `permissions`, `esb`, `support/SystemWrites`.
  - `shared`: INTERNAL COPY of the core signer and safe HTTP client (QRun-IO/qqq#1109). Replace with `core.utils.webhooks` / `core.utils.http` at QQQ 4.1.0-RC.5 (DESIGN section 11). Keep it identical to core until then.

## Invariants

- A delivery is sent only by the runner whose insert of (`deliveryId`, `attemptNumber`) succeeded. Never send without a claim.
- Payloads are built at queue time and stored; retries resend the same bytes and the same `webhook-id`.
- Secrets are never stored, logged, audited, put in process results, or returned in plain text. Only fingerprints.
- Outbound requests go only through `SafeHttpClient` (via `WebhooksRuntime.httpClient`).
- The qbit's own writes go through `SystemWrites` (authorized scope; customizers can tell them from people's).
- No static config caches: read config with `WebhooksRuntime.config()`.

## Naming (owner rule)

- Tables are lowercase snake_case, prefixed `webhooks_`; the backend name is the table name; column names are snake_case.
- Permissions: `webhooks.view`, `webhooks.manage`, `webhooks.admin` (and `webhooks.hasAccess`).
- Data lives in standard visible tables. Processes are table or record actions, never in the nav. The nav lists only the main tables.

## Code Style

3-space indent, braces on the next line, `return (x);`, flower-box Javadoc on every class and method, fluent `withX()` setters.

## Temporary (until the owner approves removing them)

- The 0.x sources (`actions/`, `model/`, `tables/`, the old `processes/*`, `registry/*Plugin`) are still in the tree.
- `pom.xml` excludes them from compile, test-compile and Checkstyle.
- Delete them and the excludes together.

## Release

- CircleCI qqq-orb 0.6.8.
- `develop` publishes SNAPSHOTs.
- `release/*` publishes RCs.
- `vX.Y.Z` tags publish releases (GA tags only).
