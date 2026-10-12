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


import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import com.kingsrook.qbits.webhooks.actions.WebhookSubscriptionsHelper;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookActiveStatus;
import com.kingsrook.qbits.webhooks.model.WebhookEvent;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.model.WebhookEventStatus;
import com.kingsrook.qbits.webhooks.model.WebhookHealthStatus;
import com.kingsrook.qbits.webhooks.model.WebhookSubscription;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.fail;


/*******************************************************************************
 **
 *******************************************************************************/
public class BaseTest
{
   protected static Random random = new Random();



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void baseBeforeEach() throws Exception
   {
      QInstance qInstance = defineQInstance();
      new QInstanceValidator().validate(qInstance);
      QContext.init(qInstance, new QSession().withSecurityKeyValue(WebhooksTestApplication.STORE_ID_ALL_ACCESS_KEY, true));
      WebhookSubscriptionsHelper.clearMemoizations();

      MemoryRecordStore.fullReset();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void afterEach()
   {
      MemoryRecordStore.fullReset();
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private QInstance defineQInstance() throws QException
   {
      return new WebhooksTestApplication().defineQInstance();
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static void registerAdHocEventType(String name) throws QException
   {
      WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).registerWebhookEventType(new WebhookEventType()
         .withName(name).withCategory(WebhookEventCategory.AD_HOC));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static void registerEventType(String name, WebhookEventCategory category, String tableName) throws QException
   {
      WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).registerWebhookEventType(new WebhookEventType()
         .withName(name).withCategory(category).withTableName(tableName));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static void registerEventType(String name, WebhookEventCategory category, String tableName, String fieldName) throws QException
   {
      WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).registerWebhookEventType(new WebhookEventType()
         .withName(name).withCategory(category).withTableName(tableName).withFieldName(fieldName));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static void registerEventType(String name, WebhookEventCategory category, String tableName, String fieldName, Serializable value) throws QException
   {
      WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).registerWebhookEventType(new WebhookEventType()
         .withName(name).withCategory(category).withTableName(tableName).withFieldName(fieldName).withValue(value));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static Integer insert(Webhook webhook) throws QException
   {
      Integer id = new InsertAction().execute(new InsertInput(Webhook.TABLE_NAME)
         .withRecordEntity(webhook)).getRecords().get(0).getValueInteger("id");
      if(id == null)
      {
         fail("Failed to insert a test webhook");
      }
      return (id);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static Webhook newWebhook(String name)
   {
      return new Webhook()
         .withId(random.nextInt(100000))
         .withName(name)
         .withUrl("http://localhost/")
         .withActiveStatusId(WebhookActiveStatus.ACTIVE.getId())
         .withHealthStatusId(WebhookHealthStatus.HEALTHY.getId());
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static Integer insert(WebhookSubscription subscription) throws QException
   {
      Integer id = new InsertAction().execute(new InsertInput(WebhookSubscription.TABLE_NAME)
         .withRecordEntity(subscription)).getRecords().get(0).getValueInteger("id");
      if(id == null)
      {
         fail("Failed to insert a test subscription");
      }
      return (id);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static WebhookSubscription newWebhookSubscription(String eventTypeName)
   {
      return new WebhookSubscription()
         .withId(random.nextInt(100000))
         .withWebhookEventTypeName(eventTypeName)
         .withApiName(WebhooksTestApplication.API_NAME)
         .withApiVersion(WebhooksTestApplication.API_V1)
         .withWebhookId(-1);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static Integer insert(WebhookEvent event) throws QException
   {
      return insert(List.of(event)).get(0);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static List<Integer> insert(List<WebhookEvent> events) throws QException
   {
      List<Integer> ids = new InsertAction().execute(new InsertInput(WebhookEvent.TABLE_NAME)
         .withRecordEntities(events)).getRecords().stream().map(qr -> qr.getValueInteger("id")).collect(Collectors.toList());

      if(ids.contains(null))
      {
         fail("Failed to insert a test event");
      }
      return (ids);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static WebhookEvent newWebhookEvent(WebhookSubscription subscription, String eventTypeName) throws QException
   {
      WebhookEvent webhookEvent = new WebhookEvent();
      webhookEvent.setWebhookId(subscription.getWebhookId());
      webhookEvent.setWebhookSubscriptionId(subscription.getId());
      webhookEvent.setWebhookEventTypeName(subscription.getWebhookEventTypeName());
      webhookEvent.setEventStatusId(WebhookEventStatus.NEW.getId());
      return (webhookEvent);
   }

}

