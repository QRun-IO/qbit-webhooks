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

package com.kingsrook.qbits.webhooks.events;


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.Webhooks;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Where events come from, and which subscriptions get them, with what payload.
 *******************************************************************************/
class EventSourcesTest
{
   private Integer endpointId;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp();
      endpointId = WebhooksTestSupport.endpoint("crm", "http://127.0.0.1:9/hook");
   }



   /*******************************************************************************
    ** Payload data of delivery n.
    *******************************************************************************/
   private static JSONObject data(int n) throws QException
   {
      return (new JSONObject(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(n).getValueString("payload")).getJSONObject("data"));
   }



   /*******************************************************************************
    ** A person.
    *******************************************************************************/
   private static Integer person(String first, String status) throws QException
   {
      return (WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", first).withValue("lastName", "L")
         .withValue("email", first.toLowerCase() + "@example.com").withValue("status", status)).getValueInteger("id"));
   }



   /*******************************************************************************
    ** Update and delete events carry the old record and changed fields.
    *******************************************************************************/
   @Test
   void updateAndDeleteEvents() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "person.updated", s -> { });
      WebhooksTestSupport.subscribe(endpointId, "person.deleted", s -> { });
      Integer id = person("Ada", "new");

      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("status", "active")));
      JSONObject updated = data(0);
      assertThat(updated.getJSONArray("changedFields").toList()).containsExactly("status");
      assertThat(updated.getJSONObject("record").getString("status")).isEqualTo("active");
      assertThat(updated.getJSONObject("record").getString("firstName")).isEqualTo("Ada");
      assertThat(updated.getJSONObject("oldRecord").getString("status")).isEqualTo("new");

      new DeleteAction().execute(new DeleteInput(WebhooksTestSupport.PERSON).withPrimaryKeys(List.of(id)));
      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).hasSize(2);
      assertThat(deliveries.get(1).getValueString("eventTypeName")).isEqualTo("person.deleted");
      assertThat(deliveries.get(1).getValueString("sourceRecordId")).isEqualTo(String.valueOf(id));
      JSONObject deleted = data(1);
      assertThat(deleted.has("record")).isFalse();
      assertThat(deleted.getJSONObject("oldRecord").getString("firstName")).isEqualTo("Ada");
   }



   /*******************************************************************************
    ** A field type fires only when that field changes (to a value).
    *******************************************************************************/
   @Test
   void fieldConditionTypes() throws Exception
   {
      WebhooksRegistry.of(QContext.getQInstance()).register(new WebhookEventType().withName("person.activated").withKind(WebhookEventKind.TABLE_UPDATE)
         .withTableName(WebhooksTestSupport.PERSON).withFieldName("status").withValue("active"));
      WebhooksTestSupport.subscribe(endpointId, "person.activated", s -> { });
      Integer id = person("Ada", "new");

      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("lastName", "Byron")));
      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("status", "paused")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).isEmpty();
      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("status", "active")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(1);
      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("status", "active")));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(1);
   }



   /*******************************************************************************
    ** A record filter on a subscription.
    *******************************************************************************/
   @Test
   void subscriptionFilter() throws Exception
   {
      String filter = JsonUtils.toJson(new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.EQUALS, "vip")));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> s.setValue("filterJson", filter));
      person("Ada", "regular");
      person("Grace", "vip");
      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).hasSize(1);
      assertThat(data(0).getJSONObject("record").getString("firstName")).isEqualTo("Grace");
   }



   /*******************************************************************************
    ** Detail level, include and exclude lists.
    *******************************************************************************/
   @Test
   void payloadShaping() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> s.withValue("excludeFields", "email"));
      Integer second = WebhooksTestSupport.endpoint("keys", "http://127.0.0.1:9/keys");
      WebhooksTestSupport.subscribe(second, "person.inserted", s -> s.withValue("detailLevel", "KEYS_ONLY"));
      Integer third = WebhooksTestSupport.endpoint("only", "http://127.0.0.1:9/only");
      WebhooksTestSupport.subscribe(third, "person.updated", s -> s.withValue("detailLevel", "CHANGED_FIELDS").withValue("includeFields", "status,lastName"));

      Integer id = person("Ada", "new");
      JSONObject excluded = data(0);
      assertThat(excluded.getJSONObject("record").has("email")).isFalse();
      assertThat(excluded.getJSONObject("record").getString("firstName")).isEqualTo("Ada");
      JSONObject keys = data(1);
      assertThat(keys.getJSONObject("record").keySet()).containsExactly("id");

      new UpdateAction().execute(new UpdateInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("id", id).withValue("status", "active").withValue("firstName", "Augusta")));
      JSONObject changed = data(2);
      assertThat(changed.getJSONObject("record").keySet()).containsExactlyInAnyOrder("id", "status");
      assertThat(changed.has("oldRecord")).isFalse();
   }



   /*******************************************************************************
    ** Process completed and failed events.
    *******************************************************************************/
   @Test
   void processEvents() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "process.testProcess.completed", s -> { });
      WebhooksTestSupport.subscribe(endpointId, "process.testProcess.failed", s -> { });

      RunProcessInput ok = new RunProcessInput();
      ok.setProcessName(WebhooksTestSupport.TEST_PROCESS);
      ok.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      new RunProcessAction().execute(ok);

      RunProcessInput bad = new RunProcessInput();
      bad.setProcessName(WebhooksTestSupport.TEST_PROCESS);
      bad.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      bad.addValue("fail", "yes");
      try
      {
         new RunProcessAction().execute(bad);
      }
      catch(QException e)
      {
         // expected: the process throws
      }

      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).extracting(d -> d.getValueString("eventTypeName")).containsExactly("process.testProcess.completed", "process.testProcess.failed");
      assertThat(data(0).getString("processName")).isEqualTo(WebhooksTestSupport.TEST_PROCESS);
      assertThat(data(1).getString("error")).contains("failed on purpose");
      assertThat(deliveries.get(0).getValueString("source")).isEqualTo("PROCESS");
   }



   /*******************************************************************************
    ** Custom events from code, and the unknown-type guard.
    *******************************************************************************/
   @Test
   void customEvents() throws Exception
   {
      Webhooks.registerEventType(new WebhookEventType().withName("post.published").withKind(WebhookEventKind.CUSTOM));
      WebhooksTestSupport.subscribe(endpointId, "post.published", s -> { });
      assertThat(Webhooks.fire("post.published", Map.of("slug", "hello-world"))).isEqualTo(1);
      assertThat(data(0).getString("slug")).isEqualTo("hello-world");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0).getValueString("source")).isEqualTo("CUSTOM");

      Integer personId = person("Ada", "vip");
      QRecord record = new QRecord().withValue("id", personId).withValue("firstName", "Ada");
      assertThat(Webhooks.fireForRecord("post.published", WebhooksTestSupport.PERSON, record, null)).isEqualTo(1);
      assertThat(data(1).getJSONObject("record").getString("firstName")).isEqualTo("Ada");

      assertThatThrownBy(() -> Webhooks.fire("no.such.type", Map.of())).hasMessageContaining("Unknown webhook event type");
   }



   /*******************************************************************************
    ** The channel API (for the Alerts qbit): one endpoint, idempotent.
    *******************************************************************************/
   @Test
   void channelDeliveryIsIdempotent() throws Exception
   {
      Integer first  = Webhooks.deliver("crm", "alerts.alert.raised", Map.of("title", "Disk full"), "alert-1-raised");
      Integer second = Webhooks.deliver("crm", "alerts.alert.raised", Map.of("title", "Disk full"), "alert-1-raised");
      assertThat(second).isEqualTo(first);
      QRecord delivery = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0);
      assertThat(delivery.getValueString("source")).isEqualTo("CHANNEL");
      assertThat(delivery.getValue("subscriptionId")).isNull();
      assertThatThrownBy(() -> Webhooks.deliver("nobody", "x", Map.of(), null)).hasMessageContaining("No webhook endpoint");
   }



   /*******************************************************************************
    ** Paused and disabled subscriptions get nothing.
    *******************************************************************************/
   @Test
   void inactiveSubscriptionsGetNothing() throws Exception
   {
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> s.withValue("activeStatus", "PAUSED"));
      person("Ada", "x");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).isEmpty();
   }



   /*******************************************************************************
    ** Writes to the qbit's own tables never produce events.
    *******************************************************************************/
   @Test
   void ownTablesAreIgnored()
   {
      assertThat(new WebhooksRecordChangeListener().appliesTo(WebhooksTables.DELIVERY, com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType.INSERT)).isFalse();
      assertThat(new WebhooksRecordChangeListener().appliesTo(WebhooksTestSupport.PERSON, com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType.DELETE)).isTrue();
      assertThat(new WebhooksRecordChangeListener().appliesTo("unknown", com.kingsrook.qqq.backend.core.actions.tables.listeners.RecordChangeType.DELETE)).isFalse();
   }
}
