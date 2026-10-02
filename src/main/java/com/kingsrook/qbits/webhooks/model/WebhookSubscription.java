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
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.actions.ClearWebhookSubscriptionHelperMemoizationsTableCustomizer;
import com.kingsrook.qbits.webhooks.registry.WebhookEventTypePossibleValueSource;
import com.kingsrook.qqq.api.model.metadata.ApiInstanceMetaDataProvider;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
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
import com.kingsrook.qqq.backend.core.model.metadata.tables.ExposedJoin;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** QRecord Entity for WebhookSubscription table
 *******************************************************************************/
@QMetaDataProducingEntity(
   producePossibleValueSource = true,
   produceTableMetaData = true,
   tableMetaDataCustomizer = WebhookSubscription.TableMetaDataCustomizer.class,
   childTables = {
      @ChildTable(
         childTableEntityClass = WebhookEvent.class,
         joinFieldName = "webhookId",
         childJoin = @ChildJoin(enabled = true),
         childRecordListWidget = @ChildRecordListWidget(label = "Events", enabled = true, maxRows = 25, widgetMetaDataCustomizer = WebhookSubscription.EventChildRecordWidgetMetaDataCustomizer.class))
   }
)
public class WebhookSubscription extends QRecordEntity
{
   public static final String TABLE_NAME = "webhookSubscription";



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
         String parentJoinName = QJoinMetaData.makeInferredJoinName(Webhook.TABLE_NAME, WebhookSubscription.TABLE_NAME);
         String childJoinName  = QJoinMetaData.makeInferredJoinName(WebhookSubscription.TABLE_NAME, WebhookEvent.TABLE_NAME);

         table
            .withIcon(new QIcon().withName("subscriptions"))
            .withRecordLabelFormat("%s - %s")
            .withRecordLabelFields("webhookId", "webhookEventTypeName")
            .withUniqueKey(new UniqueKey("webhookEventTypeName", "webhookId"))
            .withSection(SectionFactory.defaultT1("id", "webhookId", "webhookEventTypeName"))
            .withSection(SectionFactory.defaultT2("activeStatusId", "apiName", "apiVersion"))
            // todo - if adding filters in future .withSection(SectionFactory.customT2("hidden", new QIcon().withName("hidden"), "queryFilterJson").withIsHidden(true))
            // todo - if adding filters in future .withSection(SectionFactory.customT2("filter", new QIcon("filter_alt")).withWidgetName(WebhookSubscriptionQueryFilterWidgetMetaDataProducer.NAME))
            .withSection(SectionFactory.customT2("events", new QIcon("notifications")).withWidgetName(childJoinName))
            .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
            .withExposedJoin(new ExposedJoin().withLabel("Webhook").withJoinPath(List.of(parentJoinName)).withJoinTable(Webhook.TABLE_NAME));

         QBitConfig qBitConfig = QBitProductionContext.peekQBitConfig();
         if(qBitConfig instanceof WebhooksQBitConfig webhooksQBitConfig)
         {
            if(StringUtils.hasContent(webhooksQBitConfig.getDefaultApiNameForNewSubscriptions()))
            {
               table.getField("apiName").withDefaultValue(webhooksQBitConfig.getDefaultApiNameForNewSubscriptions());
            }

            if(StringUtils.hasContent(webhooksQBitConfig.getDefaultApiVersionForNewSubscriptions()))
            {
               table.getField("apiVersion").withDefaultValue(webhooksQBitConfig.getDefaultApiVersionForNewSubscriptions());
            }

            for(RecordSecurityLock recordSecurityLock : CollectionUtils.nonNullList(webhooksQBitConfig.getRecordSecurityLocks()))
            {
               RecordSecurityLock lockClone = recordSecurityLock.clone();
               lockClone.setFieldName(Webhook.TABLE_NAME + "." + lockClone.getFieldName());
               lockClone.setJoinNameChain(List.of(parentJoinName));
               table.withRecordSecurityLock(lockClone);
            }
         }

