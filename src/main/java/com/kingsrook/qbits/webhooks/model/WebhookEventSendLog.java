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


import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;
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
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** QRecord Entity for WebhookEventSendLog table
 *******************************************************************************/
@QMetaDataProducingEntity(
   producePossibleValueSource = true,
   produceTableMetaData = true,
   tableMetaDataCustomizer = WebhookEventSendLog.TableMetaDataCustomizer.class
)
public class WebhookEventSendLog extends QRecordEntity
{
   public static final String TABLE_NAME = "webhookEventSendLog";



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
         String parentJoinName = QJoinMetaData.makeInferredJoinName(WebhookEvent.TABLE_NAME, WebhookEventSendLog.TABLE_NAME);

         table
            .withIcon(new QIcon().withName("receipt_long"))
            .withRecordLabelFormat("Event %s Attempt %s")
            .withRecordLabelFields("webhookEventId", "attemptNo")
            .withSection(SectionFactory.defaultT1("id", "webhookId", "webhookEventId", "attemptNo"))
            .withSection(SectionFactory.defaultT2("successful", "httpStatusCode", "errorMessage"))
            .withSection(SectionFactory.defaultT3("startTimestamp", "endTimestamp"))
            .withExposedJoin(new ExposedJoin().withLabel("Event").withJoinPath(List.of(parentJoinName)).withJoinTable(WebhookEvent.TABLE_NAME));

         table.getField("successful").withFieldAdornment(new FieldAdornment(AdornmentType.CHIP)
            .withValues(AdornmentType.ChipValues.iconAndColorValues(true, "done", AdornmentType.ChipValues.COLOR_SUCCESS))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(false, "error", AdornmentType.ChipValues.COLOR_ERROR)));

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

   @QField(possibleValueSourceName = Webhook.TABLE_NAME)
   private Integer webhookId;

   @QField(possibleValueSourceName = WebhookEvent.TABLE_NAME)
   private Integer webhookEventId;

   @QField(label = "HTTP Status Code")
   private Integer httpStatusCode;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String errorMessage;

   @QField()
   private Boolean successful;

   @QField()
   private Integer attemptNo;

   @QField(isEditable = false)
   private Instant startTimestamp;

   @QField(isEditable = false)
   private Instant endTimestamp;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public WebhookEventSendLog()
   {
   }



   /*******************************************************************************
    ** Constructor that takes a QRecord
    *******************************************************************************/
   public WebhookEventSendLog(QRecord record)
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
   public WebhookEventSendLog withId(Integer id)
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
   public WebhookEventSendLog withWebhookEventId(Integer webhookEventId)
   {
      this.webhookEventId = webhookEventId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for httpStatusCode
    *******************************************************************************/
   public Integer getHttpStatusCode()
   {
      return (this.httpStatusCode);
   }



   /*******************************************************************************
    ** Setter for httpStatusCode
    *******************************************************************************/
   public void setHttpStatusCode(Integer httpStatusCode)
   {
      this.httpStatusCode = httpStatusCode;
   }



   /*******************************************************************************
    ** Fluent setter for httpStatusCode
    *******************************************************************************/
   public WebhookEventSendLog withHttpStatusCode(Integer httpStatusCode)
   {
      this.httpStatusCode = httpStatusCode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for successful
    *******************************************************************************/
   public Boolean getSuccessful()
   {
      return (this.successful);
   }



   /*******************************************************************************
    ** Setter for successful
    *******************************************************************************/
   public void setSuccessful(Boolean successful)
   {
      this.successful = successful;
   }



   /*******************************************************************************
    ** Fluent setter for successful
    *******************************************************************************/
   public WebhookEventSendLog withSuccessful(Boolean successful)
   {
      this.successful = successful;
      return (this);
   }



   /*******************************************************************************
    ** Getter for attemptNo
    *******************************************************************************/
   public Integer getAttemptNo()
   {
      return (this.attemptNo);
   }



   /*******************************************************************************
    ** Setter for attemptNo
    *******************************************************************************/
   public void setAttemptNo(Integer attemptNo)
   {
      this.attemptNo = attemptNo;
   }



   /*******************************************************************************
    ** Fluent setter for attemptNo
    *******************************************************************************/
   public WebhookEventSendLog withAttemptNo(Integer attemptNo)
   {
      this.attemptNo = attemptNo;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startTimestamp
    *******************************************************************************/
   public Instant getStartTimestamp()
   {
      return (this.startTimestamp);
   }



   /*******************************************************************************
    ** Setter for startTimestamp
    *******************************************************************************/
   public void setStartTimestamp(Instant startTimestamp)
   {
      this.startTimestamp = startTimestamp;
   }



   /*******************************************************************************
    ** Fluent setter for startTimestamp
    *******************************************************************************/
   public WebhookEventSendLog withStartTimestamp(Instant startTimestamp)
   {
      this.startTimestamp = startTimestamp;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endTimestamp
    *******************************************************************************/
   public Instant getEndTimestamp()
   {
      return (this.endTimestamp);
   }



   /*******************************************************************************
    ** Setter for endTimestamp
    *******************************************************************************/
   public void setEndTimestamp(Instant endTimestamp)
   {
      this.endTimestamp = endTimestamp;
   }



   /*******************************************************************************
    ** Fluent setter for endTimestamp
    *******************************************************************************/
   public WebhookEventSendLog withEndTimestamp(Instant endTimestamp)
   {
      this.endTimestamp = endTimestamp;
      return (this);
   }



   /*******************************************************************************
    ** Getter for errorMessage
    *******************************************************************************/
   public String getErrorMessage()
   {
      return (this.errorMessage);
   }



   /*******************************************************************************
    ** Setter for errorMessage
    *******************************************************************************/
   public void setErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
   }



   /*******************************************************************************
    ** Fluent setter for errorMessage
    *******************************************************************************/
   public WebhookEventSendLog withErrorMessage(String errorMessage)
   {
      this.errorMessage = errorMessage;
      return (this);
   }



   /*******************************************************************************
    ** Getter for webhookId
    *******************************************************************************/
   public Integer getWebhookId()
   {
      return (this.webhookId);
   }



   /*******************************************************************************
    ** Setter for webhookId
    *******************************************************************************/
   public void setWebhookId(Integer webhookId)
   {
      this.webhookId = webhookId;
   }



   /*******************************************************************************
    ** Fluent setter for webhookId
    *******************************************************************************/
   public WebhookEventSendLog withWebhookId(Integer webhookId)
   {
      this.webhookId = webhookId;
      return (this);
   }


}
