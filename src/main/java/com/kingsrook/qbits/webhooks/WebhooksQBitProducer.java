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

package com.kingsrook.qbits.webhooks;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.app.WebhooksApp;
import com.kingsrook.qbits.webhooks.events.WebhooksProcessListener;
import com.kingsrook.qbits.webhooks.events.WebhooksRecordChangeListener;
import com.kingsrook.qbits.webhooks.health.WebhooksHealthChecks;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTableCustomizers;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Entry point of the webhooks qbit.
 **
 ** <pre>
 ** new WebhooksQBitProducer()
 **    .withQBitConfig(new WebhooksQBitConfig()
 **       .withBackendName("rdbms")
 **       .withSchedulerName("scheduler")
 **       .withTableEventSources(List.of("cms_blog_post")))
 **    .produce(qInstance);
 ** </pre>
 **
 ** <p>Produces the five {@code webhooks_*} tables, their joins, widgets and
 ** possible values, the processes, the Webhooks app, the event types implied
 ** by the config, and the record-change and process listeners. Call
 ** {@code WebhooksEsbSource.configure(qInstance)} afterwards to consume ESB
 ** topics.</p>
 *******************************************************************************/
public class WebhooksQBitProducer implements QBitMetaDataProducer<WebhooksQBitConfig>
{
   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "qbit-webhooks";
   public static final String VERSION     = "1.0.0";

   private WebhooksQBitConfig qBitConfig;
   private String             namespace;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public WebhooksQBitProducer withQBitConfig(WebhooksQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the config.
    *******************************************************************************/
   @Override
   public WebhooksQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the namespace.
    *******************************************************************************/
   public WebhooksQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the namespace.
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (namespace);
   }



   /*******************************************************************************
    ** Identity.
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData().withGroupId(GROUP_ID).withArtifactId(ARTIFACT_ID).withVersion(VERSION).withNamespace(namespace).withConfig(qBitConfig));
   }



   /*******************************************************************************
    ** Package-scanned producers have no config: skip them.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Produce everything into the instance.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw (new QException("Webhooks qbit configuration is invalid: " + String.join("; ", errors)));
      }
      QBitMetaData qBit = getQBitMetaData();
      qInstance.addQBit(qBit);

      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      for(QPossibleValueSource possibleValueSource : WebhooksTables.possibleValueSources())
      {
         output.add(possibleValueSource);
      }

      Map<String, QTableMetaData> tables = new LinkedHashMap<>();
      for(QTableMetaData table : WebhooksTables.tables(qBitConfig))
      {
         addCustomizers(table);
         if(Boolean.TRUE.equals(qBitConfig.getSnakeCaseColumnNames()))
         {
            snakeCaseColumns(table);
         }
         QTableMetaData customized = qBitConfig.getTableMetaDataCustomizer() == null ? table : qBitConfig.getTableMetaDataCustomizer().customizeMetaData(qInstance, table);
         customized.setSourceQBitName(qBit.getName());
         tables.put(customized.getName(), customized);
         output.add(customized);
      }
      for(QJoinMetaData join : WebhooksTables.joins())
      {
         output.add(join);
      }
      for(QWidgetMetaData widget : WebhooksTables.childWidgets())
      {
         output.add(widget);
      }
      for(QWidgetMetaData widget : WebhooksApp.widgets())
      {
         output.add(widget);
      }
      for(QProcessMetaData process : WebhooksProcesses.processes(qBitConfig))
      {
         process.setSourceQBitName(qBit.getName());
         output.add(process);
      }
      output.add(WebhooksApp.app(qBitConfig, tables));
      output.addSelfToInstance(qInstance);

      WebhooksHealthChecks.register(qInstance);

      WebhooksRegistry registry = WebhooksRegistry.of(qInstance);
      registry.registerFromConfig(qInstance, qBitConfig);
      registry.register(new WebhookEventType().withName(WebhooksProcesses.TEST_EVENT_TYPE).withKind(WebhookEventKind.CUSTOM)
         .withLabel("Test event").withDescription("Sent by the Send Test Event action."));

      qInstance.withRecordChangeListener(new QCodeReference(WebhooksRecordChangeListener.class));
      if(!qBitConfig.getProcessEventSources().isEmpty())
      {
         qInstance.withProcessLifecycleListener(new QCodeReference(WebhooksProcessListener.class));
      }
      return (new MetaDataProducerMultiOutput());
   }



   /*******************************************************************************
    ** The validating customizers.
    *******************************************************************************/
   private static void addCustomizers(QTableMetaData table)
   {
      Class<?> customizer = switch(table.getName())
      {
         case WebhooksTables.ENDPOINT -> WebhooksTableCustomizers.EndpointCustomizer.class;
         case WebhooksTables.SUBSCRIPTION -> WebhooksTableCustomizers.SubscriptionCustomizer.class;
         case WebhooksTables.SECRET -> WebhooksTableCustomizers.SecretCustomizer.class;
         default -> null;
      };
      if(customizer != null)
      {
         table.withCustomizer(TableCustomizers.PRE_INSERT_RECORD, new QCodeReference(customizer));
         table.withCustomizer(TableCustomizers.PRE_UPDATE_RECORD, new QCodeReference(customizer));
      }
   }



   /*******************************************************************************
    ** Column names in snake_case (matching docs/ddl).
    *******************************************************************************/
   private static void snakeCaseColumns(QTableMetaData table)
   {
      for(QFieldMetaData field : table.getFields().values())
      {
         if(field.getBackendName() == null)
         {
            field.setBackendName(QInstanceEnricher.inferBackendName(field.getName()));
         }
      }
   }
}
