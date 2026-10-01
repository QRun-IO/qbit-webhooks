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
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.actions.WebhookEventSender;
import com.kingsrook.qbits.webhooks.actions.WebhookHealthManager;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.ListingHash;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;
import static com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator.IN;


/*******************************************************************************
 **
 *******************************************************************************/
public class SendWebhookEventLoadStep extends AbstractLoadStep implements ProcessSummaryProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(SendWebhookEventLoadStep.class);

   ///////////////////////
   // most recent first //
   ///////////////////////
   private boolean                    doHealthChecks = false;
   private Integer                    repeatedFailsToGoUnhealthy;
   private Stack<WebhookEventSendLog> recentSendLogs;

   private ProcessSummaryLine okLine = new ProcessSummaryLine(Status.OK)
      .withMessageSuffix(" delivered")
      .withSingularPastMessage("was")
      .withPluralPastMessage("were");

   private ProcessSummaryLine failLine = new ProcessSummaryLine(Status.ERROR, "failed to deliver");

   private ProcessSummaryLine becameUnhealthyLine = new ProcessSummaryLine(Status.WARNING)
      .withMessageSuffix(" associated webhook became unhealthy.")
      .withSingularPastMessage("was not sent because its")
      .withPluralPastMessage("were not sent because their");



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput runBackendStepOutput, boolean isForResultScreen)
   {
      ArrayList<ProcessSummaryLineInterface> rs = getTransformStep().getProcessSummary(runBackendStepOutput, isForResultScreen);

      ///////////////////////////////////////////////////////////////////////////
      // replace the OK and WARNING lines from the transform step with our own //
      ///////////////////////////////////////////////////////////////////////////
      rs.removeIf(p -> p instanceof ProcessSummaryLine psl && (psl.getStatus().equals(Status.OK) || psl.getStatus().equals(Status.WARNING)));

      if(okLine.getCount() != null && okLine.getCount() > 0)
      {
         rs.add(0, okLine);
      }

      if(failLine.getCount() != null && failLine.getCount() > 0)
      {
         rs.add(0, failLine);
      }

      becameUnhealthyLine.addSelfToListIfAnyCount(rs);
      return (rs);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void preRun(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
   {
      repeatedFailsToGoUnhealthy = WebhooksQBitConfig.getConfigValue(config -> config.getRepeatedFailsToGoUnhealthy());
      if(repeatedFailsToGoUnhealthy != null)
      {
         recentSendLogs = newSendLogStack(repeatedFailsToGoUnhealthy);
         doHealthChecks = true;
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void runOnePage(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
   {
      if(runBackendStepInput.getRecords().isEmpty())
      {
         return;
      }

      Webhook webhook = ((SendWebhookEventTransformStep) getTransformStep()).getWebhook();

      ///////////////////////////////////////////////////////////////////////////////////////////
      // look up the last several send logs for this webhook, to seed the recentSuccesses list //
      ///////////////////////////////////////////////////////////////////////////////////////////
      if(doHealthChecks)
      {
         List<WebhookEventSendLog> recentSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, WebhookEventSendLog.class, new QQueryFilter()
            .withCriteria(new QFilterCriteria("webhookId", QCriteriaOperator.EQUALS, webhook.getId()))
            .withOrderBy(new QFilterOrderBy("id", false))
            .withLimit(repeatedFailsToGoUnhealthy));
         Collections.reverse(recentSendLogs);
         this.recentSendLogs.addAll(recentSendLogs);
      }

      ///////////////////////////////////////////////////
      // look up send logs for all events in this page //
      ///////////////////////////////////////////////////
      List<WebhookEvent> webhookEvents = runBackendStepInput.getRecordsAsEntities(WebhookEvent.class);
      List<WebhookEventSendLog> eventLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, WebhookEventSendLog.class, new QQueryFilter(
         new QFilterCriteria("webhookEventId", IN, webhookEvents.stream().map(e -> e.getId()).toList())));
      ListingHash<Integer, WebhookEventSendLog> eventLogsByEventId = CollectionUtils.listToListingHash(eventLogs, l -> l.getWebhookEventId());

      boolean becameUnhealthy = false;
      for(WebhookEvent webhookEvent : webhookEvents)
      {
         if(becameUnhealthy)
         {
            becameUnhealthyLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         try
         {
            boolean lastSuccessful = newWebhookEventSender().handleEvent(webhookEvent, webhook, eventLogsByEventId.getOrDefault(webhookEvent.getId(), Collections.emptyList()));

            if(lastSuccessful)
            {
               okLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            }
            else
            {
               failLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // if we're to update health, then add this last-successful boolean to the stack, and call the health manager //
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            if(doHealthChecks)
            {
               recentSendLogs.push(new WebhookEventSendLog()
                  .withSuccessful(lastSuccessful));
               webhook = new WebhookHealthManager().updateWebhookHealth(recentSendLogs, webhook);
            }

            if(WebhookHealthStatus.UNHEALTHY.getId().equals(webhook.getHealthStatusId()))
            {
               LOG.info("Webhook is now unhealthy - not attempting any more sends.", logPair("id", webhook.getId()));
               becameUnhealthy = true;
            }
         }
         catch(Exception e)
         {
            LOG.warn("Error handing webhook event", e, logPair("id", webhookEvent.getId()));
         }
      }
   }



   /***************************************************************************
    ** meant so class can be overridden to use a different sender
    ***************************************************************************/
   protected WebhookEventSender newWebhookEventSender()
   {
      return new WebhookEventSender();
   }


   /***************************************************************************
    * meant for tests - to make a stack of send log events
    ***************************************************************************/
   public Stack<WebhookEventSendLog> newSendLogStack(int maxSize)
   {
      return (new FixedStack<>(maxSize));
   }


   /***************************************************************************
    *
    ***************************************************************************/
   static class FixedStack<T> extends Stack<T>
   {
      private final int maxSize;



      /***************************************************************************
       *
       ***************************************************************************/
      public FixedStack(int maxSize)
      {
         this.maxSize = maxSize;
      }



      /***************************************************************************
       *
       ***************************************************************************/
      @Override
      public T push(T item)
      {
         if(size() >= maxSize)
         {
            remove(0);  // remove bottom element
         }
         return super.push(item);  // then push on top
      }
   }
}
