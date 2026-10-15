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

package com.kingsrook.qbits.webhooks.app;


import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.AttemptOutcome;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DeliveryStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The numbers on the Webhooks home, read as the viewer (so record security
 ** applies), from the qbit's own tables only. Nothing is estimated or made up:
 ** with no data, the home says so.
 *******************************************************************************/
public final class WebhooksHomeData
{
   private static final int ATTEMPT_SAMPLE = 20000;

   private Instant       now;
   private int           delivered24h;
   private int           failedAttempts24h;
   private int           rateLimited24h;
   private int           deadLetters;
   private int           backlog;
   private Long          p50Millis;
   private Long          p95Millis;
   private final int[]   hourlyOk     = new int[24];
   private final int[]   hourlyFailed = new int[24];
   private final List<QRecord> recentDeadLetters = new ArrayList<>();
   private List<WebhooksSetupStatus.Step> setup = new ArrayList<>();



   /*******************************************************************************
    ** Load.
    *******************************************************************************/
   public static WebhooksHomeData load() throws QException
   {
      WebhooksHomeData data  = new WebhooksHomeData();
      data.now = WebhooksRuntime.clock().instant();
      Instant since = data.now.minus(Duration.ofHours(24));

      data.delivered24h = count(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("deliveredDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since)));
      data.deadLetters = count(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, DeliveryStatus.DEAD_LETTER.name())));
      data.backlog = count(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.IN,
         DeliveryStatus.PENDING.name(), DeliveryStatus.RETRY_WAIT.name(), DeliveryStatus.SENDING.name())));

      List<Long> latencies = new ArrayList<>();
      Instant    hourZero  = data.now.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(23));
      for(QRecord attempt : query(WebhooksTables.ATTEMPT, new QQueryFilter(new QFilterCriteria("startDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since))
         .withLimit(ATTEMPT_SAMPLE)))
      {
         String  outcome = attempt.getValueString("outcome");
         Instant start   = ValueUtils.getValueAsInstant(attempt.getValue("startDate"));
         int     bucket  = start == null ? -1 : (int) Duration.between(hourZero, start.truncatedTo(ChronoUnit.HOURS)).toHours();
         boolean inRange = bucket >= 0 && bucket < 24;
         if(AttemptOutcome.SUCCEEDED.name().equals(outcome))
         {
            Integer millis = attempt.getValueInteger("durationMillis");
            if(millis != null)
            {
               latencies.add(millis.longValue());
            }
            if(inRange)
            {
               data.hourlyOk[bucket]++;
            }
         }
         else if(AttemptOutcome.RATE_LIMITED.name().equals(outcome))
         {
            data.rateLimited24h++;
         }
         else if(!AttemptOutcome.IN_FLIGHT.name().equals(outcome))
         {
            data.failedAttempts24h++;
            if(inRange)
            {
               data.hourlyFailed[bucket]++;
            }
         }
      }
      if(!latencies.isEmpty())
      {
         Collections.sort(latencies);
         data.p50Millis = latencies.get((int) Math.floor((latencies.size() - 1) * 0.50));
         data.p95Millis = latencies.get((int) Math.floor((latencies.size() - 1) * 0.95));
      }

      data.recentDeadLetters.addAll(query(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, DeliveryStatus.DEAD_LETTER.name()))
         .withOrderBy(new QFilterOrderBy("id", false)).withLimit(5)));
      data.setup = WebhooksSetupStatus.load();
      return (data);
   }



   /*******************************************************************************
    ** Query as the viewer.
    *******************************************************************************/
   private static List<QRecord> query(String table, QQueryFilter filter) throws QException
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(filter)).getRecords());
   }



   /*******************************************************************************
    ** Count as the viewer.
    *******************************************************************************/
   private static int count(String table, QQueryFilter filter) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(table).withFilter(filter)).getCount();
      return (count == null ? 0 : count);
   }



   /*******************************************************************************
    ** Getter for now.
    *******************************************************************************/
   public Instant getNow()
   {
      return (now);
   }



   /*******************************************************************************
    ** Getter for delivered24h.
    *******************************************************************************/
   public int getDelivered24h()
   {
      return (delivered24h);
   }



   /*******************************************************************************
    ** Getter for failedAttempts24h.
    *******************************************************************************/
   public int getFailedAttempts24h()
   {
      return (failedAttempts24h);
   }



   /*******************************************************************************
    ** Getter for rateLimited24h.
    *******************************************************************************/
   public int getRateLimited24h()
   {
      return (rateLimited24h);
   }



   /*******************************************************************************
    ** Getter for deadLetters.
    *******************************************************************************/
   public int getDeadLetters()
   {
      return (deadLetters);
   }



   /*******************************************************************************
    ** Getter for backlog.
    *******************************************************************************/
   public int getBacklog()
   {
      return (backlog);
   }



   /*******************************************************************************
    ** Getter for p50Millis (null with no successful attempts).
    *******************************************************************************/
   public Long getP50Millis()
   {
      return (p50Millis);
   }



   /*******************************************************************************
    ** Getter for p95Millis.
    *******************************************************************************/
   public Long getP95Millis()
   {
      return (p95Millis);
   }



   /*******************************************************************************
    ** Successful attempts per hour, oldest first (24 hours ending now).
    *******************************************************************************/
   public int[] getHourlyOk()
   {
      return (hourlyOk.clone());
   }



   /*******************************************************************************
    ** Failed attempts per hour.
    *******************************************************************************/
   public int[] getHourlyFailed()
   {
      return (hourlyFailed.clone());
   }



   /*******************************************************************************
    ** The five newest dead letters.
    *******************************************************************************/
   public List<QRecord> getRecentDeadLetters()
   {
      return (recentDeadLetters);
   }




   /*******************************************************************************
    ** The setup steps (state and next step of each thing an admin configures).
    *******************************************************************************/
   public List<WebhooksSetupStatus.Step> getSetup()
   {
      return (setup);
   }
}
