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


import java.util.List;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory.Kind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Unit test for IdentifyWebhookSubscriptions 
 *******************************************************************************/
class WebhookSubscriptionsHelperTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void test() throws QException
   {
      String personTableName                      = "person";
      String placeTableName                       = "place";
      String insertedPersonEventName              = "inserted-person";
      String insertedPlaceEventName               = "inserted-place";
      String insertedPersonWithFirstNameEventName = "inserted-person-firstName";

      //////////////////////////////////////////////
      // nothing exists yet, so all null or empty //
      //////////////////////////////////////////////
      assertThat(getEventTypes(Kind.INSERT, "noSuchTable")).isNullOrEmpty();
      assertThat(getEventTypes(Kind.INSERT, "noSuchTable")).isNullOrEmpty();
      assertThat(getEventTypes(Kind.UPDATE, personTableName)).isNullOrEmpty();
      assertThat(getEventTypes(Kind.AD_HOC, personTableName)).isNullOrEmpty();

      ///////////////////////////////////////////////////////////////////////////////////////
      // register first event type, but no subscriptions to it, so still all empty results //
      ///////////////////////////////////////////////////////////////////////////////////////
      registerEventType(insertedPersonEventName, WebhookEventCategory.INSERT, personTableName);
      assertThat(getEventTypes(Kind.INSERT, personTableName)).isNullOrEmpty();
      assertThat(getEventTypes(Kind.UPDATE, personTableName)).isNullOrEmpty();
      assertThat(getEventTypes(Kind.AD_HOC, personTableName)).isNullOrEmpty();

      //////////////////////////////////////////////////////////////
      // add a second insert event with a sub on the person table //
      //////////////////////////////////////////////////////////////
      registerEventType(insertedPersonWithFirstNameEventName, WebhookEventCategory.INSERT, personTableName, "firstName");
      insert(newWebhookSubscription(insertedPersonWithFirstNameEventName));
      WebhookSubscriptionsHelper.clearMemoizations();
      assertThat(getEventTypes(Kind.INSERT, personTableName)).hasSize(1);
      assertThat(getEventTypes(Kind.UPDATE, personTableName)).isNullOrEmpty();
      assertThat(getEventTypes(Kind.AD_HOC, personTableName)).isNullOrEmpty();

      ////////////////////////////////////////
      // add insert event for another table //
      ////////////////////////////////////////
      registerEventType(insertedPlaceEventName, WebhookEventCategory.INSERT, placeTableName);
      insert(newWebhookSubscription(insertedPlaceEventName));
      WebhookSubscriptionsHelper.clearMemoizations();
      assertThat(getEventTypes(Kind.INSERT, personTableName)).hasSize(1);
      assertThat(getEventTypes(Kind.INSERT, placeTableName)).hasSize(1);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private List<WebhookEventType> getEventTypes(Kind kind, String tableName)
   {
      return WebhookSubscriptionsHelper.getWebhookEventTypesToConsiderFiringEventsFor(kind, tableName);
   }

}