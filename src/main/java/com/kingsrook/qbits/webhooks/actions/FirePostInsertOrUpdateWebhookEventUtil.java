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

package com.kingsrook.qbits.webhooks.actions;


import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** utility for the fire post insert/update events
 *******************************************************************************/
public abstract class FirePostInsertOrUpdateWebhookEventUtil
{
   private static final QLogger LOG = QLogger.getLogger(FirePostInsertOrUpdateWebhookEventUtil.class);



   /***************************************************************************
    **
    ***************************************************************************/
   public static WebhookEventBuilder processSubscriptions(String tableName, WebhookEventType webhookEventType, QRecord record, WebhookEventBuilder webhookEventBuilder, QBackendTransaction transaction)
   {
      List<WebhookSubscription> webhookSubscriptions = CollectionUtils.nonNullList(WebhookSubscriptionsHelper.getSubscriptionsForEventType(webhookEventType));
      for(WebhookSubscription webhookSubscription : webhookSubscriptions)
      {
         try
         {
            if(WebhookSubscriptionsHelper.doesRecordMatchSubscription(webhookEventType, webhookSubscription, record, transaction))
            {
               LOG.info("Building webhook event", logPair("tableName", tableName), logPair("recordId", record.getValue("id")), logPair("webhookEventType", () -> webhookEventType.getName()), logPair("webhookSubscriptionId", webhookSubscription.getId()));
               webhookEventBuilder = Objects.requireNonNullElseGet(webhookEventBuilder, WebhookEventBuilder::new);
               webhookEventBuilder.addWebhookEvent(webhookSubscription, record);
            }
         }
         catch(QException e)
         {
            LOG.warn("Error processing webhookSubscription", e, logPair("tableName", tableName), logPair("recordId", record.getValue("id")), logPair("webhookEventType", () -> webhookEventType.getName()), logPair("webhookSubscriptionId", webhookSubscription.getId()));
         }
      }
      return webhookEventBuilder;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static QFieldMetaData getQFieldMetaData(WebhookEventType webhookEventType)
   {
      QFieldMetaData field = null;
      if(StringUtils.hasContent(webhookEventType.getFieldName()))
      {
         field = QContext.getQInstance().getTable(webhookEventType.getTableName()).getFields().get(webhookEventType.getFieldName());
         if(field == null)
         {
            LOG.warn("No field found for webhook event type", logPair("webhookEventType", webhookEventType), logPair("tableName", webhookEventType.getTableName()), logPair("fieldName", webhookEventType.getFieldName()));
         }
      }
      return field;
   }



   /***************************************************************************
    *
    ***************************************************************************/
   public static Serializable getValue(QRecord record, QFieldMetaData field)
   {
      if(field == null)
      {
         return (null);
      }

      return ValueUtils.getValueAsFieldType(field.getType(), record.getValue(field.getName()));
   }


   /***************************************************************************
    **
    ***************************************************************************/
   public static boolean doesNewValueMatchEventValue(WebhookEventType webhookEventType, QFieldMetaData field, Serializable newValue)
   {
      Serializable eventValue = webhookEventType.getValue();
      if(eventValue instanceof Collection<?> collection)
      {
         for(Object o : collection)
         {
            Serializable eventSubValueAsFieldType = ValueUtils.getValueAsFieldType(field.getType(), o);
            if(Objects.equals(eventSubValueAsFieldType, newValue))
            {
               return true;
            }
         }
      }
      else
      {
         Serializable eventValueAsFieldType = ValueUtils.getValueAsFieldType(field.getType(), eventValue);
         if(Objects.equals(eventValueAsFieldType, newValue))
         {
            return true;
         }
      }
      return false;
   }
}
