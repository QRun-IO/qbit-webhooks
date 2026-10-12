# Migrating from qbit-webhooks 0.x to 1.0

1.0 is a breaking release (owner decision D1/D3). The only known 0.x consumer is the `qqq-all` demo app.

| 0.x | 1.0 |
|---|---|
| Tables `webhook`, `webhookSubscription`, `webhookEvent`, `webhookEventContent`, `webhookEventSendLog` | `webhooks_endpoint`, `webhooks_subscription`, `webhooks_delivery` (payload inline, no content table), `webhooks_delivery_attempt`, plus `webhooks_endpoint_secret` |
| Permission names `webhook.*` (generic table permissions) | `webhooks.view`, `webhooks.manage`, `webhooks.admin` |
| Integer status ids | Enum names stored as text (`ACTIVE`, `DELIVERED`, ...) |
| `WebhookEventCategory` (insert, update, store, with-field, with-value, ad hoc) | `WebhookEventKind` (TABLE_INSERT / UPDATE / DELETE / STORE, PROCESS_COMPLETED / FAILED, CUSTOM) with optional `fieldName` / `value` |
| `FireAdHocWebhookEvent.fire(...)` | `Webhooks.fire(...)` / `Webhooks.fireForRecord(...)` |
| `WebhookEventTypeCustomizerInterface` | `WebhookPayloadCustomizerInterface` (changes `data` only) |
| Payload `{webhookEventDetails, record}` | Envelope `{id, type, timestamp, source, sourceEventId, data}` |
| Unsigned POSTs to any URL | Signed (Standard Webhooks); private targets blocked unless an admin allowlists them |
| One scheduled job per webhook | One scheduled delivery process for all endpoints |
| `httpTimeoutMillis` ignored | Honoured (read timeout) |

## Data

Existing 0.x rows are configuration and history. The simplest path, which needs no downtime handling, is:

1. Create the 1.0 tables (`docs/ddl/webhooks-postgres.sql`).
2. Re-create endpoints and subscriptions in the new tables, from the old `webhook` and `webhookSubscription` rows: name, url, event type name, API name and version.
3. Rotate a signing secret for each endpoint, and give it to the receiver.
4. Keep or archive the old tables. 1.0 does not read them.

Event type names change if they were custom. Re-register them as `WebhookEventType` with a `WebhookEventKind`.
