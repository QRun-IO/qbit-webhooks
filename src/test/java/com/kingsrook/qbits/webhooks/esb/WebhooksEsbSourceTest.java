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

package com.kingsrook.qbits.webhooks.esb;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.events.WebhookEvent;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.esb.envelope.EsbEvent;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import com.kingsrook.qqq.esb.model.EsbProcessMetaData;
import com.kingsrook.qqq.esb.runtime.EsbTriggerHandler;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** ESB-sourced events: mapping CloudEvents, skipping our own, deduplicating
 ** redeliveries, and the trigger wiring.
 *******************************************************************************/
class WebhooksEsbSourceTest
{
   private QInstance instance;
   private Integer   endpointId;



   /*******************************************************************************
    ** Setup: our ESB instance is "test-app"; a remote table "orders" is an ESB
    ** table source.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      instance = WebhooksTestSupport.setUp(config -> config.withEsbDestinations(List.of("shop.tables")).withEsbTableEventSources(List.of("orders")));
      EsbInstanceMetaData.of(instance).withInstanceName("test-app");
      endpointId = WebhooksTestSupport.endpoint("crm", "http://127.0.0.1:9/hook");
   }



   /*******************************************************************************
    ** A table CloudEvent.
    *******************************************************************************/
   private static EsbEvent tableEvent(String id, String source, String table, String suffix, Map<String, Serializable> record)
   {
      Map<String, Serializable> data = new LinkedHashMap<>();
      data.put("deleted".equals(suffix) ? "oldRecord" : "record", new LinkedHashMap<>(record));
      return (new EsbEvent().withId(id).withSource(source).withType("qqq.table." + table + "." + suffix).withSubject(String.valueOf(record.get("id")))
         .withTime(WebhooksTestSupport.CLOCK.instant()).withData(data));
   }



   /*******************************************************************************
    ** Run the ingest step on some events.
    *******************************************************************************/
   private static void ingest(EsbEvent... events) throws Exception
   {
      RunBackendStepInput input = new RunBackendStepInput();
      input.addValue(EsbTriggerHandler.VALUE_ESB_MESSAGES, new ArrayList<>(List.of(events)));
      new WebhooksEsbSource.IngestStep().run(input, new RunBackendStepOutput());
   }



   /*******************************************************************************
    ** A remote table's event is queued; a redelivery queues nothing new.
    *******************************************************************************/
   @Test
   void remoteTableEventsAreQueuedOnce() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "orders.inserted", s -> { });
      EsbEvent event = tableEvent("ce-1", "qqq://shop/table/orders", "orders", "inserted", Map.of("id", 7, "total", "19.99"));
      ingest(event);
      ingest(event);

      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).hasSize(1);
      QRecord delivery = deliveries.get(0);
      assertThat(delivery.getValueString("source")).isEqualTo("ESB");
      assertThat(delivery.getValueString("sourceEventId")).isEqualTo("ce-1");
      assertThat(delivery.getValueString("sourceRecordId")).isEqualTo("7");
      JSONObject payload = new JSONObject(delivery.getValueString("payload"));
      assertThat(payload.getString("source")).isEqualTo("qqq://shop/table/orders");
      assertThat(payload.getJSONObject("data").getJSONObject("record").getString("total")).isEqualTo("19.99");
   }



   /*******************************************************************************
    ** Our own events (already queued in-process) are skipped; another
    ** service's events about the same local table are queued.
    *******************************************************************************/
   @Test
   void ownEventsAreSkippedSoNothingIsSentTwice() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(1);

      ingest(tableEvent("ce-own", "qqq://test-app/table/person", "person", "inserted", Map.of("id", 1, "firstName", "Ada")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(1);

      ingest(tableEvent("ce-worker", "qqq://worker/table/person", "person", "inserted", Map.of("id", 2, "firstName", "Grace")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(2);
   }



   /*******************************************************************************
    ** Delete, process and custom CloudEvents map; unknown types are ignored.
    *******************************************************************************/
   @Test
   void mapping() throws Exception
   {
      WebhookEvent deleted = WebhooksEsbSource.toWebhookEvent(instance, tableEvent("ce-2", "qqq://shop/table/orders", "orders", "deleted", Map.of("id", 9)));
      assertThat(deleted.getEventTypeName()).isEqualTo("orders.deleted");
      assertThat(deleted.getRecord()).isNull();
      assertThat(deleted.getOldRecord().getValue("id")).isEqualTo(9);

      WebhookEvent process = WebhooksEsbSource.toWebhookEvent(instance, new EsbEvent().withId("ce-3").withSource("qqq://worker/process/testProcess")
         .withType("qqq.process.testProcess.failed").withData(new LinkedHashMap<>(Map.of("error", "boom"))));
      assertThat(process.getEventTypeName()).isEqualTo("process.testProcess.failed");
      assertThat(process.getData()).containsEntry("error", "boom");

      WebhooksRegistry.of(QContext.getQInstance()).register(new WebhookEventType().withName("shop.order.shipped").withKind(WebhookEventKind.CUSTOM));
      WebhookEvent custom = WebhooksEsbSource.toWebhookEvent(instance, new EsbEvent().withId("ce-4").withSource("qqq://shop/x").withType("shop.order.shipped"));
      assertThat(custom.getEventTypeName()).isEqualTo("shop.order.shipped");

      assertThat(WebhooksEsbSource.toWebhookEvent(instance, new EsbEvent().withId("ce-5").withSource("qqq://shop/x").withType("qqq.table.unknown.inserted"))).isNull();
      assertThat(WebhooksEsbSource.toWebhookEvent(instance, new EsbEvent().withId("ce-6").withSource("qqq://shop/x").withType("something.else"))).isNull();
      assertThat(WebhooksEsbSource.toWebhookEvent(instance, new EsbEvent().withId("ce-7").withSource("qqq://shop/x").withType("qqq.table.orders.renamed"))).isNull();
   }



   /*******************************************************************************
    ** configure() adds the hidden ingest process with a trigger per topic.
    *******************************************************************************/
   @Test
   void configureAddsTriggerProcess()
   {
      WebhooksEsbSource.configure(instance);
      QProcessMetaData process = instance.getProcess(WebhooksEsbSource.PROCESS_NAME);
      assertThat(process).isNotNull();
      assertThat(process.getIsHidden()).isTrue();
      assertThat(EsbProcessMetaData.of(process).getTriggers()).hasSize(1);
      assertThat(EsbProcessMetaData.of(process).getTriggers().get(0).getDestinationName()).isEqualTo("shop.tables");
   }
}
