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
import com.kingsrook.qbits.webhooks.processes.SyncWebhookScheduledJobProcess;
import com.kingsrook.qqq.backend.core.actions.customizers.MultiCustomizer;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.data.QAssociation;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
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
import com.kingsrook.qqq.backend.core.scheduler.processes.BaseSyncToScheduledJobTableCustomizer;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.collections.MutableList;


/*******************************************************************************
 ** QRecord Entity for Webhook table
 *******************************************************************************/
@QMetaDataProducingEntity(
   producePossibleValueSource = true,
   produceTableMetaData = true,
   tableMetaDataCustomizer = Webhook.TableMetaDataCustomizer.class,
   childTables = {
      @ChildTable(
         childTableEntityClass = WebhookSubscription.class,
         joinFieldName = "webhookId",
         childJoin = @ChildJoin(enabled = true),
         childRecordListWidget = @ChildRecordListWidget(label = "Subscriptions", enabled = true, maxRows = 250, canAddChildRecords = true, manageAssociationName = Webhook.SUBSCRIPTIONS_ASSOCIATION_NAME)),
      @ChildTable(
         childTableEntityClass = WebhookEvent.class,
         joinFieldName = "webhookId",
         childJoin = @ChildJoin(enabled = true),
         childRecordListWidget = @ChildRecordListWidget(label = "Events", enabled = true, maxRows = 25, widgetMetaDataCustomizer = Webhook.EventAndEventSendLogChildRecordWidgetMetaDataCustomizer.class)),
      @ChildTable(
         childTableEntityClass = WebhookEventSendLog.class,
         joinFieldName = "webhookId",
         childJoin = @ChildJoin(enabled = true),
         childRecordListWidget = @ChildRecordListWidget(label = "Event Send Log", enabled = true, maxRows = 25, widgetMetaDataCustomizer = Webhook.EventAndEventSendLogChildRecordWidgetMetaDataCustomizer.class))
   }
)
public class Webhook extends QRecordEntity
{
   public static final String TABLE_NAME                     = "webhook";
   public static final String SUBSCRIPTIONS_ASSOCIATION_NAME = "subscriptions";



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
         String subscriptionChildJoinName = QJoinMetaData.makeInferredJoinName(Webhook.TABLE_NAME, WebhookSubscription.TABLE_NAME);
         String eventChildJoinName        = QJoinMetaData.makeInferredJoinName(Webhook.TABLE_NAME, WebhookEvent.TABLE_NAME);
         String eventSendLogChildJoinName = QJoinMetaData.makeInferredJoinName(Webhook.TABLE_NAME, WebhookEventSendLog.TABLE_NAME);

         QFieldSection t1section = SectionFactory.defaultT1("id", "name", "description");

         table
            .withIcon(new QIcon().withName("webhook"))
            .withRecordLabelFormat("%s")
            .withRecordLabelFields("name")
            .withSection(t1section)
            .withSection(SectionFactory.defaultT2("url", "activeStatusId", "healthStatusId"))
            .withSection(SectionFactory.customT2("subscriptions", new QIcon("subscriptions")).withWidgetName(subscriptionChildJoinName))
            .withSection(SectionFactory.customT2("events", new QIcon("notifications")).withWidgetName(eventChildJoinName))
            .withSection(SectionFactory.customT2("sendLog", new QIcon("receipt_long")).withWidgetName(eventSendLogChildJoinName))
            .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
            .withExposedJoin(new ExposedJoin().withLabel("Subscriptions").withJoinPath(List.of(subscriptionChildJoinName)).withJoinTable(WebhookSubscription.TABLE_NAME))
            .withAssociation(new Association().withName(SUBSCRIPTIONS_ASSOCIATION_NAME).withAssociatedTableName(WebhookSubscription.TABLE_NAME).withJoinName(subscriptionChildJoinName));

         addActiveStatusChipAdornment(table.getField("activeStatusId"));
         addHealthStatusChipAdornment(table.getField("healthStatusId"));

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

         ////////////////////////////////////////////////////////////////////////
         // we need 2 customizers to run post insert, update, and delete:      //
         // first, to clear the webhookSubscriptionHelper class's memoizations //
         // second, to sync scheduled jobs for this table                      //
         ////////////////////////////////////////////////////////////////////////
         QCodeReference syncScheduledJobCustomizer    = BaseSyncToScheduledJobTableCustomizer.makeCodeReference(table, new SyncWebhookScheduledJobProcess());
         QCodeReference postEverythingMultiCustomizer = MultiCustomizer.of(new QCodeReference(ClearWebhookSubscriptionHelperMemoizationsTableCustomizer.class), syncScheduledJobCustomizer);
         table.withCustomizer(TableCustomizers.POST_INSERT_RECORD, postEverythingMultiCustomizer);
         table.withCustomizer(TableCustomizers.POST_UPDATE_RECORD, postEverythingMultiCustomizer);
         table.withCustomizer(TableCustomizers.POST_DELETE_RECORD, postEverythingMultiCustomizer);

