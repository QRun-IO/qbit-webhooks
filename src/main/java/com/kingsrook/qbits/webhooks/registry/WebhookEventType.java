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
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** A specific event type for which the application can send webhook events.
 *******************************************************************************/
public class WebhookEventType implements Serializable
{
   private String               name;
   private String               label;
   private WebhookEventCategory category;
   private String               tableName;
   private String               fieldName;
   private Serializable         value;
   private QCodeReference       customizer;



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public WebhookEventType withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for category
    *******************************************************************************/
   public WebhookEventCategory getCategory()
   {
      return (this.category);
   }



   /*******************************************************************************
    ** Setter for category
    *******************************************************************************/
   public void setCategory(WebhookEventCategory category)
   {
      this.category = category;
   }



   /*******************************************************************************
    ** Fluent setter for category
    *******************************************************************************/
   public WebhookEventType withCategory(WebhookEventCategory category)
   {
      this.category = category;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Setter for tableName
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public WebhookEventType withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fieldName
    *******************************************************************************/
   public String getFieldName()
   {
      return (this.fieldName);
   }



   /*******************************************************************************
    ** Setter for fieldName
    *******************************************************************************/
   public void setFieldName(String fieldName)
   {
      this.fieldName = fieldName;
   }



   /*******************************************************************************
    ** Fluent setter for fieldName
    *******************************************************************************/
   public WebhookEventType withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for value
    *******************************************************************************/
   public Serializable getValue()
   {
      return (this.value);
   }



   /*******************************************************************************
    ** Setter for value
    *******************************************************************************/
   public void setValue(Serializable value)
   {
      this.value = value;
   }



   /*******************************************************************************
    ** Fluent setter for value
    *******************************************************************************/
   public WebhookEventType withValue(Serializable value)
   {
      this.value = value;
      return (this);
   }



   /*******************************************************************************
    ** Getter for customizer
    *******************************************************************************/
   public QCodeReference getCustomizer()
   {
      return (this.customizer);
   }



   /*******************************************************************************
    ** Setter for customizer
    *******************************************************************************/
   public void setCustomizer(QCodeReference customizer)
   {
      this.customizer = customizer;
   }



   /*******************************************************************************
    ** Fluent setter for customizer
    *******************************************************************************/
   public WebhookEventType withCustomizer(QCodeReference customizer)
   {
      this.customizer = customizer;
      return (this);
   }


   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (this.label);
   }



   /*******************************************************************************
    ** Setter for label
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Fluent setter for label
    *******************************************************************************/
   public WebhookEventType withLabel(String label)
   {
      this.label = label;
      return (this);
   }


}
