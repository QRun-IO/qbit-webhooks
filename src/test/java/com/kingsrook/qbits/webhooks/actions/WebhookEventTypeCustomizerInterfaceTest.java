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


import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qbits.webhooks.WebhooksTestApplication;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Unit test for WebhookEventTypeCustomizerInterface
 *******************************************************************************/
class WebhookEventTypeCustomizerInterfaceTest extends BaseTest
{

   /*******************************************************************************
    ** the post body sent to receivers must keep fields whose value is null, so a
    ** receiver can tell "field is null" from "field not in this api version".
    *******************************************************************************/
   @Test
   void testBuildEventContentKeepsNullValues() throws QException
   {
      String eventTypeName = "personAdHoc";
      registerAdHocEventType(eventTypeName);

      QRecord person = new QRecord()
         .withTableName(WebhooksTestApplication.TABLE_NAME_PERSON)
         .withValue("id", 1)
         .withValue("firstName", "Jean Luc");

      WebhookEventContent content = new DefaultWebhookEventTypeCustomizer()
         .buildEventContent(person, eventTypeName, WebhooksTestApplication.API_NAME, WebhooksTestApplication.API_V1);

      JSONObject record = new JSONObject(content.getPostBody()).getJSONObject("record");
      assertEquals("Jean Luc", record.getString("firstName"));
      assertTrue(record.has("lastName"), "null-valued field should be present in the post body");
      assertTrue(record.isNull("lastName"));
   }

}