         table.getField("activeStatusId").withFieldAdornment(new FieldAdornment(AdornmentType.CHIP)
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.ACTIVE, "play_circle_outline", AdornmentType.ChipValues.COLOR_SUCCESS))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.PAUSED, "pause_circle_outline", AdornmentType.ChipValues.COLOR_WARNING))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.DISABLED, "not_interested", AdornmentType.ChipValues.COLOR_ERROR)));

         // todo - if adding filters in future table.getField("queryFilterJson").withBehavior(new FilterJsonFieldDisplayValueFormatter());

         ///////////////////////////////////////////////////////////////////////////////////////////
         // to clear the webhookSubscriptionHelper class's memoizations post insert/update/delete //
         ///////////////////////////////////////////////////////////////////////////////////////////
         QCodeReference clearWebhookSubscriptionHerlpMemoizations = new QCodeReference(ClearWebhookSubscriptionHelperMemoizationsTableCustomizer.class);
         table.withCustomizer(TableCustomizers.POST_INSERT_RECORD, clearWebhookSubscriptionHerlpMemoizations);
         table.withCustomizer(TableCustomizers.POST_UPDATE_RECORD, clearWebhookSubscriptionHerlpMemoizations);
         table.withCustomizer(TableCustomizers.POST_DELETE_RECORD, clearWebhookSubscriptionHerlpMemoizations);

         return (table);
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class EventChildRecordWidgetMetaDataCustomizer implements MetaDataCustomizerInterface<QWidgetMetaData>
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public QWidgetMetaData customizeMetaData(QInstance qInstance, QWidgetMetaData widget) throws QException
      {
         ////////////////////////////
         // sort events by id desc //
         ////////////////////////////
         ArrayList<QFilterOrderBy> orderBys = new ArrayList<>(List.of(new QFilterOrderBy("id", false)));
         return widget.withDefaultValue("orderBy", orderBys);
      }
   }



   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, possibleValueSourceName = Webhook.TABLE_NAME)
   private Integer webhookId;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.ERROR, isRequired = true, possibleValueSourceName = WebhookEventTypePossibleValueSource.NAME, label = "Event Type")
   private String webhookEventTypeName;

   //////////////////////////////
   // text, so omit max length //
   //////////////////////////////
   // todo - if adding filters in future @QField(label = "Filter")
   // todo - if adding filters in future private String queryFilterJson;

   @QField(isRequired = true, possibleValueSourceName = WebhookActiveStatus.NAME, defaultValue = WebhookActiveStatus.DEFAULT_VALUE)
   private Integer activeStatusId;

   @QField(isRequired = true, maxLength = 50, valueTooLongBehavior = ValueTooLongBehavior.ERROR, label = "API Name", possibleValueSourceName = ApiInstanceMetaDataProvider.API_NAME_PVS_NAME)
   private String apiName;

   @QField(isRequired = true, maxLength = 50, valueTooLongBehavior = ValueTooLongBehavior.ERROR, label = "API Version", possibleValueSourceName = ApiInstanceMetaDataProvider.API_VERSION_PVS_NAME)
   private String apiVersion;

   @QField(isRequired = true, isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public WebhookSubscription()
   {
   }



   /*******************************************************************************
    ** Constructor that takes a QRecord
    *******************************************************************************/
   public WebhookSubscription(QRecord record)
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
   public WebhookSubscription withId(Integer id)
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
   public WebhookSubscription withWebhookId(Integer webhookId)
   {
      this.webhookId = webhookId;
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
   public WebhookSubscription withWebhookEventTypeName(String webhookEventTypeName)
   {
      this.webhookEventTypeName = webhookEventTypeName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiName
    *******************************************************************************/
   public String getApiName()
   {
      return (this.apiName);
   }



   /*******************************************************************************
    ** Setter for apiName
    *******************************************************************************/
   public void setApiName(String apiName)
   {
      this.apiName = apiName;
   }



   /*******************************************************************************
    ** Fluent setter for apiName
    *******************************************************************************/
   public WebhookSubscription withApiName(String apiName)
   {
      this.apiName = apiName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for apiVersion
    *******************************************************************************/
   public String getApiVersion()
   {
      return (this.apiVersion);
   }



   /*******************************************************************************
    ** Setter for apiVersion
    *******************************************************************************/
   public void setApiVersion(String apiVersion)
   {
      this.apiVersion = apiVersion;
   }



   /*******************************************************************************
    ** Fluent setter for apiVersion
    *******************************************************************************/
   public WebhookSubscription withApiVersion(String apiVersion)
   {
      this.apiVersion = apiVersion;
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
   public WebhookSubscription withCreateDate(Instant createDate)
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
   public WebhookSubscription withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for activeStatusId
    *******************************************************************************/
   public Integer getActiveStatusId()
   {
      return (this.activeStatusId);
   }



   /*******************************************************************************
    ** Setter for activeStatusId
    *******************************************************************************/
   public void setActiveStatusId(Integer activeStatusId)
   {
      this.activeStatusId = activeStatusId;
   }



   /*******************************************************************************
    ** Fluent setter for activeStatusId
    *******************************************************************************/
   public WebhookSubscription withActiveStatusId(Integer activeStatusId)
   {
      this.activeStatusId = activeStatusId;
      return (this);
   }

}
