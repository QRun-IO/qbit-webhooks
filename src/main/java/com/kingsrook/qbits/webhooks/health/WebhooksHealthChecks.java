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


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DeliveryStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.HealthStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthCategory;
import com.kingsrook.qqq.backend.core.health.HealthCheck;
import com.kingsrook.qqq.backend.core.health.HealthCheckInput;
import com.kingsrook.qqq.backend.core.health.HealthCheckKind;
import com.kingsrook.qqq.backend.core.health.HealthCheckMetaData;
import com.kingsrook.qqq.backend.core.health.HealthCheckOutcome;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplier;
import com.kingsrook.qqq.backend.core.health.HealthCheckSupplierMetaData;
import com.kingsrook.qqq.backend.core.health.HealthChecksMetaData;
import com.kingsrook.qqq.backend.core.health.HealthDetails;
import com.kingsrook.qqq.backend.core.health.HealthMetric;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** Passive central checks over the existing endpoint state machine and outbox.
 *******************************************************************************/
public class WebhooksHealthChecks implements HealthCheckSupplier
{
   /*******************************************************************************
    ** Register the dispatcher and dynamic endpoint supplier from the producer.
    *******************************************************************************/
   public static void register(QInstance instance)
   {
      HealthChecksMetaData.ofOrWithNew(instance)
         .withCheck(definition("webhooks.dispatcher", WebhooksHealthChecks::dispatcher))
         .withSupplier(new HealthCheckSupplierMetaData().withNamePrefix("webhooks.endpoint").withComponent("webhooks")
            .withSupplier(new QCodeReference(WebhooksHealthChecks.class)));
   }



   /*******************************************************************************
    ** Keep auto-disabled endpoints visible until an operator recovers them.
    *******************************************************************************/
   @Override
   public List<HealthCheckMetaData> supply(QInstance qInstance) throws QException
   {
      List<HealthCheckMetaData> checks = new ArrayList<>();
      for(QRecord endpoint : SystemWrites.call(() -> new QueryAction().execute(new QueryInput(WebhooksTables.ENDPOINT)
         .withFilter(new QQueryFilter(new QFilterCriteria("activeStatus", QCriteriaOperator.IN, ActiveStatus.ACTIVE.name(), ActiveStatus.DISABLED.name())))
         .withFieldNamesToInclude(Set.of("id", "activeStatus", "autoDisabledDate")).withTimeoutSeconds(5)).getRecords()))
      {
         if(monitored(endpoint))
         {
            Integer id = endpoint.getValueInteger("id");
            checks.add(definition("webhooks.endpoint." + id.toString().toLowerCase(Locale.ROOT), input -> endpoint(input, id)));
         }
      }
      return (checks);
   }



   /*******************************************************************************
    ** Common metadata and a boundary that never exposes backend error text.
    *******************************************************************************/
   private static HealthCheckMetaData definition(String name, HealthCheck check)
   {
      return (new HealthCheckMetaData().withName(name).withLabel(name).withComponent("webhooks")
         .withCategory(HealthCategory.INTEGRATION).withKind(HealthCheckKind.DEPENDENCY).withIntervalSeconds(300).withTimeoutMs(5000)
         .withCheckInstance(input ->
         {
            if(expired(input))
            {
               return (HealthCheckOutcome.down("Health check timed out"));
            }
            try
            {
               HealthCheckOutcome outcome = check.run(input);
               return (expired(input) ? HealthCheckOutcome.down("Health check timed out") : outcome);
            }
            catch(Exception e)
            {
               return (HealthCheckOutcome.down("Unable to read webhook health"));
            }
         }));
   }



