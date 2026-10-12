/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.webhooks.events;


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DeliveryStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Turns events into {@code webhooks_delivery} rows: one per matching
 ** subscription, each with its own event id and a stored payload, so retries
 ** send identical bytes.
 **
 ** <p>When called with the source write's transaction (same backend), the
 ** deliveries commit or roll back with the write (a transactional outbox).
 ** Every delivery has a {@code dedupKey} of {@code <sourceEventId>:<subscription>}
 ** (unique), so an event that arrives twice (ESB redelivery, a retried
 ** trigger) is queued once.</p>
 *******************************************************************************/
public final class DeliveryOutbox
{
   private static final QLogger LOG = QLogger.getLogger(DeliveryOutbox.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private DeliveryOutbox()
   {
   }



   /*******************************************************************************
    ** Queue events for every matching subscription. Returns the delivery rows
    ** inserted.
    *******************************************************************************/
   public static List<QRecord> enqueue(List<WebhookEvent> events, QBackendTransaction transaction) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      if(config == null || events.isEmpty())
      {
         return (List.of());
      }
      Set<String> typeNames = new LinkedHashSet<>();
      events.forEach(e -> typeNames.add(e.getEventTypeName()));
      Map<String, List<SubscriptionMatcher.Target>> targets = SubscriptionMatcher.targetsByType(typeNames);
      if(targets.isEmpty())
      {
         return (List.of());
      }

      WebhooksRegistry registry = WebhooksRegistry.of(QContext.getQInstance());
      List<QRecord>    rows     = new ArrayList<>();
      for(WebhookEvent event : events)
      {
         WebhookEventType type = registry.get(event.getEventTypeName());
         for(SubscriptionMatcher.Target target : targets.getOrDefault(event.getEventTypeName(), List.of()))
         {
            if(SubscriptionMatcher.matches(config, type, event, target.subscription()))
            {
               rows.add(row(config, event, type, target.subscription(), target.endpoint().getValueInteger("id"),
                  event.getSourceEventId() + ":" + target.subscription().getValue("id")));
            }
         }
      }
      return (insert(rows, transaction));
   }



   /*******************************************************************************
    ** Queue one event for one endpoint, with no subscription (a channel
    ** delivery from another qbit, or a test send).
    **
    ** @param idempotencyKey makes repeated calls queue once (null: always queue)
    *******************************************************************************/
   public static QRecord enqueueDirect(WebhookEvent event, Integer endpointId, String idempotencyKey) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      if(config == null)
      {
         throw (new QException("The webhooks qbit is not installed"));
      }
      WebhookEventType type = WebhooksRegistry.of(QContext.getQInstance()).get(event.getEventTypeName());
      String dedupKey = (idempotencyKey == null ? event.getSourceEventId() : idempotencyKey) + ":endpoint:" + endpointId;
      List<QRecord> inserted = insert(List.of(row(config, event, type, null, endpointId, dedupKey)), null);
      if(inserted.isEmpty())
      {
         List<QRecord> existing = SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("dedupKey", QCriteriaOperator.EQUALS, dedupKey)));
         if(existing.isEmpty())
         {
            throw (new QException("Could not queue the webhook delivery"));
         }
         return (existing.get(0));
      }
      return (inserted.get(0));
   }



   /*******************************************************************************
    ** A delivery row.
    *******************************************************************************/
   private static QRecord row(WebhooksQBitConfig config, WebhookEvent event, WebhookEventType type, QRecord subscription, Integer endpointId, String dedupKey) throws QException
   {
      Instant now     = WebhooksRuntime.clock().instant();
      String  eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
      if(event.getOccurredAt() == null)
      {
         event.withOccurredAt(now);
      }
      String payload = PayloadBuilder.build(event, type, subscription, eventId);

      QRecord row = new QRecord()
         .withValue("eventId", eventId)
         .withValue("eventTypeName", event.getEventTypeName())
         .withValue("endpointId", endpointId)
         .withValue("subscriptionId", subscription == null ? null : subscription.getValue("id"))
         .withValue("source", event.getSource().name())
         .withValue("sourceTable", event.getSourceTable())
         .withValue("sourceRecordId", event.getSourceRecordId())
         .withValue("sourceEventId", event.getSourceEventId())
         .withValue("dedupKey", dedupKey)
         .withValue("status", DeliveryStatus.PENDING.name())
         .withValue("attemptCount", 0)
         .withValue("cycleAttemptCount", 0)
         .withValue("replayCount", 0)
         .withValue("occurredDate", event.getOccurredAt())
         .withValue("nextAttemptDate", now)
         .withValue("payload", payload)
         .withValue("payloadBytes", payload.getBytes(StandardCharsets.UTF_8).length);

      QRecord subject = event.subjectRecord();
      for(QFieldMetaData field : config.getSecurityFields())
      {
         Object value = subject == null ? null : subject.getValue(field.getName());
         if(value == null && subscription != null)
         {
            value = subscription.getValue(field.getName());
         }
         row.setValue(field.getName(), (java.io.Serializable) value);
      }
      return (row);
   }



   /*******************************************************************************
    ** Insert, skipping rows whose dedupKey already exists.
    *******************************************************************************/
   private static List<QRecord> insert(List<QRecord> rows, QBackendTransaction transaction) throws QException
   {
      if(rows.isEmpty())
      {
         return (rows);
      }
      List<String> keys = rows.stream().map(r -> r.getValueString("dedupKey")).toList();
      Set<String>  seen = new HashSet<>();
      for(QRecord existing : SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("dedupKey", QCriteriaOperator.IN, new ArrayList<>(keys))), transaction))
      {
         seen.add(existing.getValueString("dedupKey"));
      }
      List<QRecord> fresh = new ArrayList<>();
      for(QRecord row : rows)
      {
         if(seen.add(row.getValueString("dedupKey")))
         {
            fresh.add(row);
         }
      }
      if(fresh.isEmpty())
      {
         return (fresh);
      }
      List<QRecord> inserted = new ArrayList<>();
      for(QRecord record : SystemWrites.insert(WebhooksTables.DELIVERY, fresh, transaction).getRecords())
      {
         if(CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            LOG.info("A webhook delivery was not queued (probably a duplicate)", logPair("dedupKey", record.getValueString("dedupKey")),
               logPair("error", record.getErrors().get(0).getMessage()));
         }
         else
         {
            inserted.add(record);
         }
      }
      return (inserted);
   }
}
