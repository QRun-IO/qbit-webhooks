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


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.AttemptOutcome;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DeliveryStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.HealthStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpClient;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpException;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpResponse;
import com.kingsrook.qbits.webhooks.shared.signing.WebhookSecret;
import com.kingsrook.qbits.webhooks.shared.signing.WebhookSigner;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Sends due deliveries. Safe to run on any number of replicas at once.
 **
 ** <h2>Claiming (no double send)</h2>
 ** <p>To send attempt <i>n</i> of a delivery, a runner first inserts the
 ** {@code webhooks_delivery_attempt} row ({@code deliveryId}, <i>n</i>), which
 ** is unique. Exactly one runner's insert succeeds; the others get a duplicate
 ** and skip. Only the winner sends, then records the outcome. This is a
 ** compare-and-set on the attempt number that works on every SQL backend, with
 ** no {@code SELECT ... FOR UPDATE} needed.</p>
 **
 ** <h2>Leases</h2>
 ** <p>A claimed delivery is SENDING with a lease (config {@code leaseSeconds},
 ** longer than the total HTTP timeout). If its runner dies, the lease expires,
 ** the stale attempt is marked LEASE_EXPIRED, and the next run claims attempt
 ** <i>n+1</i>. So delivery is at least once: a runner that died after the
 ** receiver got the request but before recording it causes one repeat, with
 ** the same {@code webhook-id}.</p>
 **
 ** <h2>Outcomes</h2>
 ** <ul>
 **   <li>2xx: DELIVERED.</li>
 **   <li>429: waits for Retry-After (capped), does not count against the
 **       endpoint's health or the delivery's attempt budget.</li>
 **   <li>Anything else (3xx included, redirects are not followed), a network
 **       failure or a policy block: RETRY_WAIT with backoff; after
 **       {@code maxAttempts} since the last replay, DEAD_LETTER.</li>
 ** </ul>
 **
 ** <h2>Ordering</h2>
 ** <p>By default there is no ordering guarantee (a failing delivery backs off
 ** while later ones go). An endpoint with {@code serialDelivery} gets one
 ** delivery at a time, oldest first; a failing one blocks the rest until it
 ** is delivered or dead-lettered.</p>
 *******************************************************************************/
public class DeliveryEngine
{
   private static final QLogger LOG = QLogger.getLogger(DeliveryEngine.class);

   private static final int MAX_EXCERPT = 2000;
   private static final int MAX_ERROR   = 1000;

   private static final List<String> DUE_STATUSES = List.of(DeliveryStatus.PENDING.name(), DeliveryStatus.RETRY_WAIT.name());



   /*******************************************************************************
    ** What a run did.
    *******************************************************************************/
   public static class RunSummary
   {
      private int delivered;
      private int failed;
      private int deadLettered;
      private int rateLimited;
      private int claimedElsewhere;
      private int skippedForRateLimit;
      private int waitingForSecret;



      /*******************************************************************************
       ** Getter for delivered.
       *******************************************************************************/
      public int getDelivered()
      {
         return (delivered);
      }



      /*******************************************************************************
       ** Getter for failed (including dead-lettered).
       *******************************************************************************/
      public int getFailed()
      {
         return (failed);
      }



      /*******************************************************************************
       ** Getter for deadLettered.
       *******************************************************************************/
      public int getDeadLettered()
      {
         return (deadLettered);
      }



      /*******************************************************************************
       ** Getter for rateLimited (429 from the receiver).
       *******************************************************************************/
      public int getRateLimited()
      {
         return (rateLimited);
      }



      /*******************************************************************************
       ** Getter for claimedElsewhere (another runner had it).
       *******************************************************************************/
      public int getClaimedElsewhere()
      {
         return (claimedElsewhere);
      }



      /*******************************************************************************
       ** Getter for skippedForRateLimit (the endpoint's own per-minute cap).
       *******************************************************************************/
      public int getSkippedForRateLimit()
      {
         return (skippedForRateLimit);
      }



