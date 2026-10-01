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
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookActiveStatus;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 **
 *******************************************************************************/
public class SendWebhookEventExtractStep extends ExtractViaQueryStep
{
   private static final QLogger LOG = QLogger.getLogger(SendWebhookEventExtractStep.class);

   private static QQueryFilter falseFilter = new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.FALSE));



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected QQueryFilter getQueryFilter(RunBackendStepInput runBackendStepInput) throws QException
   {
      /////////////////////////////////////////////////////////////////////////////////////////////////////////
      // if we've been asked to run for a specific webhook, then look up events that need sent for that hook //
      /////////////////////////////////////////////////////////////////////////////////////////////////////////
      Integer webhookId = runBackendStepInput.getValueInteger("webhookId");
      if(webhookId != null)
      {
         return getQueryFilterForSingleWebhook(webhookId);
      }

      return super.getQueryFilter(runBackendStepInput);
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static QQueryFilter getQueryFilterForSingleWebhook(Integer webhookId) throws QException
   {
      QRecord webhookRecord = GetAction.execute(Webhook.TABLE_NAME, webhookId);
      if(webhookRecord == null)
      {
         LOG.debug("Running for non-existing webhook - returning query to find no rows.", logPair("webhookId", webhookId));
         return (falseFilter);
      }

      /////////////////////////////////////////////
      // only return records for active webhooks //
      /////////////////////////////////////////////
      Webhook webhook = new Webhook(webhookRecord);
      if(!WebhookActiveStatus.ACTIVE.getId().equals(webhook.getActiveStatusId()))
      {
         LOG.debug("Running for non-active webhook - returning query to find no rows.", logPair("webhookId", webhookId));
         return falseFilter;
      }

      /////////////////////////////////////////////////
      // don't return records for unhealthy webhooks //
      /////////////////////////////////////////////////
      if(WebhookHealthStatus.UNHEALTHY.getId().equals(webhook.getHealthStatusId()))
      {
         LOG.debug("Running for non-healthy webhook - returning query to find no rows.", logPair("webhookId", webhookId));
         return falseFilter;
      }

      ///////////////////////////////////////////////////////////////////////////////////
      // webhook_id = ?                                                                //
      // AND                                                                           //
      // (                                                                             //
      //   (status = NEW)                                                              //
      //   OR (status IN (SENDING, AWAITING_RETRY) AND next_attempt_timestamp < now()) //
      // )                                                                             //
      ///////////////////////////////////////////////////////////////////////////////////
      QQueryFilter filter = new QQueryFilter()
         .withCriteria(new QFilterCriteria("webhookId", QCriteriaOperator.EQUALS, webhookId));

      QQueryFilter orStatusFilters = new QQueryFilter().withBooleanOperator(QQueryFilter.BooleanOperator.OR);
      filter.addSubFilter(orStatusFilters);

      orStatusFilters.addSubFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("eventStatusId", QCriteriaOperator.EQUALS, WebhookEventStatus.NEW.getId())));

      orStatusFilters.addSubFilter(new QQueryFilter()
         .withCriteria(new QFilterCriteria("eventStatusId", QCriteriaOperator.IN, WebhookEventStatus.SENDING.getId(), WebhookEventStatus.AWAITING_RETRY.getId()))
         .withCriteria(new QFilterCriteria("nextAttemptTimestamp", QCriteriaOperator.IS_NOT_BLANK))
         .withCriteria(new QFilterCriteria("nextAttemptTimestamp", QCriteriaOperator.LESS_THAN, Instant.now())));

      filter.addOrderBy(new QFilterOrderBy("id"));
      return (filter);
   }
}
