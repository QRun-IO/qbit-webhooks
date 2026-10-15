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

package com.kingsrook.qbits.webhooks.health;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.delivery.EndpointHealth;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.actions.tables.QueryOrGetInputInterface;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Passive health checks use memory records and never contact a receiver.
 *******************************************************************************/
class WebhooksHealthChecksTest
{
   private static final String SENTINEL = "S3NT1NEL-secret";
   private QInstance instance;



   /*******************************************************************************
    ** Set up the producer and a sentinel credential reference.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      instance = WebhooksTestSupport.setUp();
      System.setProperty("WEBHOOKS_HEALTH_SECRET", SENTINEL + "-long-enough");
   }



   /*******************************************************************************
    ** Remove the test credential.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      System.clearProperty("WEBHOOKS_HEALTH_SECRET");
   }



   /*******************************************************************************
    ** Supplier refresh finds new endpoints and excludes manual disable/pause.
    *******************************************************************************/
   @Test
   void registrationAndDynamicDiscovery() throws Exception
   {
      HealthChecksMetaData registry = HealthChecksMetaData.ofOrWithNew(instance);
      assertThat(registry.getChecks()).extracting(HealthCheckMetaData::getName).containsExactly("webhooks.dispatcher");
      assertThat(registry.getSuppliers()).hasSize(1);
      assertThat(registry.getSuppliers().getFirst().getNamePrefix()).isEqualTo("webhooks.endpoint");
      assertThat(registry.getSuppliers().getFirst().getComponent()).isEqualTo("webhooks");
      assertThat(registry.resolveAll(instance)).hasSize(1);
      Integer active = endpoint();
      Integer disabled = endpoint();
      update(disabled, "activeStatus", "DISABLED");
      Integer paused = endpoint();
      update(paused, "activeStatus", "PAUSED");
      Integer automatic = endpoint();
      update(automatic, "activeStatus", "DISABLED");
      update(automatic, "autoDisabledDate", WebhooksTestSupport.CLOCK.instant());
      QContext.init(instance, new QSession());
      assertThat(registry.resolveAll(instance)).extracting(HealthCheckMetaData::getName)
         .containsExactlyInAnyOrder("webhooks.dispatcher", "webhooks.endpoint." + active, "webhooks.endpoint." + automatic);
      assertThat(registry.resolveAll(instance)).allSatisfy(check ->
      {
         assertThat(check.getKind()).isEqualTo(HealthCheckKind.DEPENDENCY);
         assertThat(check.getCategory()).isEqualTo(HealthCategory.INTEGRATION);
         assertThat(check.getComponent()).isEqualTo("webhooks");
         assertThat(check.getIntervalSeconds()).isEqualTo(300);
         assertThat(check.getTimeoutMs()).isLessThan(300000);
         assertThat(check.getIncludeInReadiness()).isFalse();
      });
   }



