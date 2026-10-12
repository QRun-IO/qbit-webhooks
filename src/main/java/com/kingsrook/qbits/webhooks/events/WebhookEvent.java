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


import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Something that happened, before it is queued for the endpoints that
 ** subscribe to it.
 **
 ** <p>{@code sourceEventId} identifies the happening across every endpoint it
 ** goes to (the ESB CloudEvent id for ESB events, else a new UUID). Each
 ** delivery also gets its own {@code eventId}, sent as {@code webhook-id}.</p>
 *******************************************************************************/
public class WebhookEvent
{
   private String              eventTypeName;
   private EventSource         source        = EventSource.CUSTOM;
   private String              sourceTable;
   private String              sourceRecordId;
   private String              sourceEventId = UUID.randomUUID().toString();
   private String              sourceUri;
   private Instant             occurredAt;
   private QRecord             record;
   private QRecord             oldRecord;
   private Map<String, Object> data          = new LinkedHashMap<>();



   /*******************************************************************************
    ** Fluent setter for eventTypeName.
    *******************************************************************************/
   public WebhookEvent withEventTypeName(String eventTypeName)
   {
      this.eventTypeName = eventTypeName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for source.
    *******************************************************************************/
   public WebhookEvent withSource(EventSource source)
   {
      this.source = source;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sourceTable.
    *******************************************************************************/
   public WebhookEvent withSourceTable(String sourceTable)
   {
      this.sourceTable = sourceTable;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sourceRecordId.
    *******************************************************************************/
   public WebhookEvent withSourceRecordId(String sourceRecordId)
   {
      this.sourceRecordId = sourceRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sourceEventId.
    *******************************************************************************/
   public WebhookEvent withSourceEventId(String sourceEventId)
   {
      this.sourceEventId = sourceEventId;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sourceUri (e.g. qqq://kof22/table/cms_blog_post).
    *******************************************************************************/
   public WebhookEvent withSourceUri(String sourceUri)
   {
      this.sourceUri = sourceUri;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for occurredAt.
    *******************************************************************************/
   public WebhookEvent withOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for record (the new values; for a delete, null).
    *******************************************************************************/
   public WebhookEvent withRecord(QRecord record)
   {
      this.record = record;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for oldRecord (update and delete).
    *******************************************************************************/
   public WebhookEvent withOldRecord(QRecord oldRecord)
   {
      this.oldRecord = oldRecord;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for data (process and custom events).
    *******************************************************************************/
   public WebhookEvent withData(Map<String, ?> data)
   {
      this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
      return (this);
   }



   /*******************************************************************************
    ** The record the event is about: the new one, or the old one for a delete.
    *******************************************************************************/
   public QRecord subjectRecord()
   {
      return (record != null ? record : oldRecord);
   }



   /*******************************************************************************
    ** Getter for eventTypeName.
    *******************************************************************************/
   public String getEventTypeName()
   {
      return (eventTypeName);
   }



   /*******************************************************************************
    ** Getter for source.
    *******************************************************************************/
   public EventSource getSource()
   {
      return (source);
   }



   /*******************************************************************************
    ** Getter for sourceTable.
    *******************************************************************************/
   public String getSourceTable()
   {
      return (sourceTable);
   }



   /*******************************************************************************
    ** Getter for sourceRecordId.
    *******************************************************************************/
   public String getSourceRecordId()
   {
      return (sourceRecordId);
   }



   /*******************************************************************************
    ** Getter for sourceEventId.
    *******************************************************************************/
   public String getSourceEventId()
   {
      return (sourceEventId);
   }



   /*******************************************************************************
    ** Getter for sourceUri.
    *******************************************************************************/
   public String getSourceUri()
   {
      return (sourceUri);
   }



   /*******************************************************************************
    ** Getter for occurredAt.
    *******************************************************************************/
   public Instant getOccurredAt()
   {
      return (occurredAt);
   }



   /*******************************************************************************
    ** Getter for record.
    *******************************************************************************/
   public QRecord getRecord()
   {
      return (record);
   }



   /*******************************************************************************
    ** Getter for oldRecord.
    *******************************************************************************/
   public QRecord getOldRecord()
   {
      return (oldRecord);
   }



   /*******************************************************************************
    ** Getter for data.
    *******************************************************************************/
   public Map<String, Object> getData()
   {
      return (data);
   }
}
