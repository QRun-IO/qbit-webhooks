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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookActiveStatus;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Unit test for SendWebhookEventTransformStep 
 *******************************************************************************/
class SendWebhookEventTransformStepTest extends BaseTest
{


   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void test() throws QException
   {
      ///////////////////////////////////////
      // everything "normal", will be sent //
      ///////////////////////////////////////
      testOneScenario("will be sent", null, null, null);

      ////////////////////////////
      // missing webhook errors //
      ////////////////////////////
      testOneScenario("webhook was not found", e -> e.setWebhookId(-1), null, null);
      testOneScenario("webhook was not found", e -> e.setWebhookId(null), null, null);

      /////////////////////////////////
      // missing subscription errors //
      /////////////////////////////////
      testOneScenario("subscription was not found", e -> e.setWebhookSubscriptionId(-1), null, null);
      testOneScenario("subscription was not found", e -> e.setWebhookSubscriptionId(null), null, null);

      /////////////////////////////////
      // disabled things do not send //
      /////////////////////////////////
      testOneScenario("webhook is disabled", null, w -> w.setActiveStatusId(WebhookActiveStatus.DISABLED.getId()), null);
      testOneScenario("subscription is disabled", null, null, s -> s.setActiveStatusId(WebhookActiveStatus.DISABLED.getId()));

      ////////////////////////////////////////////////////////////////////////////////////////////////
      // paused one do - if they were manually pulled into the process (extract step excludes them) //
      ////////////////////////////////////////////////////////////////////////////////////////////////
      testOneScenario("will be sent", null, w -> w.setActiveStatusId(WebhookActiveStatus.PAUSED.getId()), null);
      testOneScenario("will be sent", null, null, s -> s.setActiveStatusId(WebhookActiveStatus.PAUSED.getId()));

      //////////////////////////////////////////////////////
      // unhealthy webhook does not send - probation does //
      //////////////////////////////////////////////////////
      testOneScenario("webhook is unhealthy", null, w -> w.setHealthStatusId(WebhookHealthStatus.UNHEALTHY.getId()), null);
      testOneScenario("will be sent", null, w -> w.setHealthStatusId(WebhookHealthStatus.PROBATION.getId()), null);

      /////////////////////////////
      // already-delivered warns //
      /////////////////////////////
      testOneScenario("will be sent again", e -> e.setEventStatusId(WebhookEventStatus.DELIVERED.getId()), null, null);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private ArrayList<ProcessSummaryLineInterface> testOneScenario(String expectedSummaryLineToContain, Consumer<WebhookEvent> eventCustomizer, Consumer<Webhook> webhookCustomizer, Consumer<WebhookSubscription> subscriptionCustomizer) throws QException
   {
      Webhook webhook = new Webhook()
         .withName(UUID.randomUUID().toString())
         .withUrl("a");
      if(webhookCustomizer != null)
      {
         webhookCustomizer.accept(webhook);
      }
      QRecord insertedWebhook = new InsertAction().execute(new InsertInput(Webhook.TABLE_NAME).withRecordEntities(List.of(webhook))).getRecords().get(0);

      WebhookSubscription subscription = newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME)
         .withWebhookId(insertedWebhook.getValueInteger("id"));
      if(subscriptionCustomizer != null)
      {
         subscriptionCustomizer.accept(subscription);
      }
      WebhookSubscription insertedSubscription = new WebhookSubscription(new InsertAction().execute(new InsertInput(WebhookSubscription.TABLE_NAME).withRecordEntities(List.of(subscription))).getRecords().get(0));

      WebhookEvent webhookEvent = newWebhookEvent(insertedSubscription, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME);
      if(eventCustomizer != null)
      {
         eventCustomizer.accept(webhookEvent);
      }
      QRecord insertedEvent = new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME).withRecordEntities(List.of(webhookEvent))).getRecords().get(0);

      RunBackendStepInput  input  = new RunBackendStepInput();
      RunBackendStepOutput output = new RunBackendStepOutput();

      input.setRecords(List.of(insertedEvent));

      SendWebhookEventTransformStep sendWebhookEventTransformStep = new SendWebhookEventTransformStep();
      sendWebhookEventTransformStep.preRun(input, output);
      sendWebhookEventTransformStep.runOnePage(input, output);

      ArrayList<ProcessSummaryLineInterface> processSummaryLineInterfaces = sendWebhookEventTransformStep.doGetProcessSummary(output, false);
      assertEquals(1, processSummaryLineInterfaces.size());
      assertThat(processSummaryLineInterfaces.get(0).getMessage()).contains(expectedSummaryLineToContain);

      return processSummaryLineInterfaces;
   }

}