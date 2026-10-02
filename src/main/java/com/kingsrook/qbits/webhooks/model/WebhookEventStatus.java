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

package com.kingsrook.qbits.webhooks.model;


import java.util.Objects;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PossibleValueEnum;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.QMetaDataProducingPossibleValueEnum;


/*******************************************************************************
 **
 *******************************************************************************/
@QMetaDataProducingPossibleValueEnum()
public enum WebhookEventStatus implements PossibleValueEnum<Integer>
{
   NEW(1, "New"),
   SENDING(2, "Sending"),
   DELIVERED(3, "Delivered"),
   AWAITING_RETRY(4, "Awaiting Retry"),
   FAILED(5, "Failed");

   private final Integer id;
   private final String  label;

   public static final String NAME = "WebhookEventStatus";

   public static final String DEFAULT_VALUE = "1";

   static
   {
      Objects.requireNonNull(getById(Integer.parseInt(DEFAULT_VALUE)), "Default value '" + DEFAULT_VALUE + " in WebhookEventStatus is not a defined enum value");
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   WebhookEventStatus(Integer id, String label)
   {
      this.id = id;
      this.label = label;
   }



   /*******************************************************************************
    ** Get instance by id
    **
    *******************************************************************************/
   public static WebhookEventStatus getById(Integer id)
   {
      if(id == null)
      {
         return (null);
      }

      for(WebhookEventStatus value : WebhookEventStatus.values())
      {
         if(Objects.equals(value.id, id))
         {
            return (value);
         }
      }

      return (null);
   }



   /*******************************************************************************
    ** Getter for id
    **
    *******************************************************************************/
   public Integer getId()
   {
      return id;
   }



   /*******************************************************************************
    ** Getter for label
    **
    *******************************************************************************/
   public String getLabel()
   {
      return label;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public Integer getPossibleValueId()
   {
      return (getId());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getPossibleValueLabel()
   {
      return (getLabel());
   }
}