   /*******************************************************************************
    ** Read the latest EndpointHealth state, without probing or mutating it.
    *******************************************************************************/
   private static HealthCheckOutcome endpoint(HealthCheckInput input, Integer id) throws QException
   {
      List<QRecord> records = query(input, WebhooksTables.ENDPOINT,
         new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, id)).withLimit(1),
         Set.of("id", "activeStatus", "autoDisabledDate", "healthStatus", "consecutiveFailures", "unhealthySinceDate"));
      if(records.isEmpty() || !monitored(records.getFirst()))
      {
         return (HealthCheckOutcome.unknown("Endpoint is absent or disabled"));
      }
      QRecord endpoint = records.getFirst();
      String health = endpoint.getValueString("healthStatus");
      HealthCheckOutcome outcome;
      if(ActiveStatus.DISABLED.name().equals(endpoint.getValueString("activeStatus")))
      {
         outcome = HealthCheckOutcome.down("Endpoint automatically disabled");
      }
      else if(HealthStatus.HEALTHY.name().equals(health))
      {
         outcome = HealthCheckOutcome.up("Endpoint healthy");
      }
      else if(HealthStatus.UNHEALTHY.name().equals(health) || HealthStatus.PROBATION.name().equals(health))
      {
         outcome = HealthCheckOutcome.degraded("Endpoint unhealthy or on probation");
      }
      else
      {
         return (HealthCheckOutcome.unknown("Endpoint health is unavailable"));
      }
      // Only known state names may enter details, even if persisted data is corrupt.
      String safeHealth = List.of("HEALTHY", "UNHEALTHY", "PROBATION").contains(health == null ? "" : health) ? health : "UNKNOWN";
      Integer failures = endpoint.getValueInteger("consecutiveFailures");
      HealthDetails details = new HealthDetails().withValue("healthStatus", safeHealth)
         .withValue("consecutiveFailures", failures == null ? 0 : failures);
      Instant unhealthySince = endpoint.getValueInstant("unhealthySinceDate");
      if(unhealthySince != null)
      {
         details.withValue("unhealthySince", unhealthySince);
      }
      return (outcome.withDetails(details));
   }



   /*******************************************************************************
    ** Age of the oldest queued delivery, including retries and in-flight work.
    *******************************************************************************/
   private static HealthCheckOutcome dispatcher(HealthCheckInput input) throws QException
   {
      List<QRecord> records = query(input, WebhooksTables.DELIVERY,
         new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.IN,
            DeliveryStatus.PENDING.name(), DeliveryStatus.RETRY_WAIT.name(), DeliveryStatus.SENDING.name()))
            .withOrderBy(new QFilterOrderBy("createDate", true)).withLimit(1), Set.of("createDate"));
      long seconds = 0;
      if(!records.isEmpty())
      {
         Instant queued = records.getFirst().getValueInstant("createDate");
         if(queued == null)
         {
            return (HealthCheckOutcome.unknown("Pending delivery age is unavailable"));
         }
         seconds = Math.max(0, Duration.between(queued, WebhooksRuntime.clock().instant()).getSeconds());
      }
      HealthCheckOutcome outcome = seconds > 3600 ? HealthCheckOutcome.down("Delivery backlog exceeds one hour")
         : seconds > 900 ? HealthCheckOutcome.degraded("Delivery backlog exceeds fifteen minutes") : HealthCheckOutcome.up("Delivery backlog within threshold");
      return (outcome.withMetrics(List.of(new HealthMetric().withName("oldestPendingSeconds").withValue(seconds).withUnit("seconds"))));
   }



   /*******************************************************************************
    ** Only active or automatically disabled endpoints participate.
    *******************************************************************************/
   private static boolean monitored(QRecord endpoint)
   {
      String active = endpoint.getValueString("activeStatus");
      return (ActiveStatus.ACTIVE.name().equals(active)
         || (ActiveStatus.DISABLED.name().equals(active) && endpoint.getValue("autoDisabledDate") != null));
   }



   /*******************************************************************************
    ** Query only allow-listed fields with the remaining database time budget.
    ** The runner enforces the precise deadline when the backend blocks.
    *******************************************************************************/
   private static List<QRecord> query(HealthCheckInput input, String table, QQueryFilter filter, Set<String> fields) throws QException
   {
      int seconds = (int) Math.max(1, Math.min(5, Duration.between(Instant.now(), input.getDeadline()).toSeconds()));
      return (SystemWrites.call(() -> new QueryAction().execute(new QueryInput(table).withFilter(filter)
         .withFieldNamesToInclude(fields).withTimeoutSeconds(seconds)).getRecords()));
   }



   /*******************************************************************************
    ** Use wall time for the runner deadline, independently of the domain clock.
    *******************************************************************************/
   private static boolean expired(HealthCheckInput input)
   {
      return (!Instant.now().isBefore(input.getDeadline()));
   }
}
