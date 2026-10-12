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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DetailLevel;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhookPayloadCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Builds the JSON body of a delivery.
 **
 ** <pre>
 ** {
 **   "id":            "&lt;eventId, same as the webhook-id header&gt;",
 **   "type":          "cms_blog_post.updated",
 **   "timestamp":     "2026-10-12T20:15:00Z",
 **   "source":        "qqq://kof22/table/cms_blog_post",
 **   "sourceEventId": "&lt;the same for every endpoint that gets this event&gt;",
 **   "data": {
 **     "table": "cms_blog_post", "primaryKey": "42",
 **     "record": { ... }, "oldRecord": { ... }, "changedFields": [ ... ]
 **   }
 ** }
 ** </pre>
 **
 ** <p>Process and custom events put their own object in {@code data}. The
 ** subscription shapes the record: its API name and version render it through
 ** the QQQ API (stable, versioned field names) when the API module is present;
 ** the detail level, include and exclude lists then trim it; finally the event
 ** type's code customizer may change {@code data}.</p>
 *******************************************************************************/
public final class PayloadBuilder
{
   private static final boolean API_PRESENT = classPresent("com.kingsrook.qqq.api.actions.QRecordApiAdapter");



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private PayloadBuilder()
   {
   }



   /*******************************************************************************
    ** The body for one delivery.
    **
    ** @param subscription the subscription row, or null for a directed delivery
    **                     (channel or test), which gets the full data
    *******************************************************************************/
   public static String build(WebhookEvent event, WebhookEventType type, QRecord subscription, String eventId) throws QException
   {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("id", eventId);
      envelope.put("type", event.getEventTypeName());
      envelope.put("timestamp", event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
      envelope.put("source", event.getSourceUri());
      envelope.put("sourceEventId", event.getSourceEventId());

      Map<String, Object> data = event.subjectRecord() != null && StringUtils.hasContent(event.getSourceTable())
         ? recordData(event, subscription)
         : shape(new LinkedHashMap<>(event.getData()), subscription, null);

      if(type != null && type.getCustomizer() != null)
      {
         WebhookPayloadCustomizerInterface customizer = QCodeLoader.getAdHoc(WebhookPayloadCustomizerInterface.class, type.getCustomizer());
         customizer.customizeData(event, subscription, data);
      }
      envelope.put("data", data);
      return (JsonUtils.toJsonCustomized(envelope, builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS)));
   }



   /*******************************************************************************
    ** The data object for a table event.
    *******************************************************************************/
   private static Map<String, Object> recordData(WebhookEvent event, QRecord subscription) throws QException
   {
      QTableMetaData table = QContext.getQInstance().getTable(event.getSourceTable());
      String         pk    = table == null ? "id" : table.getPrimaryKeyField();

      Map<String, Object> data = new LinkedHashMap<>();
      data.put("table", event.getSourceTable());
      data.put("primaryKey", event.getSourceRecordId());

      DetailLevel level = detailLevel(subscription);
      List<String> changed = changedFields(event);
      if(event.getRecord() != null && event.getOldRecord() != null)
      {
         data.put("changedFields", changed);
      }

      if(event.getRecord() != null)
      {
         data.put("record", shape(values(event.getRecord(), event.getOldRecord(), table, subscription), subscription, level == DetailLevel.CHANGED_FIELDS ? changedPlus(changed, pk) : null, pk));
      }
      if(event.getOldRecord() != null && (event.getRecord() == null || level == DetailLevel.FULL))
      {
         data.put("oldRecord", shape(values(event.getOldRecord(), null, table, subscription), subscription, null, pk));
      }
      return (data);
   }



