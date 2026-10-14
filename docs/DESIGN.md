# qbit-webhooks 1.0 design

**Status:** implemented for outbound (1.0). Inbound webhooks are phase 2 (section 10), after outbound GA.
**Source:** the assessment in [QRun-IO/qqq#1105](https://github.com/QRun-IO/qqq/pull/1105) (`docs/specs/2026-10-12-qbit-webhooks-assessment.md`). Owner decisions D1 to D8 were adopted as recommended:

| # | Decision |
|---|---|
| D1 | Enhance the existing qbit into 1.0; breaking changes allowed |
| D2 | The signer and SSRF-safe client live in `qqq-backend-core` (QRun-IO/qqq#1109). Signing uses core directly at the QQQ 4.1.0-RC.5 baseline; the internal HTTP copy remains pending a separate migration (see section 11) |
| D3 | Tables renamed to `webhooks_*` |
| D4 | Internal and private targets blocked by default; admin-only allowlist per endpoint |
| D5 | Inbound is phase 2, in this qbit |
| D6 | The Alerts spec (#1090) no longer claims a signing scheme here (fixed on its branch, commit 797814c) |
| D7 | PR #5 (licence and CI alignment) merged first |
| D8 | At-least-once delivery; optional serial ordering per endpoint |

## 1. Flow

```
 table write ─► RecordChangeListener ─┐
 process end ─► ProcessLifecycleListener ─┤
 ESB topic ──► EsbTrigger process ────────┼─► DeliveryOutbox ─► webhooks_delivery (PENDING, payload stored)
 Webhooks.fire(...) (custom) ─────────────┤        one row per matching subscription,
 Webhooks.deliver(...) (channel) ─────────┘        unique dedup key, same transaction as the write
                                                              │
                     scheduler (every 15 s) ─► DeliveryEngine ┘
                         claim attempt n (unique insert) ─► sign ─► SafeHttpClient POST
                         ─► DELIVERED │ RETRY_WAIT (backoff, Retry-After) │ DEAD_LETTER
                         ─► endpoint health ─► Alerts SPI (optional)
```

## 2. Signing (Standard Webhooks)

Every request carries:

| Header | Value |
|---|---|
| `webhook-id` | The delivery's event id (`evt_` + 32 hex). The same on every retry and replay, so it is the receiver's **idempotency key** |
| `webhook-timestamp` | Unix seconds when this attempt was sent |
| `webhook-signature` | `v1,<base64(HMAC-SHA256(key, "<id>.<timestamp>.<body>"))>`; during a rotation, one entry per active secret, space separated |
| `webhook-attempt` | 1, 2, ... (informational, not signed) |
| `Content-Type` | `application/json` |

- **Key:** a secret written `whsec_<base64>` (the HMAC key is the decoded bytes; generated keys are 32 random bytes). A secret without the prefix is used as raw UTF-8 bytes.
- **Verification** (core `WebhookVerifier`):
  - All three headers are required.
  - The timestamp must be within 5 minutes of now, in either direction.
  - The receiver recomputes `v1` for each secret it holds and compares in constant time.
  - Unknown versions are ignored.
- **Rotation:**
  - "Rotate Signing Secret" makes the current ACTIVE secret PREVIOUS, with `expiresDate` = now + `rotationOverlapHours` (24), and creates a new ACTIVE secret.
  - Until it expires, every delivery is signed by both, so a receiver can switch secrets whenever it likes.
  - Maintenance retires expired PREVIOUS secrets.
- **Storage:** a secret is never stored, logged, audited or returned in plain text. Each row keeps a 12-hex fingerprint (part of the SHA-256), which is safe to show. There are two kinds:
  - **GENERATED:** AES-256-GCM. The key is base64 of 32 bytes in `WEBHOOKS_SECRET_KEY` (the env name is configurable). The endpoint id is bound in as associated data. The stored form is `v1:<base64(nonce || ciphertext)>`.
  - **REFERENCE:** the name of an environment variable (for example, injected from Infisical). The value is read at send time.
- **Showing a generated secret:**
  - The result screen of Rotate never shows it, because process tracing can store result screens.
  - Instead, the secret record has a "Secret Value" widget for `webhooks.admin` only. The value is decrypted on request and shown behind a "Show the secret" toggle.
- **No secret:** deliveries to an endpoint with no usable secret wait (they are not attempted, and the home flags it). The exception is an endpoint with "Allow Unsigned Delivery", which gets `webhook-id` and `webhook-timestamp` without a signature.

Receiver code: [RECEIVERS.md](RECEIVERS.md).

## 3. SSRF policy (core `SafeHttpClient`, `AddressRules`)

- **URL:**
  - `https` only (`allowPlainHttp` is false by default).
  - No user name or password in the URL; a host is required.
- **Blocked by default:**
  - IPv4:
    - unspecified 0/8
    - private 10/8, 172.16/12, 192.168/16
    - CGNAT 100.64/10
    - loopback 127/8
    - link-local 169.254/16, which includes cloud metadata
    - IETF and documentation 192.0.0/24, 192.0.2/24, 198.51.100/24, 203.0.113/24
    - 6to4 relay 192.88.99/24
    - benchmarking 198.18/15
    - multicast and reserved 224/4, 240/4
  - IPv6: `::`, `::1`, discard 100::/64, documentation 2001:db8::/32, ULA fc00::/7, link-local fe80::/10, site-local fec0::/10, multicast ff00::/8.
  - IPv4 embedded in IPv6 is checked as IPv4: mapped, compatible, NAT64 64:ff9b::/96 and 6to4 2002::/16.
- **Always blocked, even when allowlisted:** 169.254.169.254, 100.100.100.200, fd00:ec2::254, and the unspecified addresses.
- **Allowlist (D4):**
  - The endpoint's "Allow Internal Targets" plus "Internal Allowlist". Entries are CIDR blocks, addresses, exact host names, or `.suffix` host names.
  - Only `webhooks.admin` can set or change them. A table customizer enforces this, so it applies to the UI, the API and code alike.
- **DNS:** resolved once per request, and every address is checked; one blocked address refuses the whole request. The connection is pinned to the checked addresses through the HTTP client's DNS resolver, so DNS rebinding between the check and the connect cannot redirect it.
- **Redirects:** never followed. A 3xx is a failed attempt ("redirects are not followed").
- **TLS:** 1.2 or 1.3, the JVM trust store, standard host name verification.
- **Limits:**
  - Timeouts: connect 5 s; read `httpTimeoutMillis` 10 s (the endpoint's `timeoutSeconds` overrides it); total 30 s. Before 1.0 the timeout setting was ignored.
  - Response: at most 64 KiB is read. The attempt row stores at most 2,000 characters, marked `[truncated]`.
- **When it is checked:** on save, the endpoint URL is checked (an unresolvable host is allowed, because DNS may not exist yet), and again on every send.
- **Policy blocks** (BLOCKED attempts) do not count against endpoint health.

## 4. Event sources

| Source | Event types | How | Dedup key |
|---|---|---|---|
| Table (in-process) | `<table>.inserted / updated / deleted` for `tableEventSources`; custom types with `fieldName` (and `value`) for "field changed (to)" | QQQ 4.1 record change listener; deletes included. Delivery rows are inserted in the write's transaction when it is on the qbit's backend; otherwise right after the write (or after its commit) | new UUID per change |
| Process (in-process) | `process.<name>.completed / failed` for `processEventSources` | QQQ process lifecycle listener. Data: name, label, run id, table, error. Never the process's inputs | `<processUUID>-<completed|failed>` |
| ESB | Table and process CloudEvents (`qqq.table.<t>.<inserted|updated|deleted>`, `qqq.process.<p>.<completed|failed>`) and registered custom types | `WebhooksEsbSource.configure(qInstance)`: a hidden process with an `EsbTrigger` (shared durable subscription "webhooks") on each `esbDestinations` topic | the CloudEvent `id` |
| Custom (domain) | any registered `CUSTOM` type | `Webhooks.fire(type, data[, transaction])`, `Webhooks.fireForRecord(...)` | new UUID |
| Channel (other qbits, e.g. Alerts) | any type | `Webhooks.deliver(endpointName, type, data, idempotencyKey)`: one endpoint, no subscription | the idempotency key |

**No double sends between the in-process and ESB paths:**
- An ESB event whose source is `qqq://<this instance's ESB name>/...` is skipped (`esbSkipOwnEvents`, on by default), because the in-process listener already queued it.
- Events from other services about the same tables are queued.
- Every delivery's `dedup_key` (`<source event id>:<subscription>`) is unique, so a redelivered message queues nothing new.

## 5. Subscriptions

A subscription matches an event when all of these hold:
1. The subscription is Active, and its endpoint is not Disabled.
2. The event type is the same.
3. The type's field condition holds (an update changed the field, to the value if one is set).
4. The **record filter** matches. It is a QQQ `QQueryFilter` as JSON, evaluated in memory against the merged record (old values overlaid with new).
5. **Record security** allows it: a session made from the subscription's security-field values must be able to read the record.

Creating a subscription to a table's events needs read permission on that table.

**Payload shaping:**
- **API version:** `apiName` + `apiVersion` render the record through the QQQ API middleware (stable, versioned field names) when it is on the classpath.
- **Detail level:**
  - `FULL`: the record, plus `oldRecord` for updates.
  - `CHANGED_FIELDS`: only the changed fields and the key.
  - `KEYS_ONLY`: the key.
- **Field lists:** "Only These Fields" and "Never These Fields".
- **Code:** an event type's `WebhookPayloadCustomizerInterface` can change `data` last.

The payload is built and stored when the event is queued, so retries send identical bytes.

```json
{
  "id": "evt_6f1c...",
  "type": "cms_blog_post.updated",
  "timestamp": "2026-10-12T20:15:00Z",
  "source": "qqq://kof22/table/cms_blog_post",
  "sourceEventId": "4b9e...",
  "data": { "table": "cms_blog_post", "primaryKey": "42", "changedFields": ["title"], "record": { }, "oldRecord": { } }
}
```

## 6. Delivery semantics

- **Claim:**
  - To send attempt *n*, a runner inserts `webhooks_delivery_attempt` (`delivery_id`, *n*); that pair is unique.
  - One runner wins. The losers get a duplicate (a pre-check error or a constraint violation) and skip the delivery.
  - This is a compare-and-set that works on any SQL backend, with no `SELECT ... FOR UPDATE SKIP LOCKED` needed.
  - Tested with two replicas and two runners each on one H2 database: 150 deliveries, each sent exactly once.
- **Lease:**
  - The winner marks the delivery SENDING with `lease_expires_date` = now + `leaseSeconds` (120, which must be more than the total HTTP timeout).
  - If the runner dies, the next run claims attempt *n+1* after the lease, and marks *n* LEASE_EXPIRED.
- **At least once (D8):**
  - A runner that dies after the receiver got the request, but before recording the outcome, causes one repeat with the same `webhook-id`.
  - Receivers deduplicate on that id.
- **Outcomes:**

  | Response | Delivery becomes | Notes |
  |---|---|---|
  | 2xx | DELIVERED | |
  | 429 | RETRY_WAIT | After `Retry-After` (seconds or an HTTP date, capped at 1 h). Uses no attempt budget and no health |
  | Anything else (3xx included), a network failure or a policy block | RETRY_WAIT | Backoff of `retryMinutes` (1, 5, 15, 60, 240, 720) |
  | `maxAttempts` (6) failures since the last replay | DEAD_LETTER | |

  Dead letters stay until replayed or deleted, and are kept forever by default.
- **Ordering:**
  - No global order. Within an endpoint, oldest first, but a failing delivery backs off while newer ones go.
  - With **Deliver In Order** (`serial_delivery`), one delivery at a time: the oldest unfinished one blocks the rest until it is delivered or dead-lettered.
- **Rate limit:** "Max Deliveries Per Minute" counts that endpoint's attempts in the last minute, from the database, so it holds across replicas (approximately: concurrent runners can overshoot by a few).
- **Health:**

  | From | To | When |
  |---|---|---|
  | HEALTHY | UNHEALTHY | `unhealthyAfterConsecutiveFailures` (10) failures in a row. No attempts while unhealthy |
  | UNHEALTHY | PROBATION | after `probationAfterMinutes` (60). One attempt per run |
  | PROBATION | HEALTHY | a success |
  | PROBATION | UNHEALTHY | a failure |
  | PROBATION | DISABLED | after `autoDisableAfterProbationFailures` (3) failed probations |

  Send Now and Send Test Event ignore health. A disabled endpoint sends nothing.
- **Alerts (optional):**
  - When the Alerts producer SPI (`com.kingsrook.qqq.backend.core.alerts.QAlerts`, #1090) is on the classpath, called by reflection so there is no compile dependency:
    - `webhooks.endpoint.unhealthy` (ERROR), resolved on recovery
    - `webhooks.endpoint.disabled` (ERROR)
    - `webhooks.delivery.dead_letter` (WARNING)
  - The dedup key is `endpoint:<id>`.
  - Titles are external-safe (endpoint name and counts only).
- **No static caches:**
  - Config is read from the current QInstance on every use.
  - Subscriptions are read per change, so every replica sees edits at once.
  - The only JVM-wide state is the HTTP connection pool per policy.
- **Retention:**
  - A daily purge deletes delivered and cancelled deliveries older than `deliveredRetentionDays` (400), and attempts older than `attemptRetentionDays` (400). Dead letters are deleted only after `deadLetterRetentionDays` (0, never).
  - 0 keeps forever. kof22 keeps data, so it sets 0 for all three.

## 7. Data model

Lowercase snake_case tables with the `webhooks_` prefix; backend names equal table names; columns are snake_case of the field names. DDL: [ddl/webhooks-postgres.sql](ddl/webhooks-postgres.sql).

| Table | Key fields | Unique |
|---|---|---|
| `webhooks_endpoint` | name, url, active_status, health_status, serial_delivery, max_per_minute, timeout_seconds, allow_unsigned, allow_internal_targets, internal_allowlist, consecutive_failures, probation_failures, last_success_date, last_failure_date, last_failure_message, unhealthy_since_date, auto_disabled_date | name |
| `webhooks_endpoint_secret` | endpoint_id, status (ACTIVE / PREVIOUS / RETIRED), source (GENERATED / REFERENCE), secret_ref, encrypted_secret, fingerprint, expires_date, retired_date, note | |
| `webhooks_subscription` | endpoint_id, event_type_name, active_status, description, filter_json, detail_level, include_fields, exclude_fields, api_name, api_version | |
| `webhooks_delivery` | event_id, event_type_name, endpoint_id, subscription_id, source, source_table, source_record_id, source_event_id, dedup_key, status, attempt_count, cycle_attempt_count, replay_count, occurred_date, next_attempt_date, lease_expires_date, last_http_status, last_error, delivered_date, dead_letter_date, payload_bytes, payload | dedup_key, event_id |
| `webhooks_delivery_attempt` | delivery_id, endpoint_id, attempt_number, outcome, claimed_by, lease_expires_date, start_date, end_date, duration_millis, http_status, response_excerpt, error_message, remote_address, request_url, signed_with | (delivery_id, attempt_number) |

Host security fields (config) are added to endpoint, subscription and delivery.

## 8. Standards (owner rules)

- **Data:** every table is a standard, visible QQQ table (query, view, export, audit). People write endpoints and subscriptions. Secrets are written through the Rotate action or as references. Deliveries and attempts are written only by the qbit.
- **Permissions:** `webhooks.view`, `webhooks.manage` and `webhooks.admin` (full qbit name), through a custom checker. The qbit's own writes run in core's authorized scope, so core permission enforcement can stay on.
- **Processes:** six table or record actions with the standard streamed-ETL review and result screens, plus three hidden scheduled processes. None is in the nav. The labels are plain.
- **App:**
  - The home dashboard shows real data only:
    - delivered (24 h), failed attempts (24 h), dead letters, waiting to send, endpoints needing attention, and latency median / p95
    - attempts per hour for 24 h (succeeded and failed)
    - endpoints needing attention, with the reason
    - the newest dead letters, with the Replay hint
  - The nav lists Endpoints, Subscriptions and Deliveries. Secrets, attempts, a subscription's deliveries and an endpoint's deliveries are relations on their parent records.
- **UI:** the home is one HTML widget with no inner scroll and no horizontal scroll. Tables are full width and become stacked cards below 640 px. Dates are compact and the labels plain.
- **Integration:**
  - `WebhooksIntegrations.esbTableNames()` (publish the main tables), `quickSearchTables()` (field weights) and `apiTableNames()`.
  - The API goes through the same tables, permissions and customizers as the admin, so it behaves identically.

## 9. Cross-qbit use

- **Alerts (#1090):** its generic webhook channel can call `Webhooks.deliver(endpoint, type, data, key)` when this qbit is installed, or sign with the core signer itself (same format).
- **CDN (#1084):** uses the core signer and safe client (trusted-internal allowlist) for revalidation hooks; it does not need the outbox.

## 10. Phase 2: inbound webhooks (design only)

Goal: verify and record signed callbacks from third parties (Authentik, GitHub, Stripe, other QQQ apps), then hand them to the ESB or a process. This is after outbound GA, in this qbit.

- **Route:** `POST /qqq/v1/webhooks/inbound/{sourceName}`, a route provider, on the same pattern as the Authentik design. The raw body is kept byte for byte for verification.
- **Tables:**
  - `webhooks_inbound_source`:
    - name, active_status, secret (a reference or generated, same storage as outbound)
    - scheme: STANDARD_WEBHOOKS, GITHUB (`X-Hub-Signature-256: sha256=<hex>`), STRIPE (`Stripe-Signature: t=..,v1=..`), or SHARED_TOKEN (bearer, constant-time compare)
    - tolerance_seconds
    - target: an ESB destination name, a process name, or both
    - max_body_bytes
  - `webhooks_inbound_event`:
    - source_id, external_id (the provider's message id), received_date, status (ACCEPTED / REJECTED / DUPLICATE / HANDED_OFF / FAILED)
    - reject_reason, headers (allowlisted names only), body (capped), handed_off_date
    - unique (source_id, external_id)
- **Checks, in order** (each rejection is recorded with a reason; secrets are never logged):
  1. The source exists and is active.
  2. The body is within the size limit.
  3. The signature verifies under the scheme (core `WebhookVerifier` for Standard Webhooks).
  4. The timestamp is within tolerance (replay window).
  5. The `external_id` was not seen before; otherwise DUPLICATE, answered 200 so the sender stops retrying.
- **Response:**
  - It answers 2xx as soon as the event is stored (the outbox pattern again).
  - The hand-off runs after commit: it publishes a CloudEvent (`webhooks.inbound.<source>.<type>`, id = `external_id`) to the target ESB destination, or runs the target process with the event id.
  - A failed hand-off retries, and stays visible as FAILED with Retry and Replay actions.
- **Security:**
  - Rate limiting per source.
  - No authentication other than the signature (the route is public).
  - `webhooks.admin` manages sources.
  - Only allowlisted headers are stored, and payloads can be purged by retention.
- **UI:** an "Inbound" area on the home (received / rejected / duplicates per source). Sources join the nav; inbound events are a relation.
- **Effort:** about 8 working days (assessment #14).

## 11. Moving to the core primitives (QQQ 4.1.0-RC.5)

QRun-IO/qqq#1160 (F11) migrates signing to the released `qqq-bom-pom` 4.1.0-RC.5 baseline. Delivery signing, secret generation/rotation and receiver verification use `com.kingsrook.qqq.backend.core.utils.webhooks` directly. The local `shared.signing` package is removed; endpoints, encrypted secret storage and delivery remain in this qbit. Inbound routes are still phase 2 (design only).

Comparison against core sources at tag `v4.1.0-RC.5` found no executable differences in `WebhookSigner`, `WebhookVerifier`, `WebhookSecret` or `WebhookVerificationException`; only package names, copyright years and migration comments differed. No compatibility adapter is needed:

- Headers remain `webhook-id`, `webhook-timestamp`, `webhook-signature` (case-insensitive when verifying).
- HMAC-SHA256 signs UTF-8 `id.timestamp.` followed by unchanged body bytes; signatures are padded Base64 with `v1,` and a single space between rotation signatures, in secret-list order.
- Secrets remain `whsec_` plus padded Base64 key bytes; unprefixed secrets use trimmed UTF-8 bytes. Generation uses 32 bytes, with a 16-byte minimum for imported keys.
- Default timestamp tolerance remains five minutes in either direction, including the boundary.
- Verification accepts any matching `v1` entry with any known secret and ignores unknown signature versions.

Signing contract tests retain the reference vector and test independent local HMAC signing against core verification and core signing against local verification, including two-secret rotation and exact tolerance boundaries. Delivery integration tests verify actual requests with core.

The `shared.http` copy is outside F11 and remains for a separate migration to `com.kingsrook.qqq.backend.core.utils.http`.
