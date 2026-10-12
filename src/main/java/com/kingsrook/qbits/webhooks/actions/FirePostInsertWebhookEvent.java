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
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.model.WebhooksActionFlags;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** post-insert table customizer to fire webhook events
 *******************************************************************************/
public class FirePostInsertWebhookEvent implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(FirePostInsertWebhookEvent.class);



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public List<QRecord> postInsert(InsertInput insertInput, List<QRecord> records) throws QException
   {
      if(insertInput.hasFlag(WebhooksActionFlags.OMIT_WEBHOOKS))
      {
         LOG.debug("Requested to omit webhooks after inert; returning early.", logPair("tableName", insertInput.getTableName()));
         return (records);
      }

      List<WebhookEventType> insertWebhookEventTypes = WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(WebhookEventCategory.Kind.INSERT, insertInput.getTableName());
      List<WebhookEventType> storeWebhookEventTypes  = WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(WebhookEventCategory.Kind.STORE, insertInput.getTableName());
      if(CollectionUtils.nullSafeIsEmpty(insertWebhookEventTypes) && CollectionUtils.nullSafeIsEmpty(storeWebhookEventTypes))
      {
         return (records);
      }

      List<WebhookEventType> webhookEventTypes = new ArrayList<>();
      CollectionUtils.addAllIfNotNull(webhookEventTypes, insertWebhookEventTypes);
      CollectionUtils.addAllIfNotNull(webhookEventTypes, storeWebhookEventTypes);

      WebhookEventBuilder webhookEventBuilder = null;
      for(WebhookEventType webhookEventType : webhookEventTypes)
      {
         QFieldMetaData field = FirePostInsertOrUpdateWebhookEventUtil.getQFieldMetaData(webhookEventType);
         for(QRecord record : records)
         {
            if(CollectionUtils.nullSafeHasContents(record.getErrors()))
            {
               continue;
            }

            if(record.getTableName() == null)
            {
               record.setTableName(insertInput.getTableName());
            }

            if(doesRecordMatchWebhookEventType(record, webhookEventType, field))
            {
               webhookEventBuilder = FirePostInsertOrUpdateWebhookEventUtil.processSubscriptions(insertInput.getTableName(), webhookEventType, record, webhookEventBuilder, insertInput.getTransaction());
            }
         }
      }

      if(webhookEventBuilder != null)
      {
         webhookEventBuilder.storeWebhookEvents(insertInput.getTransaction());
      }

      return records;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   boolean doesRecordMatchWebhookEventType(QRecord record, WebhookEventType webhookEventType, QFieldMetaData field)
   {
      switch(webhookEventType.getCategory())
      {
         case INSERT, STORE ->
         {
            return (true);
         }
         case INSERT_WITH_FIELD, STORE_WITH_FIELD ->
         {
            Serializable value = FirePostInsertOrUpdateWebhookEventUtil.getValue(record, field);
            if(value != null && !"".equals(ValueUtils.getValueAsString(value)))
            {
               return (true);
            }
         }
         case INSERT_WITH_VALUE, STORE_WITH_VALUE ->
         {
            Serializable value = FirePostInsertOrUpdateWebhookEventUtil.getValue(record, field);

            if(FirePostInsertOrUpdateWebhookEventUtil.doesNewValueMatchEventValue(webhookEventType, field, value))
            {
               return (true);
            }
         }
         default ->
         {
            return (false);
         }
      }

      return (false);
   }

}
