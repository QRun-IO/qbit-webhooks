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

package com.kingsrook.qbits.webhooks.registry;


import java.util.List;
import java.util.function.Consumer;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.actions.DefaultWebhookEventTypeCustomizer;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QInstanceValidationException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;


/*******************************************************************************
 ** Unit test for validation in the webhooks registry
 *******************************************************************************/
public class WebhooksRegistryValidationAndEnrichmentTest extends BaseTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testValidation() throws QException
   {
      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType())),
         "Webhook Event Type: null is missing a name",
         "Webhook Event Type: null is missing a category"
      );

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT)
         )),
         "Webhook Event Type: myEventType requires a tableName, but does not have one"
      );

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT)
            .withTableName("nonTable")
         )),
         "Webhook Event Type: myEventType references a non-existing tableName: nonTable"
      );

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT_WITH_FIELD)
            .withTableName(WebhooksTestApplication.TABLE_NAME_PERSON)
         )),
         "Webhook Event Type: myEventType requires a fieldName, but does not have one"
      );

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT_WITH_FIELD)
            .withTableName(WebhooksTestApplication.TABLE_NAME_PERSON)
            .withFieldName("nonField")
         )),
         "Webhook Event Type: myEventType references a non-existing fieldName: nonField for table person"
      );

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT_WITH_VALUE)
            .withTableName(WebhooksTestApplication.TABLE_NAME_PERSON)
            .withFieldName("firstName")
         )),
         "Webhook Event Type: myEventType requires a value, but does not have one"
      );

      assertValidationSuccess(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.INSERT_WITH_VALUE)
            .withTableName(WebhooksTestApplication.TABLE_NAME_PERSON)
            .withFieldName("firstName")
            .withValue("Jean-Luc")
         )));

      assertValidationFailureReasons(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.AD_HOC)
            .withCustomizer(new QCodeReference(getClass()))
         )),
         "Webhook Event Type: myEventType customizer codeReference: CodeReference is not of the expected type: interface com.kingsrook.qbits.webhooks.actions.WebhookEventTypeCustomizerInterface"
      );

      assertValidationSuccess(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.AD_HOC)
            .withCustomizer(new QCodeReference(DefaultWebhookEventTypeCustomizer.class))
         )));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testEnrichment() throws QException
   {
      QInstance enrichedInstance = assertValidationSuccess(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withCategory(WebhookEventCategory.AD_HOC)
         )));
      assertEquals("My Event Type", WebhooksRegistry.of(enrichedInstance).getWebhookEventType("myEventType").getLabel());

      QInstance didntNeedEnrichedInstance = assertValidationSuccess(
         (qInstance -> WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
            .withName("myEventType")
            .withLabel("My Label")
            .withCategory(WebhookEventCategory.AD_HOC)
         )));
      assertEquals("My Label", WebhooksRegistry.of(didntNeedEnrichedInstance).getWebhookEventType("myEventType").getLabel());
   }



   /*******************************************************************************
    ** Assert that an instance is valid!
    *******************************************************************************/
   public static QInstance assertValidationSuccess(Consumer<QInstance> setup) throws QException
   {
      try
      {
         QInstance qInstance = new WebhooksTestApplication().defineQInstance();
         setup.accept(qInstance);
         new QInstanceValidator().validate(qInstance);
         return (qInstance);
      }
      catch(QInstanceValidationException e)
      {
         fail("Expected no validation errors, but received: " + e.getMessage());
         return null;
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static void assertValidationFailureReasons(Consumer<QInstance> setup, String... expectedReasons) throws QException
   {
      try
      {
         QInstance qInstance = new WebhooksTestApplication().defineQInstance();
         setup.accept(qInstance);
         new QInstanceValidator().validate(qInstance);
         fail("Should have thrown validationException");
      }
      catch(QInstanceValidationException e)
      {
         assertValidationFailureReasons(false, e.getReasons(), expectedReasons);
      }

   }



   /*******************************************************************************
    **
    *******************************************************************************/
   public static void assertValidationFailureReasons(boolean allowExtraReasons, List<String> actualReasons, String... expectedReasons)
   {
      if(!allowExtraReasons)
      {
         int noOfReasons = actualReasons == null ? 0 : actualReasons.size();
         assertEquals(expectedReasons.length, noOfReasons, "Expected number of validation failure reasons.\nExpected reasons: " + String.join(",", expectedReasons)
            + "\nActual reasons: " + (noOfReasons > 0 ? String.join("\n", actualReasons) : "--"));
      }

      for(String reason : expectedReasons)
      {
         assertReason(reason, actualReasons);
      }
   }



   /*******************************************************************************
    ** utility method for asserting that a specific reason string is found within
    ** the list of reasons in the QInstanceValidationException.
    **
    *******************************************************************************/
   public static void assertReason(String reason, List<String> actualReasons)
   {
      assertNotNull(actualReasons, "Expected there to be a reason for the failure (but there was not)");
      assertThat(actualReasons)
         .withFailMessage("Expected any of:\n%s\nTo match: [%s]", actualReasons, reason)
         .anyMatch(s -> s.contains(reason));
   }

}