         return (table);
      }



      /***************************************************************************
       *
       ***************************************************************************/
      public static QFieldMetaData addActiveStatusChipAdornment(QFieldMetaData field)
      {
         return field.withFieldAdornment(new FieldAdornment(AdornmentType.CHIP)
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.ACTIVE, "play_circle_outline", AdornmentType.ChipValues.COLOR_SUCCESS))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.PAUSED, "pause_circle_outline", AdornmentType.ChipValues.COLOR_WARNING))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookActiveStatus.DISABLED, "not_interested", AdornmentType.ChipValues.COLOR_ERROR)));

      }



      /***************************************************************************
       **
       ***************************************************************************/
      public static QFieldMetaData addHealthStatusChipAdornment(QFieldMetaData field)
      {
         return field.withFieldAdornment(new FieldAdornment(AdornmentType.CHIP)
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookHealthStatus.HEALTHY, "favorite_border", AdornmentType.ChipValues.COLOR_SUCCESS))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookHealthStatus.UNHEALTHY, "heart_broken", AdornmentType.ChipValues.COLOR_ERROR))
            .withValues(AdornmentType.ChipValues.iconAndColorValues(WebhookHealthStatus.PROBATION, "healing", AdornmentType.ChipValues.COLOR_WARNING)));
      }

   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class EventAndEventSendLogChildRecordWidgetMetaDataCustomizer implements MetaDataCustomizerInterface<QWidgetMetaData>
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

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.ERROR, isRequired = true)
   private String name;

   @QField(maxLength = 250, valueTooLongBehavior = ValueTooLongBehavior.ERROR)
   private String description;

   @QField(maxLength = 250, valueTooLongBehavior = ValueTooLongBehavior.ERROR, isRequired = true, label = "URL")
   private String url;

   @QField(isRequired = true, possibleValueSourceName = WebhookActiveStatus.NAME, defaultValue = WebhookActiveStatus.DEFAULT_VALUE)
   private Integer activeStatusId;

   @QField(isRequired = true, possibleValueSourceName = WebhookHealthStatus.NAME, defaultValue = WebhookHealthStatus.DEFAULT_VALUE, isEditable = false)
   private Integer healthStatusId;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;

   @QAssociation(name = SUBSCRIPTIONS_ASSOCIATION_NAME)
   private List<WebhookSubscription> subscriptions;



   /*******************************************************************************
    ** Default constructor
    *******************************************************************************/
   public Webhook()
   {
   }



   /*******************************************************************************
    ** Constructor that takes a QRecord
    *******************************************************************************/
   public Webhook(QRecord record)
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
   public Webhook withId(Integer id)
   {
      this.id = id;
      return (this);
   }



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
   public Webhook withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for description
    *******************************************************************************/
   public String getDescription()
   {
      return (this.description);
   }



   /*******************************************************************************
    ** Setter for description
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }



   /*******************************************************************************
    ** Fluent setter for description
    *******************************************************************************/
   public Webhook withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for url
    *******************************************************************************/
   public String getUrl()
   {
      return (this.url);
   }



   /*******************************************************************************
    ** Setter for url
    *******************************************************************************/
   public void setUrl(String url)
   {
      this.url = url;
   }



   /*******************************************************************************
    ** Fluent setter for url
    *******************************************************************************/
   public Webhook withUrl(String url)
   {
      this.url = url;
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
   public Webhook withCreateDate(Instant createDate)
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
   public Webhook withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for subscriptions
    *******************************************************************************/
   public List<WebhookSubscription> getSubscriptions()
   {
      return (this.subscriptions);
   }



   /*******************************************************************************
    ** Setter for subscriptions
    *******************************************************************************/
   public void setSubscriptions(List<WebhookSubscription> subscriptions)
   {
      this.subscriptions = subscriptions;
   }



   /*******************************************************************************
    ** Fluent setter for subscriptions
    *******************************************************************************/
   public Webhook withSubscriptions(List<WebhookSubscription> subscriptions)
   {
      this.subscriptions = subscriptions;
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
   public Webhook withActiveStatusId(Integer activeStatusId)
   {
      this.activeStatusId = activeStatusId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for healthStatusId
    *******************************************************************************/
   public Integer getHealthStatusId()
   {
      return (this.healthStatusId);
   }



   /*******************************************************************************
    ** Setter for healthStatusId
    *******************************************************************************/
   public void setHealthStatusId(Integer healthStatusId)
   {
      this.healthStatusId = healthStatusId;
   }



   /*******************************************************************************
    ** Fluent setter for healthStatusId
    *******************************************************************************/
   public Webhook withHealthStatusId(Integer healthStatusId)
   {
      this.healthStatusId = healthStatusId;
      return (this);
   }

}
