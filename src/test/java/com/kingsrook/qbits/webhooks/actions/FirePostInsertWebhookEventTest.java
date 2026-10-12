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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.model.WebhooksActionFlags;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Unit test for FirePostInsertWebhookEvent 
 *******************************************************************************/
class FirePostInsertWebhookEventTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void test() throws QException
   {
      try
      {
         QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(FirePostInsertOrUpdateWebhookEventUtil.class);

         ////////////////////////////////////////////////
         // insert without any webhook - assert no log //
         ////////////////////////////////////////////////
         new InsertAction().execute(new InsertInput(WebhooksTestApplication.TABLE_NAME_PERSON).withRecord(new QRecord()));
         assertThat(collectingLogger.getCollectedMessages())
            .noneMatch(m -> m.getMessage().matches(".*Building webhook event.*"));

         ///////////////////////////////////////////////////////////////////
         // register event type and make a sub - then assert positive log //
         ///////////////////////////////////////////////////////////////////
         insert(newWebhookSubscription(WebhooksTestApplication.PERSON_INSERTED_EVENT_TYPE_NAME));
         WebhookSubscriptionsHelper.clearMemoizations();

         Integer personId = new InsertAction().execute(new InsertInput(WebhooksTestApplication.TABLE_NAME_PERSON)
               .withRecord(new QRecord().withValue("firstName", "Bubba").withValue("lastName", "Fit")))
            .getRecords().get(0).getValueInteger("id");
         assertThat(collectingLogger.getCollectedMessages())
            .anyMatch(m -> m.getMessage().matches(".*Building webhook event.*"));
         collectingLogger.clear();

         ///////////////////////////////////////////////
         // query to make sure event was inserted too //
         ///////////////////////////////////////////////
         List<QRecord> insertedEvents = new QueryAction().execute(new QueryInput(WebhookEvent.TABLE_NAME).withIncludeAssociations(true)).getRecords();
         assertEquals(1, insertedEvents.size());
         assertEquals(personId, insertedEvents.get(0).getValueInteger("eventSourceRecordId"));

         ///////////////////////////////////////
         // run again, with omit flag present //
         ///////////////////////////////////////
         new InsertAction().execute(new InsertInput(WebhooksTestApplication.TABLE_NAME_PERSON)
               .withFlag(WebhooksActionFlags.OMIT_WEBHOOKS)
               .withRecord(new QRecord().withValue("firstName", "Bubba").withValue("lastName", "Fit")))
            .getRecords().get(0).getValueInteger("id");
         assertThat(collectingLogger.getCollectedMessages())
            .noneMatch(m -> m.getMessage().matches(".*Building webhook event.*"));

         ////////////////////////////////////
         // assert about the contents too! //
         ////////////////////////////////////
         List<QRecord> contents = insertedEvents.get(0).getAssociatedRecords().get(WebhookEvent.CONTENT_ASSOCIATION_NAME);
         assertEquals(1, contents.size());
         String postBody = contents.get(0).getValueString("postBody");
         assertNotNull(postBody);
         JSONObject postBodyJson = new JSONObject(postBody);
         assertTrue(postBodyJson.has("record"));
         assertEquals("Bubba", postBodyJson.getJSONObject("record").getString("firstName"));
         assertEquals(personId, postBodyJson.getJSONObject("record").getInt("id"));
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(FirePostInsertOrUpdateWebhookEventUtil.class);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testStoreEventKind() throws QException
   {
      try
      {
         QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(FirePostInsertOrUpdateWebhookEventUtil.class);

         /////////////////////////////////////////////////////////////////////////////
         // register a 'store' event type and make a sub - then assert positive log //
         /////////////////////////////////////////////////////////////////////////////
         insert(newWebhookSubscription(WebhooksTestApplication.PERSON_STORED_EVENT_TYPE_NAME));

         Integer personId = new InsertAction().execute(new InsertInput(WebhooksTestApplication.TABLE_NAME_PERSON)
               .withRecord(new QRecord().withValue("firstName", "Bubba").withValue("lastName", "Fit")))
            .getRecords().get(0).getValueInteger("id");
         assertThat(collectingLogger.getCollectedMessages())
            .anyMatch(m -> m.getMessage().matches(".*Building webhook event.*"));
         collectingLogger.clear();

         ///////////////////////////////////////////////
         // query to make sure event was inserted too //
         ///////////////////////////////////////////////
         List<QRecord> insertedEvents = new QueryAction().execute(new QueryInput(WebhookEvent.TABLE_NAME).withIncludeAssociations(true)).getRecords();
         assertEquals(1, insertedEvents.size());
         assertEquals(personId, insertedEvents.get(0).getValueInteger("eventSourceRecordId"));
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(FirePostInsertOrUpdateWebhookEventUtil.class);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testDoesRecordMatchWebhookEventType()
   {
      WebhookEventType insert                   = new WebhookEventType().withCategory(WebhookEventCategory.INSERT);
      WebhookEventType insertFirstName          = new WebhookEventType().withCategory(WebhookEventCategory.INSERT_WITH_FIELD).withFieldName("firstName");
      WebhookEventType insertFirstNameJohn      = new WebhookEventType().withCategory(WebhookEventCategory.INSERT_WITH_VALUE).withFieldName("firstName").withValue("John");
      WebhookEventType insertFirstNameAnyBeatle = new WebhookEventType().withCategory(WebhookEventCategory.INSERT_WITH_VALUE).withFieldName("firstName").withValue(new ArrayList<>(List.of("John", "George", "Paul", "Ringo")));
      WebhookEventType store                    = new WebhookEventType().withCategory(WebhookEventCategory.STORE);
      WebhookEventType storeFirstName           = new WebhookEventType().withCategory(WebhookEventCategory.STORE_WITH_FIELD).withFieldName("firstName");
      WebhookEventType storeFirstNameJohn       = new WebhookEventType().withCategory(WebhookEventCategory.STORE_WITH_VALUE).withFieldName("firstName").withValue("John");

      QFieldMetaData field = QContext.getQInstance().getTable(WebhooksTestApplication.TABLE_NAME_PERSON).getField("firstName");

      QRecord emptyRecord = new QRecord();
      assertTrue(doesMatch(emptyRecord, insert, field));
      assertFalse(doesMatch(emptyRecord, insertFirstName, field));
      assertFalse(doesMatch(emptyRecord, insertFirstNameJohn, field));
      assertFalse(doesMatch(emptyRecord, insertFirstNameAnyBeatle, field));
      assertTrue(doesMatch(emptyRecord, store, field));
      assertFalse(doesMatch(emptyRecord, storeFirstName, field));
      assertFalse(doesMatch(emptyRecord, storeFirstNameJohn, field));

      QRecord firstNameJoeRecord = new QRecord().withValue("firstName", "Joe");
      assertTrue(doesMatch(firstNameJoeRecord, insert, field));
      assertTrue(doesMatch(firstNameJoeRecord, insertFirstName, field));
      assertFalse(doesMatch(firstNameJoeRecord, insertFirstNameJohn, field));
      assertFalse(doesMatch(firstNameJoeRecord, insertFirstNameAnyBeatle, field));
      assertTrue(doesMatch(firstNameJoeRecord, store, field));
      assertTrue(doesMatch(firstNameJoeRecord, storeFirstName, field));
      assertFalse(doesMatch(firstNameJoeRecord, storeFirstNameJohn, field));

      QRecord firstNameJohnRecord = new QRecord().withValue("firstName", "John");
      assertTrue(doesMatch(firstNameJohnRecord, insert, field));
      assertTrue(doesMatch(firstNameJohnRecord, insertFirstName, field));
      assertTrue(doesMatch(firstNameJohnRecord, insertFirstNameJohn, field));
      assertTrue(doesMatch(firstNameJohnRecord, insertFirstNameAnyBeatle, field));
      assertTrue(doesMatch(firstNameJohnRecord, store, field));
      assertTrue(doesMatch(firstNameJohnRecord, storeFirstName, field));
      assertTrue(doesMatch(firstNameJohnRecord, storeFirstNameJohn, field));

      QRecord firstNameRingoRecord = new QRecord().withValue("firstName", "Ringo");
      assertTrue(doesMatch(firstNameRingoRecord, insert, field));
      assertTrue(doesMatch(firstNameRingoRecord, insertFirstName, field));
      assertFalse(doesMatch(firstNameRingoRecord, insertFirstNameJohn, field));
      assertTrue(doesMatch(firstNameRingoRecord, insertFirstNameAnyBeatle, field));
      assertTrue(doesMatch(firstNameRingoRecord, store, field));
      assertTrue(doesMatch(firstNameRingoRecord, storeFirstName, field));
      assertFalse(doesMatch(firstNameRingoRecord, storeFirstNameJohn, field));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private boolean doesMatch(QRecord record, WebhookEventType webhookEventType, QFieldMetaData field)
   {
      return new FirePostInsertWebhookEvent().doesRecordMatchWebhookEventType(record, webhookEventType, field);
   }
}