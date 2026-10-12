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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.tables.helpers.ValidateRecordSecurityLockHelper;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.utils.BackendQueryFilterUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Decides which subscriptions get an event.
 **
 ** <p>A subscription matches when it and its endpoint are not disabled, its
 ** event type is the event's type, and:</p>
 ** <ol>
 **   <li>for a type with a {@code fieldName}: an update changed that field
 **       (to the type's {@code value}, when set);</li>
 **   <li>its record filter (a QQQ query filter, as JSON) matches the record
 **       (the old record for a delete);</li>
 **   <li>record security: when the qbit is configured with security fields and
 **       locks, the subscription's own security values must allow reading the
 **       record, so a subscription never receives records its owner could not
 **       see.</li>
 ** </ol>
 *******************************************************************************/
public final class SubscriptionMatcher
{
   private static final QLogger LOG = QLogger.getLogger(SubscriptionMatcher.class);



   /*******************************************************************************
    ** A subscription with its endpoint.
    *******************************************************************************/
   public record Target(QRecord subscription, QRecord endpoint)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private SubscriptionMatcher()
   {
   }



   /*******************************************************************************
    ** The live subscriptions (with endpoints) for some event types, by type.
    *******************************************************************************/
   public static Map<String, List<Target>> targetsByType(Collection<String> typeNames) throws QException
   {
      Map<String, List<Target>> byType = new HashMap<>();
      if(typeNames.isEmpty())
      {
         return (byType);
      }
      List<QRecord> subscriptions = SystemWrites.query(WebhooksTables.SUBSCRIPTION, new QQueryFilter()
         .withCriteria(new QFilterCriteria("eventTypeName", QCriteriaOperator.IN, new ArrayList<>(typeNames)))
         .withCriteria(new QFilterCriteria("activeStatus", QCriteriaOperator.EQUALS, ActiveStatus.ACTIVE.name())));
      if(subscriptions.isEmpty())
      {
         return (byType);
      }
      List<Serializable> endpointIds = subscriptions.stream().map(s -> s.getValue("endpointId")).filter(Objects::nonNull).distinct().map(v -> (Serializable) v).toList();
      Map<Integer, QRecord> endpoints = new HashMap<>();
      for(QRecord endpoint : SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter()
         .withCriteria(new QFilterCriteria("id", QCriteriaOperator.IN, endpointIds))
         .withCriteria(new QFilterCriteria("activeStatus", QCriteriaOperator.NOT_EQUALS, ActiveStatus.DISABLED.name()))))
      {
         endpoints.put(endpoint.getValueInteger("id"), endpoint);
      }
      for(QRecord subscription : subscriptions)
      {
         QRecord endpoint = endpoints.get(subscription.getValueInteger("endpointId"));
         if(endpoint != null)
         {
            byType.computeIfAbsent(subscription.getValueString("eventTypeName"), k -> new ArrayList<>()).add(new Target(subscription, endpoint));
         }
      }
      return (byType);
   }



   /*******************************************************************************
    ** True when the event should go to the subscription.
    *******************************************************************************/
   public static boolean matches(WebhooksQBitConfig config, WebhookEventType type, WebhookEvent event, QRecord subscription)
   {
      try
      {
         if(type != null && StringUtils.hasContent(type.getFieldName()) && !fieldConditionMet(type, event))
         {
            return (false);
         }
         QRecord subject = event.subjectRecord();
         String filterJson = subscription.getValueString("filterJson");
         if(StringUtils.hasContent(filterJson) && subject != null)
         {
            QRecord full = mergedSubject(event);
            if(!BackendQueryFilterUtils.doesRecordMatch(parseFilter(filterJson), full))
            {
               return (false);
            }
         }
         return (subject == null || recordSecurityAllows(config, event, subscription));
      }
      catch(Exception e)
      {
         LOG.warn("Could not evaluate a webhook subscription; the event is not queued for it", e, logPair("subscriptionId", subscription.getValue("id")),
            logPair("eventType", event.getEventTypeName()));
         return (false);
      }
   }



   /*******************************************************************************
    ** Parse a subscription filter.
    **
    ** @throws QException when the JSON is not a query filter
    *******************************************************************************/
   public static QQueryFilter parseFilter(String filterJson) throws QException
   {
      try
      {
         return (JsonUtils.toObject(filterJson, QQueryFilter.class));
      }
      catch(Exception e)
      {
         throw (new QException("The record filter is not a valid QQQ query filter (JSON): " + e.getMessage()));
      }
   }



   /*******************************************************************************
    ** The record to test: old values overlaid with the new ones.
    *******************************************************************************/
   private static QRecord mergedSubject(WebhookEvent event)
   {
      QRecord merged = new QRecord();
      if(event.getOldRecord() != null && event.getOldRecord().getValues() != null)
      {
         merged.getValues().putAll(event.getOldRecord().getValues());
      }
      if(event.getRecord() != null && event.getRecord().getValues() != null)
      {
         merged.getValues().putAll(event.getRecord().getValues());
      }
      merged.setTableName(event.getSourceTable());
      return (merged);
   }



   /*******************************************************************************
    ** The type's field (and value) condition.
    *******************************************************************************/
   static boolean fieldConditionMet(WebhookEventType type, WebhookEvent event)
   {
      String field = type.getFieldName();
      QRecord record = event.getRecord();
      if(record == null || record.getValues() == null || !record.getValues().containsKey(field))
      {
         return (false);
      }
      Serializable newValue = record.getValue(field);
      if(event.getOldRecord() != null && type.getKind() != WebhookEventKind.TABLE_INSERT)
      {
         Serializable oldValue = event.getOldRecord().getValue(field);
         if(Objects.equals(ValueUtils.getValueAsString(oldValue), ValueUtils.getValueAsString(newValue)))
         {
            return (false);
         }
      }
      if(type.getValue() == null)
      {
         return (true);
      }
      String newText = ValueUtils.getValueAsString(newValue);
      if(type.getValue() instanceof Collection<?> values)
      {
         return (values.stream().anyMatch(v -> Objects.equals(ValueUtils.getValueAsString((Serializable) v), newText)));
      }
      return (Objects.equals(ValueUtils.getValueAsString(type.getValue()), newText));
   }



   /*******************************************************************************
    ** Record security: a pseudo-session with the subscription's security
    ** values (plus all-access keys for lock types the qbit does not manage)
    ** must be allowed to read the record.
    *******************************************************************************/
   static boolean recordSecurityAllows(WebhooksQBitConfig config, WebhookEvent event, QRecord subscription) throws QException
   {
      QTableMetaData table = event.getSourceTable() == null ? null : QContext.getQInstance().getTable(event.getSourceTable());
      if(table == null || table.getRecordSecurityLocks() == null || table.getRecordSecurityLocks().isEmpty() || config.getRecordSecurityLocks().isEmpty())
      {
         return (true);
      }
      QSession pseudo = new QSession();
      for(RecordSecurityLock lock : config.getRecordSecurityLocks())
      {
         Serializable value = subscription.getValue(lock.getFieldName());
         if(value != null)
         {
            pseudo.withSecurityKeyValue(lock.getSecurityKeyType(), value);
         }
      }
      if(pseudo.getSecurityKeyValues() == null || pseudo.getSecurityKeyValues().isEmpty())
      {
         return (true);
      }
      List<String> managedTypes = config.getRecordSecurityLocks().stream().map(RecordSecurityLock::getSecurityKeyType).toList();
      for(QSecurityKeyType keyType : QContext.getQInstance().getSecurityKeyTypes().values())
      {
         if(!managedTypes.contains(keyType.getName()) && StringUtils.hasContent(keyType.getAllAccessKeyName()))
         {
            pseudo.withSecurityKeyValue(keyType.getAllAccessKeyName(), true);
         }
      }
      return (ValidateRecordSecurityLockHelper.allowedToReadRecord(table, mergedSubject(event), pseudo, null));
   }
}
