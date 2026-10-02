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
import com.kingsrook.qbits.webhooks.registry.WebhookEventTypePossibleValueSource;
import com.kingsrook.qbits.webhooks.tables.WebhookEventTableCustomizer;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QAssociation;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.ChildJoin;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.ChildRecordListWidget;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.ChildTable;
import com.kingsrook.qqq.backend.core.model.metadata.producers.annotations.QMetaDataProducingEntity;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Association;
import com.kingsrook.qqq.backend.core.model.metadata.tables.ExposedJoin;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.tables.QQQTable;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.collections.MutableList;


/*******************************************************************************
 ** QRecord Entity for WebhookEvent table
 *******************************************************************************/
@QMetaDataProducingEntity(
   producePossibleValueSource = true,
   produceTableMetaData = true,
   tableMetaDataCustomizer = WebhookEvent.TableMetaDataCustomizer.class,
   childTables = {
      @ChildTable(
         childTableEntityClass = WebhookEventSendLog.class,
         joinFieldName = "webhookEventId",
         childJoin = @ChildJoin(enabled = true),
         childRecordListWidget = @ChildRecordListWidget(label = "Send Log", enabled = true)),
      @ChildTable(
         childTableEntityClass = WebhookEventContent.class,
         joinFieldName = "webhookEventId",
         childJoin = @ChildJoin(enabled = true, isOneToOne = true))
   }
)
public class WebhookEvent extends QRecordEntity
{
   public static final String TABLE_NAME               = "webhookEvent";
   public static final String CONTENT_ASSOCIATION_NAME = "content";



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
         String webhookParentJoinName             = QJoinMetaData.makeInferredJoinName(Webhook.TABLE_NAME, WebhookEvent.TABLE_NAME);
         String webhookSubscriptionParentJoinName = QJoinMetaData.makeInferredJoinName(WebhookSubscription.TABLE_NAME, WebhookEvent.TABLE_NAME);
         String contentChildJoinName              = QJoinMetaData.makeInferredJoinName(WebhookEvent.TABLE_NAME, WebhookEventContent.TABLE_NAME);
         String sendLogChildJoinName              = QJoinMetaData.makeInferredJoinName(WebhookEvent.TABLE_NAME, WebhookEventSendLog.TABLE_NAME);

         QFieldSection t1section = SectionFactory.defaultT1("id", "webhookId", "webhookSubscriptionId", "webhookEventTypeName");
         table
            .withIcon(new QIcon().withName("notifications"))
            .withRecordLabelFormat("%s")
            .withRecordLabelFields("id")
            .withSection(t1section)
            .withSection(SectionFactory.defaultT2("eventStatusId", "eventSourceRecordQqqTableId", "eventSourceRecordId", "nextAttemptTimestamp"))
            .withSection(SectionFactory.customT2("sendLogs", new QIcon("receipt_long")).withWidgetName(sendLogChildJoinName))
            .withSection(SectionFactory.customT2("content", new QIcon("description"), WebhookEventContent.TABLE_NAME + ".postBody"))
            .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
            .withExposedJoin(new ExposedJoin().withLabel("Webhook").withJoinPath(List.of(webhookParentJoinName)).withJoinTable(Webhook.TABLE_NAME))
            .withExposedJoin(new ExposedJoin().withLabel("Subscription").withJoinPath(List.of(webhookSubscriptionParentJoinName)).withJoinTable(WebhookSubscription.TABLE_NAME))
            .withExposedJoin(new ExposedJoin().withLabel("Send Log").withJoinPath(List.of(sendLogChildJoinName)).withJoinTable(WebhookEventSendLog.TABLE_NAME))
            .withExposedJoin(new ExposedJoin().withLabel("Content").withJoinPath(List.of(contentChildJoinName)).withJoinTable(WebhookEventContent.TABLE_NAME))
            .withAssociation(new Association().withName(CONTENT_ASSOCIATION_NAME).withJoinName(contentChildJoinName).withAssociatedTableName(WebhookEventContent.TABLE_NAME));

