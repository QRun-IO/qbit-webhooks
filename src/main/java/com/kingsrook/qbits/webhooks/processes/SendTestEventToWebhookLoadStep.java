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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSender;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.general.ProcessSummaryWarningsAndErrorsRollup;
import org.apache.commons.lang3.BooleanUtils;


/*******************************************************************************
 ** sends test events to webhooks.
 *******************************************************************************/
public class SendTestEventToWebhookLoadStep extends AbstractLoadStep implements ProcessSummaryProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(SendTestEventToWebhookLoadStep.class);

   private ProcessSummaryLine okLine = new ProcessSummaryLine(Status.OK)
      .withMessageSuffix(" delivered successfully")
      .withSingularPastMessage("was")
      .withPluralPastMessage("were");

   private ProcessSummaryLine updatedToProbationLine = new ProcessSummaryLine(Status.INFO)
      .withMessageSuffix(" updated from Unhealthy to Probation")
      .withSingularPastMessage("was")
      .withPluralPastMessage("were");

   private ProcessSummaryWarningsAndErrorsRollup errorsRollup = new ProcessSummaryWarningsAndErrorsRollup()
      .withDoReplaceSingletonCountLinesWithSuffixOnly(false)
      .withErrorTemplate(new ProcessSummaryLine(Status.ERROR)
         .withSingularPastMessage("failed to send with error message: ")
         .withPluralPastMessage("failed to send with error message: "))
      .withOtherErrorsSummary(new ProcessSummaryLine(Status.ERROR)
         .withSingularPastMessage("had an other error.")
         .withPluralPastMessage("had other errors."));



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput runBackendStepOutput, boolean isForResultScreen)
   {
      ArrayList<ProcessSummaryLineInterface> rs = new ArrayList<>();
      okLine.addSelfToListIfAnyCount(rs);
      updatedToProbationLine.addSelfToListIfAnyCount(rs);
      errorsRollup.addToList(rs);
      return rs;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void runOnePage(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
   {
      boolean       uponSuccessUpdateUnhealthyWebhooksToProbation = BooleanUtils.isTrue(runBackendStepInput.getValueBoolean("uponSuccessUpdateUnhealthyWebhooksToProbation"));
      List<QRecord> webhooksToUpdate                              = new ArrayList<>();

      for(Webhook webhook : runBackendStepInput.getRecordsAsEntities(Webhook.class))
      {
         WebhookEventSendLog sendLog;
         try
         {
            WebhookEvent event = new WebhookEvent().withContent(List.of(new WebhookEventContent()
               .withPostBody("""
                  {
                    "webhookEventDetails":
                    {
                      "webhookEventTypeName": "testEvent",
                      "eventTimestamp": "%s",
                      "tableName": null,
                      "apiName": null,
                      "apiVersion": null
                    },
                    "record":
                    {
                    }
                  }""".formatted(Instant.now().toString()))
            ));

            sendLog = getWebhookEventSender().post(event, webhook);
         }
         catch(Exception e)
         {
            sendLog = new WebhookEventSendLog()
               .withSuccessful(false)
               .withErrorMessage(e.getMessage());
         }

         if(sendLog.getSuccessful())
         {
            if(WebhookHealthStatus.UNHEALTHY.getId().equals(webhook.getHealthStatusId()))
            {
               webhooksToUpdate.add(new QRecord().withValue("id", webhook.getId()).withValue("healthStatusId", WebhookHealthStatus.PROBATION.getId()));
               updatedToProbationLine.incrementCountAndAddPrimaryKey(webhook.getId());
            }

            okLine.incrementCountAndAddPrimaryKey(webhook.getId());
         }
         else
         {
            if(sendLog.getHttpStatusCode() != null)
            {
               errorsRollup.addError("Status Code: " + sendLog.getHttpStatusCode() + "; " + sendLog.getErrorMessage(), webhook.getId());
            }
            else
            {
               errorsRollup.addError(sendLog.getErrorMessage(), webhook.getId());
            }
         }
      }

      if(uponSuccessUpdateUnhealthyWebhooksToProbation && !webhooksToUpdate.isEmpty())
      {
         new UpdateAction().execute(new UpdateInput(Webhook.TABLE_NAME).withRecords(webhooksToUpdate));
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   protected WebhookEventSender getWebhookEventSender()
   {
      return new WebhookEventSender();
   }
}
