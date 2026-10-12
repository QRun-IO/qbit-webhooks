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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** PVS for webhook event types - custom PVS implementation, that uses Webhook
 ** Registry as backend.
 *******************************************************************************/
public class WebhookEventTypePossibleValueSource implements QCustomPossibleValueProvider<String>, MetaDataProducerInterface<QPossibleValueSource>
{
   public static final String NAME = "WebhookEventType";



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public QPossibleValueSource produce(QInstance qInstance) throws QException
   {
      return new QPossibleValueSource()
         .withName(NAME)
         .withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(getClass()));
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public QPossibleValue<String> getPossibleValue(Serializable id)
   {
      WebhookEventType webhookEventType = WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).getWebhookEventType(ValueUtils.getValueAsString(id));
      if(webhookEventType == null)
      {
         return (null);
      }

      return getPossibleValue(webhookEventType);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private static QPossibleValue<String> getPossibleValue(WebhookEventType webhookEventType)
   {
      return new QPossibleValue<>(webhookEventType.getName(), webhookEventType.getLabel());
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
   {
      List<QPossibleValue<String>> allPossibleValues = new ArrayList<>();
      for(WebhookEventType webhookEventType : WebhooksRegistry.ofOrWithNew(QContext.getQInstance()).getAllWebhookEventTypes())
      {
         allPossibleValues.add(getPossibleValue(webhookEventType));
      }

      return completeCustomPVSSearch(input, allPossibleValues);
   }

}
