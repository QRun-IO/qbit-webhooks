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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookActiveStatus;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;


/*******************************************************************************
 **
 *******************************************************************************/
public class SendWebhookEventTransformStep extends AbstractTransformStep
{
   private Integer webhookId;
   private Webhook webhook;

   private Map<Integer, WebhookSubscription> subscriptionMap = new HashMap<>();

   private ProcessSummaryLine okLine = new ProcessSummaryLine(Status.OK)
      .withMessageSuffix(" sent")
      .withSingularFutureMessage("will be")
      .withPluralFutureMessage("will be")
      .withSingularPastMessage("was")
      .withPluralPastMessage("were");

   private ProcessSummaryLine differentWebhookLine = new ProcessSummaryLine(Status.ERROR)
      .withMessageSuffix(" sent because events were found for more than one webhook")
      .withSingularFutureMessage("will not be")
      .withPluralFutureMessage("will not be")
      .withSingularPastMessage("was not")
      .withPluralPastMessage("were not");

   private ProcessSummaryLine missingWebhookLine = new ProcessSummaryLine(Status.ERROR)
      .withMessageSuffix(" associated webhook was not found")
      .withSingularFutureMessage("will not be sent because its")
      .withPluralFutureMessage("will not be sent because their")
      .withSingularPastMessage("was not sent because its")
      .withPluralPastMessage("were not sent because their");

   private ProcessSummaryLine disabledSubscriptionLine = new ProcessSummaryLine(Status.ERROR)
      .withSingularFutureMessage("will not be sent because its associated subscription is disabled")
      .withPluralFutureMessage("will not be sent because their associated subscriptions are disabled")
      .withSingularPastMessage("was not sent because its associated subscription is disabled")
      .withPluralPastMessage("were not sent because their associated subscriptions are disabled");

   private ProcessSummaryLine disabledWebhookLine = new ProcessSummaryLine(Status.ERROR)
      .withSingularFutureMessage("will not be sent because its associated webhook is disabled")
      .withPluralFutureMessage("will not be sent because their associated webhooks disabled")
      .withSingularPastMessage("was not sent because its associated webhook is disabled")
      .withPluralPastMessage("were not sent because their associated webhooks are disabled");

   private ProcessSummaryLine unhealthyWebhookLine = new ProcessSummaryLine(Status.ERROR)
      .withSingularFutureMessage("will not be sent because its associated webhook is unhealthy")
      .withPluralFutureMessage("will not be sent because their associated webhooks unhealthy")
      .withSingularPastMessage("was not sent because its associated webhook is unhealthy")
      .withPluralPastMessage("were not sent because their associated webhooks are unhealthy");

   private ProcessSummaryLine missingSubscriptionLine = new ProcessSummaryLine(Status.ERROR)
      .withMessageSuffix(" associated subscription was not found")
      .withSingularFutureMessage("will not be sent because its")
      .withPluralFutureMessage("will not be sent because their")
      .withSingularPastMessage("was not sent because its")
      .withPluralPastMessage("were not sent because their");

