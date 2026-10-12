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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.model.WebhooksActionFlags;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.actions.customizers.OldRecordHelper;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** post-update table customizer to fire webhook events
 *******************************************************************************/
public class FirePostUpdateWebhookEvent implements TableCustomizerInterface
{
   private static final QLogger LOG = QLogger.getLogger(FirePostUpdateWebhookEvent.class);



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public List<QRecord> postUpdate(UpdateInput updateInput, List<QRecord> records, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(updateInput.hasFlag(WebhooksActionFlags.OMIT_WEBHOOKS))
      {
         LOG.debug("Requested to omit webhooks after update; returning early.", logPair("tableName", updateInput.getTableName()));
         return (records);
      }

      List<WebhookEventType> updateWebhookEventTypes = WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(WebhookEventCategory.Kind.UPDATE, updateInput.getTableName());
      List<WebhookEventType> storeWebhookEventTypes  = WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(WebhookEventCategory.Kind.STORE, updateInput.getTableName());
      if(CollectionUtils.nullSafeIsEmpty(updateWebhookEventTypes) && CollectionUtils.nullSafeIsEmpty(storeWebhookEventTypes))
      {
         return (records);
      }

      List<WebhookEventType> webhookEventTypes = new ArrayList<>();
      CollectionUtils.addAllIfNotNull(webhookEventTypes, updateWebhookEventTypes);
      CollectionUtils.addAllIfNotNull(webhookEventTypes, storeWebhookEventTypes);

      OldRecordHelper oldRecordHelper = new OldRecordHelper(updateInput.getTableName(), oldRecordList);

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
               record.setTableName(updateInput.getTableName());
            }

            Optional<QRecord> oldRecord = oldRecordHelper.getOldRecord(record);
            if(doesRecordMatchWebhookEventType(record, oldRecord, webhookEventType, field))
            {
               /////////////////////////////////////////////////////////////////////////////////////////
               // in case the input record is "sparse" (e.g., missing some field values), and we have //
               // the old record, then make a hybrid of the new + old for processing.                 //
               /////////////////////////////////////////////////////////////////////////////////////////
               QRecord recordToProcess = record;
               if(oldRecord.isPresent())
               {
                  recordToProcess = new QRecord(record);
                  for(Map.Entry<String, Serializable> entry : oldRecord.get().getValues().entrySet())
                  {
                     if(!recordToProcess.getValues().containsKey(entry.getKey()))
                     {
                        recordToProcess.getValues().put(entry.getKey(), entry.getValue());
                     }
                  }
               }

               webhookEventBuilder = FirePostInsertOrUpdateWebhookEventUtil.processSubscriptions(updateInput.getTableName(), webhookEventType, recordToProcess, webhookEventBuilder, updateInput.getTransaction());
            }
         }
      }

      if(webhookEventBuilder != null)
      {
         webhookEventBuilder.storeWebhookEvents(updateInput.getTransaction());
      }

      return records;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   boolean doesRecordMatchWebhookEventType(QRecord record, Optional<QRecord> optionalOldRecord, WebhookEventType webhookEventType, QFieldMetaData field)
   {
      switch(webhookEventType.getCategory())
      {
         case UPDATE, STORE ->
         {
            return (true);
         }
         case UPDATE_WITH_FIELD, STORE_WITH_FIELD ->
         {
            if(optionalOldRecord.isEmpty())
            {
               return (false);
            }

            Serializable newValue = FirePostInsertOrUpdateWebhookEventUtil.getValue(record, field);
            Serializable oldValue = FirePostInsertOrUpdateWebhookEventUtil.getValue(optionalOldRecord.get(), field);
            if(!Objects.equals(newValue, oldValue))
            {
               return (true);
            }
         }
         case UPDATE_WITH_VALUE, STORE_WITH_VALUE ->
         {
            if(optionalOldRecord.isEmpty())
            {
               return (false);
            }

            Serializable newValue = FirePostInsertOrUpdateWebhookEventUtil.getValue(record, field);
            Serializable oldValue = FirePostInsertOrUpdateWebhookEventUtil.getValue(optionalOldRecord.get(), field);
            if(!Objects.equals(newValue, oldValue) && FirePostInsertOrUpdateWebhookEventUtil.doesNewValueMatchEventValue(webhookEventType, field, newValue))
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
