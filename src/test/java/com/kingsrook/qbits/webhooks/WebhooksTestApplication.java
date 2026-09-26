/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.webhooks;


import java.util.List;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.api.model.APIVersion;
import com.kingsrook.qqq.api.model.metadata.ApiInstanceMetaData;
import com.kingsrook.qqq.api.model.metadata.ApiInstanceMetaDataContainer;
import com.kingsrook.qqq.api.model.metadata.ApiInstanceMetaDataProvider;
import com.kingsrook.qqq.api.model.metadata.tables.ApiTableMetaData;
import com.kingsrook.qqq.api.model.metadata.tables.ApiTableMetaDataContainer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.tables.QQQTablesMetaDataProvider;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;


/*******************************************************************************
 **
 *******************************************************************************/
public class WebhooksTestApplication extends AbstractQQQApplication
{
   public static final String MEMORY_BACKEND_NAME = "memory";
   public static final String TABLE_NAME_PERSON   = "person";
   public static final String TABLE_NAME_ORDER    = "order";

   public static final String API_NAME = "test-api";
   public static final String API_PATH = "/test-api/";
   public static final String API_V1   = "v1";

   public static final String PERSON_INSERTED_EVENT_TYPE_NAME = "person.inserted";
   public static final String PERSON_STORED_EVENT_TYPE_NAME   = "person.stored";

   public static final String STORE_ID_ALL_ACCESS_KEY = "storeIdAllAccess";
   public static final String STORE_ID_KEY            = "storeId";



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance qInstance = new QInstance();

      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withType(QAuthenticationType.FULLY_ANONYMOUS));

      qInstance.addBackend(new QBackendMetaData()
         .withName(MEMORY_BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      //////////////////////////////
      // add tables table and PVS //
      //////////////////////////////
      new QQQTablesMetaDataProvider().defineAll(qInstance, MEMORY_BACKEND_NAME, MEMORY_BACKEND_NAME, null);

      //////////////////////
      // produce our qbit //
      //////////////////////
      WebhooksQBitProducer producer = new WebhooksQBitProducer()
         .withQBitConfig(new WebhooksQBitConfig()
            .withSecurityFields(List.of(new QFieldMetaData("storeId", QFieldType.INTEGER)))
            .withRecordSecurityLocks(List.of(new RecordSecurityLock().withFieldName("storeId").withSecurityKeyType(STORE_ID_KEY)))
            .withDefaultBackendNameForTables(MEMORY_BACKEND_NAME));

      MetaDataProducerMultiOutput allQBitMetaData = producer.produce(qInstance);
      allQBitMetaData.addSelfToInstance(qInstance);

      //////////////////////////////
      // add a webhook event type //
      //////////////////////////////
      WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
         .withName(PERSON_INSERTED_EVENT_TYPE_NAME)
         .withLabel("Person Inserted")
         .withCategory(WebhookEventCategory.INSERT)
         .withTableName(TABLE_NAME_PERSON));

      WebhooksRegistry.ofOrWithNew(qInstance).registerWebhookEventType(new WebhookEventType()
         .withName(PERSON_STORED_EVENT_TYPE_NAME)
         .withLabel("Person Stored")
         .withCategory(WebhookEventCategory.STORE)
         .withTableName(TABLE_NAME_PERSON));

      /////////////
      // add api //
      /////////////
      defineApiMetaData(qInstance);
      ApiInstanceMetaDataProvider.definePossibleValueSourcesForApiNameAndVersion(qInstance);

      ////////////////////////////////////////
      // produce some test tables           //
      // note - nice to be a qbit this too! //
      ////////////////////////////////////////
      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME_PERSON)
         .withBackendName(MEMORY_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("firstName", QFieldType.STRING).withIsEditable(true))
         .withField(new QFieldMetaData("lastName", QFieldType.STRING).withIsEditable(true))
         .withSupplementalMetaData(new ApiTableMetaDataContainer().withApiTableMetaData(API_NAME, new ApiTableMetaData()
            .withInitialVersion(API_V1)))
      );

      qInstance.addTable(new QTableMetaData()
         .withName(TABLE_NAME_ORDER)
         .withBackendName(MEMORY_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("orderNo", QFieldType.STRING).withIsEditable(true))
         .withField(new QFieldMetaData("storeId", QFieldType.INTEGER).withIsEditable(true))
         .withRecordSecurityLock(new RecordSecurityLock().withFieldName("storeId").withSecurityKeyType(STORE_ID_KEY))
         .withSupplementalMetaData(new ApiTableMetaDataContainer().withApiTableMetaData(API_NAME, new ApiTableMetaData()
            .withInitialVersion(API_V1)))
      );

      qInstance.addSecurityKeyType(new QSecurityKeyType().withName(STORE_ID_KEY).withAllAccessKeyName(STORE_ID_ALL_ACCESS_KEY));

      ///////////////////////////////////////////
      // turn off audits (why on by default??) //
      ///////////////////////////////////////////
      qInstance.getTables().values().forEach(t -> t.setAuditRules(new QAuditRules().withAuditLevel(AuditLevel.NONE)));

      /////////////////
      // create apps //
      /////////////////
      qInstance.addApp(new QAppMetaData()
         .withName("webhooks")
         .withIcon(new QIcon("webhooks"))
         .withSection(WebhooksQBitProducer.produceAppSection()));

      qInstance.addApp(new QAppMetaData()
         .withName("testData")
         .withIcon(new QIcon("dataset"))
         .withSection(new QAppSection()
            .withName("people")
            .withTables(List.of(TABLE_NAME_PERSON))));

      return qInstance;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   private void defineApiMetaData(QInstance qInstance)
   {
      qInstance.withSupplementalMetaData(new ApiInstanceMetaDataContainer()
         .withApiInstanceMetaData(new ApiInstanceMetaData()
            .withName(API_NAME)
            .withPath(API_PATH)
            .withLabel("Test API")
            .withDescription("QQQ Test API")
            .withContactEmail("contact@kingsrook.com")
            .withCurrentVersion(new APIVersion(API_V1))
            .withSupportedVersions(List.of(new APIVersion(API_V1)))
         ));
   }

}
