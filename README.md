# qbit-webhooks

Outbound webhook notifications for QQQ applications.

**For:** QQQ developers who need to notify external systems when records change  
**Status:** Stable

## Why This Exists

Modern applications don't exist in isolation. When an order is created, you might need to notify a fulfillment system. When a user updates their profile, a CRM needs to know. Polling for changes is inefficient and adds latency.

This QBit sends HTTP callbacks when records are inserted, updated, or deleted. Configure endpoints, select which events to send, and the system handles delivery with retries and logging.

## Features

- **Event-Driven Delivery** - Fires on insert, update, delete operations
- **Configurable Endpoints** - Register multiple webhook URLs per table
- **Payload Customization** - Include full record, changed fields only, or custom format
- **Retry Logic** - Automatic retries with exponential backoff
- **Delivery Logging** - Track success, failures, and response codes
- **Secret Signing** - HMAC signatures for payload verification

## Quick Start

### Prerequisites

- QQQ application (v0.20+)
- Database backend configured

### Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.qrun</groupId>
    <artifactId>qbit-webhooks</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Register the QBit

```java
public class AppMetaProvider extends QMetaProvider {
    @Override
    public void configure(QInstance qInstance) {
        new WebhooksQBit().configure(qInstance);
    }
}
```

### Configure a Webhook

```java
new QWebhookMetaData()
    .withName("orderCreatedNotification")
    .withTable("order")
    .withEvents(WebhookEvent.INSERT)
    .withUrl("https://api.example.com/webhooks/orders")
    .withSecret("your-signing-secret");
```

## Usage

### Multiple Events

```java
new QWebhookMetaData()
    .withName("customerSync")
    .withTable("customer")
    .withEvents(WebhookEvent.INSERT, WebhookEvent.UPDATE, WebhookEvent.DELETE)
    .withUrl("https://crm.example.com/sync");
```

### Conditional Delivery

```java
// Only fire when status changes to 'approved'
new QWebhookMetaData()
    .withName("approvalNotification")
    .withTable("order")
    .withEvents(WebhookEvent.UPDATE)
    .withCondition((oldRecord, newRecord) -> 
        "approved".equals(newRecord.getValue("status")) &&
        !"approved".equals(oldRecord.getValue("status")))
    .withUrl("https://notify.example.com/approved");
```

### Custom Payload

```java
new QWebhookMetaData()
    .withName("slackNotification")
    .withTable("alert")
    .withEvents(WebhookEvent.INSERT)
    .withPayloadTransformer((record) -> Map.of(
        "text", "New alert: " + record.getValue("message"),
        "channel", "#alerts"
    ))
    .withUrl("https://hooks.slack.com/services/xxx");
```

### Verifying Signatures

Receiving systems can verify the payload using the HMAC-SHA256 signature in the `X-Webhook-Signature` header:

```java
String payload = requestBody;
String signature = request.getHeader("X-Webhook-Signature");
String expected = HmacUtils.hmacSha256Hex(secret, payload);
if (!expected.equals(signature)) {
    throw new SecurityException("Invalid signature");
}
```

## Configuration

The QBit creates these tables:

| Table | Purpose |
|-------|---------|
| `webhook` | Webhook endpoint configurations |
| `webhook_log` | Delivery attempts and responses |

### Retry Settings

```java
new WebhooksQBit()
    .withMaxRetries(5)
    .withRetryDelayMs(1000)
    .withRetryBackoffMultiplier(2.0);
```

## Project Status

Stable and production-ready.

### Roadmap

- Batch delivery (group multiple events)
- Dead letter queue for failed deliveries
- Dashboard UI for webhook management

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run tests: `mvn clean verify`
4. Submit a pull request

## License

Proprietary - QRun.IO