   private ProcessSummaryLine alreadyDeliveredWarningLine = new ProcessSummaryLine(Status.WARNING)
      .withSingularFutureMessage("was already successfully delivered, but will be sent again")
      .withPluralFutureMessage("were already successfully delivered, but will be sent again")
      .withSingularPastMessage("was already successfully delivered, but was sent again")
      .withPluralPastMessage("were already successfully delivered, but were sent again");



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void preRun(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
   {
      ////////////////////////////////////////////////////////////////
      // capture if we're running for a single specified webhook Id //
      ////////////////////////////////////////////////////////////////
      webhookId = runBackendStepInput.getValueInteger("webhookId");
      super.preRun(runBackendStepInput, runBackendStepOutput);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput runBackendStepOutput, boolean isForResultScreen)
   {
      ArrayList<ProcessSummaryLineInterface> rs = new ArrayList<>();
      okLine.addSelfToListIfAnyCount(rs);
      alreadyDeliveredWarningLine.addSelfToListIfAnyCount(rs);
      differentWebhookLine.addSelfToListIfAnyCount(rs);
      missingWebhookLine.addSelfToListIfAnyCount(rs);
      missingSubscriptionLine.addSelfToListIfAnyCount(rs);
      disabledWebhookLine.addSelfToListIfAnyCount(rs);
      unhealthyWebhookLine.addSelfToListIfAnyCount(rs);
      disabledSubscriptionLine.addSelfToListIfAnyCount(rs);
      return rs;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void runOnePage(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
   {
      ////////////////////////////////////////////////////
      // fetch the subscriptions for the webhook events //
      ////////////////////////////////////////////////////
      List<WebhookEvent> webhookEvents   = runBackendStepInput.getRecordsAsEntities(WebhookEvent.class);
      Set<Integer>       subscriptionIds = webhookEvents.stream().map(we -> we.getWebhookSubscriptionId()).collect(Collectors.toSet());
      loadSubscriptions(subscriptionIds);

      for(WebhookEvent webhookEvent : webhookEvents)
      {
         if(webhookEvent.getWebhookId() == null)
         {
            //////////////////////////////////////////////////
            // a little bit of a misnomer, but close enough //
            //////////////////////////////////////////////////
            missingWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         if(webhookEvent.getWebhookSubscriptionId() == null || !subscriptionIds.contains(webhookEvent.getWebhookSubscriptionId()))
         {
            missingSubscriptionLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         /////////////////////////////////////////////////////////////////////////////////////////////
         // if we aren't running for a specific webhook, then grab the id off the first one we find //
         /////////////////////////////////////////////////////////////////////////////////////////////
         if(webhookId == null)
         {
            webhookId = webhookEvent.getWebhookId();
         }

         //////////////////////////////////////////////////////////////////////////////////
         // in case events from multiple webhooks were found, only allow them for one    //
         // (the first one we found). this will simplify sending, at least at this time. //
         //////////////////////////////////////////////////////////////////////////////////
         if(!Objects.equals(webhookId, webhookEvent.getWebhookId()))
         {
            differentWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
         // if we haven't loaded the webhook record yet, then do so now (should be on the first/only one we're using) //
         ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
         if(webhook == null && webhookId != null)
         {
            QRecord webhookRecord = GetAction.execute(Webhook.TABLE_NAME, webhookId);
            if(webhookRecord == null)
            {
               /////////////////////////////////////////////////////////////////////////////////////////
               // if that webhook is missing, skip this line.                                         //
               // note, if we had many records selected, all for a non-existing webhook, we'd do this //
               // query a bunch, but, not too concerned about that scenario realistically happening.  //
               /////////////////////////////////////////////////////////////////////////////////////////
               missingWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
               continue;
            }

            webhook = new Webhook(webhookRecord);
         }

         if(webhook == null)
         {
            //////////////////////////////////////////////////////////////////////////////////////////////
            // I think this is redundant, but it stops compiler warnings about webhook maybe being null //
            //////////////////////////////////////////////////////////////////////////////////////////////
            missingWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         if(Objects.equals(WebhookActiveStatus.DISABLED.getId(), webhook.getActiveStatusId()))
         {
            disabledWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         if(Objects.equals(WebhookHealthStatus.UNHEALTHY.getId(), webhook.getHealthStatusId()))
         {
            unhealthyWebhookLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         WebhookSubscription webhookSubscription = subscriptionMap.get(webhookEvent.getWebhookSubscriptionId());
         if(webhookSubscription == null)
         {
            missingSubscriptionLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         if(Objects.equals(WebhookActiveStatus.DISABLED.getId(), webhookSubscription.getActiveStatusId()))
         {
            disabledSubscriptionLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            continue;
         }

         if(WebhookEventStatus.DELIVERED.getId().equals(webhookEvent.getEventStatusId()))
         {
            ///////////////////////////////////////////////////////////////////////
            // already delivered gets a warning, but still proceeds to load step //
            ///////////////////////////////////////////////////////////////////////
            alreadyDeliveredWarningLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            runBackendStepOutput.addRecordEntity(webhookEvent);
         }
         else
         {
            ///////////////////////////////////////////////////////
            // finally all ok - move the record to the load step //
            ///////////////////////////////////////////////////////
            okLine.incrementCountAndAddPrimaryKey(webhookEvent.getId());
            runBackendStepOutput.addRecordEntity(webhookEvent);
         }
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void loadSubscriptions(Set<Integer> subscriptionIds) throws QException
   {
      Set<Integer> subscriptionsToLookup = new HashSet<>(subscriptionIds);
      subscriptionsToLookup.removeAll(subscriptionMap.keySet());
      if(!subscriptionsToLookup.isEmpty())
      {
         List<WebhookSubscription> subscriptions = QueryAction.execute(WebhookSubscription.TABLE_NAME, WebhookSubscription.class, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, subscriptionsToLookup)));
         for(WebhookSubscription subscription : subscriptions)
         {
            subscriptionMap.put(subscription.getId(), subscription);
         }
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   Webhook getWebhook()
   {
      return webhook;
   }



   /***************************************************************************
    ** originally meant for tests
    ***************************************************************************/
   SendWebhookEventTransformStep withWebhook(Webhook webhook)
   {
      this.webhook = webhook;
      return this;
   }
}
