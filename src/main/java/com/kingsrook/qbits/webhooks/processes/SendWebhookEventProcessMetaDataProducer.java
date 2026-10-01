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

package com.kingsrook.qbits.webhooks.processes;


import java.util.List;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qbits.webhooks.registry.WebhookEventTypePossibleValueSource;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.tables.QQQTable;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Meta Data Producer for SendWebhookEventProcess
 *******************************************************************************/
public class SendWebhookEventProcessMetaDataProducer extends MetaDataProducer<QProcessMetaData>
{
   public static final String NAME = "SendWebhookEvent";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QProcessMetaData produce(QInstance qInstance) throws QException
   {
      QProcessMetaData processMetaData = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(NAME)
         .withTableName(WebhookEvent.TABLE_NAME)
         .withIcon(new QIcon().withName("send"))
         .withExtractStepClass(SendWebhookEventExtractStep.class)
         .withTransformStepClass(SendWebhookEventTransformStep.class)
         .withLoadStepClass(SendWebhookEventLoadStep.class)
         .withSourceTable(WebhookEvent.TABLE_NAME)
         .withDestinationTable(WebhookEvent.TABLE_NAME)
         .withPreviewMessage(StreamedETLWithFrontendProcess.DEFAULT_PREVIEW_MESSAGE_PREFIX + " considered for sending")
         .withReviewStepRecordFields(List.of(
            new QFieldMetaData("id", QFieldType.INTEGER),
            new QFieldMetaData("webhookId", QFieldType.INTEGER).withPossibleValueSourceName(Webhook.TABLE_NAME),
            new QFieldMetaData("webhookSubscriptionId", QFieldType.INTEGER).withPossibleValueSourceName(WebhookSubscription.TABLE_NAME),
            new QFieldMetaData("webhookEventTypeName", QFieldType.STRING).withLabel("Event Type").withPossibleValueSourceName(WebhookEventTypePossibleValueSource.NAME),
            new QFieldMetaData("eventSourceRecordQqqTableId", QFieldType.INTEGER).withLabel("Event Source Record Table").withPossibleValueSourceName(QQQTable.TABLE_NAME),
            new QFieldMetaData("eventSourceRecordId", QFieldType.INTEGER),
            WebhookEvent.TableMetaDataCustomizer.addEventStatusChipAdornment(new QFieldMetaData("eventStatusId", QFieldType.INTEGER).withPossibleValueSourceName(WebhookEventStatus.NAME))
         ))
         .withInputFieldDefaultValue(StreamedETLWithFrontendProcess.FIELD_INCLUDE_ASSOCIATIONS, true)
         .getProcessMetaData();

      return (processMetaData);
   }

}
