/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
