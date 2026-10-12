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

package com.kingsrook.qbits.webhooks.delivery;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.ProcessRunner;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Retries, dead letters, 429 handling, serial order, rate limits and
 ** endpoint health.
 *******************************************************************************/
class DeliveryLifecycleTest
{
   private Receiver receiver;
   private Integer  endpointId;



   /*******************************************************************************
    ** Setup: one endpoint subscribed to person inserts.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp();
      receiver = new Receiver();
      endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      receiver.close();
   }



   /*******************************************************************************
    ** Insert n people.
    *******************************************************************************/
   private void people(int n) throws Exception
   {
      for(int i = 0; i < n; i++)
      {
         WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "P" + i));
      }
   }



   /*******************************************************************************
    ** The delivery row.
    *******************************************************************************/
   private QRecord delivery(int index) throws Exception
   {
      return (WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(index));
   }



   /*******************************************************************************
    ** Max attempts, then dead letter, then Replay delivers it.
    *******************************************************************************/
   @Test
   void deadLetterAfterMaxAttemptsThenReplay() throws Exception
   {
      receiver.respond(r -> Receiver.Response.of(503));
      people(1);
      for(int i = 0; i < 3; i++)
      {
         new DeliveryEngine().runOnce();
         WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(10));
      }
      QRecord dead = delivery(0);
      assertThat(dead.getValueString("status")).isEqualTo("DEAD_LETTER");
      assertThat(dead.getValueInteger("attemptCount")).isEqualTo(3);
      assertThat(dead.getValue("deadLetterDate")).isNotNull();

      ////////////////////////////////////////////
      // a dead letter is never retried by itself //
      ////////////////////////////////////////////
      WebhooksTestSupport.CLOCK.advance(Duration.ofHours(1));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(3);

      receiver.respond(r -> Receiver.Response.of(200));
      ProcessRunner.run(com.kingsrook.qbits.webhooks.processes.WebhooksProcesses.REPLAY, dead.getValue("id"));
      QRecord replayed = delivery(0);
      assertThat(replayed.getValueString("status")).isEqualTo("PENDING");
      assertThat(replayed.getValueInteger("replayCount")).isEqualTo(1);
      assertThat(replayed.getValueInteger("cycleAttemptCount")).isEqualTo(0);

      ////////////////////////////////////////////////////////////////////
      // the three failures also made the endpoint unhealthy, so the      //
      // scheduled run skips it; Send Now (a person's choice) does not     //
      ////////////////////////////////////////////////////////////////////
      assertThat(WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0).getValueString("healthStatus")).isEqualTo("UNHEALTHY");
      new DeliveryEngine().runOnce();
      assertThat(delivery(0).getValueString("status")).isEqualTo("PENDING");
      ProcessRunner.Lines lines = ProcessRunner.run(com.kingsrook.qbits.webhooks.processes.WebhooksProcesses.SEND_NOW, dead.getValue("id"));
      assertThat(lines.result()).anyMatch(l -> l.contains("delivered"));
      assertThat(delivery(0).getValueString("status")).isEqualTo("DELIVERED");
      assertThat(delivery(0).getValueInteger("attemptCount")).isEqualTo(4);
      assertThat(WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0).getValueString("healthStatus")).isEqualTo("HEALTHY");
      assertThat(receiver.requests().get(3).headers().get("webhook-id")).isEqualTo(receiver.requests().get(0).headers().get("webhook-id"));
   }



   /*******************************************************************************
    ** 429 waits for Retry-After, without using the attempt budget or health.
    *******************************************************************************/
   @Test
   void rateLimitedHonoursRetryAfter() throws Exception
   {
      receiver.respond(r -> new Receiver.Response(429, Map.of("Retry-After", "120"), "slow down", 0));
      people(1);
      new DeliveryEngine().runOnce();
      QRecord waiting = delivery(0);
      assertThat(waiting.getValueString("status")).isEqualTo("RETRY_WAIT");
      assertThat(waiting.getValueInteger("cycleAttemptCount")).isEqualTo(0);
      assertThat(java.time.Duration.between(WebhooksTestSupport.CLOCK.instant(), (java.time.Instant) waiting.getValue("nextAttemptDate")).toSeconds()).isBetween(119L, 121L);
      QRecord endpoint = WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0);
      assertThat(endpoint.getValueInteger("consecutiveFailures")).isEqualTo(0);
      assertThat(WebhooksTestSupport.all(WebhooksTables.ATTEMPT).get(0).getValueString("outcome")).isEqualTo("RATE_LIMITED");

      WebhooksTestSupport.CLOCK.advance(Duration.ofSeconds(60));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(1);
      WebhooksTestSupport.CLOCK.advance(Duration.ofSeconds(61));
      receiver.respond(r -> Receiver.Response.of(200));
      new DeliveryEngine().runOnce();
      assertThat(delivery(0).getValueString("status")).isEqualTo("DELIVERED");
   }



   /*******************************************************************************
    ** Serial endpoints deliver in order: a failing head blocks the rest.
    *******************************************************************************/
   @Test
   void serialDeliveryKeepsOrder() throws Exception
   {
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", endpointId).withValue("serialDelivery", true));
      List<String> order = new ArrayList<>();
      receiver.respond(r ->
      {
         String name = new JSONObject(r.text()).getJSONObject("data").getJSONObject("record").getString("firstName");
         order.add(name);
         return (Receiver.Response.of("P0".equals(name) && order.size() == 1 ? 500 : 200));
      });
      people(3);

      new DeliveryEngine().runOnce();
      assertThat(order).containsExactly("P0");
      new DeliveryEngine().runOnce();
      assertThat(order).containsExactly("P0");

      WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(2));
      for(int i = 0; i < 3; i++)
      {
         new DeliveryEngine().runOnce();
      }
      assertThat(order).containsExactly("P0", "P0", "P1", "P2");
   }



   /*******************************************************************************
    ** Max per minute caps a run; the rest go next minute.
    *******************************************************************************/
   @Test
   void perEndpointRateLimit() throws Exception
   {
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", endpointId).withValue("maxPerMinute", 2));
      people(5);
      DeliveryEngine.RunSummary summary = new DeliveryEngine().runOnce();
      assertThat(summary.getDelivered()).isEqualTo(2);
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(2);
      WebhooksTestSupport.CLOCK.advance(Duration.ofSeconds(61));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(4);
   }



   /*******************************************************************************
    ** Unhealthy after failures, probation, recovery; and auto-disable.
    *******************************************************************************/
   @Test
   void healthStateMachine() throws Exception
   {
      receiver.respond(r -> Receiver.Response.of(500));
      people(3);
      new DeliveryEngine().runOnce();
      QRecord endpoint = WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0);
      assertThat(endpoint.getValueString("healthStatus")).isEqualTo("UNHEALTHY");
      assertThat(endpoint.getValueInteger("consecutiveFailures")).isEqualTo(3);

      //////////////////////////////////////////
      // unhealthy endpoints get no attempts //
      //////////////////////////////////////////
      WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(2));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(3);

      WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(10));
      assertThat(EndpointHealth.promoteToProbation(com.kingsrook.qbits.webhooks.WebhooksRuntime.config())).containsExactly(endpointId);
      receiver.respond(r -> Receiver.Response.of(200));
      DeliveryEngine.RunSummary probation = new DeliveryEngine().runOnce();
      assertThat(probation.getDelivered()).isEqualTo(1);
      assertThat(WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0).getValueString("healthStatus")).isEqualTo("HEALTHY");

      ///////////////////////////////////////////////////////////////////
      // two failed probations disable it (autoDisableAfter = 2 in tests) //
      ///////////////////////////////////////////////////////////////////
      receiver.respond(r -> Receiver.Response.of(500));
      people(3);
      new DeliveryEngine().runOnce();
      for(int i = 0; i < 2; i++)
      {
         WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(11));
         EndpointHealth.promoteToProbation(com.kingsrook.qbits.webhooks.WebhooksRuntime.config());
         new DeliveryEngine().runOnce();
      }
      endpoint = WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0);
      assertThat(endpoint.getValueString("activeStatus")).isEqualTo("DISABLED");
      assertThat(endpoint.getValue("autoDisabledDate")).isNotNull();
      assertThat(endpoint.getValueInteger("probationFailures")).isEqualTo(2);
   }



   /*******************************************************************************
    ** A paused endpoint keeps queuing but sends nothing.
    *******************************************************************************/
   @Test
   void pausedEndpointQueuesButDoesNotSend() throws Exception
   {
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", endpointId).withValue("activeStatus", "PAUSED"));
      people(2);
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).isEmpty();
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(2);
   }
}