      /*******************************************************************************
       ** Getter for waitingForSecret (due, but the endpoint has no usable
       ** signing secret and does not allow unsigned delivery: not attempted).
       *******************************************************************************/
      public int getWaitingForSecret()
      {
         return (waitingForSecret);
      }



      /*******************************************************************************
       ** Total attempts made.
       *******************************************************************************/
      public int getAttempts()
      {
         return (delivered + failed + rateLimited);
      }
   }



   /*******************************************************************************
    ** Result of sending one delivery now.
    *******************************************************************************/
   public enum SendResult
   {
      DELIVERED,
      FAILED,
      CLAIMED_ELSEWHERE,
      NOT_SENT
   }



   /*******************************************************************************
    ** Send one delivery now (a person asked): ignores its next-attempt time and
    ** the endpoint's health, but not a disabled endpoint or a finished delivery.
    *******************************************************************************/
   public SendResult sendNow(Integer deliveryId) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      List<QRecord> deliveries = SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, deliveryId)));
      if(config == null || deliveries.isEmpty())
      {
         return (SendResult.NOT_SENT);
      }
      QRecord delivery = deliveries.get(0);
      String  status   = delivery.getValueString("status");
      if(!DUE_STATUSES.contains(status) && !DeliveryStatus.SENDING.name().equals(status))
      {
         return (SendResult.NOT_SENT);
      }
      if(DeliveryStatus.SENDING.name().equals(status))
      {
         Instant lease = ValueUtils.getValueAsInstant(delivery.getValue("leaseExpiresDate"));
         if(lease != null && lease.isAfter(WebhooksRuntime.clock().instant()))
         {
            return (SendResult.CLAIMED_ELSEWHERE);
         }
      }
      List<QRecord> endpoints = SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, delivery.getValue("endpointId"))));
      if(endpoints.isEmpty() || ActiveStatus.DISABLED.name().equals(endpoints.get(0).getValueString("activeStatus")))
      {
         return (SendResult.NOT_SENT);
      }
      Attempt attempt = claim(config, delivery, endpoints.get(0));
      if(attempt == null)
      {
         return (SendResult.CLAIMED_ELSEWHERE);
      }
      RunSummary summary = new RunSummary();
      send(config, attempt, delivery, endpoints.get(0), summary);
      return (summary.getDelivered() > 0 ? SendResult.DELIVERED : SendResult.FAILED);
   }



   /*******************************************************************************
    ** Send everything due, for every active endpoint.
    *******************************************************************************/
   public RunSummary runOnce() throws QException
   {
      return (run(null));
   }



   /*******************************************************************************
    ** Send everything due for some endpoints (null: all).
    *******************************************************************************/
   public RunSummary run(List<Integer> endpointIds) throws QException
   {
      WebhooksQBitConfig config  = WebhooksRuntime.config();
      RunSummary         summary = new RunSummary();
      if(config == null)
      {
         return (summary);
      }
      QQueryFilter endpointFilter = new QQueryFilter()
         .withCriteria(new QFilterCriteria("activeStatus", QCriteriaOperator.EQUALS, ActiveStatus.ACTIVE.name()))
         .withCriteria(new QFilterCriteria("healthStatus", QCriteriaOperator.NOT_EQUALS, HealthStatus.UNHEALTHY.name()))
         .withOrderBy(new QFilterOrderBy("id"));
      if(endpointIds != null)
      {
         if(endpointIds.isEmpty())
         {
            return (summary);
         }
         endpointFilter.withCriteria(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<>(endpointIds)));
      }

      int budget = config.getDeliveryBatchSize();
      for(QRecord endpoint : SystemWrites.query(WebhooksTables.ENDPOINT, endpointFilter))
      {
         if(budget <= 0)
         {
            break;
         }
         budget -= runEndpoint(config, endpoint, budget, summary);
      }
      return (summary);
   }



   /*******************************************************************************
    ** Send due deliveries for one endpoint; returns how many were attempted.
    *******************************************************************************/
   int runEndpoint(WebhooksQBitConfig config, QRecord endpoint, int budget, RunSummary summary) throws QException
   {
      Integer endpointId = endpoint.getValueInteger("id");
      Instant now        = WebhooksRuntime.clock().instant();
      int     limit      = budget;

      Integer perMinute = endpoint.getValueInteger("maxPerMinute");
      if(perMinute != null && perMinute > 0)
      {
         int recent = SystemWrites.count(WebhooksTables.ATTEMPT, new QQueryFilter()
            .withCriteria(new QFilterCriteria("endpointId", QCriteriaOperator.EQUALS, endpointId))
            .withCriteria(new QFilterCriteria("startDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(Duration.ofMinutes(1)))));
         limit = Math.min(limit, perMinute - recent);
      }
      if(HealthStatus.PROBATION.name().equals(endpoint.getValueString("healthStatus")))
      {
         limit = Math.min(limit, 1);
      }

      List<QRecord> due = Boolean.TRUE.equals(endpoint.getValueBoolean("serialDelivery")) ? serialHead(endpointId, now) : due(endpointId, now, Math.max(limit, 1));
      if(limit <= 0)
      {
         summary.skippedForRateLimit += due.size();
         return (0);
      }
      if(!due.isEmpty() && !Boolean.TRUE.equals(endpoint.getValueBoolean("allowUnsigned")) && EndpointSecrets.signingKeys(endpointId).isEmpty())
      {
         summary.waitingForSecret += due.size();
         return (0);
      }

      int attempted = 0;
      for(QRecord delivery : due)
      {
         if(attempted >= limit)
         {
            summary.skippedForRateLimit++;
            continue;
         }
         Attempt attempt = claim(config, delivery, endpoint);
         if(attempt == null)
         {
            summary.claimedElsewhere++;
            continue;
         }
         attempted++;
         send(config, attempt, delivery, endpoint, summary);
         endpoint = SystemWrites.query(WebhooksTables.ENDPOINT, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, endpointId))).get(0);
         if(!ActiveStatus.ACTIVE.name().equals(endpoint.getValueString("activeStatus")) || HealthStatus.UNHEALTHY.name().equals(endpoint.getValueString("healthStatus")))
         {
            break;
         }
      }
      return (attempted);
   }



   /*******************************************************************************
    ** Due deliveries: pending or waiting and due, or sending with an expired
    ** lease; oldest first.
    *******************************************************************************/
   private static List<QRecord> due(Integer endpointId, Instant now, int limit) throws QException
   {
      QQueryFilter filter = new QQueryFilter()
         .withCriteria(new QFilterCriteria("endpointId", QCriteriaOperator.EQUALS, endpointId))
         .withSubFilters(List.of(new QQueryFilter().withBooleanOperator(QQueryFilter.BooleanOperator.OR)
            .withSubFilters(List.of(
               new QQueryFilter()
                  .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, new ArrayList<>(DUE_STATUSES)))
                  .withCriteria(new QFilterCriteria("nextAttemptDate", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)),
               new QQueryFilter()
                  .withCriteria(new QFilterCriteria("status", QCriteriaOperator.EQUALS, DeliveryStatus.SENDING.name()))
                  .withCriteria(new QFilterCriteria("leaseExpiresDate", QCriteriaOperator.LESS_THAN_OR_EQUALS, now))))))
         .withOrderBy(new QFilterOrderBy("id"))
         .withLimit(limit);
      return (SystemWrites.query(WebhooksTables.DELIVERY, filter));
   }



   /*******************************************************************************
    ** Serial endpoints: the oldest unfinished delivery, if it is due.
    *******************************************************************************/
   private static List<QRecord> serialHead(Integer endpointId, Instant now) throws QException
   {
      List<QRecord> head = SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter()
         .withCriteria(new QFilterCriteria("endpointId", QCriteriaOperator.EQUALS, endpointId))
         .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, DeliveryStatus.PENDING.name(), DeliveryStatus.RETRY_WAIT.name(), DeliveryStatus.SENDING.name()))
         .withOrderBy(new QFilterOrderBy("id"))
         .withLimit(1));
      if(head.isEmpty())
      {
         return (head);
      }
      QRecord delivery = head.get(0);
      boolean sending  = DeliveryStatus.SENDING.name().equals(delivery.getValueString("status"));
      Instant when     = ValueUtils.getValueAsInstant(delivery.getValue(sending ? "leaseExpiresDate" : "nextAttemptDate"));
      return (when == null || !when.isAfter(now) ? head : List.of());
   }



   /*******************************************************************************
    ** A claimed attempt.
    *******************************************************************************/
   record Attempt(Integer id, int number, Instant start)
   {
   }



   /*******************************************************************************
    ** Claim the next attempt of a delivery, or null if another runner has it.
    *******************************************************************************/
   Attempt claim(WebhooksQBitConfig config, QRecord delivery, QRecord endpoint) throws QException
   {
      Integer deliveryId = delivery.getValueInteger("id");
      int     previous   = ValueUtils.getValueAsInteger(delivery.getValue("attemptCount")) == null ? 0 : delivery.getValueInteger("attemptCount");
      int     number     = previous + 1;
      Instant now        = WebhooksRuntime.clock().instant();
      Instant lease      = now.plusSeconds(config.getLeaseSeconds());

      QRecord inserted;
      try
      {
         inserted = SystemWrites.insertOne(WebhooksTables.ATTEMPT, new QRecord()
            .withValue("deliveryId", deliveryId)
            .withValue("endpointId", endpoint.getValue("id"))
            .withValue("attemptNumber", number)
            .withValue("outcome", AttemptOutcome.IN_FLIGHT.name())
            .withValue("claimedBy", WebhooksRuntime.nodeId())
            .withValue("leaseExpiresDate", lease)
            .withValue("startDate", now)
            .withValue("requestUrl", truncate(endpoint.getValueString("url"), 500)));
      }
      catch(QException e)
      {
         LOG.debug("Lost the claim for a webhook delivery attempt", logPair("deliveryId", deliveryId), logPair("attempt", number));
         return (null);
      }
      if(inserted == null || CollectionUtils.nullSafeHasContents(inserted.getErrors()) || inserted.getValueInteger("id") == null)
      {
         return (null);
      }

      if(previous > 0 && DeliveryStatus.SENDING.name().equals(delivery.getValueString("status")))
      {
         List<QRecord> stale = SystemWrites.query(WebhooksTables.ATTEMPT, new QQueryFilter()
            .withCriteria(new QFilterCriteria("deliveryId", QCriteriaOperator.EQUALS, deliveryId))
            .withCriteria(new QFilterCriteria("attemptNumber", QCriteriaOperator.EQUALS, previous))
            .withCriteria(new QFilterCriteria("outcome", QCriteriaOperator.EQUALS, AttemptOutcome.IN_FLIGHT.name())));
         for(QRecord row : stale)
         {
            SystemWrites.updateOne(WebhooksTables.ATTEMPT, new QRecord().withValue("id", row.getValue("id"))
               .withValue("outcome", AttemptOutcome.LEASE_EXPIRED.name()).withValue("endDate", now)
               .withValue("errorMessage", "The runner that claimed this attempt stopped before recording an outcome; the delivery was retried."));
         }
      }

      int cycle = delivery.getValueInteger("cycleAttemptCount") == null ? 0 : delivery.getValueInteger("cycleAttemptCount");
      SystemWrites.updateOne(WebhooksTables.DELIVERY, new QRecord()
         .withValue("id", deliveryId)
         .withValue("status", DeliveryStatus.SENDING.name())
         .withValue("attemptCount", number)
         .withValue("cycleAttemptCount", cycle + 1)
         .withValue("leaseExpiresDate", lease)
         .withValue("nextAttemptDate", lease));
      delivery.setValue("cycleAttemptCount", cycle + 1);
      return (new Attempt(inserted.getValueInteger("id"), number, now));
   }



   /*******************************************************************************
    ** Send a claimed attempt and record the outcome.
    *******************************************************************************/
   void send(WebhooksQBitConfig config, Attempt attempt, QRecord delivery, QRecord endpoint, RunSummary summary) throws QException
   {
      Integer endpointId = endpoint.getValueInteger("id");
      byte[]  body       = delivery.getValueString("payload") == null ? new byte[0] : delivery.getValueString("payload").getBytes(StandardCharsets.UTF_8);

      SafeHttpResponse response = null;
      String           error    = null;
      boolean          policy   = false;
      String           signedWith = null;
      try
      {
         List<EndpointSecrets.SigningKey> keys = EndpointSecrets.signingKeys(endpointId);
         Map<String, String> headers = new LinkedHashMap<>();
         headers.put("Content-Type", "application/json");
         headers.put("webhook-attempt", String.valueOf(attempt.number()));
         if(keys.isEmpty())
         {
            if(!Boolean.TRUE.equals(endpoint.getValueBoolean("allowUnsigned")))
            {
               throw (new SafeHttpException(SafeHttpException.Reason.INVALID_URL,
                  "Not sent: this endpoint has no usable signing secret (rotate one in, or allow unsigned delivery)"));
            }
            headers.put(WebhookSigner.HEADER_ID, delivery.getValueString("eventId"));
            headers.put(WebhookSigner.HEADER_TIMESTAMP, String.valueOf(WebhooksRuntime.clock().instant().getEpochSecond()));
         }
         else
         {
            List<WebhookSecret> secrets = keys.stream().map(EndpointSecrets.SigningKey::secret).toList();
            headers.putAll(WebhookSigner.headers(delivery.getValueString("eventId"), WebhooksRuntime.clock().instant(), body, secrets));
            signedWith = String.join(" ", keys.stream().map(EndpointSecrets.SigningKey::fingerprint).toList());
         }
         SafeHttpClient client = WebhooksRuntime.httpClient(WebhooksRuntime.policyFor(config, endpoint.getValueInteger("timeoutSeconds"), allowlist(endpoint)));
         response = client.post(endpoint.getValueString("url"), headers, body);
      }
      catch(SafeHttpException e)
      {
         error = e.getMessage();
         policy = e.getReason().isPolicy();
      }
      catch(Exception e)
      {
         error = "Unexpected error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
         LOG.warn("Unexpected error sending a webhook", e, logPair("deliveryId", delivery.getValue("id")));
      }

      Instant end      = WebhooksRuntime.clock().instant();
      long    duration = response != null ? response.durationMillis() : Math.max(0, Duration.between(attempt.start(), end).toMillis());

      AttemptOutcome outcome;
      if(response != null && response.isSuccess())
      {
         outcome = AttemptOutcome.SUCCEEDED;
      }
      else if(response != null && response.statusCode() == 429)
      {
         outcome = AttemptOutcome.RATE_LIMITED;
      }
      else if(policy)
      {
         outcome = AttemptOutcome.BLOCKED;
      }
      else
      {
         outcome = AttemptOutcome.FAILED;
      }
      if(response != null && !response.isSuccess() && error == null)
      {
         error = "HTTP " + response.statusCode() + (response.statusCode() >= 300 && response.statusCode() < 400 ? " (redirects are not followed)" : "");
      }

      SystemWrites.updateOne(WebhooksTables.ATTEMPT, new QRecord()
         .withValue("id", attempt.id())
         .withValue("outcome", outcome.name())
         .withValue("endDate", end)
         .withValue("durationMillis", (int) duration)
         .withValue("httpStatus", response == null ? null : response.statusCode())
         .withValue("responseExcerpt", response == null ? null : response.bodyExcerpt(MAX_EXCERPT) + (response.truncated() ? "\n[truncated]" : ""))
         .withValue("errorMessage", truncate(error, MAX_ERROR))
         .withValue("remoteAddress", response == null ? null : response.remoteAddress())
         .withValue("signedWith", signedWith));

      QRecord update = new QRecord()
         .withValue("id", delivery.getValue("id"))
         .withValue("leaseExpiresDate", null)
         .withValue("lastHttpStatus", response == null ? null : response.statusCode())
         .withValue("lastError", truncate(error, 500));

      switch(outcome)
      {
         case SUCCEEDED ->
         {
            update.withValue("status", DeliveryStatus.DELIVERED.name()).withValue("deliveredDate", end).withValue("nextAttemptDate", null);
            summary.delivered++;
            EndpointHealth.recordSuccess(endpoint, end);
         }
         case RATE_LIMITED ->
         {
            int cycle = delivery.getValueInteger("cycleAttemptCount") == null ? 1 : delivery.getValueInteger("cycleAttemptCount");
            update.withValue("status", DeliveryStatus.RETRY_WAIT.name())
               .withValue("cycleAttemptCount", Math.max(0, cycle - 1))
               .withValue("nextAttemptDate", end.plusSeconds(retryAfterSeconds(config, response, cycle)));
            summary.rateLimited++;
         }
         default ->
         {
            int     cycle = delivery.getValueInteger("cycleAttemptCount") == null ? 1 : delivery.getValueInteger("cycleAttemptCount");
            boolean probation = HealthStatus.PROBATION.name().equals(endpoint.getValueString("healthStatus"));
            if(cycle >= config.getMaxAttempts() && !probation)
            {
               update.withValue("status", DeliveryStatus.DEAD_LETTER.name()).withValue("deadLetterDate", end).withValue("nextAttemptDate", null);
               summary.deadLettered++;
               WebhooksAlerts.deadLetter(endpoint, delivery);
            }
            else
            {
               update.withValue("status", DeliveryStatus.RETRY_WAIT.name())
                  .withValue("nextAttemptDate", end.plus(Duration.ofMinutes(config.retryMinutesAfterAttempt(cycle))));
            }
            summary.failed++;
            if(outcome == AttemptOutcome.FAILED)
            {
               EndpointHealth.recordFailure(config, endpoint, end, error);
            }
         }
      }
      SystemWrites.updateOne(WebhooksTables.DELIVERY, update);
   }



   /*******************************************************************************
    ** The endpoint's allowlist, only when internal targets are allowed.
    *******************************************************************************/
   static List<String> allowlist(QRecord endpoint)
   {
      if(!Boolean.TRUE.equals(endpoint.getValueBoolean("allowInternalTargets")))
      {
         return (List.of());
      }
      String text = endpoint.getValueString("internalAllowlist");
      if(!StringUtils.hasContent(text))
      {
         return (List.of());
      }
      List<String> list = new ArrayList<>();
      for(String part : text.split("[,\\s]+"))
      {
         if(!part.isBlank())
         {
            list.add(part.trim());
         }
      }
      return (list);
   }



   /*******************************************************************************
    ** Seconds to wait after a 429: Retry-After (seconds or an HTTP date),
    ** capped, else the normal backoff.
    *******************************************************************************/
   static long retryAfterSeconds(WebhooksQBitConfig config, SafeHttpResponse response, int cycle)
   {
      long   fallback = Duration.ofMinutes(config.retryMinutesAfterAttempt(Math.max(cycle, 1))).toSeconds();
      String header   = response == null ? null : response.header("retry-after");
      long   seconds  = fallback;
      if(StringUtils.hasContent(header))
      {
         try
         {
            seconds = Long.parseLong(header.trim());
         }
         catch(NumberFormatException e)
         {
            try
            {
               seconds = Duration.between(WebhooksRuntime.clock().instant(), ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds();
            }
            catch(DateTimeParseException e2)
            {
               seconds = fallback;
            }
         }
      }
      return (Math.max(1, Math.min(seconds, config.getMaxRetryAfterSeconds())));
   }



   /*******************************************************************************
    ** Cut text to a length.
    *******************************************************************************/
   static String truncate(String text, int max)
   {
      return (text == null || text.length() <= max ? text : text.substring(0, max));
   }
}
