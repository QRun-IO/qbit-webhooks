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
import java.util.List;
import java.util.Map;
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
import com.kingsrook.qqq.backend.core.actions.reporting.RecordPipe;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Unit test for SendWebhookEventExtractStep 
 *******************************************************************************/
class SendWebhookEventExtractStepTest extends BaseTest
{


   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testEventsFilteredByWebhook() throws QException
   {
      /////////////////////////////////////////////////////////////////////////////////////////////////////////////
      // make sure if there are valid events for multiple webhooks, that we only find the ones we're looking for //
      /////////////////////////////////////////////////////////////////////////////////////////////////////////////
      Integer webhookId1 = 1;
      Integer webhookId2 = 2;
      List<QRecord> webhooks = new InsertAction().execute(new InsertInput(Webhook.TABLE_NAME).withRecordEntities(List.of(
         new Webhook().withId(webhookId1).withName("a").withUrl("a").withSubscriptions(List.of(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME))),
         new Webhook().withId(webhookId2).withName("b").withUrl("b").withSubscriptions(List.of(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME)))
      ))).getRecords();

      WebhookSubscription subscription1 = new WebhookSubscription(webhooks.get(0).getAssociatedRecords().get(Webhook.SUBSCRIPTIONS_ASSOCIATION_NAME).get(0));
      WebhookSubscription subscription2 = new WebhookSubscription(webhooks.get(1).getAssociatedRecords().get(Webhook.SUBSCRIPTIONS_ASSOCIATION_NAME).get(0));

      new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME).withRecordEntities(List.of(
         ////////////////////////////
         // 3 events for webhook 1 //
         ////////////////////////////
         newWebhookEvent(subscription1, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(11),
         newWebhookEvent(subscription1, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(12),
         newWebhookEvent(subscription1, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(13),

         ////////////////////////////
         // 2 events for webhook 2 //
         ////////////////////////////
         newWebhookEvent(subscription2, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(20),
         newWebhookEvent(subscription2, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(21)
      )));

      assertEquals(3, runStepForWebhookId(webhookId1).size());
      assertEquals(2, runStepForWebhookId(webhookId2).size());
      assertEquals(0, runStepForWebhookId(-1).size());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWebhookScenarios() throws QException
   {
      testWebhookScenario(true, new Webhook());
      testWebhookScenario(false, new Webhook().withActiveStatusId(WebhookActiveStatus.PAUSED.getId()));
      testWebhookScenario(false, new Webhook().withActiveStatusId(WebhookActiveStatus.DISABLED.getId()));
      testWebhookScenario(false, new Webhook().withHealthStatusId(WebhookHealthStatus.UNHEALTHY.getId()));
      testWebhookScenario(true, new Webhook().withHealthStatusId(WebhookHealthStatus.PROBATION.getId()));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void testWebhookScenario(boolean expectToFindEvent, Webhook webhook) throws QException
   {
      QRecord insertedWebhook = new InsertAction().execute(new InsertInput(Webhook.TABLE_NAME).withRecordEntities(List.of(webhook
         .withName(UUID.randomUUID().toString())
         .withUrl("a")
         .withSubscriptions(List.of(
            newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME)
         ))))).getRecords().get(0);

      WebhookSubscription subscription = new WebhookSubscription(insertedWebhook.getAssociatedRecords().get(Webhook.SUBSCRIPTIONS_ASSOCIATION_NAME).get(0));

      new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME).withRecordEntities(List.of(
         newWebhookEvent(subscription, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withEventSourceRecordId(100)
      )));

      List<QRecord> events = runStepForWebhookId(insertedWebhook.getValueInteger("id"));
      if(expectToFindEvent)
      {
         assertFalse(events.isEmpty());
      }
      else
      {
         assertTrue(events.isEmpty());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testWebhookEventScenarios() throws QException
   {
      Instant past = Instant.now().minusSeconds(60);
      Instant future = Instant.now().plusSeconds(60);

      ////////////////////////////////////////////////////////////////////////////////////////////
      // new should always match                                                                //
      // though we could imagine a change in the future to allow new events to be future-dated? //
      ////////////////////////////////////////////////////////////////////////////////////////////
      testWebhookEventScenario(true, we -> we.withEventStatusId(WebhookEventStatus.NEW.getId()));
      testWebhookEventScenario(true, we -> we.withEventStatusId(WebhookEventStatus.NEW.getId()).withNextAttemptTimestamp(future));

      //////////////////////////////////////////////////////////////////////////////////////////////////
      // sending or awaiting retry - they both use nextAttemptTimestamp, so only true if in the past. //
      //////////////////////////////////////////////////////////////////////////////////////////////////
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.SENDING.getId()));
      testWebhookEventScenario(true, we -> we.withEventStatusId(WebhookEventStatus.SENDING.getId()).withNextAttemptTimestamp(past));
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.SENDING.getId()).withNextAttemptTimestamp(future));

      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.AWAITING_RETRY.getId()));
      testWebhookEventScenario(true, we -> we.withEventStatusId(WebhookEventStatus.AWAITING_RETRY.getId()).withNextAttemptTimestamp(past));
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.AWAITING_RETRY.getId()).withNextAttemptTimestamp(future));

      ////////////////////////////////////////////////////////////////////////////////////
      // delivered and failed - should never be found, even if timestamp is in the past //
      ////////////////////////////////////////////////////////////////////////////////////
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.DELIVERED.getId()));
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.DELIVERED.getId()).withNextAttemptTimestamp(past));

      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.FAILED.getId()));
      testWebhookEventScenario(false, we -> we.withEventStatusId(WebhookEventStatus.FAILED.getId()).withNextAttemptTimestamp(past));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void testWebhookEventScenario(boolean expectToFindEvent, Consumer<WebhookEvent> webhookEventCustomizer) throws QException
   {
      QRecord insertedWebhook = new InsertAction().execute(new InsertInput(Webhook.TABLE_NAME).withRecordEntities(List.of(new Webhook()
         .withName(UUID.randomUUID().toString())
         .withUrl("a")
         .withSubscriptions(List.of(
            newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME)
         ))))).getRecords().get(0);

      WebhookSubscription subscription = new WebhookSubscription(insertedWebhook.getAssociatedRecords().get(Webhook.SUBSCRIPTIONS_ASSOCIATION_NAME).get(0));

      WebhookEvent webhookEvent = newWebhookEvent(subscription, WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME);
      webhookEventCustomizer.accept(webhookEvent);

      new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME).withRecordEntities(List.of(webhookEvent)));

      List<QRecord> events = runStepForWebhookId(insertedWebhook.getValueInteger("id"));
      if(expectToFindEvent)
      {
         assertFalse(events.isEmpty());
      }
      else
      {
         assertTrue(events.isEmpty());
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static List<QRecord> runStepForWebhookId(Integer webhookId1) throws QException
   {
      RunBackendStepInput  input  = new RunBackendStepInput().withValues(Map.of("webhookId", webhookId1, ExtractViaQueryStep.FIELD_SOURCE_TABLE, WebhookEvent.TABLE_NAME));
      RunBackendStepOutput output = new RunBackendStepOutput();

      SendWebhookEventExtractStep sendWebhookEventExtractStep = new SendWebhookEventExtractStep();
      RecordPipe                  recordPipe                  = new RecordPipe();

      sendWebhookEventExtractStep.setRecordPipe(recordPipe);
      sendWebhookEventExtractStep.preRun(input, output);
      sendWebhookEventExtractStep.run(input, output);

      List<QRecord> records = recordPipe.consumeAvailableRecords();
      return records;
   }

}