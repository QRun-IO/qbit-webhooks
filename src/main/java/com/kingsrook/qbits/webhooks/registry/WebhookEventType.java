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
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;


/*******************************************************************************
 ** A kind of event that endpoints can subscribe to, such as
 ** {@code cms_blog_post.updated} or {@code post.published}.
 **
 ** <p>Table and process types are registered automatically from the qbit
 ** config ({@code tableEventSources}, {@code processEventSources},
 ** {@code esbTableEventSources}). Custom types are registered in code with
 ** {@link WebhooksRegistry#register(WebhookEventType)}.</p>
 **
 ** <p>Optional {@code fieldName} narrows a table type: an update fires only
 ** when that field changed, and with {@code value} only when it changed to that
 ** value (a collection means any of them). The optional {@code customizer}
 ** shapes the payload in code ({@link WebhookPayloadCustomizerInterface}).</p>
 *******************************************************************************/
public class WebhookEventType implements Serializable
{
   private String           name;
   private String           label;
   private String           description;
   private WebhookEventKind kind;
   private String           tableName;
   private String           processName;
   private String           fieldName;
   private Serializable     value;
   private Boolean          remote = false;
   private QCodeReference   customizer;



   /*******************************************************************************
    ** Fluent setter for name.
    *******************************************************************************/
   public WebhookEventType withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for label.
    *******************************************************************************/
   public WebhookEventType withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for description (shown to people choosing a type).
    *******************************************************************************/
   public WebhookEventType withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for kind.
    *******************************************************************************/
   public WebhookEventType withKind(WebhookEventKind kind)
   {
      this.kind = kind;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for tableName.
    *******************************************************************************/
   public WebhookEventType withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for processName.
    *******************************************************************************/
   public WebhookEventType withProcessName(String processName)
   {
      this.processName = processName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for fieldName.
    *******************************************************************************/
   public WebhookEventType withFieldName(String fieldName)
   {
      this.fieldName = fieldName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for value.
    *******************************************************************************/
   public WebhookEventType withValue(Serializable value)
   {
      this.value = value;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for remote (the table is in another service; events come
    ** only from the ESB).
    *******************************************************************************/
   public WebhookEventType withRemote(Boolean remote)
   {
      this.remote = remote;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for customizer.
    *******************************************************************************/
   public WebhookEventType withCustomizer(QCodeReference customizer)
   {
      this.customizer = customizer;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name.
    *******************************************************************************/
   public String getName()
   {
      return (name);
   }



   /*******************************************************************************
    ** Getter for label.
    *******************************************************************************/
   public String getLabel()
   {
      return (label);
   }



   /*******************************************************************************
    ** Setter for label.
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Getter for description.
    *******************************************************************************/
   public String getDescription()
   {
      return (description);
   }



   /*******************************************************************************
    ** Getter for kind.
    *******************************************************************************/
   public WebhookEventKind getKind()
   {
      return (kind);
   }



   /*******************************************************************************
    ** Getter for tableName.
    *******************************************************************************/
   public String getTableName()
   {
      return (tableName);
   }



   /*******************************************************************************
    ** Getter for processName.
    *******************************************************************************/
   public String getProcessName()
   {
      return (processName);
   }



   /*******************************************************************************
    ** Getter for fieldName.
    *******************************************************************************/
   public String getFieldName()
   {
      return (fieldName);
   }



   /*******************************************************************************
    ** Getter for value.
    *******************************************************************************/
   public Serializable getValue()
   {
      return (value);
   }



   /*******************************************************************************
    ** Getter for remote.
    *******************************************************************************/
   public Boolean getRemote()
   {
      return (remote);
   }



   /*******************************************************************************
    ** Getter for customizer.
    *******************************************************************************/
   public QCodeReference getCustomizer()
   {
      return (customizer);
   }
}
