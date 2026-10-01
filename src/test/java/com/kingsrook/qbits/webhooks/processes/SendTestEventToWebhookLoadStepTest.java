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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSender;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSenderTest;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Unit test for SendTestEventToWebhookLoadStep 
 *******************************************************************************/
class SendTestEventToWebhookLoadStepTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSuccess() throws QException
   {
      ProcessSummaryLine processSummaryLine = runProcessForSender(new WebhookEventSenderTest.WebhookEventSenderThatSucceeds(), 2);
      assertEquals(Status.OK, processSummaryLine.getStatus());
      assertEquals("were delivered successfully", processSummaryLine.getMessage());
      assertEquals(2, processSummaryLine.getCount());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testErrorWithoutStatusCode() throws QException
   {
      ProcessSummaryLine processSummaryLine = runProcessForSender(new WebhookEventSenderTest.WebhookEventSenderThatFails());
      assertEquals(Status.ERROR, processSummaryLine.getStatus());
      assertEquals("failed to send with error message: Test failure", processSummaryLine.getMessage());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testErrorsRollup() throws QException
   {
      ProcessSummaryLine processSummaryLine = runProcessForSender(new WebhookEventSenderTest.WebhookEventSenderThatFails(), 3);
      assertEquals(Status.ERROR, processSummaryLine.getStatus());
      assertEquals("failed to send with error message: Test failure", processSummaryLine.getMessage());
      assertEquals(3, processSummaryLine.getCount());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testErrorWithStatusCode() throws QException
   {
      ProcessSummaryLine processSummaryLine = runProcessForSender(new WebhookEventSenderTest.WebhookEventSenderThatMocksHttp(500, "Server Error"));
      assertEquals(Status.ERROR, processSummaryLine.getStatus());
      assertEquals("failed to send with error message: Status Code: 500; Server Error", processSummaryLine.getMessage());
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static ProcessSummaryLine runProcessForSender(WebhookEventSender webhookEventSender) throws QException
   {
      return runProcessForSender(webhookEventSender, 1);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static ProcessSummaryLine runProcessForSender(WebhookEventSender webhookEventSender, int nCopies) throws QException
   {
      SendTestEventToWebhookLoadStep step                 = getSendTestEventToWebhookLoadStep(webhookEventSender);
      RunBackendStepOutput           runBackendStepOutput = new RunBackendStepOutput();

      List<QRecord> records = Collections.nCopies(nCopies, new Webhook().withUrl("http://localhost:8000/").toQRecord());

      step.runOnePage(new RunBackendStepInput().withRecords(records), runBackendStepOutput);
      ArrayList<ProcessSummaryLineInterface> processSummary     = step.doGetProcessSummary(runBackendStepOutput, true);
      ProcessSummaryLine                     processSummaryLine = (ProcessSummaryLine) processSummary.get(0);
      return processSummaryLine;
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static SendTestEventToWebhookLoadStep getSendTestEventToWebhookLoadStep(WebhookEventSender webhookEventSender)
   {
      SendTestEventToWebhookLoadStep step = new SendTestEventToWebhookLoadStep()
      {
         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         protected WebhookEventSender getWebhookEventSender()
         {
            return webhookEventSender;
         }
      };
      return step;
   }

}