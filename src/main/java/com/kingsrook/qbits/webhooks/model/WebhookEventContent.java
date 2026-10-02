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


import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.QMetaDataProducingEntity;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.ExposedJoin;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** QRecord Entity for WebhookEventContent table
 *******************************************************************************/
@QMetaDataProducingEntity(
   producePossibleValueSource = true,
   produceTableMetaData = true,
   tableMetaDataCustomizer = WebhookEventContent.TableMetaDataCustomizer.class
)
public class WebhookEventContent extends QRecordEntity
{
   public static final String TABLE_NAME = "webhookEventContent";



   /***************************************************************************
    **
    ***************************************************************************/
   public static class TableMetaDataCustomizer implements MetaDataCustomizerInterface<QTableMetaData>
   {

      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public QTableMetaData customizeMetaData(QInstance qInstance, QTableMetaData table) throws QException
      {
         String parentJoinName = QJoinMetaData.makeInferredJoinName(WebhookEvent.TABLE_NAME, WebhookEventContent.TABLE_NAME);

         table
            .withIcon(new QIcon().withName("description"))
            .withUniqueKey(new UniqueKey("webhookEventId"))
            .withRecordLabelFormat("Event %s")
            .withRecordLabelFields("webhookEventId")
            .withSection(SectionFactory.defaultT1("id", "webhookEventId"))
            .withSection(SectionFactory.defaultT2("postBody"))
            .withExposedJoin(new ExposedJoin().withLabel("Event").withJoinPath(List.of(parentJoinName)).withJoinTable(WebhookEvent.TABLE_NAME));

         table.getField("postBody").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR)
            .withValue(AdornmentType.CodeEditorValues.languageMode("json")));

         QBitConfig qBitConfig = QBitProductionContext.peekQBitConfig();
         if(qBitConfig instanceof WebhooksQBitConfig webhooksQBitConfig)
         {
            for(RecordSecurityLock recordSecurityLock : CollectionUtils.nonNullList(webhooksQBitConfig.getRecordSecurityLocks()))
            {
               RecordSecurityLock lockClone = recordSecurityLock.clone();
               lockClone.setFieldName(WebhookEvent.TABLE_NAME + "." + lockClone.getFieldName());
               lockClone.setJoinNameChain(List.of(parentJoinName));
               table.withRecordSecurityLock(lockClone);
            }
         }

         return (table);
      }
   }



   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(possibleValueSourceName = WebhookEvent.TABLE_NAME)
   private Integer webhookEventId;

   ///////////////////////////
   // text, so no maxLength //
   ///////////////////////////
   @QField()
   private String postBody;

   // todo? ///////////////////////////
   // todo? // text, so no maxLength //
   // todo? ///////////////////////////
   // todo? @QField()
   // todo? private String headers;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public WebhookEventContent()
   {
   }



   /*******************************************************************************
    ** Constructor that takes a QRecord
    *******************************************************************************/
   public WebhookEventContent(QRecord record)
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public WebhookEventContent withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for webhookEventId
    *******************************************************************************/
   public Integer getWebhookEventId()
   {
      return (this.webhookEventId);
   }



   /*******************************************************************************
    ** Setter for webhookEventId
    *******************************************************************************/
   public void setWebhookEventId(Integer webhookEventId)
   {
      this.webhookEventId = webhookEventId;
   }



   /*******************************************************************************
    ** Fluent setter for webhookEventId
    *******************************************************************************/
   public WebhookEventContent withWebhookEventId(Integer webhookEventId)
   {
      this.webhookEventId = webhookEventId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for postBody
    *******************************************************************************/
   public String getPostBody()
   {
      return (this.postBody);
   }



   /*******************************************************************************
    ** Setter for postBody
    *******************************************************************************/
   public void setPostBody(String postBody)
   {
      this.postBody = postBody;
   }



   /*******************************************************************************
    ** Fluent setter for postBody
    *******************************************************************************/
   public WebhookEventContent withPostBody(String postBody)
   {
      this.postBody = postBody;
      return (this);
   }

}
