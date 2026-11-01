/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.NoopTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** Meta Data Producer for SendTestEventToWebhook
 *******************************************************************************/
public class SendTestEventToWebhookProcessMetaDataProducer extends MetaDataProducer<QProcessMetaData>
{
   public static final String NAME = "SendTestEventToWebhook";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QProcessMetaData produce(QInstance qInstance) throws QException
   {
      QProcessMetaData processMetaData = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(NAME)
         .withTableName(Webhook.TABLE_NAME)
         .withIcon(new QIcon().withName("quiz"))
         .withExtractStepClass(ExtractViaQueryStep.class)
         .withTransformStepClass(NoopTransformStep.class)
         .withLoadStepClass(SendTestEventToWebhookLoadStep.class)
         .withSourceTable(Webhook.TABLE_NAME)
         .withDestinationTable(Webhook.TABLE_NAME)
         .withPreviewMessage(StreamedETLWithFrontendProcess.DEFAULT_PREVIEW_MESSAGE_PREFIX + " sent a test event")
         .withReviewStepRecordFields(List.of(
            new QFieldMetaData("id", QFieldType.INTEGER),
            new QFieldMetaData("name", QFieldType.STRING),
            new QFieldMetaData("url", QFieldType.STRING),
            Webhook.TableMetaDataCustomizer.addHealthStatusChipAdornment(new QFieldMetaData("healthStatusId", QFieldType.INTEGER).withPossibleValueSourceName(WebhookHealthStatus.NAME))
         ))
         .getProcessMetaData();

      //////////////////////////////////////////////////////////////////////////
      // the input screen goes after the first backend (preview) step, not   //
      // before it: the records chosen when the process starts reach only    //
      // the steps of that first run, and the preview step is what keeps     //
      // them (as queryFilterJson) for the later steps. As step 0 the screen //
      // stopped the first run before any backend step, so the records were  //
      // lost and the extract failed with "No records were selected".        //
      //////////////////////////////////////////////////////////////////////////
      processMetaData.withStep(1, new QFrontendStepMetaData()
         .withName("input")
         .withComponent(new QFrontendComponentMetaData()
            .withType(QComponentType.EDIT_FORM))
         .withFormField(new QFieldMetaData("uponSuccessUpdateUnhealthyWebhooksToProbation", QFieldType.BOOLEAN)
            .withDefaultValue("true")
            .withLabel("Upon Success Update Unhealthy Webhooks To Probation?")));

      return (processMetaData);
   }

}
