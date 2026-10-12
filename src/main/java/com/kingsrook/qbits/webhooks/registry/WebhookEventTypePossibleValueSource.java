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
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** The registered event types as a possible-value source (the "Event Type"
 ** picker on subscriptions and deliveries). Labels read
 ** {@code <label> (<name>)}, so people see both.
 *******************************************************************************/
public class WebhookEventTypePossibleValueSource implements QCustomPossibleValueProvider<String>
{
   public static final String NAME = "webhooks_event_type";



   /*******************************************************************************
    ** The source definition.
    *******************************************************************************/
   public static QPossibleValueSource definition()
   {
      return (new QPossibleValueSource()
         .withName(NAME)
         .withLabel("Webhook Event Type")
         .withIdType(QFieldType.STRING)
         .withCustomCodeReference(new QCodeReference(WebhookEventTypePossibleValueSource.class)));
   }



   /*******************************************************************************
    ** One value by id.
    *******************************************************************************/
   @Override
   public QPossibleValue<String> getPossibleValue(Serializable id)
   {
      WebhookEventType type = WebhooksRegistry.of(QContext.getQInstance()).get(ValueUtils.getValueAsString(id));
      return (type == null ? null : toValue(type));
   }



   /*******************************************************************************
    ** Search.
    *******************************************************************************/
   @Override
   public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
   {
      List<QPossibleValue<String>> values = new ArrayList<>();
      for(WebhookEventType type : WebhooksRegistry.of(QContext.getQInstance()).getAll())
      {
         values.add(toValue(type));
      }
      return (completeCustomPVSSearch(input, values));
   }



   /*******************************************************************************
    ** Value for a type.
    *******************************************************************************/
   private static QPossibleValue<String> toValue(WebhookEventType type)
   {
      String label = type.getLabel() == null ? type.getName() : type.getLabel() + " (" + type.getName() + ")";
      return (new QPossibleValue<>(type.getName(), label));
   }
}