   /*******************************************************************************
    ** The record's values: the old values overlaid with the new (an update
    ** carries only the changed fields), rendered through the API if asked.
    *******************************************************************************/
   private static Map<String, Object> values(QRecord record, QRecord base, QTableMetaData table, QRecord subscription) throws QException
   {
      QRecord merged = new QRecord();
      if(base != null && base.getValues() != null)
      {
         merged.getValues().putAll(base.getValues());
      }
      if(record.getValues() != null)
      {
         merged.getValues().putAll(record.getValues());
      }
      String apiName    = subscription == null ? null : subscription.getValueString("apiName");
      String apiVersion = subscription == null ? null : subscription.getValueString("apiVersion");
      if(API_PRESENT && table != null && StringUtils.hasContent(apiName) && StringUtils.hasContent(apiVersion))
      {
         merged.setTableName(table.getName());
         Map<String, Serializable> api = com.kingsrook.qqq.api.actions.QRecordApiAdapter.qRecordToApiMap(merged, table.getName(), apiName, apiVersion);
         return (api == null ? new LinkedHashMap<>() : new LinkedHashMap<>(api));
      }
      return (new LinkedHashMap<>(merged.getValues()));
   }



   /*******************************************************************************
    ** Apply the detail level and the include and exclude lists.
    *******************************************************************************/
   static Map<String, Object> shape(Map<String, Object> values, QRecord subscription, Set<String> only, String... alwaysKeep)
   {
      DetailLevel  level   = detailLevel(subscription);
      Set<String>  include = fieldList(subscription, "includeFields");
      Set<String>  exclude = fieldList(subscription, "excludeFields");
      List<String> keep    = alwaysKeep == null ? List.of() : Arrays.stream(alwaysKeep).filter(Objects::nonNull).toList();

      Map<String, Object> shaped = new LinkedHashMap<>();
      for(Map.Entry<String, Object> entry : values.entrySet())
      {
         String  key      = entry.getKey();
         boolean required = keep.contains(key);
         if(!required)
         {
            if(level == DetailLevel.KEYS_ONLY && !keep.isEmpty())
            {
               continue;
            }
            if(only != null && !only.contains(key))
            {
               continue;
            }
            if(!include.isEmpty() && !include.contains(key))
            {
               continue;
            }
         }
         if(exclude.contains(key))
         {
            continue;
         }
         shaped.put(key, entry.getValue());
      }
      return (shaped);
   }



   /*******************************************************************************
    ** Fields whose value differs between the old record and the update.
    *******************************************************************************/
   public static List<String> changedFields(WebhookEvent event)
   {
      List<String> changed = new ArrayList<>();
      if(event.getRecord() == null || event.getOldRecord() == null || event.getRecord().getValues() == null)
      {
         return (changed);
      }
      for(Map.Entry<String, Serializable> entry : event.getRecord().getValues().entrySet())
      {
         Serializable old = event.getOldRecord().getValue(entry.getKey());
         if(!Objects.equals(ValueUtils.getValueAsString(old), ValueUtils.getValueAsString(entry.getValue())))
         {
            changed.add(entry.getKey());
         }
      }
      return (changed);
   }



   /*******************************************************************************
    ** The changed fields plus the primary key.
    *******************************************************************************/
   private static Set<String> changedPlus(List<String> changed, String pk)
   {
      Set<String> set = new LinkedHashSet<>(changed);
      set.add(pk);
      return (set);
   }



   /*******************************************************************************
    ** The subscription's detail level (FULL when none).
    *******************************************************************************/
   private static DetailLevel detailLevel(QRecord subscription)
   {
      String value = subscription == null ? null : subscription.getValueString("detailLevel");
      try
      {
         return (value == null ? DetailLevel.FULL : DetailLevel.valueOf(value));
      }
      catch(IllegalArgumentException e)
      {
         return (DetailLevel.FULL);
      }
   }



   /*******************************************************************************
    ** A comma (or whitespace) separated field list.
    *******************************************************************************/
   static Set<String> fieldList(QRecord subscription, String fieldName)
   {
      Set<String> set   = new LinkedHashSet<>();
      String      value = subscription == null ? null : subscription.getValueString(fieldName);
      if(StringUtils.hasContent(value))
      {
         for(String part : value.split("[,\\s]+"))
         {
            if(!part.isBlank())
            {
               set.add(part.trim());
            }
         }
      }
      return (set);
   }



   /*******************************************************************************
    ** True when a class can be loaded.
    *******************************************************************************/
   private static boolean classPresent(String name)
   {
      try
      {
         Class.forName(name, false, PayloadBuilder.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }
}
