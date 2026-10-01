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
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.LoadViaUpdateStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Meta Data Producer for ManageWebhookHealth
 *******************************************************************************/
public class ManageWebhookHealthProcessMetaDataProducer extends MetaDataProducer<QProcessMetaData>
{
   public static final String NAME = "ManageWebhookHealth";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QProcessMetaData produce(QInstance qInstance) throws QException
   {
      QProcessMetaData processMetaData = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(NAME)
         .withTableName(Webhook.TABLE_NAME)
         .withIcon(new QIcon().withName("healing"))
         .withExtractStepClass(ExtractViaQueryStep.class)
         .withTransformStepClass(ManageWebhookHealthTransformStep.class)
         .withLoadStepClass(LoadViaUpdateStep.class)
         .withSourceTable(Webhook.TABLE_NAME)
         .withDestinationTable(Webhook.TABLE_NAME)
         .withPreviewMessage(StreamedETLWithFrontendProcess.DEFAULT_PREVIEW_MESSAGE_FOR_UPDATE)
         .withReviewStepRecordFields(List.of(
            new QFieldMetaData("id", QFieldType.INTEGER),
            new QFieldMetaData("name", QFieldType.STRING),
            Webhook.TableMetaDataCustomizer.addHealthStatusChipAdornment(new QFieldMetaData("healthStatusId", QFieldType.INTEGER).withPossibleValueSourceName(WebhookHealthStatus.NAME))
         ))
         .getProcessMetaData();

      return (processMetaData);
   }

}
