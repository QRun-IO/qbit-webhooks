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

package com.kingsrook.qbits.webhooks;


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.events.DeliveryOutbox;
import com.kingsrook.qbits.webhooks.events.WebhookEvent;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The qbit's public API for code: fire custom (domain) events, and deliver
 ** to a named endpoint as a channel (the Alerts qbit's webhook channel uses
 ** this, through reflection, when this qbit is installed).
 **
 ** <pre>
 ** // a domain event: every subscription to "post.published" gets it
 ** Webhooks.fire("post.published", Map.of("slug", slug, "title", title));
 **
 ** // in the same transaction as the write that caused it
 ** Webhooks.fireForRecord("post.published", "cms_blog_post", record, transaction);
 **
 ** // a channel delivery to one endpoint, queued once per idempotency key
 ** Integer deliveryId = Webhooks.deliver("ops-pager", "alerts.alert.raised", body, "alert-123-raised");
 ** </pre>
 *******************************************************************************/
public final class Webhooks
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private Webhooks()
   {
   }



   /*******************************************************************************
    ** Register a custom event type (call while building the instance).
    *******************************************************************************/
   public static void registerEventType(WebhookEventType eventType)
   {
      WebhooksRegistry.of(QContext.getQInstance()).register(eventType);
   }



   /*******************************************************************************
    ** Fire a custom event. Returns the number of deliveries queued.
    *******************************************************************************/
   public static int fire(String eventTypeName, Map<String, ?> data) throws QException
   {
      return (fire(eventTypeName, data, null));
   }



   /*******************************************************************************
    ** Fire a custom event inside a transaction (on the qbit's backend).
    *******************************************************************************/
   public static int fire(String eventTypeName, Map<String, ?> data, QBackendTransaction transaction) throws QException
   {
      requireType(eventTypeName);
      WebhooksQBitConfig config = WebhooksRuntime.config();
      WebhookEvent event = new WebhookEvent()
         .withEventTypeName(eventTypeName)
         .withSource(EventSource.CUSTOM)
         .withSourceUri("qqq://" + config.getSourceName() + "/event/" + eventTypeName)
         .withOccurredAt(WebhooksRuntime.clock().instant())
         .withData(data);
      return (DeliveryOutbox.enqueue(List.of(event), transaction).size());
   }



   /*******************************************************************************
    ** Fire a custom event about a record (its subscriptions' filters, record
    ** security and payload shaping apply to the record).
    *******************************************************************************/
   public static int fireForRecord(String eventTypeName, String tableName, QRecord record, QBackendTransaction transaction) throws QException
   {
      requireType(eventTypeName);
      WebhooksQBitConfig config = WebhooksRuntime.config();
      QTableMetaData     table  = QContext.getQInstance().getTable(tableName);
      Object             key    = table == null ? null : record.getValue(table.getPrimaryKeyField());
      WebhookEvent event = new WebhookEvent()
         .withEventTypeName(eventTypeName)
         .withSource(EventSource.CUSTOM)
         .withSourceTable(tableName)
         .withSourceRecordId(key == null ? null : ValueUtils.getValueAsString(key))
         .withSourceUri("qqq://" + config.getSourceName() + "/table/" + tableName)
         .withOccurredAt(WebhooksRuntime.clock().instant())
         .withRecord(record);
      return (DeliveryOutbox.enqueue(List.of(event), transaction).size());
   }



   /*******************************************************************************
    ** Deliver a payload to one endpoint (by name), with no subscription: the
    ** channel API for other qbits. Signing, retries, dead letters, health and
    ** the delivery log all apply. Calling again with the same idempotency key
    ** returns the existing delivery instead of queuing another.
    **
    ** @return the webhooks_delivery id
    *******************************************************************************/
   public static Integer deliver(String endpointName, String eventTypeName, Map<String, ?> data, String idempotencyKey) throws QException
   {
      if(!WebhooksRuntime.isInstalled())
      {
         throw (new QException("The webhooks qbit is not installed"));
      }
      List<QRecord> endpoints = SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter(new QFilterCriteria("name", QCriteriaOperator.EQUALS, endpointName)));
      if(endpoints.isEmpty())
      {
         throw (new QException("No webhook endpoint is named " + endpointName));
      }
      QRecord endpoint = endpoints.get(0);
      if(ActiveStatus.DISABLED.name().equals(endpoint.getValueString("activeStatus")))
      {
         throw (new QException("The webhook endpoint " + endpointName + " is disabled"));
      }
      WebhooksQBitConfig config = WebhooksRuntime.config();
      WebhookEvent event = new WebhookEvent()
         .withEventTypeName(eventTypeName)
         .withSource(EventSource.CHANNEL)
         .withSourceUri("qqq://" + config.getSourceName() + "/channel/" + eventTypeName)
         .withOccurredAt(WebhooksRuntime.clock().instant())
         .withData(data);
      return (DeliveryOutbox.enqueueDirect(event, endpoint.getValueInteger("id"), idempotencyKey).getValueInteger("id"));
   }



   /*******************************************************************************
    ** Fail on an unregistered type (a typo would otherwise fire into nothing).
    *******************************************************************************/
   private static void requireType(String eventTypeName) throws QException
   {
      if(!WebhooksRuntime.isInstalled())
      {
         throw (new QException("The webhooks qbit is not installed"));
      }
      if(WebhooksRegistry.of(QContext.getQInstance()).get(eventTypeName) == null)
      {
         throw (new QException("Unknown webhook event type: " + eventTypeName + " (register it with Webhooks.registerEventType)"));
      }
   }
}
