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

package com.kingsrook.qbits.webhooks.delivery;


import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qbits.webhooks.shared.signing.WebhookSecret;
import com.kingsrook.qbits.webhooks.shared.signing.WebhookVerificationException;
import com.kingsrook.qbits.webhooks.shared.signing.WebhookVerifier;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** End to end: a record change queues a delivery, the engine signs and sends
 ** it, the receiver verifies it, and rotation keeps old and new secrets valid
 ** during the overlap.
 *******************************************************************************/
class SignedDeliveryTest
{
   private Receiver receiver;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp();
      receiver = new Receiver();
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
    ** Insert, deliver, verify the signature and the envelope.
    *******************************************************************************/
   @Test
   void insertIsDeliveredSignedAndVerifiable() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada").withValue("lastName", "Lovelace"));

      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).hasSize(1);
      assertThat(deliveries.get(0).getValueString("status")).isEqualTo("PENDING");

      DeliveryEngine.RunSummary summary = new DeliveryEngine().runOnce();
      assertThat(summary.getDelivered()).isEqualTo(1);

      Receiver.Request request = receiver.requests().get(0);
      QRecord          delivery = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0);
      assertThat(delivery.getValueString("status")).isEqualTo("DELIVERED");
      assertThat(request.headers().get("webhook-id")).isEqualTo(delivery.getValueString("eventId"));
      assertThat(request.headers().get("webhook-signature")).startsWith("v1,");
      assertThat(request.headers().get("webhook-attempt")).isEqualTo("1");
      assertThat(request.headers().get("content-type")).isEqualTo("application/json");

      List<WebhookSecret> secrets = EndpointSecrets.signingKeys(endpointId).stream().map(EndpointSecrets.SigningKey::secret).toList();
      new WebhookVerifier(secrets, Duration.ofMinutes(5), WebhooksTestSupport.CLOCK).verify(request.headers(), request.body());

      JSONObject body = new JSONObject(request.text());
      assertThat(body.getString("id")).isEqualTo(delivery.getValueString("eventId"));
      assertThat(body.getString("type")).isEqualTo("person.inserted");
      assertThat(body.getString("source")).isEqualTo("qqq://test-app/table/person");
      assertThat(body.getJSONObject("data").getJSONObject("record").getString("firstName")).isEqualTo("Ada");

      List<QRecord> attempts = WebhooksTestSupport.all(WebhooksTables.ATTEMPT);
      assertThat(attempts).hasSize(1);
      assertThat(attempts.get(0).getValueString("outcome")).isEqualTo("SUCCEEDED");
      assertThat(attempts.get(0).getValueInteger("httpStatus")).isEqualTo(200);
      assertThat(attempts.get(0).getValueString("signedWith")).isNotBlank();
   }



   /*******************************************************************************
    ** A tampered body fails verification.
    *******************************************************************************/
   @Test
   void tamperedBodyFailsVerification() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));
      new DeliveryEngine().runOnce();

      Receiver.Request request = receiver.requests().get(0);
      List<WebhookSecret> secrets = EndpointSecrets.signingKeys(endpointId).stream().map(EndpointSecrets.SigningKey::secret).toList();
      byte[] tampered = request.text().replace("Ada", "Eve").getBytes();
      assertThatThrownBy(() -> new WebhookVerifier(secrets, Duration.ofMinutes(5), WebhooksTestSupport.CLOCK).verify(request.headers(), tampered))
         .isInstanceOf(WebhookVerificationException.class);
   }



   /*******************************************************************************
    ** Rotation: both secrets sign during the overlap; afterwards only the new.
    *******************************************************************************/
   @Test
   void rotationSignsWithBothSecretsDuringOverlap() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhookSecret oldSecret = EndpointSecrets.signingKeys(endpointId).get(0).secret();

      String newText = EndpointSecrets.rotate(endpointId, "rotation test");
      WebhookSecret newSecret = WebhookSecret.parse(newText);
      assertThat(newSecret).isNotEqualTo(oldSecret);

      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Grace"));
      new DeliveryEngine().runOnce();

      Receiver.Request request = receiver.requests().get(0);
      assertThat(request.headers().get("webhook-signature").split(" ")).hasSize(2);
      new WebhookVerifier(List.of(oldSecret), Duration.ofMinutes(5), WebhooksTestSupport.CLOCK).verify(request.headers(), request.body());
      new WebhookVerifier(List.of(newSecret), Duration.ofMinutes(5), WebhooksTestSupport.CLOCK).verify(request.headers(), request.body());

      ////////////////////////////////////////////////////////////////
      // after the overlap the old secret is retired and stops signing //
      ////////////////////////////////////////////////////////////////
      WebhooksTestSupport.CLOCK.advance(Duration.ofHours(25));
      assertThat(EndpointSecrets.retireExpired()).isEqualTo(1);
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Katherine"));
      new DeliveryEngine().runOnce();
      Receiver.Request later = receiver.requests().get(1);
      assertThat(later.headers().get("webhook-signature").split(" ")).hasSize(1);
      assertThatThrownBy(() -> new WebhookVerifier(List.of(oldSecret), Duration.ofMinutes(5), WebhooksTestSupport.CLOCK).verify(later.headers(), later.body()))
         .isInstanceOf(WebhookVerificationException.class);
   }



   /*******************************************************************************
    ** A retry keeps the same webhook-id (the idempotency key) and payload.
    *******************************************************************************/
   @Test
   void retryKeepsTheSameIdAndBody() throws Exception
   {
      receiver.respond(r -> Receiver.Response.of(500));
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));

      new DeliveryEngine().runOnce();
      QRecord delivery = WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0);
      assertThat(delivery.getValueString("status")).isEqualTo("RETRY_WAIT");

      receiver.respond(r -> Receiver.Response.of(204));
      WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(2));
      new DeliveryEngine().runOnce();

      List<Receiver.Request> requests = receiver.requests();
      assertThat(requests).hasSize(2);
      assertThat(requests.get(1).headers().get("webhook-id")).isEqualTo(requests.get(0).headers().get("webhook-id"));
      assertThat(requests.get(1).text()).isEqualTo(requests.get(0).text());
      assertThat(requests.get(1).headers().get("webhook-attempt")).isEqualTo("2");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0).getValueString("status")).isEqualTo("DELIVERED");
   }



   /*******************************************************************************
    ** Without a usable secret, deliveries wait (they are not attempted), unless
    ** the endpoint allows unsigned delivery.
    *******************************************************************************/
   @Test
   void noSecretMeansDeliveriesWait() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "plain").withValue("url", receiver.url("/hook"))
         .withValue("allowInternalTargets", true).withValue("internalAllowlist", "127.0.0.1")).getValueInteger("id");
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));

      DeliveryEngine.RunSummary summary = new DeliveryEngine().runOnce();
      assertThat(summary.getWaitingForSecret()).isEqualTo(1);
      assertThat(receiver.requests()).isEmpty();

      com.kingsrook.qbits.webhooks.support.SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", endpointId).withValue("allowUnsigned", true));
      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(1);
      assertThat(receiver.requests().get(0).headers()).containsKey("webhook-id").doesNotContainKey("webhook-signature");
   }



   /*******************************************************************************
    ** Test event type is registered by the producer.
    *******************************************************************************/
   @Test
   void testEventTypeIsRegistered()
   {
      assertThat(JsonUtils.toJson(WebhooksProcesses.TEST_EVENT_TYPE)).contains("webhooks.test");
   }
}
