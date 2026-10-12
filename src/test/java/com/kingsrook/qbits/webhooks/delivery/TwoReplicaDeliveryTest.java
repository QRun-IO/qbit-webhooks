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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kingsrook.qbits.webhooks.H2Support;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Two replicas (two QInstances, two runner threads each) deliver from the
 ** same SQL database at the same time. Every delivery reaches the receiver
 ** exactly once: the unique (delivery, attempt number) claim lets only one
 ** runner send each attempt.
 *******************************************************************************/
class TwoReplicaDeliveryTest
{
   private static final int DELIVERIES = 150;

   private RDBMSBackendMetaData backend;
   private QInstance            replicaA;
   private QInstance            replicaB;
   private Receiver             receiver;



   /*******************************************************************************
    ** Two replicas over one H2 database; deliveries queued through replica A.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksRuntime.setClock(java.time.Clock.systemUTC());
      WebhooksRuntime.setHostResolver(null);
      System.setProperty(WebhooksTestSupport.KEY_PROPERTY, java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
      backend = H2Support.backend("mem", "two_replicas");
      H2Support.prime(backend);
      replicaA = H2Support.instance(backend, config -> config.withDeliveryBatchSize(10));
      replicaB = H2Support.instance(backend, config -> config.withDeliveryBatchSize(10));
      receiver = new Receiver();
      receiver.respond(r ->
      {
         try
         {
            Thread.sleep(3);
         }
         catch(InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
         return (Receiver.Response.of(200));
      });

      QContext.init(replicaA, WebhooksTestSupport.adminSession());
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      for(int i = 0; i < DELIVERIES; i++)
      {
         WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "P" + i).withValue("lastName", "L"));
      }
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(DELIVERIES);
      QContext.clear();
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      receiver.close();
      WebhooksRuntime.setClock(WebhooksTestSupport.CLOCK);
   }



   /*******************************************************************************
    ** A runner: its replica's context, runs until nothing is due.
    *******************************************************************************/
   private Callable<Integer> runner(QInstance replica)
   {
      return (() ->
      {
         QContext.init(replica, WebhooksTestSupport.adminSession());
         int sent = 0;
         for(int i = 0; i < 200; i++)
         {
            DeliveryEngine.RunSummary summary = new DeliveryEngine().runOnce();
            sent += summary.getDelivered();
            if(summary.getAttempts() == 0 && summary.getClaimedElsewhere() == 0)
            {
               int left = SystemWrites.count(WebhooksTables.DELIVERY, new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter(
                  new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria("status",
                     com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator.NOT_EQUALS, "DELIVERED")));
               if(left == 0)
               {
                  break;
               }
            }
         }
         QContext.clear();
         return (sent);
      });
   }



   /*******************************************************************************
    ** No double sends.
    *******************************************************************************/
   @Test
   void everyDeliveryIsSentExactlyOnce() throws Exception
   {
      ExecutorService pool = Executors.newFixedThreadPool(4);
      List<Future<Integer>> futures = new ArrayList<>();
      futures.add(pool.submit(runner(replicaA)));
      futures.add(pool.submit(runner(replicaB)));
      futures.add(pool.submit(runner(replicaA)));
      futures.add(pool.submit(runner(replicaB)));
      int sent = 0;
      List<Integer> perRunner = new ArrayList<>();
      for(Future<Integer> future : futures)
      {
         int n = future.get();
         perRunner.add(n);
         sent += n;
      }
      pool.shutdown();

      List<Receiver.Request> requests = receiver.requests();
      Map<String, Long> byId = requests.stream().collect(Collectors.groupingBy(r -> r.headers().get("webhook-id"), Collectors.counting()));
      assertThat(byId).hasSize(DELIVERIES);
      assertThat(byId.values()).allMatch(count -> count == 1L);
      assertThat(requests).hasSize(DELIVERIES);
      assertThat(sent).isEqualTo(DELIVERIES);
      assertThat(perRunner.stream().filter(n -> n > 0).count()).as("work was shared: " + perRunner).isGreaterThan(1);

      QContext.init(replicaA, WebhooksTestSupport.adminSession());
      List<QRecord> attempts = WebhooksTestSupport.all(WebhooksTables.ATTEMPT);
      assertThat(attempts).hasSize(DELIVERIES);
      assertThat(attempts).allMatch(a -> "SUCCEEDED".equals(a.getValueString("outcome")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).allMatch(d -> "DELIVERED".equals(d.getValueString("status")));
   }



   /*******************************************************************************
    ** A runner that died mid-send: its lease expires, another runner takes
    ** the next attempt and marks the stale one.
    *******************************************************************************/
   @Test
   void expiredLeaseIsRecovered() throws Exception
   {
      QContext.init(replicaA, WebhooksTestSupport.adminSession());
      QRecord delivery = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0);
      Instant past = Instant.now().minus(Duration.ofMinutes(5));
      SystemWrites.insertOne(WebhooksTables.ATTEMPT, new QRecord().withValue("deliveryId", delivery.getValue("id")).withValue("endpointId", delivery.getValue("endpointId"))
         .withValue("attemptNumber", 1).withValue("outcome", "IN_FLIGHT").withValue("claimedBy", "dead-node").withValue("startDate", past).withValue("leaseExpiresDate", past));
      SystemWrites.updateOne(WebhooksTables.DELIVERY, new QRecord().withValue("id", delivery.getValue("id")).withValue("status", "SENDING")
         .withValue("attemptCount", 1).withValue("cycleAttemptCount", 1).withValue("leaseExpiresDate", past).withValue("nextAttemptDate", past));

      QContext.init(replicaB, WebhooksTestSupport.adminSession());
      Function<QRecord, String> outcome = a -> a.getValueString("outcome");
      DeliveryEngine.SendResult result = new DeliveryEngine().sendNow(delivery.getValueInteger("id"));
      assertThat(result).isEqualTo(DeliveryEngine.SendResult.DELIVERED);
      List<QRecord> attempts = WebhooksTestSupport.all(WebhooksTables.ATTEMPT).stream()
         .filter(a -> a.getValue("deliveryId").equals(delivery.getValue("id"))).toList();
      assertThat(attempts).extracting(outcome).containsExactly("LEASE_EXPIRED", "SUCCEEDED");
      assertThat(receiver.requests()).hasSize(1);
      QContext.clear();
   }
}
