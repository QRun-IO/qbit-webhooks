# Receiving webhooks from QQQ

QQQ webhooks follow [Standard Webhooks](https://www.standardwebhooks.com/), so the official Standard Webhooks libraries verify them too.

## What arrives

```
POST /your/endpoint
Content-Type: application/json
webhook-id: evt_6f1c2d...
webhook-timestamp: 1760300100
webhook-signature: v1,K5oZfzN95Z9UVu1EsfQmfVNQhnkZ2pj9o9NDN/H/pI4=
webhook-attempt: 1

{"id":"evt_6f1c2d...","type":"cms_blog_post.updated","timestamp":"2026-10-12T20:15:00Z","source":"qqq://kof22/table/cms_blog_post","sourceEventId":"...","data":{...}}
```

## How to verify

1. Take the raw request body bytes before any JSON parsing.
2. Build `signed = webhook-id + "." + webhook-timestamp + "." + body`.
3. Compute `base64(HMAC-SHA256(key, signed))`. The key is the base64-decoded part after `whsec_` in the secret you were given.
4. `webhook-signature` holds one or more space-separated `v1,<signature>` entries (two during a secret rotation). Accept the request if any `v1` entry equals yours, comparing in constant time.
5. Reject a `webhook-timestamp` more than 5 minutes from your clock (replay protection).
6. Remember `webhook-id`s you have processed and ignore repeats. Delivery is at least once, and retries and replays reuse the id.
7. Answer 2xx quickly. Any other status, or a redirect, is retried with backoff. A 429 with `Retry-After` is honoured.

## Java (QQQ core 4.1.0-RC.5 or later)

```java
WebhookVerifier verifier = new WebhookVerifier(List.of(WebhookSecret.parse(System.getenv("WEBHOOK_SECRET"))));
verifier.verify(headersAsMap, rawBodyBytes);   // throws WebhookVerificationException with a safe reason
```

## Node.js

```js
import crypto from "node:crypto";

export function verify(headers, rawBody, secret) {
  const id = headers["webhook-id"], ts = headers["webhook-timestamp"], sigs = headers["webhook-signature"];
  if (!id || !ts || !sigs) throw new Error("missing headers");
  if (Math.abs(Date.now() / 1000 - Number(ts)) > 300) throw new Error("timestamp outside tolerance");
  const key = Buffer.from(secret.replace(/^whsec_/, ""), "base64");
  const expected = crypto.createHmac("sha256", key).update(`${id}.${ts}.`).update(rawBody).digest();
  const ok = sigs.split(" ").some((part) => {
    const [version, value] = part.split(",");
    if (version !== "v1" || !value) return false;
    const given = Buffer.from(value, "base64");
    return given.length === expected.length && crypto.timingSafeEqual(given, expected);
  });
  if (!ok) throw new Error("bad signature");
}
```

## Python

```python
import base64, hashlib, hmac, time

def verify(headers: dict, raw_body: bytes, secret: str) -> None:
    msg_id, ts, sigs = headers["webhook-id"], headers["webhook-timestamp"], headers["webhook-signature"]
    if abs(time.time() - int(ts)) > 300:
        raise ValueError("timestamp outside tolerance")
    key = base64.b64decode(secret.removeprefix("whsec_"))
    expected = base64.b64encode(hmac.new(key, f"{msg_id}.{ts}.".encode() + raw_body, hashlib.sha256).digest()).decode()
    for part in sigs.split(" "):
        version, _, value = part.partition(",")
        if version == "v1" and hmac.compare_digest(value, expected):
            return
    raise ValueError("bad signature")
```

## Secret rotation

When the sender rotates, it signs with the old and the new secret for 24 hours (configurable). Switch your receiver to the new secret any time in that window. Get the new value from the endpoint's Signing Secrets, or from the environment variable your admin set as a reference.
