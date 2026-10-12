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
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.model.WebhookEventSendLog;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.collections.ListBuilder;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Unit test for WebhookEventSender 
 *******************************************************************************/
public class WebhookEventSenderTest extends BaseTest
{
   private TemporalUnitWithinOffset WITHIN_5_SEC = new TemporalUnitWithinOffset(5, ChronoUnit.SECONDS);

   QQueryFilter sendLogFilter = new QQueryFilter().withOrderBy(new QFilterOrderBy("id", false));



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testSimpleSuccess() throws QException
   {
      /////////////////////////////////////////////////////////////////
      // use random ids to avoid everything being 1 and false-passes //
      /////////////////////////////////////////////////////////////////
      Integer webhookId      = insert(newWebhook("Test"));
      Integer subscriptionId = insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withWebhookId(webhookId));
      Integer eventId        = insert(newWebhookEvent(new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId)), WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));

      WebhookEvent              event    = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      Webhook                   webhook  = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId));
      List<WebhookEventSendLog> sendLogs = new ArrayList<>();

      /////////////////
      // try to send //
      /////////////////
      WebhookEventSender sender        = new WebhookEventSenderThatSucceeds();
      boolean            wasSuccessful = sender.handleEvent(event, webhook, sendLogs);

      ///////////////////////////////
      // assert failure everywhere //
      ///////////////////////////////
      assertTrue(wasSuccessful);

      List<QRecord> insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(1, insertedSendLogs.size());

      WebhookEventSendLog sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertNull(sendLog.getErrorMessage());
      assertEquals(1, sendLog.getAttemptNo());
      assertEquals(webhookId, sendLog.getWebhookId());
      assertEquals(eventId, sendLog.getWebhookEventId());
      assertTrue(sendLog.getSuccessful());
      assertThat(sendLog.getStartTimestamp()).isCloseTo(Instant.now(), WITHIN_5_SEC);
      assertThat(sendLog.getEndTimestamp()).isCloseTo(Instant.now(), WITHIN_5_SEC);

      WebhookEvent updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.DELIVERED.getId(), updatedEvent.getEventStatusId());
      assertNull(updatedEvent.getNextAttemptTimestamp());
      sendLogs.add(sendLog);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testFiveFailures() throws QException
   {
      /////////////////////////////////////////////////////////////////
      // use random ids to avoid everything being 1 and false-passes //
      /////////////////////////////////////////////////////////////////
      Random  random         = new Random();
      Integer webhookId      = insert(newWebhook("Test"));
      Integer subscriptionId = insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withWebhookId(webhookId));
      Integer eventId        = insert(newWebhookEvent(new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId)), WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));

      WebhookEvent              event    = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      Webhook                   webhook  = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId));
      List<WebhookEventSendLog> sendLogs = new ArrayList<>();

      /////////////////
      // try to send //
      /////////////////
      WebhookEventSender sender        = new WebhookEventSenderThatFails();
      boolean            wasSuccessful = sender.handleEvent(event, webhook, sendLogs);

      ///////////////////////////////
      // assert failure everywhere //
      ///////////////////////////////
      assertFalse(wasSuccessful);

      List<QRecord> insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(1, insertedSendLogs.size());

      WebhookEventSendLog sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals("Test failure", sendLog.getErrorMessage());
      assertEquals(1, sendLog.getAttemptNo());
      assertEquals(webhookId, sendLog.getWebhookId());
      assertEquals(eventId, sendLog.getWebhookEventId());
      assertFalse(sendLog.getSuccessful());
      assertThat(sendLog.getStartTimestamp()).isCloseTo(Instant.now(), WITHIN_5_SEC);
      assertThat(sendLog.getEndTimestamp()).isCloseTo(Instant.now(), WITHIN_5_SEC);

      WebhookEvent updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(60), WITHIN_5_SEC);
      sendLogs.add(sendLog);

      ////////////
      // try #2 //
      ////////////
      wasSuccessful = sender.handleEvent(updatedEvent, webhook, sendLogs);
      assertFalse(wasSuccessful);

      insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(2, insertedSendLogs.size());
      sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(2, sendLog.getAttemptNo());

      updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(5 * 60), WITHIN_5_SEC);
      sendLogs.add(sendLog);

      ////////////
      // try #3 //
      ////////////
      wasSuccessful = sender.handleEvent(updatedEvent, webhook, sendLogs);
      assertFalse(wasSuccessful);

      insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(3, insertedSendLogs.size());
      sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(3, sendLog.getAttemptNo());

      updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(15 * 60), WITHIN_5_SEC);
      sendLogs.add(sendLog);

      ////////////
      // try #4 //
      ////////////
      wasSuccessful = sender.handleEvent(updatedEvent, webhook, sendLogs);
      assertFalse(wasSuccessful);

      insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(4, insertedSendLogs.size());
      sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(4, sendLog.getAttemptNo());

      updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(60 * 60), WITHIN_5_SEC);
      sendLogs.add(sendLog);

      ////////////
      // try #5 //
      ////////////
      wasSuccessful = sender.handleEvent(updatedEvent, webhook, sendLogs);
      assertFalse(wasSuccessful);

      insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      assertEquals(5, insertedSendLogs.size());
      sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(5, sendLog.getAttemptNo());

      updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.FAILED.getId(), updatedEvent.getEventStatusId());
      assertNull(updatedEvent.getNextAttemptTimestamp());
      sendLogs.add(sendLog);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testFifthFailureOnProbation() throws QException
   {
      /////////////////////////////////////////////////////////////////
      // use random ids to avoid everything being 1 and false-passes //
      /////////////////////////////////////////////////////////////////
      Random  random         = new Random();
      Integer webhookId      = insert(newWebhook("Test").withHealthStatusId(WebhookHealthStatus.PROBATION.getId()));
      Integer subscriptionId = insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withWebhookId(webhookId));
      Integer eventId        = insert(newWebhookEvent(new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId)), WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));

      WebhookEvent event   = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      Webhook      webhook = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId));
      List<WebhookEventSendLog> sendLogs = ListBuilder.of(
         new WebhookEventSendLog().withAttemptNo(1),
         new WebhookEventSendLog().withAttemptNo(2),
         new WebhookEventSendLog().withAttemptNo(3),
         new WebhookEventSendLog().withAttemptNo(4)
      );

      /////////////////
      // try to send //
      /////////////////
      WebhookEventSender sender        = new WebhookEventSenderThatFails();
      boolean            wasSuccessful = sender.handleEvent(event, webhook, sendLogs);

      ////////////////////
      // assert failure //
      ////////////////////
      assertFalse(wasSuccessful);

      ///////////////////////////////////////////////////////////////////////
      // make sure we didn't go into failure, but stayed in awaiting retry //
      ///////////////////////////////////////////////////////////////////////
      WebhookEvent updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(4 * 60 * 60), WITHIN_5_SEC);

      List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(5, sendLog.getAttemptNo());
      sendLogs.add(sendLog);

      /////////////////////////////////////////////////////
      // try once more - attemptNo should go up beyond 5 //
      /////////////////////////////////////////////////////
      sender.handleEvent(event, webhook, sendLogs);

      updatedEvent = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      assertEquals(WebhookEventStatus.AWAITING_RETRY.getId(), updatedEvent.getEventStatusId());
      assertThat(updatedEvent.getNextAttemptTimestamp()).isCloseTo(Instant.now().plusSeconds(4 * 60 * 60), WITHIN_5_SEC);

      insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
      sendLog = new WebhookEventSendLog(insertedSendLogs.get(0));
      assertEquals(6, sendLog.getAttemptNo());
      sendLogs.add(sendLog);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testHttpStatusCodes() throws QException
   {
      /////////////////////////////////////////////////////////////////
      // use random ids to avoid everything being 1 and false-passes //
      /////////////////////////////////////////////////////////////////
      Random  random         = new Random();
      Integer webhookId      = insert(newWebhook("Test"));
      Integer subscriptionId = insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withWebhookId(webhookId));
      Integer eventId        = insert(newWebhookEvent(new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId)), WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));

      WebhookEvent event = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      event.setContent(List.of(new WebhookEventContent().withWebhookEventId(eventId).withPostBody("{}")));

      Webhook                   webhook  = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId));
      List<WebhookEventSendLog> sendLogs = new ArrayList<>();

      ////////////////
      // 200 - okay //
      ////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(200, "OK");
         assertTrue(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertNull(sendLog.getErrorMessage());
         assertTrue(sendLog.getSuccessful());
         assertEquals(200, sendLog.getHttpStatusCode());
      }

      ////////////////
      // 201 - okay //
      ////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(201, "OK");
         assertTrue(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertNull(sendLog.getErrorMessage());
         assertTrue(sendLog.getSuccessful());
         assertEquals(201, sendLog.getHttpStatusCode());
      }

      ////////////////
      // 301 - fail //
      ////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(301, "Go away");
         assertFalse(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertEquals("Go away", sendLog.getErrorMessage());
         assertFalse(sendLog.getSuccessful());
         assertEquals(301, sendLog.getHttpStatusCode());
      }

      /////////////////////////////
      // 500 with no body - fail //
      /////////////////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(500, "");
         assertFalse(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertEquals("No response body returned", sendLog.getErrorMessage());
         assertFalse(sendLog.getSuccessful());
         assertEquals(500, sendLog.getHttpStatusCode());
      }

      ////////////////
      // 199 - fail //
      ////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(199, "what even?");
         assertFalse(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertEquals("what even?", sendLog.getErrorMessage());
         assertFalse(sendLog.getSuccessful());
         assertEquals(199, sendLog.getHttpStatusCode());
      }

      ////////////////////////////////////////
      // IO Exception (e.g., can't connect) //
      ////////////////////////////////////////
      {
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(new IOException("Connection Refused"));
         assertFalse(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         assertEquals("Connection Refused", sendLog.getErrorMessage());
         assertFalse(sendLog.getSuccessful());
         assertNull(sendLog.getHttpStatusCode());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRateLimit() throws QException
   {
      /////////////////////////////////////////////////////////////////
      // use random ids to avoid everything being 1 and false-passes //
      /////////////////////////////////////////////////////////////////
      Random  random         = new Random();
      Integer webhookId      = insert(newWebhook("Test"));
      Integer subscriptionId = insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME).withWebhookId(webhookId));
      Integer eventId        = insert(newWebhookEvent(new WebhookSubscription(GetAction.execute(WebhookSubscription.TABLE_NAME, subscriptionId)), WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));

      WebhookEvent event = new WebhookEvent(GetAction.execute(WebhookEvent.TABLE_NAME, eventId));
      event.setContent(List.of(new WebhookEventContent().withWebhookEventId(eventId).withPostBody("{}")));

      Webhook                   webhook  = new Webhook(GetAction.execute(Webhook.TABLE_NAME, webhookId));
      List<WebhookEventSendLog> sendLogs = new ArrayList<>();

      {
         ///////////////////////////////////////////////////////////
         // use http mocker that will always fail with rate limit //
         ///////////////////////////////////////////////////////////
         long               start  = System.currentTimeMillis();
         WebhookEventSender sender = new WebhookEventSenderThatMocksHttp(429, "Too many requests");
         assertFalse(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));

         long end = System.currentTimeMillis();
         assertEquals("Giving up after too many rate-limit errors (3).  Latest response: Too many requests", sendLog.getErrorMessage());
         assertFalse(sendLog.getSuccessful());
         assertEquals(429, sendLog.getHttpStatusCode());
         assertThat(end - start).isGreaterThan(WebhookEventSenderThatMocksHttp.BACKOFF_MILLIS * 3L);
      }

      {
         ///////////////////////////////////////////////////////////////
         // use a sender that will rate-limit twice, but then succeed //
         ///////////////////////////////////////////////////////////////
         QCollectingLogger  collectingLogger = QLogger.activateCollectingLoggerForClass(WebhookEventSender.class);
         long               start  = System.currentTimeMillis();
         WebhookEventSender sender = new RateLimitPassEventuallySender("Too fast");
         assertTrue(sender.handleEvent(event, webhook, sendLogs));
         List<QRecord>       insertedSendLogs = QueryAction.execute(WebhookEventSendLog.TABLE_NAME, sendLogFilter);
         WebhookEventSendLog sendLog          = new WebhookEventSendLog(insertedSendLogs.get(0));
         QLogger.deactivateCollectingLoggerForClass(WebhookEventSender.class);

         long end = System.currentTimeMillis();
         assertNull(sendLog.getErrorMessage());
         assertTrue(sendLog.getSuccessful());
         assertEquals(200, sendLog.getHttpStatusCode());
         assertThat(end - start).isGreaterThan(WebhookEventSenderThatMocksHttp.BACKOFF_MILLIS * 3L);
         assertThat(collectingLogger.getCollectedMessages())
            .anyMatch(m -> m.getMessage().contains("Caught rate limit.  Will sleep and re-try"));
      }

   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class WebhookEventSenderThatFails extends WebhookEventSender
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected void doPost(WebhookEvent webhookEvent, Webhook webhook, WebhookEventSendLog sendLog) throws WebhookPostException
      {
         throw new WebhookPostException("Test failure");
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class WebhookEventSenderThatSucceeds extends WebhookEventSender
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected void doPost(WebhookEvent webhookEvent, Webhook webhook, WebhookEventSendLog sendLog) throws WebhookPostException
      {
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class WebhookEventSenderThatMocksHttp extends WebhookEventSender
   {
      protected Integer     statusCode;
      protected String      responseBody;
      private   IOException e;

      public static final Integer BACKOFF_MILLIS = 3;



      /*******************************************************************************
       ** Constructor
       **
       *******************************************************************************/
      public WebhookEventSenderThatMocksHttp(int statusCode, String responseBody)
      {
         this.statusCode = statusCode;
         this.responseBody = responseBody;
      }



      /*******************************************************************************
       ** Constructor
       **
       *******************************************************************************/
      public WebhookEventSenderThatMocksHttp(IOException e)
      {
         this.e = e;
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected int getInitialRateLimitBackoffMillis()
      {
         return (BACKOFF_MILLIS);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected CloseableHttpClient buildHttpClient()
      {
         return new CloseableHttpClient()
         {
            @Override
            protected CloseableHttpResponse doExecute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException, ClientProtocolException
            {
               return null;
            }



            @Override
            public void close() throws IOException
            {

            }



            @Override
            public HttpParams getParams()
            {
               return null;
            }



            @Override
            public ClientConnectionManager getConnectionManager()
            {
               return null;
            }
         };
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected CloseableHttpResponse executeHttpRequest(CloseableHttpClient httpClient, HttpPost request) throws IOException
      {
         if(e != null)
         {
            throw (e);
         }

         return new CloseableHttpResponse()
         {
            @Override
            public void close() throws IOException
            {

            }



            @Override
            public StatusLine getStatusLine()
            {
               return new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), statusCode, "Yeah.");
            }



            @Override
            public void setStatusLine(StatusLine statusLine)
            {

            }



            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i)
            {

            }



            @Override
            public void setStatusLine(ProtocolVersion protocolVersion, int i, String s)
            {

            }



            @Override
            public void setStatusCode(int i) throws IllegalStateException
            {

            }



            @Override
            public void setReasonPhrase(String s) throws IllegalStateException
            {

            }



            @Override
            public HttpEntity getEntity()
            {
               try
               {
                  return new StringEntity(responseBody);
               }
               catch(UnsupportedEncodingException e)
               {
                  throw new RuntimeException(e);
               }
            }



            @Override
            public void setEntity(HttpEntity httpEntity)
            {

            }



            @Override
            public Locale getLocale()
            {
               return null;
            }



            @Override
            public void setLocale(Locale locale)
            {

            }



            @Override
            public ProtocolVersion getProtocolVersion()
            {
               return null;
            }



            @Override
            public boolean containsHeader(String s)
            {
               return false;
            }



            @Override
            public Header[] getHeaders(String s)
            {
               return new Header[0];
            }



            @Override
            public Header getFirstHeader(String s)
            {
               return null;
            }



            @Override
            public Header getLastHeader(String s)
            {
               return null;
            }



            @Override
            public Header[] getAllHeaders()
            {
               return new Header[0];
            }



            @Override
            public void addHeader(Header header)
            {

            }



            @Override
            public void addHeader(String s, String s1)
            {

            }



            @Override
            public void setHeader(Header header)
            {

            }



            @Override
            public void setHeader(String s, String s1)
            {

            }



            @Override
            public void setHeaders(Header[] headers)
            {

            }



            @Override
            public void removeHeader(Header header)
            {

            }



            @Override
            public void removeHeaders(String s)
            {

            }



            @Override
            public HeaderIterator headerIterator()
            {
               return null;
            }



            @Override
            public HeaderIterator headerIterator(String s)
            {
               return null;
            }



            @Override
            public HttpParams getParams()
            {
               return null;
            }



            @Override
            public void setParams(HttpParams httpParams)
            {

            }
         };
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public static class RateLimitPassEventuallySender extends WebhookEventSenderThatMocksHttp
   {
      int count = 0;



      /*******************************************************************************
       ** Constructor
       **
       *******************************************************************************/
      public RateLimitPassEventuallySender(String message)
      {
         super(429, message);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      protected CloseableHttpResponse executeHttpRequest(CloseableHttpClient httpClient, HttpPost request) throws IOException
      {
         count++;

         if(count >= 3)
         {
            statusCode = 200;
            responseBody = "OK";
         }

         return super.executeHttpRequest(httpClient, request);
      }
   }

}