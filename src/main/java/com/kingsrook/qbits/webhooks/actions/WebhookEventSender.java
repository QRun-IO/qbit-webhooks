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

package com.kingsrook.qbits.webhooks.actions;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.audits.AuditAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.SleepUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** class that send webhook events to a webhook
 *******************************************************************************/
public class WebhookEventSender
{
   private static final QLogger LOG = QLogger.getLogger(WebhookEventSender.class);

   private static Integer   maxAllowedAttempts                 = null;
   private static Integer   minutesToConsiderLeakedSendAttempt = null;
   private static Integer[] backoffMinutes                     = null;
   private static Integer   maxAllowedRateLimitErrors          = null;
   private static Integer   initialRateLimitBackoffMillis      = null;



   /***************************************************************************
    **
    ***************************************************************************/
   public boolean handleEvent(WebhookEvent webhookEvent, Webhook webhook, List<WebhookEventSendLog> sendLogs) throws QException
   {
      /////////////////////
      // mark as sending //
      /////////////////////
      Instant nextAttemptTimestamp = Instant.now().plus(getMinutesToConsiderLeakedSendAttempt() * 60, ChronoUnit.SECONDS);
      updateWebhookEvent(webhookEvent.getId(), WebhookEventStatus.SENDING, nextAttemptTimestamp, null);

      /////////////////
      // try to send //
      /////////////////
      WebhookEventSendLog sendLog = post(webhookEvent, webhook);
      sendLog.setAttemptNo(sendLogs.size() + 1);

      InsertInput insertSendLogInput = new InsertInput(WebhookEventSendLog.TABLE_NAME).withRecordEntity(sendLog);

      try(QBackendTransaction transaction = QBackendTransaction.openFor(insertSendLogInput))
      {
         insertSendLogInput.setTransaction(transaction);
         if(sendLog.getSuccessful())
         {
            ///////////////////////
            // mark as delivered //
            ///////////////////////
            new InsertAction().execute(insertSendLogInput);
            updateWebhookEvent(webhookEvent.getId(), WebhookEventStatus.DELIVERED, null, transaction);
            transaction.commit();
            return (true);
         }
         else
         {
            //////////////////////////////////////////////////////////////////////////////////////////
            // consider if this event is now failed, or if it is retryable (based on # of failures) //
            //////////////////////////////////////////////////////////////////////////////////////////
            WebhookEventStatus eventStatus = sendLog.getAttemptNo() >= getMaxAllowedAttempts() ? WebhookEventStatus.FAILED : WebhookEventStatus.AWAITING_RETRY;
            if(eventStatus.equals(WebhookEventStatus.FAILED) && WebhookHealthStatus.PROBATION.getId().equals(webhook.getHealthStatusId()))
            {
               String message = "This Webhook Event has had too many failures, but since its Webhook's health status is Probation, its status will be Awaiting Retry instead of Failure.";
               AuditAction.execute(WebhookEvent.TABLE_NAME, webhookEvent.getId(), null, message);
               eventStatus = WebhookEventStatus.AWAITING_RETRY;
            }

            ///////////////////////////////////////////////////////////////////////
            // set the next-send time based on how many attempts there have been //
            ///////////////////////////////////////////////////////////////////////
            nextAttemptTimestamp = calculateNextAttemptBackoff(sendLog.getAttemptNo());
            if(eventStatus.equals(WebhookEventStatus.FAILED))
            {
               nextAttemptTimestamp = null;
            }

            new InsertAction().execute(insertSendLogInput);
            updateWebhookEvent(webhookEvent.getId(), eventStatus, nextAttemptTimestamp, transaction);
            transaction.commit();
            return (false);
         }
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static Instant calculateNextAttemptBackoff(int attemptNumber)
   {
      Integer[] backoffMinutes = getBackoffMinutes();
      int       index          = Math.min(attemptNumber - 1, backoffMinutes.length - 1);
      return Instant.now().plusSeconds(backoffMinutes[index] * 60);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public WebhookEventSendLog post(WebhookEvent webhookEvent, Webhook webhook)
   {
      WebhookEventSendLog sendLog = new WebhookEventSendLog();
      sendLog.setWebhookEventId(webhookEvent.getId());
      sendLog.setWebhookId(webhook.getId());
      sendLog.setStartTimestamp(Instant.now());

      try
      {
         doPost(webhookEvent, webhook, sendLog);

         //////////////////////////////////////////////////
         // interpret no exception as meaning successful //
         //////////////////////////////////////////////////
         sendLog.setSuccessful(true);
      }
      catch(WebhookPostException e)
      {
         ///////////////////////////////////////////////////////////////////////////
         // any exception thrown means not successful - capture its error message //
         ///////////////////////////////////////////////////////////////////////////
         sendLog.setSuccessful(false);
         sendLog.setErrorMessage(e.getMessage());
      }

      sendLog.setEndTimestamp(Instant.now());
      return (sendLog);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   protected void doPost(WebhookEvent webhookEvent, Webhook webhook, WebhookEventSendLog sendLog) throws WebhookPostException
   {
      int rateLimitsCaught     = 0;
      int rateLimitSleepMillis = getInitialRateLimitBackoffMillis();

      ///////////////////////////////////
      // loop in case of rate limiting //
      ///////////////////////////////////
      while(true)
      {
         try(CloseableHttpClient httpClient = buildHttpClient())
         {
            HttpPost request = new HttpPost(webhook.getUrl());
            request.setHeader("Content-Type", "application/json");

            String postBody = getPostBody(webhookEvent);
            request.setEntity(new StringEntity(postBody, StandardCharsets.UTF_8));

            try(CloseableHttpResponse response = executeHttpRequest(httpClient, request))
            {
               int    statusCode     = response.getStatusLine().getStatusCode();
               String responseString = EntityUtils.toString(response.getEntity());

               if(statusCode == HttpStatus.SC_TOO_MANY_REQUESTS) // 429
               {
                  throw (new WebhookRateLimitException(responseString));
               }

               sendLog.setHttpStatusCode(statusCode);

               if(statusCode < 200 || statusCode >= 300)
               {
                  if(!StringUtils.hasContent(responseString))
                  {
                     LOG.warn("Unsuccessful http status code, but no response body returned", logPair("statusCode", statusCode), logPair("webhookId", webhook.getId()));
                     responseString = "No response body returned";
                  }
                  throw new WebhookPostException(responseString);
               }

               ////////////////////////////////////////////
               // successful exit of the rate-limit loop //
               ////////////////////////////////////////////
               break;
            }
            catch(WebhookRateLimitException rle)
            {
               rateLimitsCaught++;
               if(rateLimitsCaught > getMaxAllowedRateLimitErrors())
               {
                  sendLog.setHttpStatusCode(HttpStatus.SC_TOO_MANY_REQUESTS); // 429
                  LOG.warn("Giving up webhook post after too many rate limit errors", logPair("webhookId", webhook.getId()), logPair("rateLimitsCaught", rateLimitsCaught), logPair("response", rle.getMessage()));
                  throw (new WebhookPostException("Giving up after too many rate-limit errors (" + getMaxAllowedRateLimitErrors() + ").  Latest response: " + rle.getMessage()));
               }

               LOG.info("Caught rate limit.  Will sleep and re-try", logPair("rateLimitsCaught", rateLimitsCaught), logPair("webhookId", webhook.getId()), logPair("sleeping", rateLimitSleepMillis));
               SleepUtils.sleep(rateLimitSleepMillis, TimeUnit.MILLISECONDS);
               rateLimitSleepMillis *= 2;
            }
            catch(WebhookPostException wpe)
            {
               throw (wpe);
            }
            catch(Exception e)
            {
               LOG.warn("Exception executing http request", e, logPair("webhookId", webhook.getId()));
               throw (new WebhookPostException(e.getMessage(), e));
            }
         }
         catch(WebhookPostException wpe)
         {
            throw (wpe);
         }
         catch(Exception e)
         {
            LOG.warn("Exception building http client", e, logPair("webhookId", webhook.getId()));
            throw (new WebhookPostException(e.getMessage(), e));
         }
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   protected String getPostBody(WebhookEvent webhookEvent) throws QException
   {
      String postBody = null;
      if(CollectionUtils.nullSafeIsEmpty(webhookEvent.getContent()))
      {
         List<QRecord> contentRecords = QueryAction.execute(WebhookEventContent.TABLE_NAME, new QQueryFilter(new QFilterCriteria("webhookEventId", QCriteriaOperator.EQUALS, webhookEvent.getId())));
         if(CollectionUtils.nullSafeHasContents(contentRecords))
         {
            postBody = contentRecords.get(0).getValueString("postBody");
         }
      }
      else
      {
         postBody = webhookEvent.getContent().get(0).getPostBody();
      }

      if(!StringUtils.hasContent(postBody))
      {
         throw (new QException("Missing content/postBody for webhook event: " + webhookEvent.getId()));
      }

      return postBody;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   protected CloseableHttpResponse executeHttpRequest(CloseableHttpClient httpClient, HttpPost request) throws IOException
   {
      return httpClient.execute(request);
   }



   /*******************************************************************************
    ** Build the default HttpClient used by the makeRequest method
    *******************************************************************************/
   protected CloseableHttpClient buildHttpClient()
   {
      ///////////////////////////////////////////////////////////////////////////////////////
      // do we want this?? .setConnectionManager(new PoolingHttpClientConnectionManager()) //
      // needs some good scrutiny.                                                         //
      ///////////////////////////////////////////////////////////////////////////////////////
      return HttpClientBuilder.create()
         .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000)
            .setSocketTimeout(5000).build())
         .build();
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void updateWebhookEvent(Integer id, WebhookEventStatus status, Instant nextAttemptTimestamp, QBackendTransaction transaction) throws QException
   {
      new UpdateAction().execute(new UpdateInput(WebhookEvent.TABLE_NAME)
         .withTransaction(transaction)
         .withRecord(new QRecord()
            .withValue("id", id)
            .withValue("nextAttemptTimestamp", nextAttemptTimestamp)
            .withValue("eventStatusId", status.getId())));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected int getMaxAllowedRateLimitErrors()
   {
      if(maxAllowedRateLimitErrors == null)
      {
         maxAllowedRateLimitErrors = WebhooksQBitConfig.getConfigValue(config -> config.getMaxRateLimitRetries());
      }
      return (maxAllowedRateLimitErrors);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   protected int getInitialRateLimitBackoffMillis()
   {
      if(initialRateLimitBackoffMillis == null)
      {
         initialRateLimitBackoffMillis = WebhooksQBitConfig.getConfigValue(config -> config.getInitialRateLimitBackoffMillis());
      }
      return (initialRateLimitBackoffMillis);
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static Integer getMaxAllowedAttempts()
   {
      if(maxAllowedAttempts == null)
      {
         maxAllowedAttempts = WebhooksQBitConfig.getConfigValue(config -> config.getMaxSendAttemptsBeforeFailure());
      }
      return (maxAllowedAttempts);
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static Integer getMinutesToConsiderLeakedSendAttempt()
   {
      if(minutesToConsiderLeakedSendAttempt == null)
      {
         minutesToConsiderLeakedSendAttempt = WebhooksQBitConfig.getConfigValue(config -> config.getMinutesToConsiderLeakedSendAttempt());
      }
      return (minutesToConsiderLeakedSendAttempt);
   }



   /***************************************************************************
    *
    ***************************************************************************/
   private static Integer[] getBackoffMinutes()
   {
      if(backoffMinutes == null)
      {
         List<Integer> configValue = WebhooksQBitConfig.getConfigValue(config -> config.getMinutesBetweenRetryAttempts());
         backoffMinutes = configValue.toArray(new Integer[0]);
      }
      return (backoffMinutes);
   }

}
