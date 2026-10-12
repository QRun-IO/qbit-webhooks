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
import com.kingsrook.qbits.webhooks.actions.FirePostInsertWebhookEvent;
import com.kingsrook.qbits.webhooks.actions.FirePostUpdateWebhookEvent;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;


/*******************************************************************************
 ** meta-data producer for the webhooks qbit
 *******************************************************************************/
public class WebhooksQBitProducer implements QBitMetaDataProducer<WebhooksQBitConfig>
{
   private WebhooksQBitConfig qBitConfig;



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      QBitMetaData qBitMetaData = new QBitMetaData()
         .withGroupId("com.kingsrook.qbits")
         .withArtifactId("webhooks")
         .withVersion("0.1.4")
         .withNamespace(getNamespace())
         .withConfig(getQBitConfig());
      return qBitMetaData;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static QAppSection produceAppSection()
   {
      return new QAppSection()
         .withName("webhooks")
         .withTables(List.of(
            Webhook.TABLE_NAME,
            WebhookSubscription.TABLE_NAME,
            WebhookEvent.TABLE_NAME,
            WebhookEventContent.TABLE_NAME,
            WebhookEventSendLog.TABLE_NAME
         ));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void postProduceActions(MetaDataProducerMultiOutput metaDataProducerMultiOutput, QInstance qinstance)
   {
      registerTableActionsInInstance(qinstance);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public void registerTableActionsInInstance(QInstance qInstance)
   {
      qInstance.withTableCustomizer(TableCustomizers.POST_INSERT_RECORD, new QCodeReference(FirePostInsertWebhookEvent.class));
      qInstance.withTableCustomizer(TableCustomizers.POST_UPDATE_RECORD, new QCodeReference(FirePostUpdateWebhookEvent.class));
   }



   /*******************************************************************************
    ** Setter for qBitConfig
    *******************************************************************************/
   public void setQBitConfig(WebhooksQBitConfig qBitConfig)
   {
      this.qBitConfig = qBitConfig;
   }



   /*******************************************************************************
    ** Fluent setter for qBitConfig
    *******************************************************************************/
   public WebhooksQBitProducer withQBitConfig(WebhooksQBitConfig qBitConfig)
   {
      this.qBitConfig = qBitConfig;
      return (this);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public WebhooksQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }

}
