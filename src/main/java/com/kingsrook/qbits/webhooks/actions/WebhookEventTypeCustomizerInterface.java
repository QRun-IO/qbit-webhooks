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


import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.api.actions.QRecordApiAdapter;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** interface for doing work for a webhookEventType - where specific types can
 ** customize behavior.
 *******************************************************************************/
public interface WebhookEventTypeCustomizerInterface
{

   /***************************************************************************
    **
    ***************************************************************************/
   default WebhookEventContent buildEventContent(QRecord sourceRecord, String webhookEventTypeName, String apiName, String apiVersion) throws QException
   {
      WebhookEventType webhookEventType = WebhooksRegistry.of(QContext.getQInstance()).getWebhookEventType(webhookEventTypeName);

      Map<String, Object> postBody = new LinkedHashMap<>();

      Map<String, Object> webhookEventDetails = new LinkedHashMap<>();
      postBody.put("webhookEventDetails", webhookEventDetails);

      webhookEventDetails.put("webhookEventTypeName", webhookEventTypeName);
      webhookEventDetails.put("eventTimestamp", Instant.now().toString());

      if(webhookEventType != null && StringUtils.hasContent(webhookEventType.getTableName()))
      {
         webhookEventDetails.put("tableName", webhookEventType.getTableName());
      }

      webhookEventDetails.put("apiName", apiName);
      webhookEventDetails.put("apiVersion", apiVersion);

      Map<String, Serializable> apiRecord = QRecordApiAdapter.qRecordToApiMap(sourceRecord, sourceRecord.getTableName(), apiName, apiVersion);
      postBody.put("record", apiRecord);

      customizeEventContent(sourceRecord, webhookEventTypeName, apiName, apiVersion, postBody);

      String json = JsonUtils.toJsonCustomized(postBody, builder ->
         builder.serializationInclusion(JsonInclude.Include.ALWAYS));

      return new WebhookEventContent().withPostBody(json);
   }


   /***************************************************************************
    **
    ***************************************************************************/
   default void customizeEventContent(QRecord sourceRecord, String webhookEventTypeName, String apiName, String apiVersion, Map<String, Object> postBody) throws QException
   {
      /////////////////////
      // noop by default //
      /////////////////////
   }

}
