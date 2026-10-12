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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.tables.QQQTableTableManager;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** class that build webhook events
 *******************************************************************************/
public class WebhookEventBuilder
{
   private static final QLogger LOG = QLogger.getLogger(WebhookEventBuilder.class);

   private List<QRecord> webhookEvents = null;



   /***************************************************************************
    **
    ***************************************************************************/
   public void addWebhookEvent(WebhookSubscription subscription, QRecord sourceRecord)
   {
      try
      {
         QTableMetaData table          = QContext.getQInstance().getTable(sourceRecord.getTableName());
         Integer        sourceRecordId = ValueUtils.getValueAsInteger(sourceRecord.getValue(table.getPrimaryKeyField()));

         Integer sourceTableId = QQQTableTableManager.getQQQTableId(QContext.getQInstance(), table.getName());

         WebhookEvent webhookEvent = new WebhookEvent();
         webhookEvent.setWebhookId(subscription.getWebhookId());
         webhookEvent.setWebhookSubscriptionId(subscription.getId());
         webhookEvent.setWebhookEventTypeName(subscription.getWebhookEventTypeName());
         webhookEvent.setEventStatusId(WebhookEventStatus.NEW.getId());
         webhookEvent.setEventSourceRecordId(sourceRecordId);
         webhookEvent.setEventSourceRecordQqqTableId(sourceTableId);

         webhookEvent.setContent(List.of(buildEventContent(sourceRecord, subscription.getWebhookEventTypeName(), subscription.getApiName(), subscription.getApiVersion())));

         QRecord eventRecord = webhookEvent.toQRecord();

         ////////////////////////////////////////////////////////////////////////////
         // copy security field values from the sourceRecord into the event record //
         ////////////////////////////////////////////////////////////////////////////
         QTableMetaData webhookTable = QContext.getQInstance().getTable(Webhook.TABLE_NAME);
         QBitConfig     qbitConfig   = webhookTable.getSourceQBitConfig();
         if(qbitConfig instanceof WebhooksQBitConfig webhooksQBitConfig)
         {
            for(QFieldMetaData field : CollectionUtils.nonNullList(webhooksQBitConfig.getSecurityFields()))
            {
               //////////////////////////////////////////////////////////////////////////////////////
               // todo - do we need to be more robust re: the field name within the source record? //
               //////////////////////////////////////////////////////////////////////////////////////
               eventRecord.setValue(field.getName(), sourceRecord.getValue(field.getName()));
            }
         }

         if(webhookEvents == null)
         {
            webhookEvents = new ArrayList<>();
         }

         webhookEvents.add(eventRecord);
      }
      catch(Exception e)
      {
         LOG.warn("Error while creating webhook event", e, logPair("webhookSubscriptionId", () -> subscription.getId()), logPair("sourceRecord", sourceRecord));
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static WebhookEventContent buildEventContent(QRecord sourceRecord, String webhookEventTypeName, String apiName, String apiVersion) throws QException
   {
      WebhookEventType webhookEventType = WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).getWebhookEventType(webhookEventTypeName);

      WebhookEventTypeCustomizerInterface webhookEventTypeCustomizerInterface;
      if(webhookEventType != null && webhookEventType.getCustomizer() != null)
      {
         webhookEventTypeCustomizerInterface = QCodeLoader.getAdHoc(WebhookEventTypeCustomizerInterface.class, webhookEventType.getCustomizer());
      }
      else
      {
         webhookEventTypeCustomizerInterface = new DefaultWebhookEventTypeCustomizer();
      }

      return webhookEventTypeCustomizerInterface.buildEventContent(sourceRecord, webhookEventTypeName, apiName, apiVersion);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public void storeWebhookEvents(QBackendTransaction transaction) throws QException
   {
      if(webhookEvents != null)
      {
         new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME)
            .withRecords(webhookEvents)
            .withTransaction(transaction));
      }
   }

}