   /*******************************************************************************
    ** Read fresh persisted states and keep the existing state machine intact.
    *******************************************************************************/
   @Test
   void endpointStatesAndRecovery() throws Exception
   {
      Integer id = endpoint();
      HealthCheckMetaData check = check("webhooks.endpoint." + id);
      assertOutcome(check, HealthStatus.UP);
      for(String state : List.of("UNHEALTHY", "PROBATION"))
      {
         update(id, "healthStatus", state);
         update(id, "consecutiveFailures", 3);
         update(id, "unhealthySinceDate", WebhooksTestSupport.CLOCK.instant());
         HealthCheckOutcome outcome = assertOutcome(check, HealthStatus.DEGRADED);
         assertThat(outcome.getDetails().getValues()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "healthStatus", state, "consecutiveFailures", 3, "unhealthySince", WebhooksTestSupport.CLOCK.instant()));
      }
      update(id, "probationFailures", 1);
      QRecord row = SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter()).getFirst();
      EndpointHealth.recordFailure(WebhooksRuntime.config(), row, WebhooksTestSupport.CLOCK.instant(), SENTINEL);
      assertOutcome(check, HealthStatus.DOWN);
      assertThat(check("webhooks.endpoint." + id)).isNotNull();
      update(id, "activeStatus", "ACTIVE");
      EndpointHealth.recordSuccess(row, WebhooksTestSupport.CLOCK.instant());
      assertOutcome(check, HealthStatus.UP);
      update(id, "healthStatus", SENTINEL);
      assertOutcome(check, HealthStatus.UNKNOWN);
      update(id, "healthStatus", null);
      assertOutcome(check, HealthStatus.UNKNOWN);
      update(id, "activeStatus", "DISABLED");
      assertOutcome(check, HealthStatus.DOWN);
      update(id, "autoDisabledDate", null);
      assertOutcome(check, HealthStatus.UNKNOWN);
      SystemWrites.delete(WebhooksTables.ENDPOINT, new QQueryFilter());
      assertOutcome(check, HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** Strict thresholds and terminal-status exclusion across every queue state.
    *******************************************************************************/
   @Test
   void dispatcherThresholds() throws Exception
   {
      HealthCheckMetaData check = check("webhooks.dispatcher");
      assertThat(assertOutcome(check, HealthStatus.UP).getMetrics().getFirst().getValue().longValue()).isZero();
      for(String terminal : List.of("DELIVERED", "CANCELLED", "DEAD_LETTER"))
      {
         delivery(terminal, 10000);
      }
      assertOutcome(check, HealthStatus.UP);
      for(String status : List.of("PENDING", "RETRY_WAIT", "SENDING"))
      {
         for(long age : new long[] {-10, 0, 900, 901, 3600, 3601})
         {
            QRecord record = delivery(status, age);
            HealthCheckOutcome outcome = assertOutcome(check, age > 3600 ? HealthStatus.DOWN : age > 900 ? HealthStatus.DEGRADED : HealthStatus.UP);
            assertThat(outcome.getMetrics().getFirst().getName()).isEqualTo("oldestPendingSeconds");
            assertThat(outcome.getMetrics().getFirst().getValue().longValue()).isEqualTo(Math.max(0, age));
            SystemWrites.updateOne(WebhooksTables.DELIVERY, new QRecord().withValue("id", record.getValue("id")).withValue("status", "DELIVERED"));
         }
      }
      delivery("PENDING", 4000);
      delivery("PENDING", 1);
      assertThat(assertOutcome(check, HealthStatus.DOWN).getMetrics().getFirst().getValue().longValue()).isEqualTo(4000);
   }



   /*******************************************************************************
    ** A missing queue timestamp cannot be reported as a healthy backlog.
    *******************************************************************************/
   @Test
   void missingQueueTimestamp() throws Exception
   {
      QRecord record = delivery("PENDING", 0);
      MemoryRecordStore.getInstance().update(new UpdateInput(WebhooksTables.DELIVERY).withRecords(List.of(
         new QRecord().withValue("id", record.getValue("id")).withValue("createDate", null))), false);
      assertOutcome(check("webhooks.dispatcher"), HealthStatus.UNKNOWN);
   }



   /*******************************************************************************
    ** No reads start after the deadline; late responses are not accepted as UP.
    *******************************************************************************/
   @Test
   void deadlines() throws Exception
   {
      endpoint();
      delivery("PENDING", 0);
      for(HealthCheckMetaData check : HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance))
      {
         assertThat(check.getCheckInstance().run(input().withDeadline(Instant.now().minusSeconds(1))).getStatus()).isEqualTo(HealthStatus.DOWN);
      }
      instance.getTable(WebhooksTables.DELIVERY).withCustomizer(TableCustomizers.POST_QUERY_RECORD, new QCodeReference(SlowQuery.class));
      HealthCheckOutcome outcome = check("webhooks.dispatcher").getCheckInstance().run(input().withDeadline(Instant.now().plusMillis(50)));
      assertThat(outcome.getStatus()).isEqualTo(HealthStatus.DOWN);
      assertThat(outcome.getMessage()).contains("timed out");
   }



   /*******************************************************************************
    ** Exception messages, URLs, failure text and credentials never leave checks.
    *******************************************************************************/
   @Test
   void secretBearingFailures() throws Exception
   {
      Integer id = endpoint();
      HealthCheckMetaData endpoint = check("webhooks.endpoint." + id);
      delivery("PENDING", 0);
      assertOutcome(endpoint, HealthStatus.UP);
      assertOutcome(check("webhooks.dispatcher"), HealthStatus.UP);
      for(String table : List.of(WebhooksTables.ENDPOINT, WebhooksTables.DELIVERY))
      {
         instance.getTable(table).withCustomizer(TableCustomizers.POST_QUERY_RECORD, new QCodeReference(FailingQuery.class));
      }
      assertOutcome(endpoint, HealthStatus.DOWN);
      assertOutcome(HealthChecksMetaData.ofOrWithNew(instance).getChecks().getFirst(), HealthStatus.DOWN);
   }



   /*******************************************************************************
    ** Persist a sentinel credential without ever resolving it for a health check.
    *******************************************************************************/
   private Integer endpoint() throws Exception
   {
      Integer id = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", SENTINEL + UUID.randomUUID())
         .withValue("url", "https://example.invalid/" + SENTINEL)).getValueInteger("id");
      WebhooksTestSupport.insert(WebhooksTables.SECRET, new QRecord().withValue("endpointId", id)
         .withValue("source", "REFERENCE").withValue("secretRef", "WEBHOOKS_HEALTH_SECRET"));
      update(id, "lastFailureMessage", SENTINEL);
      return (id);
   }



   /*******************************************************************************
    ** Change an existing domain field as the qbit.
    *******************************************************************************/
   private void update(Integer id, String field, java.io.Serializable value) throws Exception
   {
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", id).withValue(field, value));
   }



   /*******************************************************************************
    ** Insert a pending or terminal delivery with deterministic queue time.
    *******************************************************************************/
   private QRecord delivery(String status, long age) throws Exception
   {
      QRecord record = SystemWrites.insertOne(WebhooksTables.DELIVERY, new QRecord().withValue("status", status)
         .withValue("payload", SENTINEL).withValue("lastError", SENTINEL));
      MemoryRecordStore.getInstance().update(new UpdateInput(WebhooksTables.DELIVERY).withRecords(List.of(
         new QRecord().withValue("id", record.getValue("id")).withValue("createDate", WebhooksTestSupport.CLOCK.instant().minusSeconds(age)))), false);
      return (record);
   }



   /*******************************************************************************
    ** Find a producer-registered check, resolving the supplier afresh.
    *******************************************************************************/
   private HealthCheckMetaData check(String name) throws Exception
   {
      return (HealthChecksMetaData.ofOrWithNew(instance).resolveAll(instance).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** Bounded runner input.
    *******************************************************************************/
   private HealthCheckInput input()
   {
      return (new HealthCheckInput().withQInstance(instance).withDeadline(Instant.now().plusSeconds(5)));
   }



   /*******************************************************************************
    ** Assert raw outcomes are clean; HealthRunner.sanitize is package-private.
    *******************************************************************************/
   private HealthCheckOutcome assertOutcome(HealthCheckMetaData check, HealthStatus status) throws Exception
   {
      HealthCheckOutcome outcome = check.getCheckInstance().run(input());
      assertThat(outcome.getStatus()).isEqualTo(status);
      assertThat(outcome.getMessage()).doesNotContain(SENTINEL).doesNotContain("example.invalid").doesNotContain("\n");
      assertThat(outcome.getDetails().toJson()).doesNotContain(SENTINEL).doesNotContain("example.invalid");
      return (outcome);
   }



   /*******************************************************************************
    ** A backend failure containing sensitive text.
    *******************************************************************************/
   public static class FailingQuery implements TableCustomizerInterface
   {
      /***************************************************************************
       ** Throw after the read, before the check sees any records.
       ***************************************************************************/
      @Override
      public List<QRecord> postQuery(QueryOrGetInputInterface input, List<QRecord> records) throws QException
      {
         throw (new QException(SENTINEL));
      }
   }



   /*******************************************************************************
    ** A database that responds after the check's deadline.
    *******************************************************************************/
   public static class SlowQuery implements TableCustomizerInterface
   {
      /***************************************************************************
       ** Simulate a slow database, with no network.
       ***************************************************************************/
      @Override
      public List<QRecord> postQuery(QueryOrGetInputInterface input, List<QRecord> records) throws QException
      {
         try
         {
            Thread.sleep(100);
         }
         catch(InterruptedException e)
         {
            Thread.currentThread().interrupt();
            throw (new QException("Interrupted", e));
         }
         return (records);
      }
   }
}
