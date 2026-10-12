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

package com.kingsrook.qbits.webhooks.processes;


import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.webhooks.ProcessRunner;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.delivery.DeliveryEngine;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The table and record actions, through the standard review and result
 ** screens, plus the scheduled maintenance and retention steps.
 *******************************************************************************/
class WebhooksProcessesTest
{
   private Receiver receiver;
   private Integer  endpointId;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp(config -> config.withDeliveredRetentionDays(30).withAttemptRetentionDays(30));
      receiver = new Receiver();
      endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      receiver.close();
   }



   /*******************************************************************************
    ** Send Test Event: a signed test delivery, reported on the result screen.
    *******************************************************************************/
   @Test
   void sendTestEvent() throws Exception
   {
      ProcessRunner.Lines lines = ProcessRunner.run(WebhooksProcesses.SEND_TEST, endpointId);
      assertThat(lines.review()).anyMatch(l -> l.contains("will get a test event"));
      assertThat(lines.result()).anyMatch(l -> l.contains("crm: delivered (HTTP 200)"));
      assertThat(receiver.requests()).hasSize(1);
      assertThat(receiver.requests().get(0).text()).contains("\"type\":\"webhooks.test\"");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0).getValueString("source")).isEqualTo("TEST");

      receiver.respond(r -> Receiver.Response.of(404));
      lines = ProcessRunner.run(WebhooksProcesses.SEND_TEST, endpointId);
      assertThat(lines.result()).anyMatch(l -> l.contains("not delivered") && l.contains("HTTP 404"));
   }



   /*******************************************************************************
    ** Rotate: a new secret, and the value never on the result screen.
    *******************************************************************************/
   @Test
   void rotateSecret() throws Exception
   {
      String before = EndpointSecrets.signingKeys(endpointId).get(0).fingerprint();
      ProcessRunner.Lines lines = ProcessRunner.run(WebhooksProcesses.ROTATE_SECRET, endpointId);
      assertThat(lines.result()).anyMatch(l -> l.contains("new signing secret"));
      assertThat(String.join(" ", lines.result())).doesNotContain("whsec_");
      List<EndpointSecrets.SigningKey> keys = EndpointSecrets.signingKeys(endpointId);
      assertThat(keys).hasSize(2);
      assertThat(keys.get(0).fingerprint()).isNotEqualTo(before);
      List<QRecord> secrets = WebhooksTestSupport.all(WebhooksTables.SECRET);
      assertThat(secrets).extracting(s -> s.getValueString("status")).containsExactly("PREVIOUS", "ACTIVE");
      assertThat(secrets.get(1).getValueString("encryptedSecret")).startsWith("v1:").doesNotContain("whsec_");
   }



   /*******************************************************************************
    ** Rotation is refused without the encryption key.
    *******************************************************************************/
   @Test
   void rotateWithoutKeyIsSkipped() throws Exception
   {
      System.clearProperty(WebhooksTestSupport.KEY_PROPERTY);
      ProcessRunner.Lines lines = ProcessRunner.run(WebhooksProcesses.ROTATE_SECRET, endpointId);
      assertThat(lines.review()).anyMatch(l -> l.contains("encryption key is not set"));
   }



   /*******************************************************************************
    ** Cancel, then Delete (finished only).
    *******************************************************************************/
   @Test
   void cancelAndDelete() throws Exception
   {
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "A"));
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "B"));
      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);

      ProcessRunner.Lines deleteFirst = ProcessRunner.run(WebhooksProcesses.DELETE_DELIVERIES, deliveries.get(0).getValue("id"));
      assertThat(deleteFirst.review()).anyMatch(l -> l.contains("is not finished"));

      ProcessRunner.Lines cancel = ProcessRunner.run(WebhooksProcesses.CANCEL, deliveries.get(0).getValue("id"));
      assertThat(cancel.result()).anyMatch(l -> l.contains("1 delivery was cancelled"));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(1);

      ProcessRunner.run(WebhooksProcesses.DELETE_DELIVERIES, deliveries.get(0).getValue("id"), deliveries.get(1).getValue("id"));
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).isEmpty();
      assertThat(WebhooksTestSupport.all(WebhooksTables.ATTEMPT)).isEmpty();
   }



   /*******************************************************************************
    ** Send Now skips finished deliveries with a reason.
    *******************************************************************************/
   @Test
   void sendNowExplainsSkips() throws Exception
   {
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "A"));
      new DeliveryEngine().runOnce();
      QRecord delivered = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0);
      ProcessRunner.Lines lines = ProcessRunner.run(WebhooksProcesses.SEND_NOW, delivered.getValue("id"));
      assertThat(lines.review()).anyMatch(l -> l.contains("already delivered (use Replay"));
   }



   /*******************************************************************************
    ** Retention purges old finished deliveries and attempts; dead letters are
    ** kept (deadLetterRetentionDays = 0).
    *******************************************************************************/
   @Test
   void retention() throws Exception
   {
      receiver.respond(r -> Receiver.Response.of(200));
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "A"));
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "B"));
      new DeliveryEngine().runOnce();
      QRecord second = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(1);
      SystemWrites.updateOne(WebhooksTables.DELIVERY, new QRecord().withValue("id", second.getValue("id")).withValue("status", "DEAD_LETTER"));

      assertThat(WebhooksProcesses.Retention.purge(WebhooksRuntime.config())).isEqualTo(0);
      WebhooksTestSupport.CLOCK.set(java.time.Instant.now().plus(Duration.ofDays(31)));
      assertThat(WebhooksProcesses.Retention.purge(WebhooksRuntime.config())).isEqualTo(1);
      List<QRecord> left = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(left).hasSize(1);
      assertThat(left.get(0).getValueString("status")).isEqualTo("DEAD_LETTER");
   }



   /*******************************************************************************
    ** The scheduled steps run.
    *******************************************************************************/
   @Test
   void scheduledSteps() throws Exception
   {
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "A"));
      com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput output = new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput();
      new WebhooksProcesses.DeliverStep().run(new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput(), output);
      assertThat(output.getValues().get("delivered")).isEqualTo(1);
      new WebhooksProcesses.MaintainStep().run(new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput(), output);
      assertThat(output.getValues().get("probation")).isEqualTo(0);
      new WebhooksProcesses.RetentionStep().run(new com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput(), output);
      assertThat(output.getValues().get("deleted")).isEqualTo(0);
   }
}