         QBitConfig qBitConfig = QBitProductionContext.peekQBitConfig();
         if(qBitConfig instanceof WebhooksQBitConfig webhooksQBitConfig)
         {
            t1section.setFieldNames(new MutableList<>(t1section.getFieldNames()));
            for(QFieldMetaData fieldMetaData : CollectionUtils.nonNullList(webhooksQBitConfig.getSecurityFields()))
            {
               table.addField(fieldMetaData.clone());
               t1section.getFieldNames().add(fieldMetaData.getName());
            }

            for(RecordSecurityLock recordSecurityLock : CollectionUtils.nonNullList(webhooksQBitConfig.getRecordSecurityLocks()))
            {
               table.withRecordSecurityLock(recordSecurityLock);
            }
         }

         table.withCustomizer(TableCustomizers.POST_QUERY_RECORD, new QCodeReference(WebhookEventTableCustomizer.class));
         table.getField("eventSourceRecordId").withFieldAdornment(new FieldAdornment(AdornmentType.LINK).withValue(AdornmentType.LinkValues.TO_RECORD_FROM_TABLE_DYNAMIC, true));

         QFieldMetaData eventStatusIdField = table.getField("eventStatusId");
         addEventStatusChipAdornment(eventStatusIdField);

         return (table);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      public static QFieldMetaData addEventStatusChipAdornment(QFieldMetaData eventStatusIdField)
      {
         eventStatusIdField.withFieldAdornment(new FieldAdornment(AdornmentType.CHIP)
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookEventStatus.NEW, "pending", AdornmentType.ChipValues.COLOR_DEFAULT))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookEventStatus.SENDING, "send", AdornmentType.ChipValues.COLOR_INFO))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookEventStatus.DELIVERED, "check", AdornmentType.ChipValues.COLOR_SUCCESS))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookEventStatus.AWAITING_RETRY, "hourglass_top", AdornmentType.ChipValues.COLOR_WARNING))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookEventStatus.FAILED, "error", AdornmentType.ChipValues.COLOR_ERROR)));
         return eventStatusIdField;
      }
   }



   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(possibleValueSourceName = Webhook.TABLE_NAME)
   private Integer webhookId;

   @QField(possibleValueSourceName = WebhookSubscription.TABLE_NAME)
   private Integer webhookSubscriptionId;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.ERROR, isRequired = true, possibleValueSourceName = WebhookEventTypePossibleValueSource.NAME, label = "Event Type")
   private String webhookEventTypeName;

   @QField(label = "Event Source Record Table", possibleValueSourceName = QQQTable.TABLE_NAME)
   private Integer eventSourceRecordQqqTableId;

   @QField()
   private Integer eventSourceRecordId;

   @QField(possibleValueSourceName = WebhookEventStatus.NAME)
   private Integer eventStatusId;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;

   @QField(isEditable = false)
   private Instant nextAttemptTimestamp;

   @QAssociation(name = CONTENT_ASSOCIATION_NAME)
   private List<WebhookEventContent> content;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public WebhookEvent()
   {
   }



   /*******************************************************************************
    ** Constructor that takes a QRecord
    *******************************************************************************/
   public WebhookEvent(QRecord record)
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
   public WebhookEvent withId(Integer id)
   {
      this.id = id;
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
   public WebhookEvent withWebhookId(Integer webhookId)
   {
      this.webhookId = webhookId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for webhookSubscriptionId
    *******************************************************************************/
   public Integer getWebhookSubscriptionId()
   {
      return (this.webhookSubscriptionId);
   }



   /*******************************************************************************
    ** Setter for webhookSubscriptionId
    *******************************************************************************/
   public void setWebhookSubscriptionId(Integer webhookSubscriptionId)
   {
      this.webhookSubscriptionId = webhookSubscriptionId;
   }



   /*******************************************************************************
    ** Fluent setter for webhookSubscriptionId
    *******************************************************************************/
   public WebhookEvent withWebhookSubscriptionId(Integer webhookSubscriptionId)
   {
      this.webhookSubscriptionId = webhookSubscriptionId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for webhookEventTypeName
    *******************************************************************************/
   public String getWebhookEventTypeName()
   {
      return (this.webhookEventTypeName);
   }



   /*******************************************************************************
    ** Setter for webhookEventTypeName
    *******************************************************************************/
   public void setWebhookEventTypeName(String webhookEventTypeName)
   {
      this.webhookEventTypeName = webhookEventTypeName;
   }



   /*******************************************************************************
    ** Fluent setter for webhookEventTypeName
    *******************************************************************************/
   public WebhookEvent withWebhookEventTypeName(String webhookEventTypeName)
   {
      this.webhookEventTypeName = webhookEventTypeName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventSourceRecordQqqTableId
    *******************************************************************************/
   public Integer getEventSourceRecordQqqTableId()
   {
      return (this.eventSourceRecordQqqTableId);
   }



   /*******************************************************************************
    ** Setter for eventSourceRecordQqqTableId
    *******************************************************************************/
   public void setEventSourceRecordQqqTableId(Integer eventSourceRecordQqqTableId)
   {
      this.eventSourceRecordQqqTableId = eventSourceRecordQqqTableId;
   }



   /*******************************************************************************
    ** Fluent setter for eventSourceRecordQqqTableId
    *******************************************************************************/
   public WebhookEvent withEventSourceRecordQqqTableId(Integer eventSourceRecordQqqTableId)
   {
      this.eventSourceRecordQqqTableId = eventSourceRecordQqqTableId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventSourceRecordId
    *******************************************************************************/
   public Integer getEventSourceRecordId()
   {
      return (this.eventSourceRecordId);
   }



   /*******************************************************************************
    ** Setter for eventSourceRecordId
    *******************************************************************************/
   public void setEventSourceRecordId(Integer eventSourceRecordId)
   {
      this.eventSourceRecordId = eventSourceRecordId;
   }



   /*******************************************************************************
    ** Fluent setter for eventSourceRecordId
    *******************************************************************************/
   public WebhookEvent withEventSourceRecordId(Integer eventSourceRecordId)
   {
      this.eventSourceRecordId = eventSourceRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventStatusId
    *******************************************************************************/
   public Integer getEventStatusId()
   {
      return (this.eventStatusId);
   }



   /*******************************************************************************
    ** Setter for eventStatusId
    *******************************************************************************/
   public void setEventStatusId(Integer eventStatusId)
   {
      this.eventStatusId = eventStatusId;
   }



   /*******************************************************************************
    ** Fluent setter for eventStatusId
    *******************************************************************************/
   public WebhookEvent withEventStatusId(Integer eventStatusId)
   {
      this.eventStatusId = eventStatusId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public WebhookEvent withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public WebhookEvent withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }


   /*******************************************************************************
    ** Getter for content
    *******************************************************************************/
   public List<WebhookEventContent> getContent()
   {
      return (this.content);
   }



   /*******************************************************************************
    ** Setter for content
    *******************************************************************************/
   public void setContent(List<WebhookEventContent> content)
   {
      this.content = content;
   }



   /*******************************************************************************
    ** Fluent setter for content
    *******************************************************************************/
   public WebhookEvent withContent(List<WebhookEventContent> content)
   {
      this.content = content;
      return (this);
   }



   /*******************************************************************************
    ** Getter for nextAttemptTimestamp
    *******************************************************************************/
   public Instant getNextAttemptTimestamp()
   {
      return (this.nextAttemptTimestamp);
   }



   /*******************************************************************************
    ** Setter for nextAttemptTimestamp
    *******************************************************************************/
   public void setNextAttemptTimestamp(Instant nextAttemptTimestamp)
   {
      this.nextAttemptTimestamp = nextAttemptTimestamp;
   }



   /*******************************************************************************
    ** Fluent setter for nextAttemptTimestamp
    *******************************************************************************/
   public WebhookEvent withNextAttemptTimestamp(Instant nextAttemptTimestamp)
   {
      this.nextAttemptTimestamp = nextAttemptTimestamp;
      return (this);
   }


}
