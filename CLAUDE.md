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

Key cautions from the review (see dossier for details):

- develop and main have diverged (8/8): main has Java 21 + parent 1.5.1 + Apache-2.0
  LICENSE + v0.3.0; develop has the qqq-bom 0.40.0-SNAPSHOT pin + AGPL LICENSE.
- develop depends on a frozen SNAPSHOT (`qqq-bom-pom:0.40.0-SNAPSHOT`); 0.40.0 GA exists.
- pom `<licenses>` and all source headers still declare AGPL-3.0 on both branches.
- Test code has two qqq-4.0 breaks: `com.kingsrook.qqq.backend.javalin.QJavalinMetaData`
  (BREAK-01 package rename) and `qInstance.setAuthentication(...)` (BREAK-04-11).
