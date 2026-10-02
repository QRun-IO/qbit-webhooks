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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Unit test for ManageWebhookHealthTransformStep 
 *******************************************************************************/
class ManageWebhookHealthTransformStepTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void test() throws QException
   {
      Integer healthyId          = insert(newWebhook("healthy").withHealthStatusId(WebhookHealthStatus.HEALTHY.getId()));
      Integer onProbationId      = insert(newWebhook("probation").withHealthStatusId(WebhookHealthStatus.PROBATION.getId()));
      Integer unhealthyJustNowId = insert(newWebhook("unhealthyJustNow").withHealthStatusId(WebhookHealthStatus.UNHEALTHY.getId()));
      Integer unhealthyLongAgoId = insert(newWebhook("unhealthyLongAgo").withHealthStatusId(WebhookHealthStatus.UNHEALTHY.getId()));
      Integer unhealthyNoLogsId  = insert(newWebhook("unhealthyNoLogs").withHealthStatusId(WebhookHealthStatus.UNHEALTHY.getId()));

      new InsertAction().execute(new InsertInput(WebhookEventSendLog.TABLE_NAME).withRecordEntities(List.of(
         new WebhookEventSendLog().withWebhookId(unhealthyJustNowId).withStartTimestamp(Instant.now()),
         new WebhookEventSendLog().withWebhookId(unhealthyLongAgoId).withStartTimestamp(Instant.now().minus(1, ChronoUnit.DAYS))
      )));

      RunBackendStepInput  input  = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();

      input.withRecords(QueryAction.execute(Webhook.TABLE_NAME, new QQueryFilter()));

      ManageWebhookHealthTransformStep manageWebhookHealthTransformStep = new ManageWebhookHealthTransformStep();
      manageWebhookHealthTransformStep.runOnePage(input, output);

      assertEquals(1, output.getRecords().size());
      assertEquals(unhealthyLongAgoId, output.getRecords().get(0).getValueInteger("id"));

      ArrayList<ProcessSummaryLineInterface> processSummaryLineInterfaces = manageWebhookHealthTransformStep.doGetProcessSummary(output, false);
      assertEquals(3, processSummaryLineInterfaces.size());

      assertEquals(Status.OK, processSummaryLineInterfaces.get(0).getStatus());
      assertEquals(1, ((ProcessSummaryLine)processSummaryLineInterfaces.get(0)).getCount());

      assertEquals(Status.INFO, processSummaryLineInterfaces.get(1).getStatus());
      assertEquals(2, ((ProcessSummaryLine)processSummaryLineInterfaces.get(1)).getCount());
      assertThat(processSummaryLineInterfaces.get(1).getMessage()).contains("not currently Unhealthy");

      assertEquals(Status.ERROR, processSummaryLineInterfaces.get(2).getStatus());
      assertEquals(2, ((ProcessSummaryLine)processSummaryLineInterfaces.get(2)).getCount());
      assertThat(processSummaryLineInterfaces.get(2).getMessage()).contains("Unhealthy long enough to be put on Probation");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testNoUnhealthyInputs() throws QException
   {
      Integer healthyId = insert(newWebhook("healthy").withHealthStatusId(WebhookHealthStatus.HEALTHY.getId()));

      RunBackendStepInput  input  = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();

      input.withRecords(QueryAction.execute(Webhook.TABLE_NAME, new QQueryFilter()));

      ManageWebhookHealthTransformStep manageWebhookHealthTransformStep = new ManageWebhookHealthTransformStep();
      manageWebhookHealthTransformStep.runOnePage(input, output);

      assertEquals(0, output.getRecords().size());
   }

}