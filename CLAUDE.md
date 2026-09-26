# qbit-webhooks

QBit plugin providing outbound webhooks for QQQ host apps (event-type registry,
subscriptions, table-as-queue delivery with retry/backoff, webhook health management,
API-versioned JSON payloads).

## Knowledge base

- Deep-review dossier for this repo:
  `R:/Git.Local/KofTwentyTwo/second-brain/knowledge/qqq/repos/qbit-webhooks.md`
  (reviewed at develop commit `3e4c2f5bd501`, 2026-07-04)
- QQQ platform knowledge hub (start here for framework context):
  `R:/Git.Local/KofTwentyTwo/second-brain/knowledge/qqq/qqq-hub.md`
  — read `architecture/metadata-model.md` first for QBit mechanics.

Key cautions (see dossier for details; it predates the parent 2.0.0 re-pin):

- Default builds take the qqq version only from `qbit-build-parent` (2.0.0 = qqq 4.0.0);
  do not add an always-on `qqq-bom-pom` import. To check the next qqq line, run
  `mvn -B verify -Pqqq-snapshot` (4.1.0-SNAPSHOT; override with `-Dqqq.snapshot.version`).
- Licensing metadata is contradictory: LICENSE/NOTICE = Apache-2.0 (from main); pom
  `<licenses>`, source headers and `checkstyle/license.txt` = AGPL-3.0; README footer says
  proprietary. Alignment is an owner decision.
- The README (from main) documents an API that does not exist here (`WebhooksQBit`,
  `QWebhookMetaData`, HMAC signing, `webhook_log`). Trust the source: entry points are
  `WebhooksQBitProducer`, `WebhooksRegistry` and `WebhookEventType`.
