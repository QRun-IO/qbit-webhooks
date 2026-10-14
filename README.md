# QBit Webhooks

Signed, SSRF-safe outbound webhooks for QQQ applications. Admins add **endpoints** and **subscribe** them to event types: table inserts, updates and deletes (in-process or from the ESB), process completions and failures, and custom domain events. The qbit queues one **delivery** per event per endpoint in the same transaction as the change. It then signs each delivery in the [Standard Webhooks](https://www.standardwebhooks.com/) format and sends it with retries, backoff, dead letters and endpoint health. Every row is a normal QQQ table, and the app has a home dashboard.

Design: [QRun-IO/qqq#1105](https://github.com/QRun-IO/qqq/pull/1105) (`docs/specs/2026-10-12-qbit-webhooks-assessment.md`), and [docs/DESIGN.md](docs/DESIGN.md) for what 1.0 implements. Signing and secret generation use QQQ core directly; the local safe HTTP client remains pending a separate migration. Both primitives are available in QQQ core from 4.1.0-RC.5 ([QRun-IO/qqq#1109](https://github.com/QRun-IO/qqq/pull/1109)).

## Requirements

- Java 21
- QQQ 4.1.0-RC.5 or later (`qqq-backend-core`). Two modules are optional:
  - `qqq-middleware-api`, for payloads rendered through a QQQ API version.
  - `qqq-esb`, for ESB topics as an event source.
- A SQL backend for the five tables (DDL in [docs/ddl/webhooks-postgres.sql](docs/ddl/webhooks-postgres.sql)), and a QQQ scheduler for delivery.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-webhooks</artifactId>
   <version>1.0.0</version>
</dependency>
```

```java
new WebhooksQBitProducer()
   .withQBitConfig(new WebhooksQBitConfig()
      .withBackendName("rdbms")
      .withSchedulerName("scheduler")
      .withSourceName("myapp")                       // events say qqq://myapp/...
      .withTableEventSources(List.of("order", "customer"))
      .withProcessEventSources(List.of("shipOrder")))
   .produce(qInstance);

// optional: also consume ESB topics (events from other services)
WebhooksEsbSource.configure(qInstance);
```

Set `WEBHOOKS_SECRET_KEY` to the base64 of 32 random bytes, so the qbit can generate signing secrets and store them encrypted. Without it, use environment-reference secrets only.

## Tables

| Table | What it holds | In the nav |
|---|---|---|
| `webhooks_endpoint` | URL, status (Active, Paused, Disabled), health (Healthy, Probation, Unhealthy), in-order delivery, max per minute, timeout, internal-network allowlist (admin only) | yes |
| `webhooks_subscription` | Endpoint + event type, record filter (QQQ query filter JSON), detail level, only-these / never-these fields, API name and version | yes |
| `webhooks_delivery` | One row per event per endpoint: event id (the `webhook-id`), stored payload, status, attempts, next attempt, lease, last error, dead letter | yes |
| `webhooks_endpoint_secret` | Signing secrets: generated (AES-GCM encrypted) or an environment variable name; status Active, Previous (still signing during a rotation), Retired; fingerprint | on the endpoint |
| `webhooks_delivery_attempt` | One row per HTTP attempt: outcome, status, duration, response excerpt, error, remote address, which secrets signed it. Unique on delivery and attempt number (the claim) | on the delivery |

Deliveries and attempts are written only by the qbit. People act on them through the actions below.

## Permissions

| Permission | Grants |
|---|---|
| `webhooks.view` | The app, the home, every table (read) |
| `webhooks.manage` | Create and edit endpoints and subscriptions; Send Now, Replay, Cancel Delivery, Send Test Event |
| `webhooks.admin` | Signing secrets (Rotate Signing Secret, environment references, the secret value), internal-network targets, deleting endpoints, Delete Deliveries |

Grant `WebhooksPermissions.ALL` (which includes `webhooks.hasAccess`) to administrators. Subscribing to a table's events also needs read permission on that table. Record security locks from `withSecurityFields` / `withRecordSecurityLocks` apply to endpoints, subscriptions and deliveries. A subscription only receives records its security values could read.

## Actions

All of these are table or record actions with the standard review and result screens. None is in the nav.

| Action | On | Does |
|---|---|---|
| Send Now | Deliveries | Sends the selected unfinished deliveries now |
| Replay | Deliveries | Queues finished deliveries (dead letters included) again, with the same event id and a fresh attempt budget |
| Cancel Delivery | Deliveries | Stops pending deliveries |
| Delete Deliveries | Deliveries | Deletes finished deliveries and their attempts (admin) |
| Send Test Event | Endpoint | Sends a signed `webhooks.test` event now and shows the result |
| Rotate Signing Secret | Endpoint | Creates a new secret; the old one keeps signing for `rotationOverlapHours` (admin) |

The scheduler runs delivery every `deliveryIntervalSeconds` (15). It runs maintenance every 5 minutes (probation, retiring old secrets) and the retention purge daily.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `backendName` | required | Backend of the five tables |
| `schedulerName` | none | Without it nothing is sent on a schedule |
| `tableEventSources` | none | Local tables: `<table>.inserted / updated / deleted` |
| `processEventSources` | none | `process.<name>.completed / failed` |
| `esbDestinations`, `esbTableEventSources`, `esbSkipOwnEvents` | none, none, true | ESB topics to consume; remote tables; skip this instance's own events |
| `maxAttempts`, `retryMinutes` | 6, [1, 5, 15, 60, 240, 720] | Then the delivery is a dead letter |
| `leaseSeconds` | 120 | A claimed delivery whose runner died is retried after this |
| `connectTimeoutMillis`, `httpTimeoutMillis`, `totalTimeoutMillis`, `maxResponseBytes` | 5000, 10000, 30000, 65536 | HTTP limits (an endpoint's `timeoutSeconds` overrides the read timeout) |
| `allowPlainHttp` | false | `https` only by default |
| `unhealthyAfterConsecutiveFailures`, `probationAfterMinutes`, `autoDisableAfterProbationFailures` | 10, 60, 3 | Endpoint health |
| `encryptionKeyEnvName`, `rotationOverlapHours` | `WEBHOOKS_SECRET_KEY`, 24 | Secret storage and rotation |
| `deliveredRetentionDays`, `attemptRetentionDays`, `deadLetterRetentionDays` | 400, 400, 0 | 0 keeps forever |
| `snakeCaseColumnNames` | true | Column names as in `docs/ddl` |
| `appName`, `appLabel`, `appPath`, `tablePathPrefix` | `webhooks`, `Webhooks` | The app and the links on its home |

## For receivers

Every request carries `webhook-id`, `webhook-timestamp`, `webhook-signature` (`v1,<base64 HMAC-SHA256>`, more than one during a rotation) and `webhook-attempt`. Verify the signature, reject old timestamps, and ignore an id you have already processed. Delivery is at least once. See [docs/RECEIVERS.md](docs/RECEIVERS.md) for the format and code in Java, Node.js and Python.

## Adopting in another app

1. Add the dependency and run [docs/ddl/webhooks-postgres.sql](docs/ddl/webhooks-postgres.sql) (as a Liquibase changeset).
2. Produce the qbit (above) after your tables exist. Add its app under your admin app if you have one.
3. Grant the permissions to your admins, and set `WEBHOOKS_SECRET_KEY` in the environment.
4. Optional hooks, through `WebhooksIntegrations`:
   - publish `esbTableNames()` to your ESB;
   - index `quickSearchTables()` in Quick Search;
   - expose `apiTableNames()` in your QQQ API.
5. Other qbits deliver through `Webhooks.deliver(endpointName, type, data, idempotencyKey)` (the channel API), and code fires domain events with `Webhooks.fire(...)`.

Upgrading from 0.x: see [docs/migration-0.x-to-1.0.md](docs/migration-0.x-to-1.0.md).

## Build

```bash
./mvnw verify      # tests, Checkstyle and the JaCoCo gate (always use the wrapper)
```

## Health checks

The producer registers passive checks with QQQ central health monitoring (requires the health SPI in QQQ 4.1.0-SNAPSHOT or a subsequent release):

| Name | Kind | Category | Interval |
|---|---|---|---|
| `webhooks.endpoint.<id>` | DEPENDENCY | INTEGRATION | 300 seconds |
| `webhooks.dispatcher` | DEPENDENCY | INTEGRATION | 300 seconds |

Both belong to component `webhooks`. Endpoint checks read the existing state machine: HEALTHY means UP; UNHEALTHY or PROBATION means DEGRADED; automatically disabled means DOWN. Active and automatically disabled endpoints are discovered dynamically; paused and manually disabled endpoints are excluded. Details contain only healthStatus, consecutiveFailures and unhealthySince when present.

The dispatcher reports `oldestPendingSeconds`, measured from the oldest queued delivery's createDate across PENDING, RETRY_WAIT and SENDING. More than 15 minutes is DEGRADED; more than 60 minutes is DOWN. An empty queue reports UP with zero age. Checks do not send requests or change endpoint state.

The home shows one native central status line linking to the webhooks checks in System Health. Disable checks centrally with `health_check.enabled`. The endpoint state machine, alerts and Send Test Event action remain unchanged.


## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
