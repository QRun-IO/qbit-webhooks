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


import java.util.List;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 * fire an ad-hoc webhook event - e.g., one not tied to a basic table insert/
 * update/delete
 *******************************************************************************/
public class FireAdHocWebhookEvent
{
   private static final QLogger LOG = QLogger.getLogger(FireAdHocWebhookEvent.class);



   /***************************************************************************
    * fire the event - creating webhookEvent records for any non-disabled
    * subscriptions that match it.
    *
    * @param eventTypeName must be a registered event type name in the instance.
    * @param tableName table that the key-record is from, which will be used as
    *                  part of the subscription-matching
    * @param keyRecord record that the event is related to - again may be used
    *                  in subscription-matching (e.g., based on security fields
    * @param transaction in case an update to the key record was made on a
    *                    transaction, the events will be created in the same one.
    ***************************************************************************/
   public void fire(String eventTypeName, String tableName, QRecord keyRecord, QBackendTransaction transaction) throws QException
   {
      List<WebhookEventType> webhookEventTypes = WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(WebhookEventCategory.Kind.AD_HOC, tableName);
      if(CollectionUtils.nullSafeIsEmpty(webhookEventTypes))
      {
         return;
      }

      WebhookEventBuilder webhookEventBuilder = null;
      for(WebhookEventType webhookEventType : webhookEventTypes)
      {
         //////////////////////////////
         // filter by the event type //
         //////////////////////////////
         if(!webhookEventType.getName().equals(eventTypeName))
         {
            continue;
         }

         if(keyRecord.getTableName() == null)
         {
            keyRecord.setTableName(tableName);
         }

         webhookEventBuilder = FirePostInsertOrUpdateWebhookEventUtil.processSubscriptions(tableName, webhookEventType, keyRecord, webhookEventBuilder, transaction);
      }

      if(webhookEventBuilder != null)
      {
         webhookEventBuilder.storeWebhookEvents(transaction);
      }
   }

}
