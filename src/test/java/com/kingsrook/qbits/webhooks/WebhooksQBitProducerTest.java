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


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppChildMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The owner's standards, checked on the produced metadata: names, nav,
 ** processes as actions, permissions, integrations.
 *******************************************************************************/
class WebhooksQBitProducerTest
{
   /*******************************************************************************
    ** Table names: lowercase snake_case with the webhooks_ prefix; backend
    ** column names snake_case; a valid instance.
    *******************************************************************************/
   @Test
   void tableNamingAndColumns() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp();
      for(String name : WebhooksTables.ALL)
      {
         assertThat(name).matches("webhooks_[a-z_]+");
         QTableMetaData table = instance.getTable(name);
         assertThat(table).as(name).isNotNull();
         for(QFieldMetaData field : table.getFields().values())
         {
            assertThat(field.getBackendName()).as(name + "." + field.getName()).matches("[a-z0-9_]+");
         }
         assertThat(table.getPermissionRules().getPermissionBaseName()).isEqualTo("webhooks");
      }
      assertThat(instance.getTable(WebhooksTables.DELIVERY).getField("nextAttemptDate").getBackendName()).isEqualTo("next_attempt_date");
   }



   /*******************************************************************************
    ** The nav: the main tables only, no processes; sub-tables are relations.
    *******************************************************************************/
   @Test
   void navHasMainTablesOnly() throws Exception
   {
      QInstance    instance = WebhooksTestSupport.setUp();
      QAppMetaData app      = instance.getApp("webhooks");
      List<String> children = app.getChildren().stream().map(QAppChildMetaData::getName).toList();
      assertThat(children).containsExactly(WebhooksTables.ENDPOINT, WebhooksTables.SUBSCRIPTION, WebhooksTables.DELIVERY);
      for(QAppChildMetaData child : app.getChildren())
      {
         assertThat(child).isNotInstanceOf(QProcessMetaData.class);
      }
      assertThat(app.getWidgets()).containsExactly("webhooksHome");
      Set<String> endpointWidgets = instance.getTable(WebhooksTables.ENDPOINT).getSections().stream().map(s -> s.getWidgetName()).filter(w -> w != null).collect(Collectors.toSet());
      assertThat(endpointWidgets).contains(WebhooksTables.WIDGET_ENDPOINT_SUBSCRIPTIONS, WebhooksTables.WIDGET_ENDPOINT_SECRETS, WebhooksTables.WIDGET_ENDPOINT_DELIVERIES);
   }



   /*******************************************************************************
    ** Processes: people's actions are bound to a table with plain labels;
    ** scheduled ones are hidden.
    *******************************************************************************/
   @Test
   void processesAreTableOrRecordActions() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp(config -> config.withSchedulerName(null));
      for(String name : List.of(WebhooksProcesses.SEND_NOW, WebhooksProcesses.REPLAY, WebhooksProcesses.CANCEL, WebhooksProcesses.DELETE_DELIVERIES,
         WebhooksProcesses.SEND_TEST, WebhooksProcesses.ROTATE_SECRET))
      {
         QProcessMetaData process = instance.getProcess(name);
         assertThat(process.getTableName()).as(name).isNotNull();
         assertThat(process.getLabel()).doesNotContainIgnoringCase("etl").doesNotStartWith("Process");
      }
      for(String name : List.of(WebhooksProcesses.DELIVER, WebhooksProcesses.MAINTAIN, WebhooksProcesses.RETENTION))
      {
         assertThat(instance.getProcess(name).getIsHidden()).isTrue();
      }
   }



   /*******************************************************************************
    ** Event types from the config, and the integrations hooks.
    *******************************************************************************/
   @Test
   void registryAndIntegrations() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp();
      WebhooksRegistry registry = WebhooksRegistry.of(instance);
      assertThat(registry.get("person.inserted")).isNotNull();
      assertThat(registry.get("person.updated")).isNotNull();
      assertThat(registry.get("person.deleted")).isNotNull();
      assertThat(registry.get("process.testProcess.completed")).isNotNull();
      assertThat(registry.get("webhooks.test")).isNotNull();

      assertThat(WebhooksIntegrations.esbTableNames()).containsExactlyElementsOf(WebhooksTables.MAIN);
      assertThat(WebhooksIntegrations.apiTableNames()).containsExactlyElementsOf(WebhooksTables.MAIN);
      assertThat(WebhooksIntegrations.quickSearchTables()).extracting(WebhooksIntegrations.SearchableTable::tableName).containsExactlyElementsOf(WebhooksTables.MAIN);
      for(WebhooksIntegrations.SearchableTable searchable : WebhooksIntegrations.quickSearchTables())
      {
         for(String field : searchable.fieldWeights().keySet())
         {
            assertThat(instance.getTable(searchable.tableName()).getFields()).containsKey(field);
         }
      }
      assertThat(WebhooksRuntime.config(instance).getAppPath()).isEqualTo("/webhooks");
   }



   /*******************************************************************************
    ** Bad configs are refused.
    *******************************************************************************/
   @Test
   void invalidConfig()
   {
      assertThatThrownBy(() -> WebhooksTestSupport.setUp(config -> config.withBackendName("nope"))).isInstanceOf(QException.class).hasMessageContaining("backendName");
      assertThatThrownBy(() -> WebhooksTestSupport.setUp(config -> config.withTableEventSources(List.of("ghost")))).hasMessageContaining("ghost");
      assertThatThrownBy(() -> WebhooksTestSupport.setUp(config -> config.withLeaseSeconds(1))).hasMessageContaining("leaseSeconds");
      assertThatThrownBy(() -> WebhooksTestSupport.setUp(config -> config.withSchedulerName("nope"))).hasMessageContaining("schedulerName");
   }



   /*******************************************************************************
    ** Without the qbit, the runtime says so.
    *******************************************************************************/
   @Test
   void notInstalled()
   {
      assertThat(WebhooksRuntime.config(new QInstance())).isNull();
      assertThat(new WebhooksQBitProducer().isEnabled()).isFalse();
   }
}
