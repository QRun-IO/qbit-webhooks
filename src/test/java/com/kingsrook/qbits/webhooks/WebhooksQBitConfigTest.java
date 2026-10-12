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

package com.kingsrook.qbits.webhooks;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


/*******************************************************************************
 ** Unit test for WebhooksQBitConfig 
 *******************************************************************************/
class WebhooksQBitConfigTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testValidation()
   {
      /////////////////////////////////////////////
      // see corresponding todo in the main code //
      /////////////////////////////////////////////
      /*
         ///////////////////////////////////////////////////////////////////////
         // defaultBackendNameForTables is optional, but if given, must exist //
         ///////////////////////////////////////////////////////////////////////
         assertSuccess(new WebhooksQBitConfig().withDefaultBackendNameForTables(null));
         assertSuccess(new WebhooksQBitConfig().withDefaultBackendNameForTables("myBackend"), qInstance -> qInstance.addBackend(new QBackendMetaData().withName("myBackend")));
         assertError("Unrecognized defaultBackendNameForTables: noSuchBackend", new WebhooksQBitConfig().withDefaultBackendNameForTables("noSuchBackend"));

         //////////////////////////////////////////////////////////
         // scheduler name is optional, but if given, must exist //
         //////////////////////////////////////////////////////////
         assertSuccess(new WebhooksQBitConfig().withSchedulerName(null));
         assertSuccess(new WebhooksQBitConfig().withSchedulerName("myScheduler"), qInstance -> qInstance.addScheduler(new SimpleSchedulerMetaData().withName("myScheduler")));
         assertError("Unrecognized schedulerName: noSuchScheduler", new WebhooksQBitConfig().withSchedulerName("noSuchScheduler"));
      */

      ////////////////////////////////////////////////////////////////////////
      // sendWebhookEventProcessRepeatSeconds - cannot be null, must be > 0 //
      ////////////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withSendWebhookEventProcessRepeatSeconds(1));
      assertError("sendWebhookEventProcessRepeatSeconds must be non-null and > 0", new WebhooksQBitConfig().withSendWebhookEventProcessRepeatSeconds(null));
      assertError("sendWebhookEventProcessRepeatSeconds must be non-null and > 0", new WebhooksQBitConfig().withSendWebhookEventProcessRepeatSeconds(-1));
      assertError("sendWebhookEventProcessRepeatSeconds must be non-null and > 0", new WebhooksQBitConfig().withSendWebhookEventProcessRepeatSeconds(0));

      ////////////////////////////////////////////////////////
      // repeatedFailsToGoUnhealthy - can be null, else > 0 //
      ////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withRepeatedFailsToGoUnhealthy(null));
      assertSuccess(new WebhooksQBitConfig().withRepeatedFailsToGoUnhealthy(5));
      assertError("If repeatedFailsToGoUnhealthy is given, it must be > 0", new WebhooksQBitConfig().withRepeatedFailsToGoUnhealthy(-1));
      assertError("If repeatedFailsToGoUnhealthy is given, it must be > 0", new WebhooksQBitConfig().withRepeatedFailsToGoUnhealthy(0));

      ////////////////////////////////////////////////////////////////
      // unhealthyToProbationTimeoutMinutes - can be null, else > 0 //
      ////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withUnhealthyToProbationTimeoutMinutes(null));
      assertSuccess(new WebhooksQBitConfig().withUnhealthyToProbationTimeoutMinutes(5));
      assertError("If unhealthyToProbationTimeoutMinutes is given, it must be > 0", new WebhooksQBitConfig().withUnhealthyToProbationTimeoutMinutes(-1));
      assertError("If unhealthyToProbationTimeoutMinutes is given, it must be > 0", new WebhooksQBitConfig().withUnhealthyToProbationTimeoutMinutes(0));

      /////////////////////////////////////////////////////////////
      // memoizationTimeoutMinutes - cannot be null, must be > 0 //
      /////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withMemoizationTimeoutMinutes(1));
      assertSuccess(new WebhooksQBitConfig().withMemoizationTimeoutMinutes(0));
      assertError("memoizationTimeoutMinutes must be non-null and >= 0", new WebhooksQBitConfig().withMemoizationTimeoutMinutes(null));
      assertError("memoizationTimeoutMinutes must be non-null and >= 0", new WebhooksQBitConfig().withMemoizationTimeoutMinutes(-1));

      /////////////////////////////////////////////////////////////////
      // maxSendAttemptsBeforeFailure - cannot be null, must be >= 1 //
      /////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withMaxSendAttemptsBeforeFailure(1));
      assertError("maxSendAttemptsBeforeFailure must be non-null and >= 1", new WebhooksQBitConfig().withMaxSendAttemptsBeforeFailure(null));
      assertError("maxSendAttemptsBeforeFailure must be non-null and >= 1", new WebhooksQBitConfig().withMaxSendAttemptsBeforeFailure(0));
      assertError("maxSendAttemptsBeforeFailure must be non-null and >= 1", new WebhooksQBitConfig().withMaxSendAttemptsBeforeFailure(-1));

      //////////////////////////////////////////////////////////////////////
      // minutesToConsiderLeakedSendAttempt - cannot be null, must be > 0 //
      //////////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withMinutesToConsiderLeakedSendAttempt(1));
      assertError("minutesToConsiderLeakedSendAttempt must be non-null and >= 1", new WebhooksQBitConfig().withMinutesToConsiderLeakedSendAttempt(null));
      assertError("minutesToConsiderLeakedSendAttempt must be non-null and >= 1", new WebhooksQBitConfig().withMinutesToConsiderLeakedSendAttempt(0));
      assertError("minutesToConsiderLeakedSendAttempt must be non-null and >= 1", new WebhooksQBitConfig().withMinutesToConsiderLeakedSendAttempt(-1));

      /////////////////////////////////////////////////////////////////////////////////////
      // minutesBetweenRetryAttempts - cannot be null or empty; all entries must be >= 0 //
      /////////////////////////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(List.of(0)));
      assertSuccess(new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(List.of(1)));
      assertSuccess(new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(List.of(1, 5, 30)));
      assertError("minutesBetweenRetryAttempts must be non-null and non-empty", new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(null));
      assertError("minutesBetweenRetryAttempts must be non-null and non-empty", new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(Collections.emptyList()));
      assertError("minutesBetweenRetryAttempts must have all values >= 0", new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(List.of(-1)));
      assertError("minutesBetweenRetryAttempts must have all values >= 0", new WebhooksQBitConfig().withMinutesBetweenRetryAttempts(List.of(1, 5, -1)));

      ////////////////////////////////////////////////////////
      // maxRateLimitRetries - cannot be null, must be >= 0 //
      ////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withMaxRateLimitRetries(1));
      assertSuccess(new WebhooksQBitConfig().withMaxRateLimitRetries(0));
      assertError("maxRateLimitRetries must be non-null and >= 0", new WebhooksQBitConfig().withMaxRateLimitRetries(null));
      assertError("maxRateLimitRetries must be non-null and >= 0", new WebhooksQBitConfig().withMaxRateLimitRetries(-1));

      /////////////////////////////////////////////////////////////////
      // initialRateLimitBackoffMillis - cannot be null, must be > 0 //
      /////////////////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withInitialRateLimitBackoffMillis(1));
      assertError("initialRateLimitBackoffMillis must be non-null and > 0", new WebhooksQBitConfig().withInitialRateLimitBackoffMillis(null));
      assertError("initialRateLimitBackoffMillis must be non-null and > 0", new WebhooksQBitConfig().withInitialRateLimitBackoffMillis(-1));
      assertError("initialRateLimitBackoffMillis must be non-null and > 0", new WebhooksQBitConfig().withInitialRateLimitBackoffMillis(0));

      /////////////////////////////////////////////////////
      // httpTimeoutMillis - cannot be null, must be > 0 //
      /////////////////////////////////////////////////////
      assertSuccess(new WebhooksQBitConfig().withHttpTimeoutMillis(1));
      assertError("httpTimeoutMillis must be non-null and > 0", new WebhooksQBitConfig().withHttpTimeoutMillis(null));
      assertError("httpTimeoutMillis must be non-null and > 0", new WebhooksQBitConfig().withHttpTimeoutMillis(-1));
      assertError("httpTimeoutMillis must be non-null and > 0", new WebhooksQBitConfig().withHttpTimeoutMillis(0));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void assertError(String expectedErrorMessage, WebhooksQBitConfig config)
   {
      List<String> errors = new ArrayList<>();
      config.validate(new QInstance(), errors);
      assertEquals(1, errors.size());
      assertEquals(expectedErrorMessage, errors.get(0));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void assertSuccess(WebhooksQBitConfig config)
   {
      assertSuccess(config, null);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void assertSuccess(WebhooksQBitConfig config, Consumer<QInstance> qInstanceConsumer)
   {
      List<String> errors = new ArrayList<>();

      QInstance qInstance = new QInstance();
      if(qInstanceConsumer != null)
      {
         qInstanceConsumer.accept(qInstance);
      }

      config.validate(qInstance, errors);
      assertEquals(0, errors.size());
   }

}