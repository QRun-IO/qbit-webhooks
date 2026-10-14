# Changelog

All notable changes to QBit Webhooks are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Every table explains itself: a help bundle (`help/qbit-webhooks.yaml`, QQQ help-bundle format) says what each `webhooks_*` table is for and how its rows get created, including where event types come from. It shows on the table screen and when the table is empty. Applied by `WebhooksHelp` (STAND-IN for QRun-IO/qqq#1198); a host's own help is kept.
- A Setup card on the Webhooks home lists event types, encryption key, endpoints, signing secrets and subscriptions with their state and the next step.
- `WebhooksTablesGuardrailTest` (each table is used at runtime, seeded or explained, and has help) and a test that a previous secret stops signing at its Signs Until date even before the maintenance job retires it.
- Nothing is seeded, deliberately: the tables hold the owner's receivers and secrets, or logs.

### Changed
- Require QQQ 4.1.0-RC.5 and use its webhook signer, verifier, secret and verification exception (QRun-IO/qqq#1160, F11). Removed the internal signing package; Java consumers must import `com.kingsrook.qqq.backend.core.utils.webhooks` instead.
- Signing wire format, timestamp tolerance, rotation, endpoint management, encrypted secret storage and delivery behavior are unchanged.

## [1.0.0] - not yet released

A rebuild of the qbit on QQQ 4.1 (design: QRun-IO/qqq#1105, docs/DESIGN.md). Breaking: see docs/migration-0.x-to-1.0.md.

### Added
- **Signing:**
  - Standard Webhooks signing (`webhook-id`, `webhook-timestamp`, `webhook-signature: v1,...`, `webhook-attempt`).
  - Per-endpoint secrets, stored encrypted (AES-256-GCM) or as environment references; rotation with an overlap; an admin-only secret value widget.
- **SSRF-safe sending:**
  - Private, loopback, link-local and metadata ranges are blocked.
  - DNS is resolved once and the connection pinned; redirects are not followed.
  - TLS 1.2+, connect, read and total timeouts, and a response size cap.
  - An admin-only internal allowlist per endpoint.
- **Event sources:**
  - Delete events.
  - Process completed and failed events.
  - The ESB as an event source (`WebhooksEsbSource`), with own-event skipping and CloudEvent-id dedup.
  - Custom events (`Webhooks.fire`) and a channel API for other qbits (`Webhooks.deliver`).
- **Delivery engine:**
  - An event id and idempotency key on every delivery.
  - Atomic claims safe across replicas (a unique attempt row) with leases.
  - Dead letters with Replay, and serial delivery per endpoint.
  - Rate limits per endpoint, 429 `Retry-After` handling without blocking the runner, and auto-disable after failed probations.
  - Retention purge.
- **Subscriptions:**
  - Record filters (QQQ query filter JSON), record security matching and a read-permission check on subscribe.
  - Detail levels, include and exclude field lists.
- **Alerts:** raised through the Alerts producer SPI when present.
- **App:**
  - A home dashboard (delivery volume, failures, dead letters, attention list, latency).
  - Plain-language table and record actions with the standard review and result screens.
- **Integrations:** ESB, Quick Search and API hooks (`WebhooksIntegrations`).
- **Repository:** Postgres DDL in docs/ddl; JaCoCo gate; Checkstyle in `verify`; Maven wrapper; repo-standard files.

### Changed
- Tables renamed to `webhooks_endpoint`, `webhooks_endpoint_secret`, `webhooks_subscription`, `webhooks_delivery`, `webhooks_delivery_attempt`; permissions to `webhooks.view / manage / admin`.
- Payload envelope is now `{id, type, timestamp, source, sourceEventId, data}`.
- One scheduled delivery process replaces the per-webhook jobs.
- `httpTimeoutMillis` is now honoured.
- Builds against QQQ 4.1.0-RC.4 by default.

### Removed
- `webhookEventContent` (the payload is a column of `webhooks_delivery`).

## [0.3.0] - 2025-12-28
- Last 0.x release (Java 21, qbit-build-parent).
