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
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.HealthStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Endpoint health: HEALTHY, then UNHEALTHY after
 ** {@code unhealthyAfterConsecutiveFailures} failures in a row (no deliveries
 ** are attempted), then PROBATION after {@code probationAfterMinutes} (one
 ** attempt per run). A success returns it to HEALTHY; a failure on probation
 ** makes it UNHEALTHY again, and after {@code autoDisableAfterProbationFailures}
 ** such failures the endpoint is DISABLED. Each change raises or resolves an
 ** alert when the Alerts qbit is installed.
 **
 ** <p>Rate-limit (429) answers and policy blocks never count as failures.
 ** Counters are read-modify-write: with several replicas a count can be off by
 ** one, which only shifts the threshold slightly.</p>
 *******************************************************************************/
public final class EndpointHealth
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private EndpointHealth()
   {
   }



   /*******************************************************************************
    ** A delivery succeeded.
    *******************************************************************************/
   public static void recordSuccess(QRecord endpoint, Instant now) throws QException
   {
      QRecord update = new QRecord().withValue("id", endpoint.getValue("id")).withValue("lastSuccessDate", now);
      boolean wasUnwell = !HealthStatus.HEALTHY.name().equals(endpoint.getValueString("healthStatus"));
      if(wasUnwell || intValue(endpoint, "consecutiveFailures") > 0 || intValue(endpoint, "probationFailures") > 0)
      {
         update.withValue("consecutiveFailures", 0).withValue("probationFailures", 0)
            .withValue("healthStatus", HealthStatus.HEALTHY.name()).withValue("unhealthySinceDate", null);
      }
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, update);
      if(wasUnwell)
      {
         WebhooksAlerts.resolveUnhealthy(endpoint);
      }
   }



   /*******************************************************************************
    ** A delivery failed (a receiver or network failure).
    *******************************************************************************/
   public static void recordFailure(WebhooksQBitConfig config, QRecord endpoint, Instant now, String message) throws QException
   {
      int    consecutive = intValue(endpoint, "consecutiveFailures") + 1;
      String health      = endpoint.getValueString("healthStatus");
      QRecord update = new QRecord().withValue("id", endpoint.getValue("id"))
         .withValue("consecutiveFailures", consecutive)
         .withValue("lastFailureDate", now)
         .withValue("lastFailureMessage", DeliveryEngine.truncate(message, 500));

      if(HealthStatus.PROBATION.name().equals(health))
      {
         int probationFailures = intValue(endpoint, "probationFailures") + 1;
         update.withValue("probationFailures", probationFailures).withValue("healthStatus", HealthStatus.UNHEALTHY.name()).withValue("unhealthySinceDate", now);
         Integer limit = config.getAutoDisableAfterProbationFailures();
         if(limit != null && limit > 0 && probationFailures >= limit)
         {
            update.withValue("activeStatus", ActiveStatus.DISABLED.name()).withValue("autoDisabledDate", now);
            SystemWrites.updateOne(WebhooksTables.ENDPOINT, update);
            WebhooksAlerts.disabled(endpoint, probationFailures);
            return;
         }
      }
      else if(HealthStatus.HEALTHY.name().equals(health) && consecutive >= config.getUnhealthyAfterConsecutiveFailures())
      {
         update.withValue("healthStatus", HealthStatus.UNHEALTHY.name()).withValue("unhealthySinceDate", now);
         SystemWrites.updateOne(WebhooksTables.ENDPOINT, update);
         WebhooksAlerts.unhealthy(endpoint, consecutive, message);
         return;
      }
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, update);
   }



   /*******************************************************************************
    ** Move endpoints that have been UNHEALTHY long enough to PROBATION.
    ** Returns their ids.
    *******************************************************************************/
   public static List<Integer> promoteToProbation(WebhooksQBitConfig config) throws QException
   {
      Instant cutoff = WebhooksRuntime.clock().instant().minus(Duration.ofMinutes(config.getProbationAfterMinutes()));
      List<Integer> ids = new ArrayList<>();
      List<QRecord> updates = new ArrayList<>();
      for(QRecord endpoint : SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter()
         .withCriteria(new QFilterCriteria("healthStatus", QCriteriaOperator.EQUALS, HealthStatus.UNHEALTHY.name()))
         .withCriteria(new QFilterCriteria("activeStatus", QCriteriaOperator.EQUALS, ActiveStatus.ACTIVE.name()))
         .withCriteria(new QFilterCriteria("unhealthySinceDate", QCriteriaOperator.LESS_THAN_OR_EQUALS, cutoff))))
      {
         ids.add(endpoint.getValueInteger("id"));
         updates.add(new QRecord().withValue("id", endpoint.getValue("id")).withValue("healthStatus", HealthStatus.PROBATION.name()));
      }
      SystemWrites.update(WebhooksTables.ENDPOINT, updates, null);
      return (ids);
   }



   /*******************************************************************************
    ** An integer field, 0 when null.
    *******************************************************************************/
   private static int intValue(QRecord record, String field)
   {
      Integer value = record.getValueInteger(field);
      return (value == null ? 0 : value);
   }
}
