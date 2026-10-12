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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeEvent;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeListenerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The in-process table source: QQQ's record change listener hears every
 ** insert, update and delete (deletes too, unlike the old post-insert and
 ** post-update customizers), and queues deliveries for subscribed types.
 **
 ** <p>When the write has a transaction on the qbit's own backend, deliveries
 ** are inserted in it (they commit or roll back with the write). With a
 ** transaction on another backend they are inserted after it commits. With no
 ** transaction they are inserted at once. A failure here is logged and never
 ** fails the caller's write.</p>
 *******************************************************************************/
public class WebhooksRecordChangeListener implements RecordChangeListenerInterface
{
   private static final QLogger LOG = QLogger.getLogger(WebhooksRecordChangeListener.class);



   /*******************************************************************************
    ** True for a table with registered event types of the change's kind.
    *******************************************************************************/
   @Override
   public boolean appliesTo(String tableName, RecordChangeType type)
   {
      if(tableName == null || WebhooksTables.ALL.contains(tableName) || !WebhooksRuntime.isInstalled())
      {
         return (false);
      }
      return (!WebhooksRegistry.of(QContext.getQInstance()).forTable(tableName, kinds(type)).isEmpty());
   }



   /*******************************************************************************
    ** Queue the deliveries.
    *******************************************************************************/
   @Override
   public void onRecordsChanged(RecordChangeEvent change) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      if(config == null || change == null || change.getRecords() == null || change.getRecords().isEmpty())
      {
         return;
      }
      List<WebhookEventType> types = WebhooksRegistry.of(QContext.getQInstance()).forTable(change.getTableName(), kinds(change.getType()));
      List<WebhookEvent>     events = events(config, change, types);
      if(events.isEmpty())
      {
         return;
      }

      QBackendTransaction transaction = change.getTransaction();
      QTableMetaData      table       = QContext.getQInstance().getTable(change.getTableName());
      boolean             sameBackend = table != null && config.getBackendName().equals(table.getBackendName());
      if(transaction != null && !sameBackend)
      {
         transaction.addAfterCommitCallback(() -> enqueue(events, null, change.getTableName()));
      }
      else
      {
         enqueue(events, sameBackend ? transaction : null, change.getTableName());
      }
   }



   /*******************************************************************************
    ** Enqueue, logging any failure.
    *******************************************************************************/
   private static void enqueue(List<WebhookEvent> events, QBackendTransaction transaction, String tableName)
   {
      try
      {
         DeliveryOutbox.enqueue(events, transaction);
      }
      catch(Exception e)
      {
         LOG.warn("Could not queue webhook deliveries for a record change", e, logPair("table", tableName), logPair("events", events.size()));
      }
   }



   /*******************************************************************************
    ** One event per record per type.
    *******************************************************************************/
   static List<WebhookEvent> events(WebhooksQBitConfig config, RecordChangeEvent change, List<WebhookEventType> types)
   {
      List<WebhookEvent> events = new ArrayList<>();
      QTableMetaData     table  = QContext.getQInstance().getTable(change.getTableName());
      String             pk     = table == null ? "id" : table.getPrimaryKeyField();
      List<QRecord>      olds   = change.getOldRecords();
      for(int i = 0; i < change.getRecords().size(); i++)
      {
         QRecord record = change.getRecords().get(i);
         QRecord old    = olds != null && i < olds.size() ? olds.get(i) : null;
         if(record == null)
         {
            continue;
         }
         boolean isDelete = change.getType() == RecordChangeType.DELETE;
         QRecord subject  = isDelete ? (old != null ? old : record) : record;
         Object  key      = subject.getValue(pk);
         if(key == null && old != null)
         {
            key = old.getValue(pk);
         }
         for(WebhookEventType type : types)
         {
            events.add(new WebhookEvent()
               .withEventTypeName(type.getName())
               .withSource(EventSource.TABLE)
               .withSourceTable(change.getTableName())
               .withSourceRecordId(key == null ? null : ValueUtils.getValueAsString(key))
               .withSourceUri("qqq://" + config.getSourceName() + "/table/" + change.getTableName())
               .withOccurredAt(WebhooksRuntime.clock().instant())
               .withRecord(isDelete ? null : record)
               .withOldRecord(isDelete ? subject : old));
         }
      }
      return (events);
   }



   /*******************************************************************************
    ** The event kinds a change type fires.
    *******************************************************************************/
   static List<WebhookEventKind> kinds(RecordChangeType type)
   {
      if(type == null)
      {
         return (List.of());
      }
      return (switch(type)
      {
         case INSERT -> List.of(WebhookEventKind.TABLE_INSERT, WebhookEventKind.TABLE_STORE);
         case UPDATE -> List.of(WebhookEventKind.TABLE_UPDATE, WebhookEventKind.TABLE_STORE);
         case DELETE -> List.of(WebhookEventKind.TABLE_DELETE);
      });
   }
}
