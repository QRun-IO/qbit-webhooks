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
import java.util.Arrays;
import java.util.Collections;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSender;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSenderTest;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Unit test for SendWebhookEventLoadStep 
 *******************************************************************************/
class SendWebhookEventLoadStepTest extends BaseTest
{

   /*******************************************************************************
    * we had 12 fails on the books, so we'll have one more fail and go to unhealthy
    *******************************************************************************/
   @Test
   void testLast12failedThenAnotherFailed() throws QException
   {
      String eventTypeName = WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME;

      Integer webhookId0 = insert(newWebhook("test"));
      Webhook webhook    = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId0));

      Integer             subscriptionId      = insert(newWebhookSubscription(eventTypeName).withWebhookId(webhookId0));
      WebhookSubscription webhookSubscription = new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId));
      Integer             webhookId           = webhookSubscription.getWebhookId();

      ///////////////////////////////////////////
      // insert 3 events, each with 4 failures //
      ///////////////////////////////////////////
      Integer eventId0 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId1 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId2 = insertEventAwaitingRetry(webhookSubscription);
      insertNFailLogsPerEvent(webhookId, 4, eventId0, eventId1, eventId2);

      ArrayList<ProcessSummaryLineInterface> processSummaryLines = runProcess(webhook, new WebhookEventSenderTest.WebhookEventSenderThatFails(), eventId0, eventId1, eventId2);

      ////////////////////////////////////////
      // make sure webhook became unhealthy //
      ////////////////////////////////////////
      assertEquals(WebhookHealthStatus.UNHEALTHY.getId(), GetAction.execute(Webhook.TABLE_NAME, webhookId).getValue("healthStatusId"));

      ///////////////////////////////////////////////////////////////////////////////////////////////////////
      // only the first event should have been processed, because the webhook became unhealthy after that. //
      ///////////////////////////////////////////////////////////////////////////////////////////////////////
      assertWebhookEventStatus(WebhookEventStatus.FAILED, eventId0);
      assertWebhookEventStatus(WebhookEventStatus.AWAITING_RETRY, eventId1);
      assertWebhookEventStatus(WebhookEventStatus.AWAITING_RETRY, eventId2);

      assertEquals(2, processSummaryLines.size());

      assertEquals(Status.ERROR, processSummaryLines.get(0).getStatus());
      assertEquals(1, ((ProcessSummaryLine) processSummaryLines.get(0)).getCount());
      assertThat(processSummaryLines.get(0).getMessage()).contains("failed to deliver");

      assertEquals(Status.WARNING, processSummaryLines.get(1).getStatus());
      assertEquals(2, ((ProcessSummaryLine) processSummaryLines.get(1)).getCount());
      assertThat(processSummaryLines.get(1).getMessage()).contains("were not sent because their associated webhook became unhealthy.");
   }


   /*******************************************************************************
    * we had 12 fails on the books, but now a success and we'll be successful
    *******************************************************************************/
   @Test
   void testLast12failedThenSuccess() throws QException
   {
      String eventTypeName = WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME;

      Integer webhookId0 = insert(newWebhook("test").withHealthStatusId(WebhookHealthStatus.PROBATION.getId()));
      Webhook webhook    = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId0));

      Integer             subscriptionId      = insert(newWebhookSubscription(eventTypeName).withWebhookId(webhookId0));
      WebhookSubscription webhookSubscription = new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId));
      Integer             webhookId           = webhookSubscription.getWebhookId();

      ///////////////////////////////////////////
      // insert 3 events, each with 4 failures //
      ///////////////////////////////////////////
      Integer eventId0 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId1 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId2 = insertEventAwaitingRetry(webhookSubscription);
      insertNFailLogsPerEvent(webhookId, 4, eventId0, eventId1, eventId2);

      ArrayList<ProcessSummaryLineInterface> processSummaryLines = runProcess(webhook, new WebhookEventSenderTest.WebhookEventSenderThatSucceeds(), eventId0, eventId1, eventId2);

      //////////////////////////////////////
      // make sure webhook became healthy //
      //////////////////////////////////////
      assertEquals(WebhookHealthStatus.HEALTHY.getId(), GetAction.execute(Webhook.TABLE_NAME, webhookId).getValue("healthStatusId"));

      ///////////////////////////////////////////////////////////////////////////////////////////////////////
      // only the first event should have been processed, because the webhook became unhealthy after that. //
      ///////////////////////////////////////////////////////////////////////////////////////////////////////
      assertWebhookEventStatus(WebhookEventStatus.DELIVERED, eventId0);
      assertWebhookEventStatus(WebhookEventStatus.DELIVERED, eventId1);
      assertWebhookEventStatus(WebhookEventStatus.DELIVERED, eventId2);

      assertEquals(1, processSummaryLines.size());

      assertEquals(Status.OK, processSummaryLines.get(0).getStatus());
      assertEquals(3, ((ProcessSummaryLine) processSummaryLines.get(0)).getCount());
      assertThat(processSummaryLines.get(0).getMessage()).contains("were delivered");
   }



   /*******************************************************************************
    * we have 6 fails on the books - now all 3 will fail, but not enough to go unhealthy
    *******************************************************************************/
   @Test
   void testLast6failed() throws QException
   {
      String eventTypeName = WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME;

      Integer webhookId0 = insert(newWebhook("test"));
      Webhook webhook    = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId0));

      Integer             subscriptionId      = insert(newWebhookSubscription(eventTypeName).withWebhookId(webhookId0));
      WebhookSubscription webhookSubscription = new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId));
      Integer             webhookId           = webhookSubscription.getWebhookId();

      ///////////////////////////////////////////
      // insert 3 events, each with 2 failures //
      ///////////////////////////////////////////
      Integer eventId0 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId1 = insertEventAwaitingRetry(webhookSubscription);
      Integer eventId2 = insertEventAwaitingRetry(webhookSubscription);
      insertNFailLogsPerEvent(webhookId, 2, eventId0, eventId1, eventId2);

      ArrayList<ProcessSummaryLineInterface> processSummaryLines = runProcess(webhook, new WebhookEventSenderTest.WebhookEventSenderThatFails(), eventId0, eventId1, eventId2);

      ////////////////////////////////////////
      // make sure webhook is still healthy //
      ////////////////////////////////////////
      assertEquals(WebhookHealthStatus.HEALTHY.getId(), GetAction.execute(Webhook.TABLE_NAME, webhookId).getValue("healthStatusId"));

      //////////////////////////////////////////
      // all 3 should still be awaiting retry //
      //////////////////////////////////////////
      assertWebhookEventStatus(WebhookEventStatus.AWAITING_RETRY, eventId0);
      assertWebhookEventStatus(WebhookEventStatus.AWAITING_RETRY, eventId1);
      assertWebhookEventStatus(WebhookEventStatus.AWAITING_RETRY, eventId2);

      assertEquals(1, processSummaryLines.size());

      assertEquals(Status.ERROR, processSummaryLines.get(0).getStatus());
      assertEquals(3, ((ProcessSummaryLine) processSummaryLines.get(0)).getCount());
      assertThat(processSummaryLines.get(0).getMessage()).contains("failed to deliver");
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private void assertWebhookEventStatus(WebhookEventStatus webhookEventStatus, Integer eventId) throws QException
   {
      assertEquals(webhookEventStatus.getId(), GetAction.execute(WebhookEvent.TABLE_NAME, eventId).getValue("eventStatusId"));
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static ArrayList<ProcessSummaryLineInterface> runProcess(Webhook webhook, WebhookEventSender webhookEventSender, Integer... eventIds) throws QException
   {
      SendWebhookEventLoadStep                           loadStep           = getSendWebhookEventLoadStep(webhookEventSender);
      loadStep.setTransformStep(new SendWebhookEventTransformStep().withWebhook(webhook));

      //////////////////////////////////
      // run process for these events //
      //////////////////////////////////
      RunBackendStepInput input = new RunBackendStepInput();
      input.withRecords(QueryAction.execute(WebhookEvent.TABLE_NAME, new QQueryFilter()
         .withCriteria(new QFilterCriteria("id", QCriteriaOperator.IN, Arrays.stream(eventIds).toList()))
         .withOrderBy(new QFilterOrderBy("id"))));
      RunBackendStepOutput output = new RunBackendStepOutput();
      loadStep.preRun(input, output);
      loadStep.runOnePage(input, output);
      ArrayList<ProcessSummaryLineInterface> processSummaryLines = loadStep.doGetProcessSummary(output, true);
      return processSummaryLines;
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static void insertNFailLogsPerEvent(Integer webhookId, int n, Integer... eventIds) throws QException
   {
      for(Integer eventId : eventIds)
      {
         new InsertAction().execute(new InsertInput(WebhookEventSendLog.TABLE_NAME).withRecordEntities(Collections.nCopies(n,
            new WebhookEventSendLog().withWebhookId(webhookId).withWebhookEventId(eventId).withSuccessful(false))));
      }
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static Integer insertEventAwaitingRetry(WebhookSubscription webhookSubscription) throws QException
   {
      return insert(newWebhookEvent(webhookSubscription, webhookSubscription.getWebhookEventTypeName()).withEventStatusId(WebhookEventStatus.AWAITING_RETRY.getId()));
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static SendWebhookEventLoadStep getSendWebhookEventLoadStep(WebhookEventSender webhookEventSender)
   {
      SendWebhookEventLoadStep loadStep = new SendWebhookEventLoadStep()
      {
         /***************************************************************************
          **
          ***************************************************************************/
         @Override
         protected WebhookEventSender newWebhookEventSender()
         {
            return webhookEventSender;
         }
      };
      return loadStep;
   }

}